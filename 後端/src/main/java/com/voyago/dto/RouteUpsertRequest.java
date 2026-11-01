package com.voyago.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 後台新增 / 編輯行程的請求內容；欄位上限與資料表定義一致。 */
public record RouteUpsertRequest(
    @NotBlank(message = "請填寫網址代稱")
    @Size(max = 80, message = "網址代稱最多 80 字")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "網址代稱只能用小寫英文、數字與連字號，例如 paris-loire")
    String slug,

    @NotBlank(message = "請填寫行程名稱") @Size(max = 120, message = "行程名稱最多 120 字")
    String name,

    @NotBlank(message = "請填寫行程摘要") @Size(max = 255, message = "摘要最多 255 字")
    String summary,

    @NotBlank(message = "請填寫行程介紹")
    String description,

    @NotBlank(message = "請填寫地點") @Size(max = 120, message = "地點最多 120 字")
    String location,

    @NotBlank(message = "請填寫國家") @Size(max = 60, message = "國家最多 60 字")
    String country,

    @NotNull(message = "請填寫天數") @Min(value = 1, message = "天數至少 1 天") @Max(value = 60, message = "天數最多 60 天")
    Integer days,

    @NotNull(message = "請填寫價格") @Min(value = 0, message = "價格不能是負數")
    Integer price,

    @DecimalMin(value = "0.0", message = "評分介於 0 到 5") @DecimalMax(value = "5.0", message = "評分介於 0 到 5")
    Double rating,

    @Min(value = 0, message = "評論數不能是負數")
    Integer reviews,

    @NotBlank(message = "請填寫圖片網址") @Size(max = 500, message = "圖片網址最多 500 字")
    @Pattern(regexp = "^https?://.+", message = "圖片網址須以 http:// 或 https:// 開頭")
    String imageUrl,

    @Size(max = 255, message = "標籤最多 255 字")
    String tags,

    Boolean featured
) {}
