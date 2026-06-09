package com.voyago.repository;

import com.voyago.entity.Booking;
import java.util.List;

/** 訂單資料存取層（DAO）— 以 Criteria 依會員查詢、分頁、計數。 */
public interface BookingDao {
    List<Booking> findByMember(Long memberId);
    List<Booking> findByMemberPaged(Long memberId, int page, int size);
    long countByMember(Long memberId);
}
