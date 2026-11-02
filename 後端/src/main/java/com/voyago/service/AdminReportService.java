package com.voyago.service;

import com.voyago.dto.AdminReportDto;
import com.voyago.entity.Booking;
import com.voyago.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 營收／訂單報表。資料量是示範站等級，撈區間內訂單在 Java 端彙總，
 * 比寫各資料庫方言不同的 DATE() 分組 SQL 好測、H2 與 TiDB 行為一致。
 * 量大時再改成 SQL 端 GROUP BY 或每日彙總表。
 */
@Service
@Transactional(readOnly = true)
public class AdminReportService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final BookingRepository bookings;
    private final Clock clock;

    public AdminReportService(BookingRepository bookings, Clock clock) {
        this.bookings = bookings;
        this.clock = clock;
    }

    public AdminReportDto report(int requestedDays) {
        int days = Math.max(7, Math.min(requestedDays, 90));
        LocalDate today = LocalDate.now(clock.withZone(TAIPEI));
        LocalDate first = today.minusDays(days - 1L);

        // created_at 是以伺服器時區寫入的 LocalDateTime；伺服器與資料庫都設 Asia/Taipei（serverTimezone），直接比日期
        Map<LocalDate, int[]> counts = new LinkedHashMap<>();      // [orders, paidOrders]
        Map<LocalDate, Long> revenue = new LinkedHashMap<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            counts.put(d, new int[2]);
            revenue.put(d, 0L);
        }
        Map<Long, AdminReportDto.TopRoute> top = new LinkedHashMap<>();

        for (Booking b : bookings.findCreatedSince(first.atStartOfDay())) {
            LocalDate d = b.getCreatedAt().toLocalDate();
            int[] c = counts.get(d);
            if (c == null) continue;
            c[0]++;
            if ("CONFIRMED".equals(b.getStatus())) {
                c[1]++;
                revenue.merge(d, (long) b.getTotalPrice(), Long::sum);
                var r = b.getRoute();
                var prev = top.get(r.getId());
                top.put(r.getId(), new AdminReportDto.TopRoute(r.getId(), r.getSlug(), r.getName(),
                        (prev == null ? 0 : prev.paidOrders()) + 1,
                        (prev == null ? 0 : prev.revenue()) + b.getTotalPrice()));
            }
        }

        List<AdminReportDto.Daily> daily = new ArrayList<>();
        long orders = 0, paid = 0, sum = 0;
        for (var e : counts.entrySet()) {
            long rev = revenue.get(e.getKey());
            daily.add(new AdminReportDto.Daily(e.getKey(), e.getValue()[0], e.getValue()[1], rev));
            orders += e.getValue()[0];
            paid += e.getValue()[1];
            sum += rev;
        }
        List<AdminReportDto.TopRoute> topRoutes = top.values().stream()
                .sorted(Comparator.comparingLong(AdminReportDto.TopRoute::revenue).reversed())
                .limit(5).toList();
        return new AdminReportDto(days, daily, topRoutes, orders, paid, sum);
    }
}
