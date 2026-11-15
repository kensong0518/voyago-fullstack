package com.voyago.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyago.ai.AiException;
import com.voyago.ai.LlmClient;
import com.voyago.dto.RouteDto;
import com.voyago.entity.AiLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** AI 顧問每次實際呼叫模型都要留紀錄：成功記幻覺次數，失敗記原因，快取命中不重複記。 */
class TripAdvisorLoggingTest {

    private final AtomicReference<String> answer = new AtomicReference<>();
    private final AtomicReference<AiException> failure = new AtomicReference<>();
    private AiLogService log;
    private TripAdvisorService advisor;

    @BeforeEach
    void setUp() {
        RouteService routes = mock(RouteService.class);
        var r = new RouteDto(1L, "swiss-jungfrau", "瑞士少女峰", "摘要", "d", "地點", "瑞士", 7, 98000, 0.0, 0, "u", "雪景", false);
        when(routes.list(any(), any(), any())).thenReturn(List.of(r));
        LlmClient fake = new LlmClient() {
            @Override public boolean isConfigured() { return true; }
            @Override public String model() { return "fake-model"; }
            @Override public String generateJson(String s, String u, Map<String, Object> sc) {
                if (failure.get() != null) throw failure.get();
                return answer.get();
            }
        };
        log = mock(AiLogService.class);
        advisor = new TripAdvisorService(fake, routes, new ObjectMapper(), log);
    }

    private AiLog captured() {
        ArgumentCaptor<AiLog> c = ArgumentCaptor.forClass(AiLog.class);
        verify(log).record(c.capture());
        return c.getValue();
    }

    @Test
    void success_recordsVersionModelPicksAndHallucinations() {
        answer.set("{\"message\":\"m\",\"picks\":[{\"slug\":\"swiss-jungfrau\",\"reason\":\"r\"},{\"slug\":\"atlantis\",\"reason\":\"編的\"}]}");
        advisor.advise("想看雪");
        AiLog e = captured();
        assertEquals("OK", e.getStatus());
        assertEquals(TripAdvisorService.PROMPT_VERSION, e.getPromptVersion());
        assertEquals("fake-model", e.getModel());
        assertEquals("想看雪", e.getQueryText());
        assertEquals("swiss-jungfrau", e.getPickSlugs());
        assertEquals(1, e.getRejectedCount(), "atlantis 是模型編的，要記一次");
        assertNull(e.getErrorReason());
    }

    @Test
    void modelFailure_recordsReason_andStillThrows() {
        failure.set(new AiException(AiException.Reason.TIMEOUT, "逾時"));
        assertThrows(AiException.class, () -> advisor.advise("想看雪"));
        AiLog e = captured();
        assertEquals("ERROR", e.getStatus());
        assertEquals("TIMEOUT", e.getErrorReason());
    }

    @Test
    void unparsableAnswer_isRecordedAsError() {
        answer.set("這不是 JSON");
        assertThrows(AiException.class, () -> advisor.advise("想看雪"));
        assertEquals("BAD_RESPONSE", captured().getErrorReason());
    }

    @Test
    void cacheHit_isNotLoggedAgain() {
        answer.set("{\"message\":\"m\",\"picks\":[]}");
        advisor.advise("同一個問題");
        advisor.advise("同一個問題");
        verify(log, times(1)).record(any());
    }

    @Test
    void longQuery_isCutToColumnLength() {
        answer.set("{\"message\":\"m\",\"picks\":[]}");
        advisor.advise("字".repeat(500));
        assertTrue(captured().getQueryText().length() <= 200);
    }

    @Test
    void promptComesFromVersionedFile_withCatalogFilled() {
        answer.set("{\"message\":\"m\",\"picks\":[]}");
        var r = new RouteDto(1L, "swiss-jungfrau", "瑞士少女峰", "摘要", "d", "地點", "瑞士", 7, 98000, 0.0, 0, "u", "雪景", false);
        String sys = advisor.systemInstruction(List.of(r));
        assertTrue(sys.contains("最多推薦 3 個"));
        assertTrue(sys.contains("\"slug\":\"swiss-jungfrau\""));
        assertFalse(sys.contains("{{"), "樣板變數都要被替換掉");
    }
}
