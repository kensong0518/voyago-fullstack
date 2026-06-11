package com.voyago.service;

import com.voyago.dto.BookingDto;
import com.voyago.dto.BookingRequest;
import com.voyago.dto.PageResult;
import java.util.List;

public interface BookingService {
    List<BookingDto> listMine(Long memberId);
    PageResult<BookingDto> listMinePaged(Long memberId, int page, int size);
    BookingDto create(Long memberId, BookingRequest req);
    BookingDto pay(Long memberId, Long bookingId);
    void cancel(Long memberId, Long bookingId);
}
