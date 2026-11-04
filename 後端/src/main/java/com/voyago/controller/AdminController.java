package com.voyago.controller;

import com.voyago.dto.AdminBookingDto;
import com.voyago.dto.AdminStatsDto;
import com.voyago.dto.BookingStatusRequest;
import com.voyago.dto.PageResult;
import com.voyago.dto.RouteDto;
import com.voyago.dto.RouteUpsertRequest;
import com.voyago.service.AdminManageService;
import com.voyago.service.AdminStatsService;
import com.voyago.service.AdminCouponService;
import com.voyago.service.AdminReportService;
import com.voyago.service.AuditService;
import com.voyago.service.CsvExportService;
import com.voyago.dto.AdminReportDto;
import com.voyago.dto.AuditLogDto;
import com.voyago.dto.CouponDto;
import com.voyago.dto.CouponUpsertRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
    private final AdminReportService reports;
    private final CsvExportService csv;
    private final AuditService audit;
    private final AdminCouponService coupons;

    public AdminController(AdminStatsService stats, AdminManageService manage, AdminReportService reports,
                           CsvExportService csv, AuditService audit, AdminCouponService coupons) {
        this.stats = stats;
        this.manage = manage;
        this.reports = reports;
        this.csv = csv;
        this.audit = audit;
        this.coupons = coupons;
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

    // ---------- 報表 ----------

    /** 近 N 天（7–90）每日訂單與已付款營收、熱賣行程。 */
    @GetMapping("/reports")
    public AdminReportDto report(@RequestParam(defaultValue = "30") int days) {
        return reports.report(days);
    }

    // ---------- CSV 匯出 ----------

    @GetMapping(value = "/export/bookings.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> exportBookings(@RequestParam(defaultValue = "") String status) {
        return csvResponse("voyago-bookings", csv.bookings(status));
    }

    @GetMapping(value = "/export/members.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> exportMembers() {
        return csvResponse("voyago-members", csv.members());
    }

    private static ResponseEntity<String> csvResponse(String name, CsvExportService.Export export) {
        String file = name + "-" + LocalDate.now(ZoneId.of("Asia/Taipei")) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file).build().toString())
                .header("X-Row-Count", String.valueOf(export.rows()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")   // 含個資，不讓瀏覽器或代理快取
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(export.content());
    }

    // ---------- 操作紀錄 ----------

    @GetMapping("/audit")
    public PageResult<AuditLogDto> auditLog(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(defaultValue = "") String category) {
        return audit.list(page, size, category);
    }

    // ---------- 優惠碼 ----------

    @GetMapping("/coupons")
    public List<CouponDto> coupons() {
        return coupons.list();
    }

    @PostMapping("/coupons")
    public ResponseEntity<CouponDto> createCoupon(@Valid @RequestBody CouponUpsertRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(coupons.create(req));
    }

    @PutMapping("/coupons/{id}")
    public CouponDto updateCoupon(@PathVariable Long id, @Valid @RequestBody CouponUpsertRequest req) {
        return coupons.update(id, req);
    }

    @DeleteMapping("/coupons/{id}")
    public Map<String, Boolean> deleteCoupon(@PathVariable Long id) {
        coupons.delete(id);
        return Map.of("ok", true);
    }
}
