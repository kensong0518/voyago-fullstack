package com.voyago.service;

import com.voyago.entity.Coupon;
import com.voyago.repository.BookingRepository;
import com.voyago.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock CouponRepository coupons;
    @Mock BookingRepository bookings;
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
    private CouponService service;

    @BeforeEach
    void setUp() {
        service = new CouponService(coupons, bookings, clock);
    }

    private Coupon coupon(String type, int value) {
        Coupon c = new Coupon();
        c.setId(7L); c.setCode("TEST"); c.setDiscountType(type); c.setDiscountValue(value);
        return c;
    }

    @Test
    void percent_discountIsFloored() {
        when(coupons.findByCode("TEST")).thenReturn(Optional.of(coupon("PERCENT", 10)));
        assertEquals(5279, service.check(" test ", 52795, 1L).discount());   // 大小寫、空白都容許；無條件捨去
    }

    @Test
    void amount_neverExceedsSubtotal() {
        when(coupons.findByCode("TEST")).thenReturn(Optional.of(coupon("AMOUNT", 3000)));
        assertEquals(1000, service.check("TEST", 1000, 1L).discount());
    }

    @Test
    void rejects_belowMinimum_expired_usedUp_inactive() {
        Coupon c = coupon("AMOUNT", 3000);
        when(coupons.findByCode("TEST")).thenReturn(Optional.of(c));

        c.setMinAmount(50000);
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.check("TEST", 49999, 1L))
                .getReason().contains("50,000"));
        c.setMinAmount(0);

        c.setExpiresAt(LocalDateTime.parse("2026-05-31T23:59:59"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.check("TEST", 1000, 1L))
                .getReason().contains("過期"));
        c.setExpiresAt(null);

        c.setMaxUses(5); c.setUsedCount(5);
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.check("TEST", 1000, 1L))
                .getReason().contains("用完"));
        c.setMaxUses(null);

        c.setActive(false);
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.check("TEST", 1000, 1L))
                .getReason().contains("停用"));
    }

    @Test
    void rejects_unknownCode_andSecondUseBySameMember() {
        when(coupons.findByCode("NOPE")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.check("nope", 1000, 1L));

        when(coupons.findByCode("TEST")).thenReturn(Optional.of(coupon("PERCENT", 10)));
        when(bookings.existsByMemberIdAndCouponCodeAndStatusNot(1L, "TEST", "CANCELLED")).thenReturn(true);
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.check("TEST", 1000, 1L))
                .getReason().contains("限用一次"));
    }

    @Test
    void redeem_conflictWhenAtomicUpdateLosesRace() {
        when(coupons.findByCode("TEST")).thenReturn(Optional.of(coupon("PERCENT", 10)));
        when(coupons.tryRedeem(7L)).thenReturn(0);   // 檢查時還有，占用時被別人搶走最後一張
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.redeem("TEST", 1000, 1L));
        assertEquals(409, e.getStatusCode().value());
    }
}
