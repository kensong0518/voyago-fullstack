package com.voyago.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 部署流程靠 /actuator/info 的 app.commit 確認線上是不是新版，所以它必須免登入、且一定有這個欄位。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InfoEndpointIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void infoIsPublic_andReportsTheDeployedCommit() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.commit").exists());
    }
}
