package com.voyago.dto;

/** 結帳頁試算：原價、折抵、應付金額都由後端算，前端只負責顯示。 */
public record CouponQuoteDto(String code, String description, int subtotal, int discount, int total) {}
