package com.voyago.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用本機假伺服器模擬 Apps Script：POST /exec 先回 302，再到另一個網址拿 JSON 結果（Google 實際就是這樣）。
 * （isConfigured 要求 https 才啟用；送信本身不限制網址，所以可以直接打本機的 http 假伺服器。）
 */
class MailRelayClientTest {

    private HttpServer server;
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private String start(String finalBody, AtomicReference<String> received) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/exec", ex -> {
            received.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.getResponseHeaders().add("Location", "/result");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/result", ex -> {
            byte[] b = finalBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/exec";
    }

    private BookingConfirmationEmail info() {
        return new BookingConfirmationEmail(12L, "a@b.com", "王小明", "巴黎", LocalDate.parse("2027-03-01"),
                2, 101000, "EURO3000", 3000);
    }

    @Test
    void followsRedirect_andSendsOnlyBookingFields() throws Exception {
        AtomicReference<String> got = new AtomicReference<>();
        String url = start("{\"ok\":true}", got);
        new MailRelayClient(url, "s3cret", json).sendBookingConfirmation(info());

        JsonNode sent = json.readTree(got.get());
        assertEquals("s3cret", sent.get("secret").asText());
        assertEquals("booking-confirmation", sent.get("type").asText());
        assertEquals("a@b.com", sent.get("to").asText());
        assertEquals(101000, sent.get("totalPrice").asInt());
        assertEquals(3000, sent.get("discountAmount").asInt());
        assertFalse(sent.has("body") || sent.has("subject"));   // 信件內容由 Apps Script 組，不由後端指定
    }

    @Test
    void relayRefusal_isReported() throws Exception {
        String url = start("{\"ok\":false,\"error\":\"unauthorized\"}", new AtomicReference<>());
        Exception e = assertThrows(IllegalStateException.class,
                () -> new MailRelayClient(url, "wrong", json).sendBookingConfirmation(info()));
        assertTrue(e.getMessage().contains("unauthorized"));
    }

    @Test
    void htmlLoginPage_isReportedAsMisconfiguration() throws Exception {
        String url = start("<html>Sign in</html>", new AtomicReference<>());
        Exception e = assertThrows(IllegalStateException.class,
                () -> new MailRelayClient(url, "s", json).sendBookingConfirmation(info()));
        assertTrue(e.getMessage().contains("任何人"));
    }

    @Test
    void notConfigured_withoutHttpsUrlOrSecret() {
        assertFalse(new MailRelayClient("", "s", json).isConfigured());
        assertFalse(new MailRelayClient("https://script.google.com/x", "", json).isConfigured());
        assertFalse(new MailRelayClient("http://insecure/x", "s", json).isConfigured());
        assertTrue(new MailRelayClient("https://script.google.com/x", "s", json).isConfigured());
    }
}
