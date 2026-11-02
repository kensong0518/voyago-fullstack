package com.voyago.service;

import com.voyago.dto.AdminBookingDto;
import com.voyago.dto.PageResult;
import com.voyago.dto.RouteDto;
import com.voyago.dto.RouteUpsertRequest;
import com.voyago.entity.Booking;
import com.voyago.entity.Route;
import com.voyago.repository.BookingRepository;
import com.voyago.repository.CouponRepository;
import com.voyago.repository.RouteRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AdminManageServiceImpl implements AdminManageService {

    private static final int MAX_PAGE_SIZE = 50;

    private final RouteRepository routes;
    private final BookingRepository bookings;
    private final CouponRepository coupons;

    public AdminManageServiceImpl(RouteRepository routes, BookingRepository bookings, CouponRepository coupons) {
        this.routes = routes;
        this.bookings = bookings;
        this.coupons = coupons;
    }

    // ---------- 行程 ----------
    // 前台列表、單筆與相似推薦都掛了 Caffeine 快取；後台一改資料就整批清掉，
    // 否則前台最多會看到 5 分鐘前的舊內容。
    //
    // 寫入一律立刻 flush：前台讀行程走 MyBatis-Plus（直接 JDBC），寫入走 JPA，
    // JPA 預設把 SQL 延到交易結束才送出，同一交易內 MyBatis 會讀到舊資料。

    @Override
    @Caching(evict = {
        @CacheEvict(cacheNames = "routes", allEntries = true),
        @CacheEvict(cacheNames = "routeBySlug", allEntries = true)
    })
    public RouteDto createRoute(RouteUpsertRequest req) {
        if (routes.findBySlug(req.slug()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "網址代稱已被其他行程使用");
        }
        Route r = new Route();
        apply(r, req);
        return RouteDto.from(routes.saveAndFlush(r));
    }

    @Override
    @Caching(evict = {
        @CacheEvict(cacheNames = "routes", allEntries = true),
        @CacheEvict(cacheNames = "routeBySlug", allEntries = true)
    })
    public RouteDto updateRoute(Long id, RouteUpsertRequest req) {
        Route r = routes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此行程"));
        routes.findBySlug(req.slug())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "網址代稱已被其他行程使用");
                });
        apply(r, req);
        return RouteDto.from(routes.saveAndFlush(r));
    }

    @Override
    @Caching(evict = {
        @CacheEvict(cacheNames = "routes", allEntries = true),
        @CacheEvict(cacheNames = "routeBySlug", allEntries = true)
    })
    public void deleteRoute(Long id) {
        if (!routes.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此行程");
        }
        // 訂單是交易紀錄，不能因為行程下架就跟著消失
        if (bookings.existsByRouteId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "這個行程已有訂單，不能刪除");
        }
        routes.deleteById(id);
        routes.flush();
    }

    private void apply(Route r, RouteUpsertRequest req) {
        r.setSlug(req.slug().trim());
        r.setName(req.name().trim());
        r.setSummary(req.summary().trim());
        r.setDescription(req.description().trim());
        r.setLocation(req.location().trim());
        r.setCountry(req.country().trim());
        r.setDays(req.days());
        r.setPrice(req.price());
        r.setImageUrl(req.imageUrl().trim());
        r.setTags(normalizeTags(req.tags()));
        r.setFeatured(Boolean.TRUE.equals(req.featured()));
    }

    /** 「城市， 美食 ,,藝術」→「城市,美食,藝術」：前台用逗號切標籤，全形逗號與空白要先整理掉。 */
    private String normalizeTags(String tags) {
        if (tags == null) return "";
        return java.util.Arrays.stream(tags.replace('，', ',').split(","))
                .map(String::trim).filter(t -> !t.isEmpty()).distinct()
                .reduce((a, b) -> a + "," + b).orElse("");
    }

    // ---------- 訂單 ----------

    @Override
    @Transactional(readOnly = true)
    public PageResult<AdminBookingDto> listBookings(String status, int page, int size) {
        int p = Math.max(0, page);
        int s = size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        String st = status == null ? "" : status.trim().toUpperCase();
        Page<Booking> result = bookings.findAllForAdmin(st, PageRequest.of(p, s));
        return new PageResult<>(result.getContent().stream().map(AdminBookingDto::from).toList(),
                p, s, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public AdminBookingDto updateBookingStatus(Long id, String status) {
        Booking b = bookings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此訂單"));
        // 優惠碼次數跟著訂單狀態走：改成取消就還回去；從取消改回來要重新占用，用完了就不能恢復
        String code = b.getCouponCode();
        boolean wasCancelled = "CANCELLED".equals(b.getStatus());
        boolean nowCancelled = "CANCELLED".equals(status);
        if (code != null && !wasCancelled && nowCancelled) {
            coupons.release(code);
        } else if (code != null && wasCancelled && !nowCancelled) {
            Long couponId = coupons.findByCode(code).map(c -> c.getId()).orElse(null);
            if (couponId != null && coupons.tryRedeem(couponId) == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "這筆訂單使用的優惠碼已用完或停用，無法恢復");
            }
        }
        b.setStatus(status);
        return AdminBookingDto.from(bookings.save(b));
    }
}
