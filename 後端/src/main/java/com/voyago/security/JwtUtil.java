package com.voyago.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * 簽發與驗證 JWT。token 帶 sub（會員 id）、role、ver（會員的 token_version）。
 * 簽章與到期由這裡驗；「帳號還在不在、版本對不對、角色是不是還一樣」由 {@link SessionValidator} 對資料庫驗。
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 至少 256 bits；太短的密鑰可以被暴力猜出，等於誰都能偽造 token
            throw new IllegalStateException("JWT_SECRET 至少要 32 個字元");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
    }

    /** 已驗過簽章與到期時間的 token 內容。 */
    public record Token(Long memberId, String email, String role, int version) {}

    public String generate(Long memberId, String email, String role) {
        return generate(memberId, email, role, 0);
    }

    public String generate(Long memberId, String email, String role, int version) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(memberId))
                .claim("email", email)
                .claim("role", role)
                .claim("ver", version)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 簽章正確且未過期才回傳內容；任何解析錯誤都當作無效，不丟例外。 */
    public Optional<Token> verify(String token) {
        try {
            Claims c = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            Number ver = c.get("ver", Number.class);
            return Optional.of(new Token(Long.valueOf(c.getSubject()), (String) c.get("email"), (String) c.get("role"),
                    ver == null ? 0 : ver.intValue()));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Long getMemberId(String token) {
        return verify(token).map(Token::memberId).orElse(null);
    }

    public String getRole(String token) {
        return verify(token).map(Token::role).orElse(null);
    }

    public boolean isValid(String token) {
        return verify(token).isPresent();
    }
}
