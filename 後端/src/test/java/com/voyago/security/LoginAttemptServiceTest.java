package com.voyago.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    /** 可以手動往前撥的時鐘。 */
    static class MovableClock extends Clock {
        final AtomicLong now = new AtomicLong(Instant.parse("2026-11-04T00:00:00Z").toEpochMilli());
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        void plusMinutes(long m) { now.addAndGet(m * 60_000); }
    }

    @Test
    void locksAfterFiveFailures_unlocksAfterWindow_successResets() {
        MovableClock clock = new MovableClock();
        LoginAttemptService s = new LoginAttemptService(5, 15, clock);
        for (int i = 0; i < 4; i++) assertFalse(s.recordFailure("A@x.com"));
        assertEquals(0, s.lockedSeconds("a@x.com"));
        assertTrue(s.recordFailure("a@x.com"));
        assertTrue(s.lockedSeconds("a@x.com") > 0);

        clock.plusMinutes(16);
        assertEquals(0, s.lockedSeconds("a@x.com"), "超過 15 分鐘自動解鎖");

        s.recordFailure("b@x.com");
        s.recordSuccess("b@x.com");
        for (int i = 0; i < 4; i++) s.recordFailure("b@x.com");
        assertEquals(0, s.lockedSeconds("b@x.com"), "登入成功會清掉之前的失敗次數");
    }
}
