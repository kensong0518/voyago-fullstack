package com.voyago.dto;

import java.time.LocalDateTime;

/** 公開顯示的評價：作者名稱只露出部分（王*明），不回傳 email 或會員 id。 */
public record ReviewDto(Long id, String authorName, Integer rating, String content, LocalDateTime updatedAt) {}
