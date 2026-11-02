package com.voyago.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 顧問的用量上限，保護免費額度：
 *   - 每個來源（訪客 IP）每天 N 次（預設 10）；
 *   - 全站每天 M 次（預設 300），就算有人一直換 IP 也燒不光額度。
 * 「一天」以台灣時間計算，日期是 key 的一部分，換日自動歸零。
 *
 * 純記憶體：單一服務足夠；服務重啟會歸零（可接受，額度上限仍由 Google 端把關）。
 */
@Component
public class AiUsageLimiter {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final int perClientDaily;
    private final int globalDaily;
    private final Clock clock;
    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .maximumSize(200_000)
            .expireAfterWrite(26, TimeUnit.HOURS)
            .build();

    public AiUsageLimiter(@Value("${app.ai.daily-limit-per-client:10}") int perClientDaily,
                          @Value("${app.ai.daily-limit-global:300}") int globalDaily,
                          Clock clock) {
        this.perClientDaily = perClientDaily;
        this.globalDaily = globalDaily;
        this.clock = clock;
    }

    /**
     * 先占用一次額度；超過上限就丟 RATE_LIMITED／QUOTA_EXCEEDED（不占用）。
     * @return 這個來源今天還剩幾次
     */
    public int acquire(String clientKey) {
        LocalDate today = LocalDate.now(clock.withZone(TAIPEI));
        AtomicInteger global = counters.get("global|" + today, k -> new AtomicInteger());
        AtomicInteger mine = counters.get("client|" + clientKey + "|" + today, k -> new AtomicInteger());

        if (mine.incrementAndGet() > perClientDaily) {
            mine.decrementAndGet();
            throw new AiException(AiException.Reason.RATE_LIMITED, "client " + clientKey + " 達每日上限");
        }
        if (global.incrementAndGet() > globalDaily) {
            global.decrementAndGet();
            mine.decrementAndGet();
            throw new AiException(AiException.Reason.QUOTA_EXCEEDED, "全站達每日上限 " + globalDaily);
        }
        return perClientDaily - mine.get();
    }

    /** 這次呼叫因為我們這邊的問題沒有真的用到 AI（例如功能未啟用），把額度還回去。 */
    public void release(String clientKey) {
        LocalDate today = LocalDate.now(clock.withZone(TAIPEI));
        AtomicInteger mine = counters.getIfPresent("client|" + clientKey + "|" + today);
        AtomicInteger global = counters.getIfPresent("global|" + today);
        if (mine != null && mine.get() > 0) mine.decrementAndGet();
        if (global != null && global.get() > 0) global.decrementAndGet();
    }

    public int perClientDaily() {
        return perClientDaily;
    }
}
