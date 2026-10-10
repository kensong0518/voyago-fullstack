package com.voyago.controller;

import com.voyago.dto.AdminStatsDto;
import com.voyago.service.AdminStatsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminStatsService stats;

    public AdminController(AdminStatsService stats) {
        this.stats = stats;
    }

    /** 後台儀表板統計：會員數、訂單狀態分布、總營收。跟 MemberController 的後台端點一樣只開給 STAFF。 */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('STAFF')")
    public AdminStatsDto stats() {
        return stats.getStats();
    }
}
