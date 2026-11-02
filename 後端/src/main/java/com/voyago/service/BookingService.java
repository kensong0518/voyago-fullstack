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

    /** 結帳頁套用優惠碼（只限待付款訂單；已套用別的碼會先換掉）。 */
    BookingDto applyCoupon(Long memberId, Long bookingId, String code);

    /** 結帳頁移除優惠碼，恢復原價並歸還次數。 */
    BookingDto removeCoupon(Long memberId, Long bookingId);
}
