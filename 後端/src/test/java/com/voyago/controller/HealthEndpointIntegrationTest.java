package com.voyago.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 雲端平台（Render、Docker healthcheck）靠 /actuator/health 判斷服務能不能上線。
 * 寄信是選用功能（沒設 MAIL_HOST 就安全跳過），不能因為沒有郵件伺服器就讓整個服務被判定 DOWN；
 * 資料庫連不上才應該是 DOWN。test profile 跟正式環境一樣沒設郵件伺服器，正好重現這個情境。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthEndpointIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void health_isUp_withoutMailServerConfigured() throws Exception {
        mvc.perform(get("/actuator/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("UP"));
    }
}
