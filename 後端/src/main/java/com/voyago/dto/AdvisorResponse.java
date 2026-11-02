package com.voyago.dto;

import java.util.List;

/**
 * AI 行程顧問的回答：推薦的行程一律是資料庫裡的真實資料（RouteDto），模型只提供推薦理由。
 * remainingToday：這位訪客今天還能問幾次（從快取回答時不扣次數）。
 */
public record AdvisorResponse(String message, List<Recommendation> recommendations, int remainingToday, boolean cached) {

    public record Recommendation(RouteDto route, String reason) {}
}
