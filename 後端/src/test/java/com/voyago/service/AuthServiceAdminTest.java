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
import static org.mockito.Mockito.*;

/** 誰能進後台：只有 ADMIN_EMAILS 名單內、且經 Google 驗證過 Email 的帳號。 */
class AuthServiceAdminTest {

    private MemberDao memberDao;
    private MemberRepository members;
    private GoogleVerifierService google;
    private AuthServiceImpl auth;

    @BeforeEach
    void setUp() {
        memberDao = mock(MemberDao.class);
        members = mock(MemberRepository.class);
        google = mock(GoogleVerifierService.class);
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.generate(any(), anyString(), anyString())).thenReturn("token");
        when(members.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
        auth = new AuthServiceImpl(memberDao, members, mock(PasswordEncoder.class), jwt, google,
                mock(AuthenticationManager.class), new AdminEmailPolicy(" Boss@Voyago.test , second@voyago.test"));
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
    void adminEmail_notVerifiedByGoogle_staysMember() {
        googleReturns("boss@voyago.test", false);
        assertEquals("MEMBER", auth.googleLogin(new GoogleLoginRequest("cred")).user().role());
    }

    @Test
    void registerWithAdminEmail_isRefused() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                auth.register(new RegisterRequest("搶註者", "boss@voyago.test", "password123", "")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(members, never()).save(any());
    }
}
