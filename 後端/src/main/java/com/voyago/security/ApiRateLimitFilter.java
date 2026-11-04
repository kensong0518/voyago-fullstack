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
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 全站 API 限流（每個訪客 IP）：防止有人寫程式大量抓取資料（例如把所有行程、評價一次爬走）
 * 或狂打寫入端點。一般瀏覽完全碰不到這個上限。
 *   - 所有 /api 請求：每分鐘 {@code app.security.api-per-minute}（預設 120）次；
 *   - 其中寫入（POST/PUT/PATCH/DELETE）：每分鐘 {@code app.security.api-writes-per-minute}（預設 30）次。
 * 登入／註冊另有更嚴格的 AuthRateLimitFilter，AI 顧問另有每日額度，兩者不重複計算。
 */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final int perMinute;
    private final int writesPerMinute;
    private final ClientIpResolver clientIp;
    private final ObjectMapper json = new ObjectMapper();
    private final Cache<String, Bucket> all = Caffeine.newBuilder()
            .maximumSize(100_000).expireAfterAccess(10, TimeUnit.MINUTES).build();
    private final Cache<String, Bucket> writes = Caffeine.newBuilder()
            .maximumSize(100_000).expireAfterAccess(10, TimeUnit.MINUTES).build();

    public ApiRateLimitFilter(ClientIpResolver clientIp,
                              @Value("${app.security.api-per-minute:120}") int perMinute,
                              @Value("${app.security.api-writes-per-minute:30}") int writesPerMinute) {
        this.clientIp = clientIp;
        this.perMinute = perMinute;
        this.writesPerMinute = writesPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        return !p.startsWith("/api/")
                || p.equals("/api/auth/login") || p.equals("/api/auth/register") || p.equals("/api/auth/google")
                || p.equals("/api/ai/advisor");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String ip = clientIp.resolve(req);
        ConsumptionProbe p = all.get(ip, k -> bucket(perMinute)).tryConsumeAndReturnRemaining(1);
        if (!p.isConsumed()) { reject(res, p); return; }
        if (isWrite(req.getMethod())) {
            ConsumptionProbe w = writes.get(ip, k -> bucket(writesPerMinute)).tryConsumeAndReturnRemaining(1);
            if (!w.isConsumed()) { reject(res, w); return; }
        }
        chain.doFilter(req, res);
    }

    private static boolean isWrite(String m) {
        return "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m) || "DELETE".equals(m);
    }

    private static Bucket bucket(int perMinute) {
        return Bucket.builder().addLimit(Bandwidth.simple(perMinute, Duration.ofMinutes(1))).build();
    }

    private void reject(HttpServletResponse res, ConsumptionProbe probe) throws IOException {
        long retry = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After", String.valueOf(retry));
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getWriter(), Map.of("error", "請求太頻繁，請 " + retry + " 秒後再試"));
    }
}
