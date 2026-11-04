package com.voyago.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 帳號層級的暴力破解防護：同一個 Email 在 15 分鐘內密碼錯 5 次，就鎖住 15 分鐘。
 *
 * IP 層級的限流（AuthRateLimitFilter）擋不住「換很多 IP 猜同一個帳號的密碼」，這裡補上。
 * 不管這個 Email 有沒有註冊都照樣計算、照樣鎖，回應也都一樣——不能讓人藉由「有沒有被鎖」反推哪些 Email 是會員。
 */
@Component
public class LoginAttemptService {

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Cache<String, Deque<Long>> failures = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    public LoginAttemptService(@Value("${app.security.login-max-failures:5}") int maxFailures,
                               @Value("${app.security.login-lock-minutes:15}") int lockMinutes,
                               Clock clock) {
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(lockMinutes);
        this.clock = clock;
    }

    /** 還要等幾秒才能再試；0 = 沒被鎖。 */
    public long lockedSeconds(String email) {
        Deque<Long> q = failures.getIfPresent(key(email));
        if (q == null) return 0;
        synchronized (q) {
            prune(q);
            if (q.size() < maxFailures) return 0;
            long unlockAt = q.peekFirst() + window.toMillis();
            return Math.max(1, (unlockAt - clock.millis() + 999) / 1000);
        }
    }

    /** 記一次失敗；回傳這次之後是否進入鎖定。 */
    public boolean recordFailure(String email) {
        Deque<Long> q = failures.get(key(email), k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(clock.millis());
            return q.size() >= maxFailures;
        }
    }

    public void recordSuccess(String email) {
        failures.invalidate(key(email));
    }

    private void prune(Deque<Long> q) {
        long from = clock.millis() - window.toMillis();
        while (!q.isEmpty() && q.peekFirst() < from) q.pollFirst();
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
