package com.voyago.controller;

import com.voyago.dto.CouponQuoteDto;
import com.voyago.dto.CouponQuoteRequest;
import com.voyago.entity.Route;
import com.voyago.repository.RouteRepository;
import com.voyago.security.CurrentUser;
import com.voyago.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService coupons;
    private final RouteRepository routes;

    public CouponController(CouponService coupons, RouteRepository routes) {
        this.coupons = coupons;
        this.routes = routes;
    }

    /** 結帳頁試算（需登入，避免匿名大量猜優惠碼）。不占用次數，真正占用在下單時。 */
    @PostMapping("/quote")
    public CouponQuoteDto quote(@Valid @RequestBody CouponQuoteRequest req) {
        Route route = routes.findById(req.routeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到此行程"));
        int subtotal = route.getPrice() * req.people();
        CouponService.Applied a = coupons.check(req.code(), subtotal, CurrentUser.id());
        return new CouponQuoteDto(a.coupon().getCode(), a.coupon().getDescription(),
                subtotal, a.discount(), subtotal - a.discount());
    }
}
