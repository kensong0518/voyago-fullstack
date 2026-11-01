package com.voyago.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 單一網址部署：同一個服務提供前端網頁與 API（SpaWebConfig + SecurityConfig）。
 * 測試資源裡的 static/index.html、static/assets/app-test.js 代表 Docker 建置時放進去的前端產物。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaHostingIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void root_servesIndexHtml() throws Exception {
        mvc.perform(get("/"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("voyago-spa-test")));
    }

    @Test
    void frontendRoutes_fallBackToIndexHtml_withoutLogin() throws Exception {
        for (String path : new String[]{"/tours", "/tours/paris-loire", "/admin", "/dashboard", "/checkout/12"}) {
            mvc.perform(get(path))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("voyago-spa-test")))
               .andExpect(header().string("Cache-Control", containsString("no-cache")));
        }
    }

    @Test
    void hashedAssets_areServedWithLongCache() throws Exception {
        mvc.perform(get("/assets/app-test.js"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("voyago-asset-test")))
           .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
           .andExpect(header().string("Cache-Control", containsString("immutable")));
    }

    @Test
    void missingFiles_are404_notIndexHtml() throws Exception {
        mvc.perform(get("/assets/does-not-exist.js")).andExpect(status().isNotFound());
        mvc.perform(get("/logo-missing.png")).andExpect(status().isNotFound());
    }

    @Test
    void unknownApiPath_isNotSwallowedByFrontend() throws Exception {
        // 沒登入：被安全規則擋下（不是回一頁 HTML）
        mvc.perform(get("/api/no-such-endpoint"))
           .andExpect(status().is4xxClientError())
           .andExpect(content().string(not(containsString("voyago-spa-test"))));
    }

    @Test
    void apiDataStaysProtected_evenThoughAdminPageIsPublic() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isOk());              // 頁面殼：公開
        mvc.perform(get("/api/admin/stats")).andExpect(status().is4xxClientError()); // 資料：要登入
        mvc.perform(get("/api/bookings")).andExpect(status().is4xxClientError());
    }

    @Test
    void publicApiAndHealth_unchanged() throws Exception {
        mvc.perform(get("/api/routes")).andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void nonGetOutsideApi_isDenied() throws Exception {
        mvc.perform(post("/admin")).andExpect(status().is4xxClientError());
    }
}
