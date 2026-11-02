package com.voyago.service;

import com.voyago.dto.ReviewDto;
import com.voyago.dto.ReviewEligibilityDto;
import com.voyago.dto.ReviewRequest;
import com.voyago.dto.ReviewSummaryDto;
import com.voyago.entity.Member;
import com.voyago.entity.Review;
import com.voyago.entity.Route;
import com.voyago.repository.BookingRepository;
import com.voyago.repository.MemberRepository;
import com.voyago.repository.ReviewRepository;
import com.voyago.repository.RouteRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 行程評價。規則：
 *   - 只有「這個行程有已付款訂單」的會員能評價，每人每行程一則，可修改、可刪除；
 *   - 行程的平均分數與評價數（route.rating / route.reviews）每次異動後從評價表重新計算，
 *     不做「舊平均 ± 新分數」的增量運算——重算永遠正確，資料量也小。
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {

    static final int LIST_LIMIT = 30;

    private final ReviewRepository reviews;
    private final RouteRepository routes;
    private final MemberRepository members;
    private final BookingRepository bookings;

    public ReviewService(ReviewRepository reviews, RouteRepository routes,
                         MemberRepository members, BookingRepository bookings) {
        this.reviews = reviews;
        this.routes = routes;
        this.members = members;
        this.bookings = bookings;
    }

    public ReviewSummaryDto summary(String slug) {
        Route route = route(slug);
        List<Integer> dist = new ArrayList<>(Collections.nCopies(5, 0));
        for (Object[] row : reviews.countByRating(route.getId())) {
            int star = ((Number) row[0]).intValue();
            if (star >= 1 && star <= 5) dist.set(star - 1, ((Number) row[1]).intValue());
        }
        List<ReviewDto> items = reviews.findLatestForRoute(route.getId(), PageRequest.of(0, LIST_LIMIT)).stream()
                .map(r -> new ReviewDto(r.getId(), maskName(r.getMember().getName()), r.getRating(),
                        r.getContent(), r.getUpdatedAt(), null, null))
                .toList();
        return new ReviewSummaryDto(route.getRating(), route.getReviews(), dist, items);
    }

    public List<ReviewDto> latest(int limit) {
        int n = Math.max(1, Math.min(limit, 12));
        return reviews.findLatestPositive(PageRequest.of(0, n)).stream()
                .map(r -> new ReviewDto(r.getId(), maskName(r.getMember().getName()), r.getRating(),
                        r.getContent(), r.getUpdatedAt(), r.getRoute().getSlug(), r.getRoute().getName()))
                .toList();
    }

    public ReviewEligibilityDto eligibility(Long memberId, String slug) {
        Route route = route(slug);
        boolean paid = bookings.existsByMemberIdAndRouteIdAndStatus(memberId, route.getId(), "CONFIRMED");
        var mine = reviews.findByMemberIdAndRouteId(memberId, route.getId());
        return new ReviewEligibilityDto(paid,
                paid ? null : "完成付款的旅客才能評價這個行程",
                mine.map(Review::getRating).orElse(null),
                mine.map(Review::getContent).orElse(null));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "routes", allEntries = true),
            @CacheEvict(cacheNames = "routeBySlug", key = "#slug")})
    public ReviewSummaryDto upsert(Long memberId, String slug, ReviewRequest req) {
        Route route = route(slug);
        if (!bookings.existsByMemberIdAndRouteIdAndStatus(memberId, route.getId(), "CONFIRMED")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "完成付款的旅客才能評價這個行程");
        }
        Member member = members.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "請先登入"));
        Review r = reviews.findByMemberIdAndRouteId(memberId, route.getId()).orElseGet(() -> {
            Review n = new Review();
            n.setMember(member);
            n.setRoute(route);
            return n;
        });
        r.setRating(req.rating());
        r.setContent(req.content().trim());
        reviews.saveAndFlush(r);
        recompute(route);
        return summary(slug);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "routes", allEntries = true),
            @CacheEvict(cacheNames = "routeBySlug", key = "#slug")})
    public ReviewSummaryDto deleteMine(Long memberId, String slug) {
        Route route = route(slug);
        reviews.findByMemberIdAndRouteId(memberId, route.getId()).ifPresent(r -> {
            reviews.delete(r);
            reviews.flush();
            recompute(route);
        });
        return summary(slug);
    }

    /** 從評價表重算平均與數量，寫回行程（列表排序、卡片顯示都用這兩個欄位）。 */
    void recompute(Route route) {
        int count = 0;
        long sum = 0;
        for (Object[] row : reviews.countByRating(route.getId())) {
            int star = ((Number) row[0]).intValue();
            int n = ((Number) row[1]).intValue();
            count += n;
            sum += (long) star * n;
        }
        route.setReviews(count);
        route.setRating(count == 0 ? 0.0 : Math.round(sum * 10.0 / count) / 10.0);
        routes.saveAndFlush(route);   // 前台讀行程走 MyBatis（直接 JDBC），要先 flush 才讀得到新值
    }

    private Route route(String slug) {
        return routes.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此行程"));
    }

    /** 王小明 → 王*明、Ken → K*n、Al → A*；公開頁面不露出完整姓名。 */
    static String maskName(String name) {
        if (name == null || name.isBlank()) return "旅客";
        String n = name.trim();
        int[] cps = n.codePoints().toArray();
        if (cps.length == 1) return n;
        if (cps.length == 2) return new String(cps, 0, 1) + "*";
        return new String(cps, 0, 1) + "*".repeat(Math.min(cps.length - 2, 3)) + new String(cps, cps.length - 1, 1);
    }
}
