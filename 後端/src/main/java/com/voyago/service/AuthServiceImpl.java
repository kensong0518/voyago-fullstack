package com.voyago.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.voyago.dto.*;
import com.voyago.entity.Member;
import com.voyago.repository.MemberDao;
import com.voyago.repository.MemberRepository;
import com.voyago.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final MemberDao memberDao;               // 查詢（Criteria）
    private final MemberRepository members;          // 寫入
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final GoogleVerifierService google;
    private final AuthenticationManager authenticationManager;

    public AuthServiceImpl(MemberDao memberDao, MemberRepository members, PasswordEncoder encoder,
                           JwtUtil jwtUtil, GoogleVerifierService google,
                           AuthenticationManager authenticationManager) {
        this.memberDao = memberDao;
        this.members = members;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
        this.google = google;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public AuthResponse register(RegisterRequest req) {
        if (memberDao.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "此 Email 已被註冊");
        }
        Member m = new Member();
        m.setName(req.name());
        m.setEmail(req.email());
        m.setPhone(req.phone());
        m.setPassword(encoder.encode(req.password()));
        m.setProvider("LOCAL");
        members.save(m);
        return toAuth(m);
    }

    @Override
    public AuthResponse login(LoginRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "帳號或密碼錯誤");
        }
        Member m = memberDao.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "帳號或密碼錯誤"));
        return toAuth(m);
    }

    @Override
    public AuthResponse googleLogin(GoogleLoginRequest req) {
        GoogleIdToken.Payload payload = google.verify(req.credential());
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google 驗證失敗");
        }
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        Member m = memberDao.findByEmail(email).orElseGet(() -> {
            Member nm = new Member();
            nm.setName(name != null ? name : email);
            nm.setEmail(email);
            nm.setProvider("GOOGLE");
            nm.setAvatarUrl(picture);
            return members.save(nm);
        });
        if (m.getAvatarUrl() == null && picture != null) {
            m.setAvatarUrl(picture);
            members.save(m);
        }
        return toAuth(m);
    }

    private AuthResponse toAuth(Member m) {
        String token = jwtUtil.generate(m.getId(), m.getEmail(), m.getRole());
        return new AuthResponse(token, MemberDto.from(m));
    }
}
