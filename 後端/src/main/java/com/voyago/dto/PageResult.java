package com.voyago.dto;

import java.util.List;

/** 分頁查詢結果（緯育課程慣例：內容 + 頁碼 + 總頁數 + 總筆數）。 */
public record PageResult<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
