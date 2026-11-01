package com.voyago.controller;

import com.voyago.dto.AdminBookingDto;
import com.voyago.dto.AdminStatsDto;
import com.voyago.dto.BookingStatusRequest;
import com.voyago.dto.PageResult;
import com.voyago.dto.RouteDto;
import com.voyago.dto.RouteUpsertRequest;
import com.voyago.service.AdminManageService;
import com.voyago.service.AdminStatsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 後台 API：整支 controller 都只開給 STAFF（類別層 @PreAuthorize），
 * 前端就算被繞過把後台畫面打開，資料端點一樣擋得住。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('STAFF')")
public class AdminController {

    private final AdminStatsService stats;
    private final AdminManageService manage;

    public AdminController(AdminStatsService stats, AdminManageService manage) {
        this.stats = stats;
        this.manage = manage;
    }

    /** 後台儀表板統計：會員數、訂單狀態分布、總營收。 */
    @GetMapping("/stats")
    public AdminStatsDto stats() {
        return stats.getStats();
    }

    // ---------- 行程管理 ----------

    @PostMapping("/routes")
    public ResponseEntity<RouteDto> createRoute(@Valid @RequestBody RouteUpsertRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(manage.createRoute(req));
    }

    @PutMapping("/routes/{id}")
    public RouteDto updateRoute(@PathVariable Long id, @Valid @RequestBody RouteUpsertRequest req) {
        return manage.updateRoute(id, req);
    }

    @DeleteMapping("/routes/{id}")
    public Map<String, Boolean> deleteRoute(@PathVariable Long id) {
        manage.deleteRoute(id);
        return Map.of("ok", true);
    }

    // ---------- 訂單管理 ----------

    @GetMapping("/bookings")
    public PageResult<AdminBookingDto> bookings(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return manage.listBookings(status, page, size);
    }

    @PutMapping("/bookings/{id}/status")
    public AdminBookingDto updateBookingStatus(@PathVariable Long id,
                                               @Valid @RequestBody BookingStatusRequest req) {
        return manage.updateBookingStatus(id, req.status());
    }
}
