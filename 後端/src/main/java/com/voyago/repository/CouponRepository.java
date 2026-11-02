package com.voyago.repository;

import com.voyago.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    List<Coupon> findAllByOrderByCreatedAtDescIdDesc();

    /**
     * 原子地占用一次：條件寫在 UPDATE 的 WHERE 裡，由資料庫保證同時有兩筆請求搶最後一張時只有一筆成功。
     * 回傳 1＝成功、0＝已用完或已停用。不能先 SELECT 檢查再 +1——兩個請求可能同時通過檢查而超賣。
     * 不清除 persistence context（clearAutomatically）：下單流程裡已載入的會員、行程還要繼續用；
     * 代價是同一交易內已載入的 Coupon 物件 usedCount 是舊值，之後不再讀它即可。
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Coupon c SET c.usedCount = c.usedCount + 1 " +
           "WHERE c.id = :id AND c.active = true AND (c.maxUses IS NULL OR c.usedCount < c.maxUses)")
    int tryRedeem(@Param("id") Long id);

    /** 用了優惠碼的訂單被取消時把次數還回去。 */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Coupon c SET c.usedCount = c.usedCount - 1 WHERE c.code = :code AND c.usedCount > 0")
    int release(@Param("code") String code);
}
