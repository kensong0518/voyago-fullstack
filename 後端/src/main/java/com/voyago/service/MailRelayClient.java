package com.voyago.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 透過 HTTPS 呼叫 Google Apps Script 寄信（程式碼在 scripts/mail-relay/Code.gs）。
 *
 * 為什麼不用 SMTP：Render 免費方案封鎖對外的 SMTP 連接埠（25/465/587），JavaMailSender 連不出去。
 * Apps Script 以站長自己的 Gmail 寄出，走的是一般 HTTPS，免費。
 *
 * 安全：只送「訂單欄位」，信件內容由 Apps Script 端組成，所以就算網址外流，也不能拿來寄任意內容；
 * 另外帶共用密鑰（MAIL_RELAY_SECRET），Apps Script 端比對不符就拒絕。
 */
@Component
public class MailRelayClient {

    private final String url;
    private final String secret;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            // Apps Script 的 /exec 會先回 302 轉到 googleusercontent 取結果；NORMAL 會跟著轉（POST 轉成 GET），才拿得到回應
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public MailRelayClient(@Value("${app.mail.relay-url:}") String url,
                           @Value("${app.mail.relay-secret:}") String secret,
                           ObjectMapper json) {
        this.url = url == null ? "" : url.trim();
        this.secret = secret == null ? "" : secret.trim();
        this.json = json;
    }

    public boolean isConfigured() {
        return url.startsWith("https://") && !secret.isEmpty();
    }

    /** 寄出訂單確認信；失敗丟例外，由呼叫端記錄（呼叫端是非同步，不影響付款）。 */
    public void sendBookingConfirmation(BookingConfirmationEmail info) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("secret", secret);
        body.put("type", "booking-confirmation");
        body.put("to", info.memberEmail());
        body.put("name", info.memberName());
        body.put("bookingId", info.bookingId());
        body.put("routeName", info.routeName());
        body.put("travelDate", info.travelDate() == null ? "" : info.travelDate().toString());
        body.put("people", info.people());
        body.put("totalPrice", info.totalPrice());
        body.put("couponCode", info.couponCode() == null ? "" : info.couponCode());
        body.put("discountAmount", info.discountAmount() == null ? 0 : info.discountAmount());

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("寄信服務回應 HTTP " + res.statusCode());
        }
        JsonNode out;
        try {
            out = json.readTree(res.body());
        } catch (Exception e) {
            // 部署設定錯（例如沒開放「任何人」存取）時，Google 回的是登入頁 HTML
            throw new IllegalStateException("寄信服務回應不是 JSON，請確認 Apps Script 部署的存取權為「任何人」");
        }
        if (!out.path("ok").asBoolean(false)) {
            throw new IllegalStateException("寄信服務拒絕：" + out.path("error").asText("未知原因"));
        }
    }
}
