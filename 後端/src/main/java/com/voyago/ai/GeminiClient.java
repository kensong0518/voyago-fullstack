package com.voyago.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini（generateContent REST API）。
 *
 * - 金鑰放在 x-goog-api-key header，不放網址參數，避免出現在任何 log 或錯誤訊息裡。
 * - 用 responseMimeType + responseSchema 要求模型直接輸出符合格式的 JSON（結構化輸出）。
 * - 模型名稱可由 GEMINI_MODEL 設定；預設用 Google 維護的「最新 Flash」別名，模型改版時不用改程式。
 * - 思考程度（thinkingLevel）預設 low：Gemini 3 之後的 Flash 預設會先「思考」很久，
 *   挑行程這種有明確資料的任務不需要，實測預設值會讓回應超過 25 秒。
 *   若換到不支援這個設定的模型（回 400 且訊息提到 thinking），自動改成不帶設定重試，之後也不再帶。
 */
@Component
public class GeminiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;
    private volatile String thinkingLevel;    // 空字串＝不指定，用模型預設

    public GeminiClient(RestClient.Builder builder, ObjectMapper json,
                        @Value("${app.ai.gemini.api-key:}") String apiKey,
                        @Value("${app.ai.gemini.model:gemini-flash-latest}") String model,
                        @Value("${app.ai.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
                        @Value("${app.ai.gemini.timeout-seconds:40}") int timeoutSeconds,
                        @Value("${app.ai.gemini.thinking-level:low}") String thinkingLevel) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(timeoutSeconds * 1_000);
        this.http = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model.trim();
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

        try {
            return http.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            HttpStatusCode code = e.getStatusCode();
            // 回應內容可能很長，只記前 300 字；不會含金鑰（金鑰在 request header）
            String detail = "HTTP " + code.value() + " " + abbreviate(e.getResponseBodyAsString());
            if (code.value() == 400 && !level.isEmpty()
                    && e.getResponseBodyAsString().toLowerCase().contains("thinking")) {
                throw new ThinkingUnsupported(detail);
            }
            if (code.value() == 429) throw new AiException(AiException.Reason.QUOTA_EXCEEDED, detail, e);
            if (code.value() == 401 || code.value() == 403) {
                log.error("Gemini 金鑰無效或沒有權限：{}", detail);
                throw new AiException(AiException.Reason.NOT_CONFIGURED, detail, e);
            }
            if (code.value() == 404) {
                log.error("Gemini 找不到模型 {}，請檢查 GEMINI_MODEL：{}", model, detail);
                throw new AiException(AiException.Reason.NOT_CONFIGURED, detail, e);
            }
            throw new AiException(AiException.Reason.TIMEOUT, detail, e);
        } catch (ResourceAccessException e) {
            throw new AiException(AiException.Reason.TIMEOUT, "連線失敗或逾時：" + e.getMessage(), e);
        } catch (RestClientException e) {
            // 讀回應內容時逾時，Spring 會包成一般的 RestClientException（不是 ResourceAccessException），
            // 要往下找原因：JSON 解析失敗是回應格式有問題；其他 I/O（逾時、斷線）才當作逾時。
            // 注意 Jackson 的解析錯誤本身也是 IOException 的子類別，所以要先判斷它。
            boolean io = false;
            for (Throwable c = e; c != null; c = c.getCause()) {
                if (c instanceof JsonProcessingException) { io = false; break; }
                if (c instanceof IOException) io = true;
            }
            throw new AiException(io ? AiException.Reason.TIMEOUT : AiException.Reason.BAD_RESPONSE,
                    "讀取回應失敗：" + e.getMessage(), e);
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
