package com.voyago.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyago.entity.Booking;
import com.voyago.entity.Coupon;
import com.voyago.entity.Member;
import com.voyago.entity.Route;
import com.voyago.repository.BookingRepository;
import com.voyago.repository.CouponRepository;
import com.voyago.repository.MemberRepository;
import com.voyago.repository.RouteRepository;
import com.voyago.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真實評價（只有付過款的會員能寫、平均自動重算）與優惠碼（試算、下單折抵、限用次數、取消歸還）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewCouponIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MemberRepository members;
    @Autowired RouteRepository routes;
    @Autowired BookingRepository bookings;
    @Autowired CouponRepository coupons;
    @Autowired JwtUtil jwtUtil;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager cacheManager;

    private Route route;

    @BeforeEach
    void seed() {
        cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
        Route r = new Route();
        r.setSlug("rc-test-" + System.nanoTime());
        r.setName("評價測試行程"); r.setSummary("s"); r.setDescription("d");
        r.setLocation("地"); r.setCountry("國"); r.setDays(5); r.setPrice(52000);
        r.setImageUrl("https://example.com/a.jpg");
        route = routes.saveAndFlush(r);
    }

    private Member member(String name) {
        Member m = new Member();
        m.setName(name);
        m.setEmail("rc-" + System.nanoTime() + "@test.com");
        m.setPassword("x");
        return members.save(m);
    }

    private String auth(Member m) {
        return "Bearer " + jwtUtil.generate(m.getId(), m.getEmail(), m.getRole());
    }

    private void paidBooking(Member m) {
        Booking b = new Booking();
        b.setMember(m); b.setRoute(route); b.setPeople(1);
        b.setTravelDate(LocalDate.now().plusDays(30)); b.setTotalPrice(52000); b.setStatus("CONFIRMED");
        bookings.saveAndFlush(b);
    }

    private String review(int rating, String content) throws Exception {
        return json.writeValueAsString(Map.of("rating", rating, "content", content));
    }

    private String reviewsUrl() {
        return "/api/routes/" + route.getSlug() + "/reviews";
    }

    // ---------- 評價 ----------

    @Test
    void newRoute_startsWithNoReviews() throws Exception {
        mvc.perform(get("/api/routes/" + route.getSlug()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.rating").value(0.0))
           .andExpect(jsonPath("$.reviews").value(0));
        mvc.perform(get(reviewsUrl()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.count").value(0))
           .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void onlyPaidMembers_canReview() throws Exception {
        Member m = member("王小明");
        mvc.perform(put(reviewsUrl() + "/mine").contentType(MediaType.APPLICATION_JSON).content(review(5, "很棒的行程！")))
           .andExpect(status().is4xxClientError());   // 沒登入
        mvc.perform(put(reviewsUrl() + "/mine").header("Authorization", auth(m))
                   .contentType(MediaType.APPLICATION_JSON).content(review(5, "很棒的行程！")))
           .andExpect(status().isForbidden());
        mvc.perform(get(reviewsUrl() + "/eligibility").header("Authorization", auth(m)))
           .andExpect(jsonPath("$.canReview").value(false));
        mvc.perform(get(reviewsUrl() + "/eligibility"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.canReview").value(false));

        // 只有待付款的訂單也不行
        Booking pending = new Booking();
        pending.setMember(m); pending.setRoute(route); pending.setPeople(1);
        pending.setTravelDate(LocalDate.now().plusDays(30)); pending.setTotalPrice(52000);
        bookings.saveAndFlush(pending);
        mvc.perform(put(reviewsUrl() + "/mine").header("Authorization", auth(m))
                   .contentType(MediaType.APPLICATION_JSON).content(review(5, "很棒的行程！")))
           .andExpect(status().isForbidden());
    }

    @Test
    void reviews_recomputeAverage_andShowOnTourImmediately() throws Exception {
        Member a = member("王小明"), b = member("陳大文");
        paidBooking(a); paidBooking(b);

        mvc.perform(get("/api/routes/" + route.getSlug())).andExpect(jsonPath("$.reviews").value(0));   // 先讓快取存舊值

        mvc.perform(put(reviewsUrl() + "/mine").header("Authorization", auth(a))
                   .contentType(MediaType.APPLICATION_JSON).content(review(5, "城堡超美，行程安排剛好")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.count").value(1));
        mvc.perform(put(reviewsUrl() + "/mine").header("Authorization", auth(b))
                   .contentType(MediaType.APPLICATION_JSON).content(review(4, "交通票券很方便")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.average").value(4.5))
           .andExpect(jsonPath("$.count").value(2))
           .andExpect(jsonPath("$.distribution[3]").value(1))
           .andExpect(jsonPath("$.distribution[4]").value(1))
           .andExpect(jsonPath("$.items[0].authorName").value("陳*文"))   // 最新的在前、名字遮蔽
           .andExpect(jsonPath("$.items[0].memberId").doesNotExist());

        // 改評價不會多一則
        mvc.perform(put(reviewsUrl() + "/mine").header("Authorization", auth(b))
                   .contentType(MediaType.APPLICATION_JSON).content(review(3, "交通票券很方便，但飯店普通")))
           .andExpect(jsonPath("$.count").value(2))
           .andExpect(jsonPath("$.average").value(4.0));

        // 行程頁、列表立刻看到新數字（快取已清）
        mvc.perform(get("/api/routes/" + route.getSlug()))
           .andExpect(jsonPath("$.rating").value(4.0))
           .andExpect(jsonPath("$.reviews").value(2));

        mvc.perform(get(reviewsUrl() + "/eligibility").header("Authorization", auth(b)))
           .andExpect(jsonPath("$.canReview").value(true))
           .andExpect(jsonPath("$.myRating").value(3));

        // 刪掉自己的評價
        mvc.perform(delete(reviewsUrl() + "/mine").header("Authorization", auth(a)))
           .andExpect(jsonPath("$.count").value(1))
           .andExpect(jsonPath("$.average").value(3.0));
    }

    @Test
    void review_validation() throws Exception {
        Member m = member("王小明");
        paidBooking(m);
        mvc.perform(put(reviewsUrl() + "/mine").header("Authorization", auth(m))
                   .contentType(MediaType.APPLICATION_JSON).content(review(6, "很棒的行程！")))
           .andExpect(status().isBadRequest());
        mvc.perform(put(reviewsUrl() + "/mine").header("Authorization", auth(m))
                   .contentType(MediaType.APPLICATION_JSON).content(review(5, "讚")))
           .andExpect(status().isBadRequest());
    }

    // ---------- 優惠碼 ----------

    private Coupon coupon(String code, String type, int value, int min, Integer maxUses) {
        Coupon c = new Coupon();
        c.setCode(code); c.setDescription(code); c.setDiscountType(type); c.setDiscountValue(value);
        c.setMinAmount(min); c.setMaxUses(maxUses);
        return coupons.saveAndFlush(c);
    }

    private int usedCount(String code) {
        return jdbc.queryForObject("SELECT used_count FROM coupon WHERE code = ?", Integer.class, code);
    }

    private String bookingBody(String code) throws Exception {
        return json.writeValueAsString(Map.of("routeId", route.getId(), "people", 2,
                "travelDate", LocalDate.now().plusDays(40).toString(), "couponCode", code));
    }

    @Test
    void quote_requiresLogin_andComputesOnServer() throws Exception {
        coupon("TQ10", "PERCENT", 10, 0, null);
        String body = json.writeValueAsString(Map.of("code", "tq10", "routeId", route.getId(), "people", 2));
        mvc.perform(post("/api/coupons/quote").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().is4xxClientError());   // 沒登入
        mvc.perform(post("/api/coupons/quote").header("Authorization", auth(member("王小明")))
                   .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value("TQ10"))
           .andExpect(jsonPath("$.subtotal").value(104000))
           .andExpect(jsonPath("$.discount").value(10400))
           .andExpect(jsonPath("$.total").value(93600));
        assertEquals(0, usedCount("TQ10"));   // 試算不占用次數
    }

    @Test
    void booking_appliesCoupon_oncePerMember_andCancelReleasesIt() throws Exception {
        coupon("TB3000", "AMOUNT", 3000, 50000, null);
        Member m = member("王小明");

        String res = mvc.perform(post("/api/bookings").header("Authorization", auth(m))
                   .contentType(MediaType.APPLICATION_JSON).content(bookingBody("tb3000")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.totalPrice").value(101000))
           .andExpect(jsonPath("$.discountAmount").value(3000))
           .andExpect(jsonPath("$.couponCode").value("TB3000"))
           .andReturn().getResponse().getContentAsString();
        assertEquals(1, usedCount("TB3000"));

        mvc.perform(post("/api/bookings").header("Authorization", auth(m))
                   .contentType(MediaType.APPLICATION_JSON).content(bookingBody("TB3000")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("每個優惠碼每位會員限用一次"));
        assertEquals(1, usedCount("TB3000"));   // 失敗的下單不占用

        JsonNode booking = json.readTree(res);
        mvc.perform(delete("/api/bookings/" + booking.get("id").asLong()).header("Authorization", auth(m)))
           .andExpect(status().isOk());
        assertEquals(0, usedCount("TB3000"));

        mvc.perform(post("/api/bookings").header("Authorization", auth(m))
                   .contentType(MediaType.APPLICATION_JSON).content(bookingBody("TB3000")))
           .andExpect(status().isCreated());
    }

    @Test
    void limitedCoupon_cannotBeOversold() throws Exception {
        coupon("TONE", "PERCENT", 20, 0, 1);
        mvc.perform(post("/api/bookings").header("Authorization", auth(member("甲")))
                   .contentType(MediaType.APPLICATION_JSON).content(bookingBody("TONE")))
           .andExpect(status().isCreated());
        mvc.perform(post("/api/bookings").header("Authorization", auth(member("乙")))
                   .contentType(MediaType.APPLICATION_JSON).content(bookingBody("TONE")))
           // 正式環境每個請求是獨立交易，會在檢查階段就擋下（400）；測試共用同一個交易時由原子 UPDATE 擋下（409）。
           // 兩種都是「沒超賣」，這裡驗證的重點是次數停在 1
           .andExpect(status().is4xxClientError())
           .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("用完")));
        assertEquals(1, usedCount("TONE"));
    }

    @Test
    void bookingWithoutCoupon_unchanged() throws Exception {
        String body = json.writeValueAsString(Map.of("routeId", route.getId(), "people", 2,
                "travelDate", LocalDate.now().plusDays(40).toString()));
        mvc.perform(post("/api/bookings").header("Authorization", auth(member("王小明")))
                   .contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.totalPrice").value(104000))
           .andExpect(jsonPath("$.discountAmount").value(0));
    }
}
