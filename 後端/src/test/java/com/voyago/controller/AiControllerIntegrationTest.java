package com.voyago.controller;

import com.voyago.ai.LlmClient;
import com.voyago.entity.Route;
import com.voyago.repository.RouteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** AI 顧問 API：免登入、用量上限、快取、未啟用與輸入驗證。模型用假的實作，不打真的 Gemini。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.ai.daily-limit-per-client=2", "app.ai.daily-limit-global=1000"})
class AiControllerIntegrationTest {

    static final AtomicBoolean ENABLED = new AtomicBoolean(true);
    static final AtomicInteger CALLS = new AtomicInteger();
    static volatile String slugToPick = "x";

    @TestConfiguration
    static class FakeLlm {
        @Bean @Primary
        LlmClient fakeLlmClient() {
            return new LlmClient() {
                @Override public boolean isConfigured() { return ENABLED.get(); }
                @Override public String generateJson(String s, String u, Map<String, Object> schema) {
                    CALLS.incrementAndGet();
                    return "{\"message\":\"推薦給你\",\"picks\":[{\"slug\":\"" + slugToPick + "\",\"reason\":\"符合需求\"}]}";
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired RouteRepository routes;
    @Autowired CacheManager caches;
    private Route seeded;

    @BeforeEach
    void seed() {
        ENABLED.set(true);
        Route r = new Route();
        r.setSlug("ai-test-" + System.nanoTime());
        r.setName("AI 測試行程");
        r.setSummary("s"); r.setDescription("d"); r.setLocation("l"); r.setCountry("c");
        r.setDays(5); r.setPrice(12345); r.setImageUrl("https://example.com/a.jpg");
        seeded = routes.save(r);   // 不包交易：行程清單走 MyBatis 讀取，要真的 commit
        slugToPick = seeded.getSlug();
        // 行程清單有快取；不清掉的話，AI 拿到的可能是前一個測試的清單，看不到剛新增的行程
        caches.getCacheNames().forEach(n -> caches.getCache(n).clear());
    }

    @AfterEach
    void cleanup() {
        routes.deleteById(seeded.getId());
    }

    private static MockHttpServletRequestBuilder ask(String query, String ip) {
        return post("/api/ai/advisor").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + query + "\"}")
                .with(r -> { r.setRemoteAddr(ip); return r; });
    }

    @Test
    void status_isPublic() throws Exception {
        mvc.perform(get("/api/ai/status"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.enabled").value(true))
           .andExpect(jsonPath("$.dailyLimit").value(2));
    }

    @Test
    void advisor_worksWithoutLogin_andReturnsDatabaseRoute() throws Exception {
        mvc.perform(ask("想要五天的行程 " + System.nanoTime(), "203.0.113.1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.message").value("推薦給你"))
           .andExpect(jsonPath("$.recommendations[0].route.slug").value(seeded.getSlug()))
           .andExpect(jsonPath("$.recommendations[0].route.price").value(12345))
           .andExpect(jsonPath("$.recommendations[0].reason").value("符合需求"))
           .andExpect(jsonPath("$.remainingToday").value(1))
           .andExpect(jsonPath("$.cached").value(false));
    }

    @Test
    void perVisitorDailyLimit_returns429_withFriendlyMessage() throws Exception {
        String ip = "203.0.113.2";
        mvc.perform(ask("問題一 " + System.nanoTime(), ip)).andExpect(status().isOk());
        mvc.perform(ask("問題二 " + System.nanoTime(), ip)).andExpect(status().isOk());
        mvc.perform(ask("問題三 " + System.nanoTime(), ip))
           .andExpect(status().isTooManyRequests())
           .andExpect(jsonPath("$.error").value("你今天的 AI 提問次數已用完，明天再來試試"));
        mvc.perform(ask("別人的問題 " + System.nanoTime(), "203.0.113.3")).andExpect(status().isOk());
    }

    @Test
    void repeatedQuestion_isCached_andDoesNotUseQuota() throws Exception {
        String q = "重複的問題 " + System.nanoTime();
        String ip = "203.0.113.4";
        int before = CALLS.get();
        mvc.perform(ask(q, ip)).andExpect(jsonPath("$.cached").value(false));
        for (int i = 0; i < 3; i++) {
            mvc.perform(ask(q, ip)).andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true));
        }
        mvc.perform(ask("另一個 " + System.nanoTime(), ip)).andExpect(status().isOk());   // 個人還有 1 次
        org.junit.jupiter.api.Assertions.assertEquals(before + 2, CALLS.get());
    }

    @Test
    void notConfigured_returns503_andDoesNotUseQuota() throws Exception {
        ENABLED.set(false);
        String ip = "203.0.113.5";
        for (int i = 0; i < 3; i++) {
            mvc.perform(ask("看雪 " + i, ip))
               .andExpect(status().isServiceUnavailable())
               .andExpect(jsonPath("$.error").value("AI 行程顧問尚未啟用，請先瀏覽下方的行程列表"));
        }
        ENABLED.set(true);
        mvc.perform(ask("啟用後 " + System.nanoTime(), ip)).andExpect(jsonPath("$.remainingToday").value(1));
    }

    @Test
    void tooShortOrMissingQuery_isRejected() throws Exception {
        mvc.perform(ask("a", "203.0.113.6")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/advisor").contentType(MediaType.APPLICATION_JSON).content("{}"))
           .andExpect(status().isBadRequest());
    }
}
