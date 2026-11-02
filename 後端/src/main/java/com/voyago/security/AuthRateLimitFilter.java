package com.voyago.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 純記憶體 Token-Bucket：限制 /api/auth/login、/register、/google
 * 每個來源 IP 每分鐘 5 次嘗試。超量回 429 Too Many Requests + Retry-After。
 *
 * 注意：單機部署足夠；多節點部署請改用 Redis-backed bucket。
 *
 * 訪客 IP 由 {@link ClientIpResolver} 判斷：只有連線方是可信任的代理，才採信它轉送的來源 IP，
 * 避免任何人隨意帶一個假的 X-Forwarded-For 就繞過限流；在雲端代理後面也不會全站共用一個額度。
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int CAPACITY = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    // maximumSize + expireAfterAccess：避免偽造大量來源把 bucket 表灌爆、吃光記憶體
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private final ClientIpResolver clientIp;

    public AuthRateLimitFilter(ClientIpResolver clientIp) {
        this.clientIp = clientIp;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 只攔登入/註冊/Google 三個寫入端點；/api/auth/me 不限速
        return !(path.equals("/api/auth/login")
              || path.equals("/api/auth/register")
              || path.equals("/api/auth/google"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Bucket bucket = buckets.get(clientIp.resolve(req), k ->
                Bucket.builder().addLimit(Bandwidth.simple(CAPACITY, WINDOW)).build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            res.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(req, res);
            return;
        }

        long retryAfterSec = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After", String.valueOf(retryAfterSec));
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getWriter(),
                Map.of("error", "嘗試次數過多，請稍後再試（" + retryAfterSec + " 秒後）"));
    }
}
