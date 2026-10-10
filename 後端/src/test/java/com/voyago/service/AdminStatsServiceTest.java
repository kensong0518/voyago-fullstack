package com.voyago.service;

import com.voyago.dto.AdminStatsDto;
import com.voyago.repository.BookingRepository;
import com.voyago.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock MemberRepository members;
    @Mock BookingRepository bookings;

    // 固定在 2026-06-15，驗證「本月新增會員」的查詢邊界算對（2026-06-01 00:00 之後）
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);
    private AdminStatsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminStatsServiceImpl(members, bookings, clock);
    }

    @Test
    void getStats_aggregatesAllCounters() {
        when(members.count()).thenReturn(42L);
        when(members.countByCreatedAtAfter(any())).thenReturn(5L);
        when(bookings.countByStatus("PENDING")).thenReturn(3L);
        when(bookings.countByStatus("CONFIRMED")).thenReturn(10L);
        when(bookings.countByStatus("CANCELLED")).thenReturn(2L);
        when(bookings.sumTotalPriceByStatus("CONFIRMED")).thenReturn(1_500_000L);

        AdminStatsDto dto = service.getStats();

        assertEquals(42L, dto.totalMembers());
        assertEquals(5L, dto.newMembersThisMonth());
        assertEquals(3L, dto.pendingBookings());
        assertEquals(10L, dto.confirmedBookings());
        assertEquals(2L, dto.cancelledBookings());
        assertEquals(1_500_000L, dto.totalRevenue());
    }
}
