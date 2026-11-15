package com.voyago.service;

import com.voyago.dto.AiStatsDto;
import com.voyago.entity.AiLog;
import com.voyago.repository.AiLogRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiLogServiceTest {

    private static AiLog row(String version, String status, String reason, int ms, String picks, int rejected, int daysAgo) {
        AiLog a = new AiLog();
        a.setPromptVersion(version); a.setModel("m"); a.setQueryText("q");
        a.setStatus(status); a.setErrorReason(reason); a.setLatencyMs(ms);
        a.setPickSlugs(picks); a.setRejectedCount(rejected);
        a.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
        return a;
    }

    @Test
    void summarize_countsErrorsLatencyAndHallucinations() {
        var rows = List.of(
                row("advisor-v1", "OK", null, 1000, "a,b", 0, 0),
                row("advisor-v1", "OK", null, 3000, "c", 1, 0),
                row("advisor-v1", "ERROR", "TIMEOUT", 40000, "", 0, 1),
                row("advisor-v2", "OK", null, 500, "", 0, 2));
        AiStatsDto s = AiLogService.summarize(7, rows);

        assertEquals(4, s.calls());
        assertEquals(1, s.errors());
        assertEquals(0.25, s.errorRate(), 1e-9);
        assertEquals(3, s.picks());
        assertEquals(1, s.rejected());
        // 平均與 p95 只算成功的呼叫：失敗那筆 40 秒不該把延遲拉高
        assertEquals(1500, s.avgLatencyMs());
        assertEquals(3000, s.p95LatencyMs());
        assertEquals(1L, s.errorReasons().get("TIMEOUT"));
        assertEquals(2, s.byVersion().size());
        assertEquals("advisor-v1", s.byVersion().get(0).version());
        assertEquals(3, s.byVersion().get(0).calls());
        assertEquals(7, s.byDay().size(), "沒有呼叫的日子也要補 0");
        assertEquals(2, s.byDay().get(6).calls());
        assertFalse(s.truncated());
    }

    @Test
    void summarize_emptyIsAllZero() {
        AiStatsDto s = AiLogService.summarize(7, List.of());
        assertEquals(0, s.calls());
        assertEquals(0, s.errorRate());
        assertEquals(0, s.avgLatencyMs());
        assertEquals(0, s.p95LatencyMs());
        assertEquals(7, s.byDay().size());
    }

    @Test
    void record_swallowsDatabaseFailure() {
        AiLogRepository repo = mock(AiLogRepository.class);
        when(repo.save(any())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> new AiLogService(repo).record(row("v", "OK", null, 1, "", 0, 0)));
    }

    @Test
    void purge_deletesOnlyOldRows() {
        AiLogRepository repo = mock(AiLogRepository.class);
        when(repo.deleteOlderThan(any())).thenReturn(3);
        new AiLogService(repo).purgeOld();
        verify(repo).deleteOlderThan(argThat(t -> t.isBefore(LocalDateTime.now().minusDays(89))
                && t.isAfter(LocalDateTime.now().minusDays(91))));
    }
}
