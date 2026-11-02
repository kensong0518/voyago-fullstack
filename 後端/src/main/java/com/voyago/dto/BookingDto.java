package com.voyago.dto;

import com.voyago.entity.Booking;
import java.time.LocalDate;

public record BookingDto(
    Long id, Integer people, LocalDate travelDate, String status, Integer totalPrice, RouteDto route,
    String couponCode, Integer discountAmount
) {
    public static BookingDto from(Booking b) {
        return new BookingDto(b.getId(), b.getPeople(), b.getTravelDate(), b.getStatus(),
            b.getTotalPrice(), RouteDto.from(b.getRoute()), b.getCouponCode(), b.getDiscountAmount());
    }
}
