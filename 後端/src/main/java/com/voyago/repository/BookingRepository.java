package com.voyago.repository;

import com.voyago.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** 刪除行程前檢查：只要還有任何訂單參照這個行程，就不能刪（外鍵也會擋）。 */
    boolean existsByRouteId(Long routeId);

    /**
     * STAFF 後台訂單列表：一次 fetch join 帶出下訂人與行程，避免逐筆 lazy load 的 N+1；
     * status 為空字串時列出全部。countQuery 另外寫，因為 fetch join 不能直接拿去 count。
     */
    @Query(value = "SELECT b FROM Booking b JOIN FETCH b.member JOIN FETCH b.route " +
                   "WHERE (:status = '' OR b.status = :status) ORDER BY b.createdAt DESC, b.id DESC",
           countQuery = "SELECT COUNT(b) FROM Booking b WHERE (:status = '' OR b.status = :status)")
    Page<Booking> findAllForAdmin(@Param("status") String status, Pageable pageable);

    /** 評價資格：這位會員有沒有已付款（CONFIRMED）的這個行程訂單。 */
    boolean existsByMemberIdAndRouteIdAndStatus(Long memberId, Long routeId, String status);

    /** 每位會員每個優惠碼只能用一次（已取消的訂單不算）。 */
    boolean existsByMemberIdAndCouponCodeAndStatusNot(Long memberId, String couponCode, String status);
}
