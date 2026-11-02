package com.voyago.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 後台營收報表。
 * daily：每天新訂單數、其中已付款數、已付款金額（以訂單建立日計，台灣時間）。
 * topRoutes：區間內已付款金額最高的行程。
 */
public record AdminReportDto(int days, List<Daily> daily, List<TopRoute> topRoutes,
                             long orders, long paidOrders, long revenue) {
    public record Daily(LocalDate date, int orders, int paidOrders, long revenue) {}
    public record TopRoute(Long routeId, String slug, String name, int paidOrders, long revenue) {}
}
