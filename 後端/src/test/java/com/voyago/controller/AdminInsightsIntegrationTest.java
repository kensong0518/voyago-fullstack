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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 後台 D：營收報表、CSV 匯出、操作紀錄、優惠碼管理。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminInsightsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MemberRepository members;
    @Autowired RouteRepository routes;
    @Autowired BookingRepository bookings;
    @Autowired JwtUtil jwtUtil;

    private Route route;
    private Member buyer;
    private String staff;

    @BeforeEach
    void seed() {
        Route r = new Route();
        r.setSlug("ins-" + System.nanoTime()); r.setName("報表測試行程"); r.setSummary("s"); r.setDescription("d");
        r.setLocation("地"); r.setCountry("國"); r.setDays(5); r.setPrice(10000);
        r.setImageUrl("https://example.com/a.jpg");
        route = routes.save(r);
        buyer = member("=HYPERLINK(\"http://evil\")", "MEMBER");   // 惡意姓名：CSV 不能被 Excel 當公式
        staff = auth(member("管理員", "STAFF"));
    }

    private Member member(String name, String role) {
        Member m = new Member();
        m.setName(name); m.setEmail("ins-" + System.nanoTime() + "@test.com"); m.setPassword("x"); m.setRole(role);
        return members.save(m);
    }

    private String auth(Member m) {
        return "Bearer " + jwtUtil.generate(m.getId(), m.getEmail(), m.getRole());
    }

    private Booking booking(String status, int total) {
        Booking b = new Booking();
        b.setMember(buyer); b.setRoute(route); b.setPeople(1); b.setTravelDate(LocalDate.now().plusDays(10));
        b.setTotalPrice(total); b.setStatus(status);
        return bookings.saveAndFlush(b);
    }

    @Test
    void everythingIsStaffOnly() throws Exception {
        String member = auth(member("一般會員", "MEMBER"));
        for (String url : new String[]{"/api/admin/reports", "/api/admin/export/bookings.csv",
                "/api/admin/export/members.csv", "/api/admin/audit", "/api/admin/coupons"}) {
            mvc.perform(get(url).header("Authorization", member)).andExpect(status().isForbidden());
            mvc.perform(get(url)).andExpect(status().is4xxClientError());
        }
    }

    @Test
    void report_countsOrders_butRevenueOnlyFromPaid() throws Exception {
        booking("CONFIRMED", 20000);
        booking("CONFIRMED", 10000);
        booking("PENDING", 99999);
        booking("CANCELLED", 88888);
        String today = LocalDate.now(java.time.ZoneId.of("Asia/Taipei")).toString();
        mvc.perform(get("/api/admin/reports?days=7").header("Authorization", staff))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.days").value(7))
           .andExpect(jsonPath("$.daily", hasSize(7)))
           .andExpect(jsonPath("$.daily[6].date").value(today))
           .andExpect(jsonPath("$.daily[6].orders").value(greaterThanOrEqualTo(4)))
           .andExpect(jsonPath("$.topRoutes[?(@.slug == '" + route.getSlug() + "')].revenue").value(contains(30000)))
           .andExpect(jsonPath("$.topRoutes[?(@.slug == '" + route.getSlug() + "')].paidOrders").value(contains(2)));
        mvc.perform(get("/api/admin/reports?days=1000").header("Authorization", staff))
           .andExpect(jsonPath("$.days").value(90));   // 上限 90 天
    }

    @Test
    void bookingsCsv_hasBom_header_escapesFormulas_andIsAudited() throws Exception {
        booking("CONFIRMED", 12345);
        var res = mvc.perform(get("/api/admin/export/bookings.csv").header("Authorization", staff))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Disposition", containsString("attachment")))
           .andExpect(header().string("Content-Disposition", containsString(".csv")))
           .andExpect(header().string("Cache-Control", "no-store"))
           .andReturn().getResponse();
        String csv = new String(res.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿訂單編號,建立時間,狀態"), "UTF-8 BOM + 中文表頭");
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"http://evil\"\")\""), "公式開頭要加單引號、引號要跳脫：" + csv);
        assertTrue(csv.contains("已付款"));
        assertTrue(csv.contains(",12345\r\n"));

        mvc.perform(get("/api/admin/audit").header("Authorization", staff))
           .andExpect(jsonPath("$.content[0].action").value("EXPORT_BOOKINGS"));
    }

    @Test
    void membersCsv_works() throws Exception {
        var res = mvc.perform(get("/api/admin/export/members.csv").header("Authorization", staff))
           .andExpect(status().isOk()).andReturn().getResponse();
        String csv = new String(res.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿會員編號,姓名,Email"));
        assertTrue(csv.contains(buyer.getEmail()));
        assertFalse(csv.contains("password") || csv.contains(",x,"), "不能匯出密碼");
    }

    @Test
    void adminActions_areRecordedInAuditLog() throws Exception {
        Booking b = booking("PENDING", 10000);
        mvc.perform(put("/api/admin/bookings/" + b.getId() + "/status").header("Authorization", staff)
                   .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
           .andExpect(status().isOk());
        mvc.perform(get("/api/admin/audit").header("Authorization", staff))
           .andExpect(jsonPath("$.content[0].action").value("BOOKING_STATUS"))
           .andExpect(jsonPath("$.content[0].targetId").value(b.getId()))
           .andExpect(jsonPath("$.content[0].detail").value(startsWith("PENDING → CONFIRMED")))
           .andExpect(jsonPath("$.content[0].actorEmail").value(containsString("ins-")));
    }

    @Test
    void coupons_crud_rules() throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("code", "summer-25", "description", "夏季", "discountType", "PERCENT",
                "discountValue", 15, "minAmount", 0));
        String created = mvc.perform(post("/api/admin/coupons").header("Authorization", staff)
                   .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.code").value("SUMMER-25"))
           .andExpect(jsonPath("$.active").value(true))
           .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        mvc.perform(post("/api/admin/coupons").header("Authorization", staff)
                   .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
           .andExpect(status().isConflict());

        body.put("discountValue", 95);
        mvc.perform(put("/api/admin/coupons/" + id).header("Authorization", staff)
                   .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
           .andExpect(status().isBadRequest());   // 百分比最多 90

        body.put("discountValue", 20); body.put("active", false);
        mvc.perform(put("/api/admin/coupons/" + id).header("Authorization", staff)
                   .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
           .andExpect(jsonPath("$.active").value(false));

        // 停用後會員不能用
        mvc.perform(post("/api/coupons/quote").header("Authorization", auth(buyer)).contentType(MediaType.APPLICATION_JSON)
                   .content("{\"code\":\"SUMMER-25\",\"routeId\":" + route.getId() + ",\"people\":1}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("這個優惠碼已停用"));

        mvc.perform(delete("/api/admin/coupons/" + id).header("Authorization", staff)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/audit").header("Authorization", staff))
           .andExpect(jsonPath("$.content[0].action").value("COUPON_DELETE"))
           .andExpect(jsonPath("$.content[1].action").value("COUPON_UPDATE"));
    }
}
