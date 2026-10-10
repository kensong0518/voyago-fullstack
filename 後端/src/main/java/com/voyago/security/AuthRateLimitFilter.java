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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 純記憶體 Token-Bucket：限制 /api/auth/login、/register、/google
 * 每個來源 IP 每分鐘 5 次嘗試。超量回 429 Too Many Requests + Retry-After。
 *
 * 注意：單機部署足夠；多節點部署請改用 Redis-backed bucket。
 *
 * 安全性：只有當直接連進來的客戶端（TCP 來源，即 remoteAddr）本身在信任清單
 * （{@code app.security.trusted-proxies}，例如雲端平台的負載平衡器位址）裡，
 * 才會採信它附上的 X-Forwarded-For；否則一律用 remoteAddr，避免任何人隨意帶一個
 * 假的 X-Forwarded-For header 就繞過限流。
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
    private final Set<String> trustedProxies;

    public AuthRateLimitFilter(@Value("${app.security.trusted-proxies:}") String trustedProxiesRaw) {
        this.trustedProxies = Arrays.stream(trustedProxiesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
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
        Bucket bucket = buckets.get(clientKey(req), k ->
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

    /**
     * 取得客戶端識別：只有當直接連線方（remoteAddr）是設定中的信任代理，才採信
     * 它附上的 X-Forwarded-For 第一個 IP；否則一律用 remoteAddr 本身。
     * 未設定 trusted-proxies 時，等同完全不信任 X-Forwarded-For（最安全的預設）。
     */
    private String clientKey(HttpServletRequest req) {
        String remoteAddr = req.getRemoteAddr();
        if (!trustedProxies.isEmpty() && trustedProxies.contains(remoteAddr)) {
            String fwd = req.getHeader("X-Forwarded-For");
            if (fwd != null && !fwd.isBlank()) {
                List<String> hops = Arrays.asList(fwd.split(","));
                return hops.get(0).trim();
            }
        }
        return remoteAddr;
    }
}
