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
 * 訂單付款成功後寄確認信。非同步執行，不卡住付款的主流程；
 * 沒設定 SMTP（MAIL_HOST 空白）時直接跳過並記一筆 info log，
 * 這樣本機 / Demo 環境不用配信箱也能正常跑完整個付款流程。
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

    public BookingEmailService(JavaMailSender mailSender,
                                @Value("${spring.mail.host:}") String mailHost,
                                @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.mailHost = mailHost;
        this.from = from;
    }

    @Async("mailTaskExecutor")
    public void sendBookingConfirmation(BookingConfirmationEmail info) {
        if (!StringUtils.hasText(mailHost)) {
            log.info("MAIL_HOST 未設定，略過訂單 #{} 的確認信（Demo / 本機環境預期行為）", info.bookingId());
            return;
        }
        if (!StringUtils.hasText(info.memberEmail())) {
            log.warn("訂單 #{} 找不到會員 email，略過確認信", info.bookingId());
            return;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(info.memberEmail());
            mail.setSubject("【歐洲自助遊】訂單確認 #" + info.bookingId());
            mail.setText(buildBody(info));
            mailSender.send(mail);
            log.info("訂單 #{} 確認信已寄出至 {}", info.bookingId(), info.memberEmail());
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
                訂單總額：NT$ %d
                訂單編號：#%d

                祝旅途愉快！
                歐洲自助遊 客服中心
                """.formatted(info.memberName(), info.routeName(), travelDate, info.people(), info.totalPrice(), info.bookingId());
    }
}
