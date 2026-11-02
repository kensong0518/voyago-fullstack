package com.voyago.service;

import com.voyago.entity.Coupon;
import com.voyago.repository.BookingRepository;
import com.voyago.repository.CouponRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 優惠碼規則集中在這裡：結帳頁試算（quote）與真正下單（redeem）走同一套檢查，金額只由後端計算。
 */
@Service
public class CouponService {

    public static final String PERCENT = "PERCENT";
    public static final String AMOUNT = "AMOUNT";

    private final CouponRepository coupons;
    private final BookingRepository bookings;
    private final Clock clock;

    public CouponService(CouponRepository coupons, BookingRepository bookings, Clock clock) {
        this.coupons = coupons;
        this.bookings = bookings;
        this.clock = clock;
    }

    /** 檢查結果：用哪張券、折多少。 */
    public record Applied(Coupon coupon, int discount) {}

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    /** 驗證這位會員能不能在這筆金額上使用這個優惠碼，並算出折抵金額（不占用次數）。 */
    @Transactional(readOnly = true)
    public Applied check(String rawCode, int subtotal, Long memberId) {
        String code = normalize(rawCode);
        Coupon c = coupons.findByCode(code)
                .orElseThrow(() -> bad("找不到這個優惠碼"));
        if (!Boolean.TRUE.equals(c.getActive())) throw bad("這個優惠碼已停用");
        if (c.getExpiresAt() != null && LocalDateTime.now(clock).isAfter(c.getExpiresAt())) throw bad("這個優惠碼已過期");
        if (c.getMaxUses() != null && c.getUsedCount() >= c.getMaxUses()) throw bad("這個優惠碼已被用完");
        if (subtotal < c.getMinAmount()) {
            throw bad("訂單滿 NT$ %,d 才能使用這個優惠碼".formatted(c.getMinAmount()));
        }
        if (memberId != null && bookings.existsByMemberIdAndCouponCodeAndStatusNot(memberId, code, "CANCELLED")) {
            throw bad("每個優惠碼每位會員限用一次");
        }
        return new Applied(c, discountFor(c, subtotal));
    }

    /** 下單時呼叫：檢查＋原子地占用一次。必須在下單的同一個交易內，訂單建立失敗會一起回滾。 */
    @Transactional
    public Applied redeem(String rawCode, int subtotal, Long memberId) {
        Applied a = check(rawCode, subtotal, memberId);
        if (coupons.tryRedeem(a.coupon().getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "這個優惠碼剛好被用完了");
        }
        return a;
    }

    /** 訂單取消時把次數還回去。 */
    @Transactional
    public void release(String code) {
        if (code != null && !code.isBlank()) coupons.release(code);
    }

    static int discountFor(Coupon c, int subtotal) {
        int d = PERCENT.equals(c.getDiscountType())
                ? (int) Math.floor(subtotal * (c.getDiscountValue() / 100.0))
                : c.getDiscountValue();
        return Math.max(0, Math.min(d, subtotal));      // 折抵不超過訂單金額，不會出現負數
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
