package com.voyago.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 用 JDK 內建的小型 HTTP 伺服器假扮 Gemini，驗證送出的請求與各種回應的處理。 */
class GeminiClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "{}";
    private volatile long delayMs = 0;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastPath.set(ex.getRequestURI().toString());
            lastKeyHeader.set(ex.getRequestHeaders().getFirst("x-goog-api-key"));
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { }
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GeminiClient client(String key, int timeoutSeconds) {
        return new GeminiClient(RestClient.builder(), json, key, "gemini-test-model",
                "http://127.0.0.1:" + server.getAddress().getPort(), timeoutSeconds);
    }

    @Test
    void sendsKeyInHeader_modelInPath_andStructuredOutputConfig() throws Exception {
        responseBody = """
            {"candidates":[{"content":{"parts":[{"text":"thinking...","thought":true},{"text":"{\\"message\\":\\"ok\\"}"}]},"finishReason":"STOP"}]}
            """;
        String out = client("secret-key", 5).generateJson("SYS", "USER", Map.of("type", "OBJECT"));

        assertEquals("{\"message\":\"ok\"}", out, "思考過程的 part 不能混進答案");
        assertEquals("/v1beta/models/gemini-test-model:generateContent", lastPath.get());
        assertFalse(lastPath.get().contains("secret-key"), "金鑰不能出現在網址");
        assertEquals("secret-key", lastKeyHeader.get());
        JsonNode body = json.readTree(lastBody.get());
        assertEquals("SYS", body.at("/systemInstruction/parts/0/text").asText());
        assertEquals("USER", body.at("/contents/0/parts/0/text").asText());
        assertEquals("application/json", body.at("/generationConfig/responseMimeType").asText());
        assertEquals("OBJECT", body.at("/generationConfig/responseSchema/type").asText());
    }

    @Test
    void notConfigured_doesNotCallTheApi() {
        AiException ex = assertThrows(AiException.class, () -> client("", 5).generateJson("s", "u", Map.of()));
        assertEquals(AiException.Reason.NOT_CONFIGURED, ex.reason());
        assertNull(lastPath.get());
    }

    @Test
    void http429_isQuotaExceeded() {
        status = 429;
        responseBody = "{\"error\":{\"code\":429,\"message\":\"Resource has been exhausted\"}}";
        AiException ex = assertThrows(AiException.class, () -> client("k", 5).generateJson("s", "u", Map.of()));
        assertEquals(AiException.Reason.QUOTA_EXCEEDED, ex.reason());
    }

    @Test
    void invalidKey_orUnknownModel_isReportedAsNotConfigured() {
        for (int code : List.of(403, 404)) {
            status = code;
            responseBody = "{\"error\":{\"code\":" + code + "}}";
            AiException ex = assertThrows(AiException.class, () -> client("k", 5).generateJson("s", "u", Map.of()));
            assertEquals(AiException.Reason.NOT_CONFIGURED, ex.reason(), "HTTP " + code);
        }
    }

    @Test
    void slowResponse_timesOut() {
        delayMs = 2_500;
        responseBody = "{\"candidates\":[]}";
        AiException ex = assertThrows(AiException.class, () -> client("k", 1).generateJson("s", "u", Map.of()));
        assertEquals(AiException.Reason.TIMEOUT, ex.reason());
    }

    @Test
    void nonJsonBody_isBadResponse_notTimeout() {
        responseBody = "<html>502 Bad Gateway</html>";
        AiException ex = assertThrows(AiException.class, () -> client("k", 5).generateJson("s", "u", Map.of()));
        assertEquals(AiException.Reason.BAD_RESPONSE, ex.reason());
    }

    @Test
    void blockedOrEmptyAnswer_isBadResponse() {
        responseBody = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
        AiException ex = assertThrows(AiException.class, () -> client("k", 5).generateJson("s", "u", Map.of()));
        assertEquals(AiException.Reason.BAD_RESPONSE, ex.reason());
        assertTrue(ex.getMessage().contains("SAFETY"));

        responseBody = "{\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"MAX_TOKENS\"}]}";
        ex = assertThrows(AiException.class, () -> client("k", 5).generateJson("s", "u", Map.of()));
        assertTrue(ex.getMessage().contains("MAX_TOKENS"));
    }
}
