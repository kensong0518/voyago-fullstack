package com.voyago.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookingRequest(
    @NotNull Long routeId,
    @NotNull @Min(value = 1, message = "人數至少 1 人") @Max(value = 20, message = "單筆訂單最多 20 人") Integer people,
    @NotNull(message = "請選擇出發日期")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "出發日期格式須為 YYYY-MM-DD")
    String travelDate,
    @Size(max = 30, message = "優惠碼最多 30 字")
    String couponCode            // 選填
) {
    public BookingRequest(Long routeId, Integer people, String travelDate) {
        this(routeId, people, travelDate, null);
    }
}
