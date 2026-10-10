package com.voyago.repository;

import com.voyago.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    /** 是否還有「非取消」狀態的訂單——刪除會員前用來擋下有效訂單被連帶清掉。 */
    boolean existsByMemberIdAndStatusNot(Long memberId, String status);

    /** STAFF 後台統計：各狀態的訂單數。 */
    long countByStatus(String status);

    /** STAFF 後台統計：總營收——只算已付款（CONFIRMED）的訂單，待付款/已取消不計入。 */
    @Query("SELECT COALESCE(SUM(b.totalPrice), 0) FROM Booking b WHERE b.status = :status")
    long sumTotalPriceByStatus(@Param("status") String status);
}
