package com.voyago.ai;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AiUsageLimiterTest {

    /** 可以撥動的時鐘：測換日歸零。 */
    static final class MovableClock extends Clock {
        final AtomicReference<Instant> now;
        MovableClock(String iso) { now = new AtomicReference<>(Instant.parse(iso)); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now.get(), zone); }
        @Override public Instant instant() { return now.get(); }
    }

    @Test
    void perClientLimit_blocksAfterN_andOtherClientsUnaffected() {
        var limiter = new AiUsageLimiter(3, 100, new MovableClock("2026-11-02T03:00:00Z"));
        assertEquals(2, limiter.acquire("a"));
        assertEquals(1, limiter.acquire("a"));
        assertEquals(0, limiter.acquire("a"));
        AiException ex = assertThrows(AiException.class, () -> limiter.acquire("a"));
        assertEquals(AiException.Reason.RATE_LIMITED, ex.reason());
        assertEquals(2, limiter.acquire("b"));
    }

    @Test
    void globalLimit_protectsQuotaAcrossClients() {
        var limiter = new AiUsageLimiter(10, 2, new MovableClock("2026-11-02T03:00:00Z"));
        limiter.acquire("a");
        limiter.acquire("b");
        AiException ex = assertThrows(AiException.class, () -> limiter.acquire("c"));
        assertEquals(AiException.Reason.QUOTA_EXCEEDED, ex.reason());
        // 騰出一個全站名額後，c 應該還有完整的 10 次——被全站上限擋下的那次不能扣到 c 的個人次數
        limiter.release("a");
        assertEquals(9, limiter.acquire("c"));
    }

    @Test
    void newTaipeiDay_resetsCounts() {
        var clock = new MovableClock("2026-11-02T15:30:00Z");   // 台灣 23:30
        var limiter = new AiUsageLimiter(1, 100, clock);
        limiter.acquire("a");
        assertThrows(AiException.class, () -> limiter.acquire("a"));
        clock.now.set(Instant.parse("2026-11-02T16:01:00Z"));    // 台灣隔天 00:01
        assertEquals(0, limiter.acquire("a"));
    }

    @Test
    void release_givesTheAttemptBack() {
        var limiter = new AiUsageLimiter(1, 100, new MovableClock("2026-11-02T03:00:00Z"));
        limiter.acquire("a");
        limiter.release("a");
        assertEquals(0, limiter.acquire("a"));
    }
}
