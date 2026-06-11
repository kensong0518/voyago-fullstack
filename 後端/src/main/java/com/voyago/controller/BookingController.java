package com.voyago.controller;

import com.voyago.dto.BookingDto;
import com.voyago.dto.BookingRequest;
import com.voyago.dto.PageResult;
import com.voyago.security.CurrentUser;
import com.voyago.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @GetMapping
    public List<BookingDto> myBookings() {
        return bookings.listMine(CurrentUser.id());
    }

    @GetMapping("/page")
    public PageResult<BookingDto> myBookingsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return bookings.listMinePaged(CurrentUser.id(), page, size);
    }

    @PostMapping
    public ResponseEntity<BookingDto> create(@Valid @RequestBody BookingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookings.create(CurrentUser.id(), req));
    }

    /** 模擬結帳：把待付款訂單轉為已付款（示範站不接真實金流） */
    @PostMapping("/{id}/pay")
    public BookingDto pay(@PathVariable Long id) {
        return bookings.pay(CurrentUser.id(), id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> cancel(@PathVariable Long id) {
        bookings.cancel(CurrentUser.id(), id);
        return Map.of("ok", true);
    }
}
