package com.voyago.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini（generateContent REST API）。
 *
 * - 金鑰放在 x-goog-api-key header，不放網址參數，避免出現在任何 log 或錯誤訊息裡。
 * - 用 responseMimeType + responseSchema 要求模型直接輸出符合格式的 JSON（結構化輸出）。
 * - 模型名稱可由 GEMINI_MODEL 設定；預設 gemini-3.5-flash-lite（低延遲）。
 * - 思考程度（thinkingLevel）預設 low：Gemini 3 之後的 Flash 預設會先「思考」很久，
 *   挑行程這種有明確資料的任務不需要。若換到不支援這個設定的模型（回 400 且訊息提到 thinking），
 *   自動改成不帶設定重試，之後也不再帶。
 * - HTTP 用 JDK 內建的 java.net.http.HttpClient（支援 HTTP/2），整個請求（含讀完回應）一個逾時上限；
 *   回應先完整讀成字串再自己解析，狀態碼與內容都看得到，出錯時 log 才講得清楚。
 */
@Component
public class GeminiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final HttpClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private volatile String thinkingLevel;    // 空字串＝不指定，用模型預設

    public GeminiClient(ObjectMapper json,
                        @Value("${app.ai.gemini.api-key:}") String apiKey,
                        @Value("${app.ai.gemini.model:gemini-3.5-flash-lite}") String model,
                        @Value("${app.ai.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
                        @Value("${app.ai.gemini.timeout-seconds:40}") int timeoutSeconds,
                        @Value("${app.ai.gemini.thinking-level:low}") String thinkingLevel) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model.trim();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.thinkingLevel = thinkingLevel == null ? "" : thinkingLevel.trim();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public String generateJson(String systemInstruction, String userInput, Map<String, Object> responseSchema) {
        if (!isConfigured()) throw new AiException(AiException.Reason.NOT_CONFIGURED, "GEMINI_API_KEY 未設定");

        try {
            return extractText(call(systemInstruction, userInput, responseSchema, thinkingLevel));
        } catch (ThinkingUnsupported e) {
            log.warn("模型 {} 不支援 thinkingLevel={}，改用模型預設（請調整 GEMINI_THINKING_LEVEL）：{}",
                    model, thinkingLevel, e.detail);
            thinkingLevel = "";
            return extractText(call(systemInstruction, userInput, responseSchema, ""));
        }
    }

    /** 模型不接受 thinkingConfig 時丟出，由 generateJson 改成不帶設定重試一次。 */
    private static final class ThinkingUnsupported extends RuntimeException {
        final String detail;
        ThinkingUnsupported(String detail) { super(detail, null, false, false); this.detail = detail; }
    }

    private JsonNode call(String systemInstruction, String userInput, Map<String, Object> responseSchema,
                          String level) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", responseSchema);
        generationConfig.put("temperature", 0.4);
        generationConfig.put("maxOutputTokens", 2048);
        if (!level.isEmpty()) generationConfig.put("thinkingConfig", Map.of("thinkingLevel", level));

        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userInput)))),
                "generationConfig", generationConfig);

        HttpResponse<String> res;
        long t0 = System.nanoTime();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"))
                    .timeout(timeout)
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new AiException(AiException.Reason.TIMEOUT, "超過 " + timeout.toSeconds() + " 秒沒有回應", e);
        } catch (IOException e) {
            throw new AiException(AiException.Reason.TIMEOUT, "連線失敗：" + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(AiException.Reason.TIMEOUT, "請求被中斷", e);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        int code = res.statusCode();
        log.info("Gemini {} 回應 HTTP {}，{} ms，thinkingLevel={}", model, code, ms, level.isEmpty() ? "預設" : level);

        if (code / 100 != 2) {
            // 回應內容可能很長，只記前 300 字；不會含金鑰（金鑰在 request header）
            String detail = "HTTP " + code + " " + abbreviate(res.body());
            if (code == 400 && !level.isEmpty() && res.body() != null && res.body().toLowerCase().contains("thinking")) {
                throw new ThinkingUnsupported(detail);
            }
            if (code == 429) throw new AiException(AiException.Reason.QUOTA_EXCEEDED, detail);
            if (code == 401 || code == 403) {
                log.error("Gemini 金鑰無效或沒有權限：{}", detail);
                throw new AiException(AiException.Reason.NOT_CONFIGURED, detail);
            }
            if (code == 404) {
                log.error("Gemini 找不到模型 {}，請檢查 GEMINI_MODEL：{}", model, detail);
                throw new AiException(AiException.Reason.NOT_CONFIGURED, detail);
            }
            if (code == 400) log.error("Gemini 拒絕請求：{}", detail);
            throw new AiException(code >= 500 ? AiException.Reason.TIMEOUT : AiException.Reason.BAD_RESPONSE, detail);
        }
        try {
            return json.readTree(res.body());
        } catch (JsonProcessingException e) {
            throw new AiException(AiException.Reason.BAD_RESPONSE, "回應不是 JSON：" + abbreviate(res.body()), e);
        }
    }

    /** 取出第一個候選回答的文字；思考過程（thought=true）不算答案。 */
    String extractText(JsonNode res) {
        JsonNode candidate = res == null ? null : res.path("candidates").path(0);
        if (candidate == null || candidate.isMissingNode()) {
            String block = res == null ? "null" : res.path("promptFeedback").path("blockReason").asText("無候選回答");
            throw new AiException(AiException.Reason.BAD_RESPONSE, "沒有候選回答：" + block);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) continue;
            text.append(part.path("text").asText(""));
        }
        if (text.isEmpty()) {
            throw new AiException(AiException.Reason.BAD_RESPONSE,
                    "回答沒有文字，finishReason=" + candidate.path("finishReason").asText("?"));
        }
        return text.toString();
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        String oneLine = s.replaceAll("\\s+", " ");
        return oneLine.length() > 300 ? oneLine.substring(0, 300) + "…" : oneLine;
    }
}
