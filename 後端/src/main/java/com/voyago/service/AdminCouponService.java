package com.voyago.service;

import com.voyago.dto.CouponDto;
import com.voyago.dto.CouponUpsertRequest;
import com.voyago.entity.Coupon;
import com.voyago.repository.CouponRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 後台優惠碼管理。用過的優惠碼不能刪（訂單還引用它），只能停用。 */
@Service
@Transactional
public class AdminCouponService {

    private final CouponRepository coupons;
    private final AuditService audit;

    public AdminCouponService(CouponRepository coupons, AuditService audit) {
        this.coupons = coupons;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CouponDto> list() {
        return coupons.findAllByOrderByCreatedAtDescIdDesc().stream().map(CouponDto::from).toList();
    }

    public CouponDto create(CouponUpsertRequest req) {
        String code = CouponService.normalize(req.code());
        if (coupons.findByCode(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "優惠碼 " + code + " 已存在");
        }
        Coupon c = new Coupon();
        c.setCode(code);
        apply(c, req);
        coupons.save(c);
        audit.record("COUPON_CREATE", "COUPON", c.getId(), code + "：" + describe(c));
        return CouponDto.from(c);
    }

    public CouponDto update(Long id, CouponUpsertRequest req) {
        Coupon c = find(id);
        String code = CouponService.normalize(req.code());
        if (!code.equals(c.getCode())) {
            if (c.getUsedCount() > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "已被使用的優惠碼不能改代碼");
            if (coupons.findByCode(code).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "優惠碼 " + code + " 已存在");
            c.setCode(code);
        }
        if (req.maxUses() != null && req.maxUses() < c.getUsedCount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "使用上限不能小於已使用次數 " + c.getUsedCount());
        }
        apply(c, req);
        audit.record("COUPON_UPDATE", "COUPON", c.getId(), code + "：" + describe(c));
        return CouponDto.from(c);
    }

    public void delete(Long id) {
        Coupon c = find(id);
        if (c.getUsedCount() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "這個優惠碼已被使用 " + c.getUsedCount() + " 次，不能刪除，請改為停用");
        }
        coupons.delete(c);
        audit.record("COUPON_DELETE", "COUPON", id, c.getCode());
    }

    private void apply(Coupon c, CouponUpsertRequest req) {
        if (CouponService.PERCENT.equals(req.discountType()) && req.discountValue() > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "百分比折扣最多 90（即 1 折）");
        }
        c.setDescription(req.description() == null ? "" : req.description().trim());
        c.setDiscountType(req.discountType());
        c.setDiscountValue(req.discountValue());
        c.setMinAmount(req.minAmount() == null ? 0 : req.minAmount());
        c.setMaxUses(req.maxUses());
        c.setExpiresAt(req.expiresAt());
        c.setActive(req.active() == null || req.active());
    }

    private Coupon find(Long id) {
        return coupons.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到優惠碼"));
    }

    private static String describe(Coupon c) {
        String d = CouponService.PERCENT.equals(c.getDiscountType())
                ? "折 " + c.getDiscountValue() + "%" : "折 NT$" + c.getDiscountValue();
        return d + (c.getMinAmount() > 0 ? "，滿 NT$" + c.getMinAmount() : "")
                + (c.getMaxUses() != null ? "，限 " + c.getMaxUses() + " 次" : "")
                + (Boolean.TRUE.equals(c.getActive()) ? "" : "（停用）");
    }
}
