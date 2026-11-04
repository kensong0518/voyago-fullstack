package com.voyago.controller;

import com.voyago.entity.Member;
import com.voyago.repository.MemberRepository;
import com.voyago.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 防冒用：token 撤銷、以資料庫為準的角色、登入鎖定與資安紀錄、瀏覽器防護標頭。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountSecurityIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired JwtUtil jwtUtil;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

    private Member member(String role) {
        Member m = new Member();
        m.setName("安全測試");
        m.setEmail("sec-" + System.nanoTime() + "@test.com");
        m.setPassword(encoder.encode("password123"));
        m.setRole(role);
        return members.saveAndFlush(m);
    }

    private String bearer(Member m) {
        return "Bearer " + jwtUtil.generate(m.getId(), m.getEmail(), m.getRole(), m.getTokenVersion());
    }

    @Test
    void logoutAll_revokesEveryExistingToken() throws Exception {
        Member m = member("MEMBER");
        String phone = bearer(m), laptop = bearer(m);
        mvc.perform(get("/api/bookings").header("Authorization", laptop)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout-all").header("Authorization", phone)).andExpect(status().isOk());

        mvc.perform(get("/api/bookings").header("Authorization", laptop)).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/bookings").header("Authorization", phone)).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/auth/me").header("Authorization", phone)).andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void forgedRoleInToken_isIgnored_roleComesFromDatabase() throws Exception {
        Member m = member("MEMBER");
        // 用正確密鑰簽、但 token 裡寫 STAFF（例如帳號被降級前發的舊 token）：以資料庫的 MEMBER 為準
        String staffClaim = "Bearer " + jwtUtil.generate(m.getId(), m.getEmail(), "STAFF", 0);
        mvc.perform(get("/api/admin/stats").header("Authorization", staffClaim)).andExpect(status().isForbidden());
    }

    @Test
    void deletedAccount_tokenStopsWorking() throws Exception {
        Member m = member("MEMBER");
        String token = bearer(m);
        members.delete(m);
        members.flush();
        mvc.perform(get("/api/bookings").header("Authorization", token)).andExpect(status().is4xxClientError());
    }

    @Test
    void forbiddenAdminCall_andFailedLogins_areRecordedAsSecurityEvents() throws Exception {
        Member m = member("MEMBER");
        mvc.perform(get("/api/admin/stats").header("Authorization", bearer(m))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                   .content("{\"email\":\"" + m.getEmail() + "\",\"password\":\"wrong-password\"}"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.error").value("帳號或密碼錯誤"));

        Member staff = member("STAFF");
        mvc.perform(get("/api/admin/audit?category=SECURITY").header("Authorization", bearer(staff)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content[*].action", hasItems("ACCESS_DENIED", "LOGIN_FAILED")))
           .andExpect(jsonPath("$.content[?(@.action == 'ACCESS_DENIED')].detail", hasItem(containsString("/api/admin/stats"))));
        mvc.perform(get("/api/admin/audit?category=ADMIN").header("Authorization", bearer(staff)))
           .andExpect(jsonPath("$.content[*].targetType", not(hasItem("SECURITY"))));
    }

    @Test
    void securityHeaders_onPagesAndApi() throws Exception {
        mvc.perform(get("/api/routes").secure(true))
           .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
           .andExpect(header().string("Content-Security-Policy", containsString("https://accounts.google.com/gsi/client")))
           .andExpect(header().string("X-Frame-Options", "DENY"))
           .andExpect(header().string("X-Content-Type-Options", "nosniff"))
           .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
           .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
           .andExpect(header().string("Permissions-Policy", containsString("camera=()")));
    }
}
