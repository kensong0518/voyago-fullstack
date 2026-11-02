package com.voyago.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;

/**
 * 訂單付款成功後寄確認信。非同步執行，不卡住付款的主流程。
 * 寄送管道依序選擇：
 *   1. MAIL_RELAY_URL（Google Apps Script，雲端免費方案用；Render 免費版封鎖 SMTP）；
 *   2. MAIL_HOST（一般 SMTP）；
 *   3. 都沒設定就跳過並記一筆 info log，本機 / Demo 環境不用配信箱也能跑完整個付款流程。
 *
 * 只收 {@link BookingConfirmationEmail}（純值），不收 Booking entity——
 * entity 的 member / route 關聯是 LAZY，@Async 另開執行緒後已經沒有
 * Hibernate session，這裡才去讀會直接炸 LazyInitializationException。
 */
@Service
public class BookingEmailService {

    private static final Logger log = LoggerFactory.getLogger(BookingEmailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final JavaMailSender mailSender;
    private final String mailHost;
    private final String from;
    private final MailRelayClient relay;

    public BookingEmailService(JavaMailSender mailSender,
                                @Value("${spring.mail.host:}") String mailHost,
                                @Value("${app.mail.from}") String from,
                                MailRelayClient relay) {
        this.mailSender = mailSender;
        this.mailHost = mailHost;
        this.from = from;
        this.relay = relay;
    }

    @Async("mailTaskExecutor")
    public void sendBookingConfirmation(BookingConfirmationEmail info) {
        if (!StringUtils.hasText(info.memberEmail())) {
            log.warn("訂單 #{} 找不到會員 email，略過確認信", info.bookingId());
            return;
        }
        if (relay.isConfigured()) {
            try {
                relay.sendBookingConfirmation(info);
                log.info("訂單 #{} 確認信已透過寄信服務寄出", info.bookingId());
            } catch (Exception e) {
                log.warn("訂單 #{} 確認信寄送失敗（寄信服務）：{}", info.bookingId(), e.getMessage());
            }
            return;
        }
        if (!StringUtils.hasText(mailHost)) {
            log.info("未設定寄信服務（MAIL_RELAY_URL / MAIL_HOST），略過訂單 #{} 的確認信", info.bookingId());
            return;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(info.memberEmail());
            mail.setSubject("【Voyago 歐洲自助遊】訂單確認 #" + info.bookingId());
            mail.setText(buildBody(info));
            mailSender.send(mail);
            log.info("訂單 #{} 確認信已寄出", info.bookingId());   // 不把會員 email 寫進 log
        } catch (Exception e) {
            // 寄信失敗不該讓付款流程受影響（這裡已經是非同步、付款早已成功），只記錄起來。
            log.warn("訂單 #{} 確認信寄送失敗：{}", info.bookingId(), e.getMessage());
        }
    }

    private String buildBody(BookingConfirmationEmail info) {
        String travelDate = info.travelDate() != null ? info.travelDate().format(DATE_FMT) : "";
        return """
                %s 您好，

                感謝您的訂購，以下是您的訂單明細：

                行程：%s
                出發日期：%s
                人數：%d 人
                %s實付金額：NT$ %,d
                訂單編號：#%d

                祝旅途愉快！
                Voyago 歐洲自助遊
                """.formatted(info.memberName(), info.routeName(), travelDate, info.people(), discountLine(info),
                info.totalPrice(), info.bookingId());
    }

    private static String discountLine(BookingConfirmationEmail info) {
        Integer d = info.discountAmount();
        if (d == null || d <= 0) return "";
        return "優惠碼 %s 折抵：NT$ %,d\n".formatted(info.couponCode(), d);
    }
}
