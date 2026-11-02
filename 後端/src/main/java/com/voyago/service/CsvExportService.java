package com.voyago.service;

import com.voyago.entity.Booking;
import com.voyago.entity.Member;
import com.voyago.repository.BookingRepository;
import com.voyago.repository.MemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 後台 CSV 匯出。
 * - 開頭加 UTF-8 BOM：Excel 直接雙擊開啟中文才不會變亂碼。
 * - 欄位含逗號、引號、換行時用雙引號包起來（RFC 4180）。
 * - 防 CSV 公式注入：以 = + - @ 開頭的文字前面加單引號，避免會員把名字取成 =HYPERLINK(...) 之類，
 *   管理員用 Excel 打開時被當成公式執行。
 */
@Service
@Transactional(readOnly = true)
public class CsvExportService {

    static final int MAX_ROWS = 10_000;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String BOM = "﻿";
    private static final Map<String, String> STATUS = Map.of("PENDING", "待付款", "CONFIRMED", "已付款", "CANCELLED", "已取消");

    private final BookingRepository bookings;
    private final MemberRepository members;
    private final AuditService audit;

    public CsvExportService(BookingRepository bookings, MemberRepository members, AuditService audit) {
        this.bookings = bookings;
        this.members = members;
        this.audit = audit;
    }

    public record Export(String content, int rows) {}

    @Transactional
    public Export bookings(String status) {
        String st = status == null ? "" : status.trim().toUpperCase();
        List<Booking> list = bookings.findAllForAdmin(st, PageRequest.of(0, MAX_ROWS)).getContent();
        StringBuilder sb = new StringBuilder(BOM);
        line(sb, "訂單編號", "建立時間", "狀態", "會員姓名", "會員 Email", "行程", "出發日期", "人數",
                "原價", "優惠碼", "折抵", "實付金額");
        for (Booking b : list) {
            int discount = b.getDiscountAmount() == null ? 0 : b.getDiscountAmount();
            line(sb, b.getId(), b.getCreatedAt().format(TS), STATUS.getOrDefault(b.getStatus(), b.getStatus()),
                    b.getMember().getName(), b.getMember().getEmail(), b.getRoute().getName(), b.getTravelDate(),
                    b.getPeople(), b.getTotalPrice() + discount, b.getCouponCode(), discount, b.getTotalPrice());
        }
        audit.record("EXPORT_BOOKINGS", "BOOKING", null, "匯出訂單 " + list.size() + " 筆" + (st.isEmpty() ? "" : "（" + st + "）"));
        return new Export(sb.toString(), list.size());
    }

    @Transactional
    public Export members() {
        List<Member> list = members.findAll(PageRequest.of(0, MAX_ROWS, Sort.by("id"))).getContent();
        StringBuilder sb = new StringBuilder(BOM);
        line(sb, "會員編號", "姓名", "Email", "電話", "角色", "註冊方式", "註冊時間");
        for (Member m : list) {
            line(sb, m.getId(), m.getName(), m.getEmail(), m.getPhone(),
                    "STAFF".equals(m.getRole()) ? "管理員" : "會員",
                    "GOOGLE".equals(m.getProvider()) ? "Google" : "Email",
                    m.getCreatedAt() == null ? "" : m.getCreatedAt().format(TS));
        }
        audit.record("EXPORT_MEMBERS", "MEMBER", null, "匯出會員 " + list.size() + " 筆");
        return new Export(sb.toString(), list.size());
    }

    private static void line(StringBuilder sb, Object... cells) {
        sb.append(Stream.of(cells).map(CsvExportService::cell).collect(Collectors.joining(","))).append("\r\n");
    }

    static String cell(Object v) {
        if (v == null) return "";
        String s = v.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !(v instanceof Number)) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
