package com.voyago.dto;

import com.voyago.entity.Booking;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 後台訂單列表：比會員自己看到的 BookingDto 多了下訂人與建立時間。 */
public record AdminBookingDto(
    Long id, Integer people, LocalDate travelDate, String status, Integer totalPrice,
    LocalDateTime createdAt,
    Long memberId, String memberName, String memberEmail,
    Long routeId, String routeName, String routeSlug
) {
    public static AdminBookingDto from(Booking b) {
        return new AdminBookingDto(b.getId(), b.getPeople(), b.getTravelDate(), b.getStatus(),
            b.getTotalPrice(), b.getCreatedAt(),
            b.getMember().getId(), b.getMember().getName(), b.getMember().getEmail(),
            b.getRoute().getId(), b.getRoute().getName(), b.getRoute().getSlug());
    }
}
