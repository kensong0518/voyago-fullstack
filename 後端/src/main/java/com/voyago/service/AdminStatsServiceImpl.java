package com.voyago.service;

import com.voyago.dto.AdminStatsDto;
import com.voyago.repository.BookingRepository;
import com.voyago.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** STAFF 後台儀表板統計：不碰任何寫入，純查詢彙總。 */
@Service
@Transactional(readOnly = true)
public class AdminStatsServiceImpl implements AdminStatsService {

    private final MemberRepository members;
    private final BookingRepository bookings;
    private final Clock clock;

    public AdminStatsServiceImpl(MemberRepository members, BookingRepository bookings, Clock clock) {
        this.members = members;
        this.bookings = bookings;
        this.clock = clock;
    }

    @Override
    public AdminStatsDto getStats() {
        LocalDateTime startOfMonth = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay();
        return new AdminStatsDto(
                members.count(),
                members.countByCreatedAtAfter(startOfMonth),
                bookings.countByStatus("PENDING"),
                bookings.countByStatus("CONFIRMED"),
                bookings.countByStatus("CANCELLED"),
                bookings.sumTotalPriceByStatus("CONFIRMED")
        );
    }
}
