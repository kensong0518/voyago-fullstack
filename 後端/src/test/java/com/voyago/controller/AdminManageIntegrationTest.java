package com.voyago.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyago.entity.Booking;
import com.voyago.entity.Member;
import com.voyago.entity.Route;
import com.voyago.repository.BookingRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 後台資料管理（行程 CRUD、全部訂單）的整合測試：權限矩陣＋業務規則
 * （代稱不可重複、有訂單的行程不能刪、改完前台立刻看到新資料）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminManageIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MemberRepository members;
    @Autowired RouteRepository routes;
    @Autowired BookingRepository bookings;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtUtil jwtUtil;

    private Route seedRoute;
    private Member seedMember;

    @BeforeEach
    void seed() {
        Route r = new Route();
        r.setSlug("admin-test-" + System.nanoTime());
        r.setName("後台測試行程");
        r.setSummary("sum");
        r.setDescription("desc");
        r.setLocation("測試地");
        r.setCountry("測試國");
        r.setDays(5);
        r.setPrice(10000);
        r.setImageUrl("https://example.com/a.jpg");
        seedRoute = routes.save(r);

        Member m = new Member();
        m.setName("下訂人");
        m.setEmail("buyer-" + System.nanoTime() + "@test.com");
        m.setPassword(passwordEncoder.encode("password123"));
        seedMember = members.save(m);
    }

    private String token(String role) {
        Member m = new Member();
        m.setName("t-" + role);
        m.setEmail(role.toLowerCase() + "-" + System.nanoTime() + "@test.com");
        m.setPassword(passwordEncoder.encode("password123"));
        m.setRole(role);
        Member saved = members.save(m);
        return "Bearer " + jwtUtil.generate(saved.getId(), saved.getEmail(), saved.getRole());
    }

    private Map<String, Object> routeBody(String slug, String name) {
        Map<String, Object> b = new HashMap<>();
        b.put("slug", slug);
        b.put("name", name);
        b.put("summary", "摘要");
        b.put("description", "介紹");
        b.put("location", "東京");
        b.put("country", "日本");
        b.put("days", 5);
        b.put("price", 39900);
        b.put("imageUrl", "https://example.com/tokyo.jpg");
        b.put("tags", "城市， 美食 ,,城市");
        b.put("featured", true);
        return b;
    }

    private Booking booking(String status) {
        Booking b = new Booking();
        b.setMember(seedMember);
        b.setRoute(seedRoute);
        b.setPeople(2);
        b.setTravelDate(LocalDate.of(2027, 1, 1));
        b.setTotalPrice(20000);
        b.setStatus(status);
        return bookings.save(b);
    }

    // ---------- 權限 ----------

    @Test
    void createRoute_withoutToken_isRejected() throws Exception {
        mvc.perform(post("/api/admin/routes").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(routeBody("tokyo-x", "東京"))))
           .andExpect(status().is4xxClientError());
    }

    @Test
    void createRoute_withMemberToken_isForbidden() throws Exception {
        mvc.perform(post("/api/admin/routes").header("Authorization", token("MEMBER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(routeBody("tokyo-x", "東京"))))
           .andExpect(status().isForbidden());
    }

    @Test
    void adminBookings_withMemberToken_isForbidden() throws Exception {
        mvc.perform(get("/api/admin/bookings").header("Authorization", token("MEMBER")))
           .andExpect(status().isForbidden());
    }

    // ---------- 行程 CRUD ----------

    @Test
    void staffCreatesRoute_andItAppearsOnPublicSite() throws Exception {
        String staff = token("STAFF");
        // 先打一次前台，讓快取裡有「舊」資料
        mvc.perform(get("/api/routes")).andExpect(status().isOk());

        mvc.perform(post("/api/admin/routes").header("Authorization", staff)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(routeBody("tokyo-food", "東京美食之旅"))))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.slug").value("tokyo-food"))
           .andExpect(jsonPath("$.tags").value("城市,美食"));

        // 新增後快取已清，前台馬上看得到
        mvc.perform(get("/api/routes/tokyo-food"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.name").value("東京美食之旅"));
    }

    @Test
    void createRoute_duplicateSlug_isConflict() throws Exception {
        mvc.perform(post("/api/admin/routes").header("Authorization", token("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(routeBody(seedRoute.getSlug(), "重複"))))
           .andExpect(status().isConflict());
    }

    @Test
    void createRoute_invalidInput_isBadRequest() throws Exception {
        Map<String, Object> body = routeBody("Bad Slug!", "東京");
        mvc.perform(post("/api/admin/routes").header("Authorization", token("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").exists());
    }

    /**
     * 這個測試不包在測試交易裡（NOT_SUPPORTED）：要驗證的是「後台改完、前台下一個請求看到新資料」，
     * 這要每個請求各自一個交易才成立——跟正式環境一樣。若整段包成一個交易，MyBatis 會重用同一個
     * SqlSession，把第一次 GET 的查詢結果從它的 session 快取直接回放，測到的是測試環境的假象。
     * 因為資料會真的 commit，結尾自己清掉。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staffUpdatesRoute_publicDetailShowsNewPrice() throws Exception {
        String staff = token("STAFF");
        try {
            mvc.perform(get("/api/routes/" + seedRoute.getSlug()))
               .andExpect(jsonPath("$.price").value(10000));   // 先進 Caffeine 快取

            Map<String, Object> body = routeBody(seedRoute.getSlug(), "改名後");
            body.put("price", 12345);
            mvc.perform(put("/api/admin/routes/" + seedRoute.getId()).header("Authorization", staff)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.price").value(12345));

            mvc.perform(get("/api/routes/" + seedRoute.getSlug()))
               .andExpect(jsonPath("$.price").value(12345))
               .andExpect(jsonPath("$.name").value("改名後"));
        } finally {
            routes.deleteById(seedRoute.getId());
            members.deleteById(seedMember.getId());
            members.findAll().stream()
                   .filter(m -> m.getEmail().startsWith("staff-"))
                   .forEach(members::delete);
        }
    }

    @Test
    void deleteRoute_withoutBookings_succeeds() throws Exception {
        mvc.perform(delete("/api/admin/routes/" + seedRoute.getId()).header("Authorization", token("STAFF")))
           .andExpect(status().isOk());
        mvc.perform(get("/api/routes/" + seedRoute.getSlug()))
           .andExpect(status().isNotFound());
    }

    @Test
    void deleteRoute_withBookings_isConflict() throws Exception {
        booking("CANCELLED");
        mvc.perform(delete("/api/admin/routes/" + seedRoute.getId()).header("Authorization", token("STAFF")))
           .andExpect(status().isConflict());
    }

    // ---------- 訂單 ----------

    @Test
    void staffListsAllBookings_withBuyerInfo_andFiltersByStatus() throws Exception {
        booking("PENDING");
        booking("CONFIRMED");
        String staff = token("STAFF");

        mvc.perform(get("/api/admin/bookings").header("Authorization", staff))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.totalElements").value(2))
           .andExpect(jsonPath("$.content[0].memberEmail").value(seedMember.getEmail()))
           .andExpect(jsonPath("$.content[0].routeName").value("後台測試行程"));

        mvc.perform(get("/api/admin/bookings?status=CONFIRMED").header("Authorization", staff))
           .andExpect(jsonPath("$.totalElements").value(1))
           .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
    }

    @Test
    void staffChangesBookingStatus() throws Exception {
        Booking b = booking("PENDING");
        mvc.perform(put("/api/admin/bookings/" + b.getId() + "/status").header("Authorization", token("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CONFIRMED\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void changeBookingStatus_invalidValue_isBadRequest() throws Exception {
        Booking b = booking("PENDING");
        mvc.perform(put("/api/admin/bookings/" + b.getId() + "/status").header("Authorization", token("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REFUNDED\"}"))
           .andExpect(status().isBadRequest());
    }
}
