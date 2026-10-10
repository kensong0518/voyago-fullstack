package com.voyago.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyago.entity.Member;
import com.voyago.entity.Route;
import com.voyago.repository.MemberRepository;
import com.voyago.repository.RouteRepository;
import com.voyago.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller 層整合測試：驗證權限矩陣（公開 / 需登入 / STAFF 專屬），
 * 而不是重複 Service 層已經測過的業務邏輯細節。用 H2（test profile）跑
 * 真正的 Spring Security filter chain + MockMvc，比單元測試更貼近真實請求。
 *
 * 每個測試方法包在交易內、結束自動 rollback，彼此資料互不影響。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiPermissionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MemberRepository members;
    @Autowired RouteRepository routes;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private Route seedRoute;

    @BeforeEach
    void seed() {
        Route r = new Route();
        r.setSlug("test-route-" + System.nanoTime());
        r.setName("測試行程");
        r.setSummary("sum");
        r.setDescription("desc");
        r.setLocation("測試地");
        r.setCountry("測試國");
        r.setDays(5);
        r.setPrice(10000);
        seedRoute = routes.save(r);
    }

    private String token(String email, String role) {
        Member m = new Member();
        m.setName("test-" + role);
        m.setEmail(email);
        m.setPassword(passwordEncoder.encode("password123"));
        m.setRole(role);
        Member saved = members.save(m);
        return jwtUtil.generate(saved.getId(), saved.getEmail(), saved.getRole());
    }

    // ---------- 公開端點：不帶 token 也能用 ----------

    @Test
    void publicRouteList_accessibleWithoutLogin() throws Exception {
        mvc.perform(get("/api/routes"))
           .andExpect(status().isOk());
    }

    @Test
    void routeDetail_accessibleWithoutLogin() throws Exception {
        mvc.perform(get("/api/routes/" + seedRoute.getSlug()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.name").value("測試行程"));
    }

    // ---------- 需登入端點：沒帶 token 一律擋下 ----------

    @Test
    void bookings_withoutToken_isRejected() throws Exception {
        mvc.perform(get("/api/bookings"))
           .andExpect(status().is4xxClientError());
    }

    @Test
    void createBooking_withoutToken_isRejected() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "routeId", seedRoute.getId(), "people", 2, "travelDate", "2027-01-01"));
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().is4xxClientError());
    }

    // ---------- 需登入端點：MEMBER token 可以用 ----------

    @Test
    void createBooking_withMemberToken_succeeds() throws Exception {
        String memberToken = token("member-" + System.nanoTime() + "@test.com", "MEMBER");
        String body = json.writeValueAsString(Map.of(
                "routeId", seedRoute.getId(), "people", 2, "travelDate", "2027-01-01"));

        mvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void myBookings_withMemberToken_succeeds() throws Exception {
        String memberToken = token("member2-" + System.nanoTime() + "@test.com", "MEMBER");
        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + memberToken))
           .andExpect(status().isOk());
    }

    // ---------- STAFF 專屬端點：公開/MEMBER 都擋下，只有 STAFF 能用 ----------

    @Test
    void staffMemberList_withoutToken_isRejected() throws Exception {
        mvc.perform(get("/api/members"))
           .andExpect(status().is4xxClientError());
    }

    @Test
    void staffMemberList_withMemberToken_isForbidden() throws Exception {
        String memberToken = token("member3-" + System.nanoTime() + "@test.com", "MEMBER");
        mvc.perform(get("/api/members").header("Authorization", "Bearer " + memberToken))
           .andExpect(status().isForbidden());
    }

    @Test
    void staffMemberList_withStaffToken_succeeds() throws Exception {
        String staffToken = token("staff-" + System.nanoTime() + "@test.com", "STAFF");
        mvc.perform(get("/api/members").header("Authorization", "Bearer " + staffToken))
           .andExpect(status().isOk());
    }

    // ---------- 收藏：需登入，MEMBER 就能用 ----------

    @Test
    void favorites_withoutToken_isRejected() throws Exception {
        mvc.perform(get("/api/favorites"))
           .andExpect(status().is4xxClientError());
    }

    @Test
    void addFavorite_withMemberToken_succeeds() throws Exception {
        String memberToken = token("member5-" + System.nanoTime() + "@test.com", "MEMBER");

        mvc.perform(post("/api/favorites/" + seedRoute.getId())
                .header("Authorization", "Bearer " + memberToken))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.route.id").value(seedRoute.getId()));

        mvc.perform(get("/api/favorites").header("Authorization", "Bearer " + memberToken))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].route.name").value("測試行程"));
    }

    // ---------- 後台統計：跟會員管理一樣只開 STAFF ----------

    @Test
    void adminStats_withoutToken_isRejected() throws Exception {
        mvc.perform(get("/api/admin/stats"))
           .andExpect(status().is4xxClientError());
    }

    @Test
    void adminStats_withMemberToken_isForbidden() throws Exception {
        String memberToken = token("member6-" + System.nanoTime() + "@test.com", "MEMBER");
        mvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + memberToken))
           .andExpect(status().isForbidden());
    }

    @Test
    void adminStats_withStaffToken_succeeds() throws Exception {
        String staffToken = token("staff2-" + System.nanoTime() + "@test.com", "STAFF");
        mvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + staffToken))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.totalMembers").exists());
    }

    @Test
    void staffCreateMember_withMemberToken_isForbidden() throws Exception {
        String memberToken = token("member4-" + System.nanoTime() + "@test.com", "MEMBER");
        String body = json.writeValueAsString(Map.of(
                "name", "新會員", "email", "new-" + System.nanoTime() + "@test.com",
                "password", "password123", "phone", "", "role", "MEMBER"));

        mvc.perform(post("/api/members")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isForbidden());
    }
}
