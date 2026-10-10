package com.voyago.service;

import java.time.LocalDate;

/**
 * 確認信要用到的資料用純值傳給非同步的 BookingEmailService，不要直接傳 JPA entity。
 * Booking.member / Booking.route 都是 LAZY 關聯，一旦跨到另一個沒有 Hibernate session
 * 的執行緒（@Async 本來就是另一條執行緒）才去讀，會丟 LazyInitializationException；
 * 這裡在原本的交易內（BookingServiceImpl.pay）就把需要的欄位讀出來，之後就是單純資料。
 */
public record BookingConfirmationEmail(
        Long bookingId,
        String memberEmail,
        String memberName,
        String routeName,
        LocalDate travelDate,
        Integer people,
        Integer totalPrice
) {}
