package com.voyago.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.voyago.dto.*;
import com.voyago.entity.Member;
import com.voyago.repository.MemberDao;
import com.voyago.repository.MemberRepository;
import com.voyago.security.AdminEmailPolicy;
import com.voyago.security.JwtUtil;
import com.voyago.security.LoginAttemptService;
import com.voyago.security.SessionValidator;
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
    private final AdminEmailPolicy adminPolicy;
    private final LoginAttemptService attempts;
    private final SessionValidator sessions;
    private final AuditService audit;

    public AuthServiceImpl(MemberDao memberDao, MemberRepository members, PasswordEncoder encoder,
                           JwtUtil jwtUtil, GoogleVerifierService google,
                           AuthenticationManager authenticationManager, AdminEmailPolicy adminPolicy,
                           LoginAttemptService attempts, SessionValidator sessions, AuditService audit) {
        this.attempts = attempts;
        this.sessions = sessions;
        this.audit = audit;
        this.memberDao = memberDao;
        this.members = members;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
        this.google = google;
        this.authenticationManager = authenticationManager;
        this.adminPolicy = adminPolicy;
    }

    @Override
    public AuthResponse register(RegisterRequest req) {
        // 管理員 Email 只能用 Google 登入：帳密註冊不驗證 Email 所有權，不能讓人搶註取得後台
        if (adminPolicy.isAdmin(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "此 Email 請使用 Google 登入");
        }
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
        // 帳號層級鎖定：先檢查，鎖定中連密碼都不比對（不然鎖定期間還是能繼續猜）
        long wait = attempts.lockedSeconds(req.email());
        if (wait > 0) {
            audit.security("LOGIN_BLOCKED", null, req.email(), "帳號鎖定中仍嘗試登入");
            throw locked(wait);
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (AuthenticationException e) {
            boolean nowLocked = attempts.recordFailure(req.email());
            audit.security(nowLocked ? "LOGIN_LOCKED" : "LOGIN_FAILED", null, req.email(),
                    nowLocked ? "密碼連續錯誤，帳號暫時鎖定" : "密碼錯誤或帳號不存在");
            if (nowLocked) throw locked(attempts.lockedSeconds(req.email()));
            // 帳號不存在與密碼錯誤回同一句話，不讓人用登入頁測試哪些 Email 是會員
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "帳號或密碼錯誤");
        }
        Member m = memberDao.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "帳號或密碼錯誤"));
        attempts.recordSuccess(req.email());
        audit.security("LOGIN_SUCCESS", m.getId(), m.getEmail(), "帳號密碼登入");
        return toAuth(m);
    }

    private static ResponseStatusException locked(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "登入失敗次數過多，帳號已暫時鎖定，請約 " + minutes + " 分鐘後再試");
    }

    @Override
    public void logoutAllDevices(Long memberId) {
        Member m = members.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "請先登入"));
        revokeAllTokens(m);
        audit.security("LOGOUT_ALL", m.getId(), m.getEmail(), "登出所有裝置");
    }

    /** token_version +1：這個帳號之前發出的所有 token 立即失效。 */
    private void revokeAllTokens(Member m) {
        m.setTokenVersion((m.getTokenVersion() == null ? 0 : m.getTokenVersion()) + 1);
        members.saveAndFlush(m);
        sessions.evict(m.getId());
    }

    @Override
    public AuthResponse googleLogin(GoogleLoginRequest req) {
        GoogleIdToken.Payload payload = google.verify(req.credential());
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google 驗證失敗");
        }
        String email = payload.getEmail();
        // Google 沒確認過所有權的 Email 一律不收：否則有人用未驗證的 Email 開 Google 帳號，就能登入同 Email 的會員帳號
        if (email == null || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            audit.security("LOGIN_FAILED", null, email, "Google 帳號的 Email 未經驗證");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "這個 Google 帳號的 Email 尚未驗證，無法登入");
        }
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        Member m = memberDao.findByEmail(email).orElseGet(() -> {
            Member nm = new Member();
            nm.setName(name != null ? name : email);
            nm.setEmail(email);
            nm.setProvider("GOOGLE");
            nm.setAvatarUrl(picture);
            nm.setEmailVerified(true);
            return members.save(nm);
        });
        boolean changed = false;
        if (!Boolean.TRUE.equals(m.getEmailVerified())) {
            // 這個帳號是用帳密註冊的，當初沒驗證 Email 是不是本人的。現在 Google 證明了 Email 的真正擁有者，
            // 可能是別人先拿這個 Email 搶註：把舊密碼作廢、讓所有舊 token 失效，帳號回到真正的擁有者手上。
            // 之後擁有者用 Google 登入即可。
            boolean hadPassword = m.getPassword() != null;
            m.setPassword(null);
            m.setEmailVerified(true);
            revokeAllTokens(m);
            changed = true;
            if (hadPassword) {
                audit.security("ACCOUNT_RECLAIMED", m.getId(), email, "Google 驗證 Email 擁有者後，作廢未驗證的密碼並登出其他裝置");
            }
        }
        if (m.getAvatarUrl() == null && picture != null) {
            m.setAvatarUrl(picture);
            changed = true;
        }
        // 名單內且 Google 確認過 Email 所有權 → 後台管理員
        if (adminPolicy.isAdmin(email) && !"STAFF".equals(m.getRole())) {
            m.setRole("STAFF");
            changed = true;
        }
        if (changed) {
            members.save(m);
            sessions.evict(m.getId());
        }
        attempts.recordSuccess(email);
        audit.security("LOGIN_SUCCESS", m.getId(), email, "Google 登入" + ("STAFF".equals(m.getRole()) ? "（管理員）" : ""));
        return toAuth(m);
    }

    private AuthResponse toAuth(Member m) {
        String token = jwtUtil.generate(m.getId(), m.getEmail(), m.getRole(),
                m.getTokenVersion() == null ? 0 : m.getTokenVersion());
        return new AuthResponse(token, MemberDto.from(m));
    }
}
