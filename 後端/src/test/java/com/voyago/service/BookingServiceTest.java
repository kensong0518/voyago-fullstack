package com.voyago.service;

import com.voyago.dto.BookingDto;
import com.voyago.dto.BookingRequest;
import com.voyago.entity.Booking;
import com.voyago.entity.Member;
import com.voyago.entity.Route;
import com.voyago.repository.BookingDao;
import com.voyago.repository.BookingRepository;
import com.voyago.repository.MemberRepository;
import com.voyago.repository.RouteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock BookingDao bookingDao;
    @Mock BookingRepository bookings;
    @Mock RouteRepository routes;
    @Mock MemberRepository members;
    @InjectMocks BookingServiceImpl service;

    @Test
    void create_computesTotalPrice_andConfirms() {
        Member m = new Member(); m.setId(1L);
        Route r = new Route(); r.setId(2L); r.setPrice(50000);
        when(members.findById(1L)).thenReturn(Optional.of(m));
        when(routes.findById(2L)).thenReturn(Optional.of(r));
        when(bookings.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingDto dto = service.create(1L, new BookingRequest(2L, 3, "2026-08-01"));

        assertEquals(150000, dto.totalPrice());   // 50000 * 3
        assertEquals("PENDING", dto.status());    // 新訂單待付款，結帳後才轉 CONFIRMED
        assertEquals(3, dto.people());
    }

    @Test
    void pay_marksConfirmed_forOwner() {
        Member owner = new Member(); owner.setId(1L);
        Route r = new Route(); r.setId(2L); r.setPrice(50000);
        Booking b = new Booking(); b.setId(5L); b.setMember(owner); b.setRoute(r);
        b.setStatus("PENDING"); b.setPeople(2); b.setTotalPrice(100000);
        b.setTravelDate(java.time.LocalDate.parse("2026-08-01"));
        when(bookings.findById(5L)).thenReturn(Optional.of(b));
        when(bookings.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingDto dto = service.pay(1L, 5L);

        assertEquals("CONFIRMED", dto.status());
    }

    @Test
    void pay_throwsForbidden_whenNotOwner() {
        Member owner = new Member(); owner.setId(99L);
        Booking b = new Booking(); b.setId(5L); b.setMember(owner);
        when(bookings.findById(5L)).thenReturn(Optional.of(b));

        assertThrows(ResponseStatusException.class, () -> service.pay(1L, 5L));
        verify(bookings, never()).save(any());
    }

    @Test
    void cancel_throwsForbidden_whenNotOwner() {
        Member owner = new Member(); owner.setId(99L);
        Booking b = new Booking(); b.setId(5L); b.setMember(owner);
        when(bookings.findById(5L)).thenReturn(Optional.of(b));

        assertThrows(ResponseStatusException.class, () -> service.cancel(1L, 5L));
        verify(bookings, never()).delete(any(Booking.class));
    }
}
