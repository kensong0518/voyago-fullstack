package com.voyago.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.voyago.dto.AuthResponse;
import com.voyago.dto.GoogleLoginRequest;
import com.voyago.dto.RegisterRequest;
import com.voyago.entity.Member;
import com.voyago.repository.MemberDao;
import com.voyago.repository.MemberRepository;
import com.voyago.security.AdminEmailPolicy;
import com.voyago.security.JwtUtil;
import com.voyago.security.LoginAttemptService;
import com.voyago.security.SessionValidator;
import com.voyago.dto.LoginRequest;
import org.springframework.security.authentication.BadCredentialsException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** 誰能進後台：只有 ADMIN_EMAILS 名單內、且經 Google 驗證過 Email 的帳號。 */
class AuthServiceAdminTest {

    private MemberDao memberDao;
    private MemberRepository members;
    private GoogleVerifierService google;
    private AuthServiceImpl auth;
    private AuthenticationManager authManager;
    private SessionValidator sessions;

    @BeforeEach
    void setUp() {
        memberDao = mock(MemberDao.class);
        members = mock(MemberRepository.class);
        google = mock(GoogleVerifierService.class);
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.generate(any(), anyString(), anyString(), anyInt())).thenReturn("token");
        when(members.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
        when(members.saveAndFlush(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
        authManager = mock(AuthenticationManager.class);
        sessions = mock(SessionValidator.class);
        LoginAttemptService attempts = new LoginAttemptService(5, 15,
                Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC));
        auth = new AuthServiceImpl(memberDao, members, mock(PasswordEncoder.class), jwt, google,
                authManager, new AdminEmailPolicy(" Boss@Voyago.test , second@voyago.test"),
                attempts, sessions, mock(AuditService.class));
    }

    private void googleReturns(String email, boolean verified) {
        GoogleIdToken.Payload p = new GoogleIdToken.Payload();
        p.setEmail(email);
        p.setEmailVerified(verified);
        p.set("name", "Someone");
        when(google.verify("cred")).thenReturn(p);
        when(memberDao.findByEmail(email)).thenReturn(Optional.empty());
    }

    @Test
    void adminEmail_viaGoogle_becomesStaff() {
        googleReturns("boss@voyago.test", true);
        AuthResponse res = auth.googleLogin(new GoogleLoginRequest("cred"));
        assertEquals("STAFF", res.user().role());
    }

    @Test
    void adminEmail_matchIsCaseInsensitive() {
        googleReturns("SECOND@voyago.test", true);
        assertEquals("STAFF", auth.googleLogin(new GoogleLoginRequest("cred")).user().role());
    }

    @Test
    void otherGoogleAccount_staysMember() {
        googleReturns("visitor@gmail.com", true);
        assertEquals("MEMBER", auth.googleLogin(new GoogleLoginRequest("cred")).user().role());
    }

    @Test
    void unverifiedGoogleEmail_isRejectedEntirely() {
        // 未經 Google 驗證的 Email 不只不能當管理員，連登入同 Email 的會員帳號都不行（否則可冒用他人帳號）
        googleReturns("boss@voyago.test", false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auth.googleLogin(new GoogleLoginRequest("cred")));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(members, never()).save(any());
    }

    @Test
    void googleLogin_reclaimsAccountSquattedWithPassword() {
        // 有人先用別人的 Email 帳密註冊（沒驗證過）；真正的擁有者用 Google 登入時，舊密碼作廢、舊 token 全部失效
        Member squatted = new Member();
        squatted.setId(9L); squatted.setEmail("victim@gmail.com"); squatted.setName("搶註者");
        squatted.setPassword("$2a$hash"); squatted.setProvider("LOCAL"); squatted.setEmailVerified(false);
        squatted.setTokenVersion(3);
        googleReturns("victim@gmail.com", true);
        when(memberDao.findByEmail("victim@gmail.com")).thenReturn(Optional.of(squatted));

        auth.googleLogin(new GoogleLoginRequest("cred"));

        assertNull(squatted.getPassword());
        assertTrue(squatted.getEmailVerified());
        assertEquals(4, squatted.getTokenVersion());
        verify(sessions, atLeastOnce()).evict(9L);
    }

    @Test
    void verifiedAccount_isNotReset() {
        Member m = new Member();
        m.setId(5L); m.setEmail("ok@gmail.com"); m.setName("OK"); m.setPassword("$2a$hash");
        m.setEmailVerified(true); m.setTokenVersion(2);
        googleReturns("ok@gmail.com", true);
        when(memberDao.findByEmail("ok@gmail.com")).thenReturn(Optional.of(m));
        auth.googleLogin(new GoogleLoginRequest("cred"));
        assertEquals("$2a$hash", m.getPassword());
        assertEquals(2, m.getTokenVersion());
    }

    @Test
    void fiveWrongPasswords_lockTheAccount_evenWithCorrectPasswordAfterwards() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        for (int i = 1; i <= 4; i++) {
            assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                    () -> auth.login(new LoginRequest("target@x.com", "wrong"))).getStatusCode(), "第 " + i + " 次");
        }
        ResponseStatusException fifth = assertThrows(ResponseStatusException.class,
                () -> auth.login(new LoginRequest("TARGET@x.com", "wrong")));   // 大小寫不同也算同一個帳號
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, fifth.getStatusCode());

        reset(authManager);   // 之後就算密碼對了，鎖定期間也不比對
        ResponseStatusException locked = assertThrows(ResponseStatusException.class,
                () -> auth.login(new LoginRequest("target@x.com", "right")));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, locked.getStatusCode());
        verify(authManager, never()).authenticate(any());
    }

    @Test
    void registerWithAdminEmail_isRefused() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                auth.register(new RegisterRequest("搶註者", "boss@voyago.test", "password123", "")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(members, never()).save(any());
    }
}
