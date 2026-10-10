package com.voyago.dto;

/** STAFF 後台儀表板用的統計摘要。 */
public record AdminStatsDto(
        long totalMembers,
        long newMembersThisMonth,
        long pendingBookings,
        long confirmedBookings,
        long cancelledBookings,
        long totalRevenue   // 只算已付款（CONFIRMED）的訂單總額
) {}
