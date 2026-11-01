package com.voyago.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理員名單：環境變數 ADMIN_EMAILS（逗號分隔）裡的 Email，用 Google 登入時升為 STAFF 進入後台；
 * 其他人維持一般會員（前台）。
 *
 * 只認 Google 登入：帳密註冊不驗證 Email 所有權，若也認帳密，任何人搶先用管理員的 Email
 * 註冊就能拿到後台。Google 登入的 Email 已由 Google ID Token 簽章保證，無法偽造。
 * 因此名單內的 Email 也禁止走帳密註冊（見 AuthServiceImpl.register）。
 */
@Component
public class AdminEmailPolicy {

    private final Set<String> adminEmails;

    public AdminEmailPolicy(@Value("${app.admin-emails:}") String raw) {
        this.adminEmails = Arrays.stream(raw.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
