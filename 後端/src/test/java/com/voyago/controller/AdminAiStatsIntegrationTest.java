package com.voyago.controller;

import com.voyago.entity.AiLog;
import com.voyago.entity.Member;
import com.voyago.repository.AiLogRepository;
import com.voyago.repository.MemberRepository;
import com.voyago.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 後台「AI 使用狀況」：只有後台人員看得到，數字來自 ai_log。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminAiStatsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired AiLogRepository logs;
    @Autowired JwtUtil jwtUtil;

    private String auth(String role) {
        Member m = new Member();
        m.setName("測試"); m.setEmail("ai-" + role + "-" + System.nanoTime() + "@test.com"); m.setPassword("x"); m.setRole(role);
        m = members.save(m);
        return "Bearer " + jwtUtil.generate(m.getId(), m.getEmail(), m.getRole());
    }

    private void log(String status, String reason, int ms, String picks, int rejected) {
        AiLog a = new AiLog();
        a.setPromptVersion("advisor-v1"); a.setModel("m"); a.setQueryText("想看雪");
        a.setStatus(status); a.setErrorReason(reason); a.setLatencyMs(ms); a.setPickSlugs(picks); a.setRejectedCount(rejected);
        logs.saveAndFlush(a);
    }

    @Test
    void memberAndAnonymousCannotRead() throws Exception {
        mvc.perform(get("/api/admin/ai-stats").header("Authorization", auth("MEMBER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/ai-stats")).andExpect(status().is4xxClientError());
    }

    @Test
    void staffSeesNumbersFromTheLog() throws Exception {
        long before = logs.count();
        log("OK", null, 800, "swiss-jungfrau", 0);
        log("ERROR", "TIMEOUT", 40000, "", 0);
        mvc.perform(get("/api/admin/ai-stats?days=7").header("Authorization", auth("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.calls").value((int) before + 2))
                .andExpect(jsonPath("$.errors").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.errorReasons.TIMEOUT").value(1))
                .andExpect(jsonPath("$.byDay.length()").value(7))
                .andExpect(jsonPath("$.recent[0].query").value("想看雪"));
    }

    @Test
    void daysIsClamped() throws Exception {
        mvc.perform(get("/api/admin/ai-stats?days=99999").header("Authorization", auth("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(90));
    }
}
