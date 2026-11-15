package com.voyago.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.voyago.ai.AiException;
import com.voyago.ai.LlmClient;
import com.voyago.dto.AdvisorResponse;
import com.voyago.dto.RouteDto;
import com.voyago.entity.AiLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * AI 行程顧問：使用者用一句話描述需求，模型「只能」從資料庫裡的真實行程挑選並說明理由。
 *
 * 防止 AI 亂編（幻覺）用了三層：
 *   1. 提示詞把行程清單當資料給模型，規定只能用清單裡的 slug；
 *   2. 回傳格式的 slug 欄位限定為清單中的值（JSON Schema enum），從生成階段就限制；
 *   3. 後端再逐筆比對資料庫，不存在的一律丟掉並記錄 —— 前兩層是「請模型遵守」，這層才是「保證」。
 * 價格、天數等數字一律用資料庫的值顯示，不採用模型寫的數字。
 */
@Service
public class TripAdvisorService {

    private static final Logger log = LoggerFactory.getLogger(TripAdvisorService.class);
    static final int MAX_PICKS = 3;
    private static final int MAX_REASON_CHARS = 120;
    private static final int MAX_MESSAGE_CHARS = 200;

    private final LlmClient llm;
    private final RouteService routes;
    private final ObjectMapper json;

    /** 相同問題（含行程清單版本）一小時內直接回快取：示範時常被重複點的範例問題不會一直消耗額度。 */
    private final Cache<String, Result> answers = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    /** 提示詞放在 resources/prompts/{版本}.txt；改提示詞就新增一個檔、改這個常數，紀錄與評估報告才分得出哪一版的表現。 */
    public static final String PROMPT_VERSION = "advisor-v1";

    private final AiLogService aiLog;      // 測試或評估時可為 null：不記錄
    private final String promptTemplate;

    @Autowired
    public TripAdvisorService(LlmClient llm, RouteService routes, ObjectMapper json, AiLogService aiLog) {
        this.llm = llm;
        this.routes = routes;
        this.json = json;
        this.aiLog = aiLog;
        this.promptTemplate = loadPrompt(PROMPT_VERSION);
    }

    public TripAdvisorService(LlmClient llm, RouteService routes, ObjectMapper json) {
        this(llm, routes, json, null);
    }

    private static String loadPrompt(String version) {
        try (var in = new ClassPathResource("prompts/" + version + ".txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("找不到提示詞檔 prompts/" + version + ".txt", e);
        }
    }

    /** 回答內容（不含剩餘次數，那是每個訪客不同的）。 */
    public record Result(String message, List<AdvisorResponse.Recommendation> recommendations) {}

    public boolean isEnabled() {
        return llm.isConfigured();
    }

    public Optional<Result> cached(String query) {
        return Optional.ofNullable(answers.getIfPresent(cacheKey(normalize(query), catalog())));
    }

    public Result advise(String rawQuery) {
        String query = normalize(rawQuery);
        List<RouteDto> catalog = catalog();
        String key = cacheKey(query, catalog);
        Result hit = answers.getIfPresent(key);
        if (hit != null) return hit;

        Map<String, RouteDto> bySlug = new LinkedHashMap<>();
        catalog.forEach(r -> bySlug.put(r.slug(), r));

        long t0 = System.nanoTime();
        String output;
        try {
            output = llm.generateJson(systemInstruction(catalog), "使用者需求：<<<" + query + ">>>",
                    responseSchema(new ArrayList<>(bySlug.keySet())));
        } catch (AiException e) {
            record(query, "ERROR", e.reason().name(), t0, List.of(), 0);
            throw e;
        }
        Parsed parsed;
        try {
            parsed = parseDetailed(output, bySlug);
        } catch (AiException e) {
            record(query, "ERROR", e.reason().name(), t0, List.of(), 0);
            throw e;
        }
        record(query, "OK", null, t0,
                parsed.result().recommendations().stream().map(r -> r.route().slug()).toList(), parsed.rejected());
        Result result = parsed.result();
        answers.put(key, result);
        return result;
    }

    /** 解析結果 + 被過濾掉的行程代號數（模型編出來、資料庫沒有的）。 */
    record Parsed(Result result, int rejected) {}

    Result parse(String output, Map<String, RouteDto> bySlug) {
        return parseDetailed(output, bySlug).result();
    }

    Parsed parseDetailed(String output, Map<String, RouteDto> bySlug) {
        JsonNode root;
        try {
            root = json.readTree(output);
        } catch (JsonProcessingException e) {
            throw new AiException(AiException.Reason.BAD_RESPONSE, "不是合法 JSON：" + abbreviate(output), e);
        }
        if (root == null || !root.isObject()) {
            throw new AiException(AiException.Reason.BAD_RESPONSE, "根節點不是物件：" + abbreviate(output));
        }

        List<AdvisorResponse.Recommendation> picks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> rejected = new ArrayList<>();
        for (JsonNode p : root.path("picks")) {
            String slug = p.path("slug").asText("").trim();
            RouteDto route = bySlug.get(slug);
            if (route == null) { rejected.add(slug); continue; }      // 第三層防線：資料庫沒有就不採用
            if (!seen.add(slug)) continue;                             // 同一個行程不重複推薦
            if (picks.size() >= MAX_PICKS) break;
            String reason = clip(p.path("reason").asText(""), MAX_REASON_CHARS);
            picks.add(new AdvisorResponse.Recommendation(route, reason.isBlank() ? route.summary() : reason));
        }
        if (!rejected.isEmpty()) {
            log.warn("AI 回傳了資料庫沒有的行程，已過濾：{}", rejected);
        }

        String message = clip(root.path("message").asText(""), MAX_MESSAGE_CHARS);
        if (message.isBlank()) {
            message = picks.isEmpty() ? "目前的行程裡沒有找到符合的選擇，換個條件試試看？" : "依照你的需求，推薦這幾個行程：";
        }
        return new Parsed(new Result(message, picks), rejected.size());
    }

    private void record(String query, String status, String errorReason, long startNanos, List<String> slugs, int rejected) {
        if (aiLog == null) return;
        AiLog e = new AiLog();
        e.setPromptVersion(PROMPT_VERSION);
        e.setModel(cut(llm.model(), 60));
        e.setQueryText(cut(query, 200));
        e.setStatus(status);
        e.setErrorReason(errorReason);
        e.setLatencyMs((int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startNanos) / 1_000_000));
        e.setPickSlugs(cut(String.join(",", slugs), 200));
        e.setRejectedCount(rejected);
        aiLog.record(e);
    }

    private List<RouteDto> catalog() {
        return routes.list("", "", "featured");   // 已有 Caffeine 快取；後台改行程會清快取
    }

    String systemInstruction(List<RouteDto> catalog) {
        List<Map<String, Object>> items = catalog.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slug", r.slug());
            m.put("name", r.name());
            m.put("country", r.country());
            m.put("location", r.location());
            m.put("days", r.days());
            m.put("pricePerPersonTWD", r.price());
            m.put("tags", r.tags());
            m.put("summary", r.summary());
            return m;
        }).toList();
        String catalogJson;
        try {
            catalogJson = json.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return promptTemplate.replace("{{MAX_PICKS}}", String.valueOf(MAX_PICKS)).replace("{{CATALOG}}", catalogJson);
    }

    static Map<String, Object> responseSchema(List<String> slugs) {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "message", Map.of("type", "STRING"),
                        "picks", Map.of(
                                "type", "ARRAY",
                                "items", Map.of(
                                        "type", "OBJECT",
                                        "properties", Map.of(
                                                // 第二層防線：slug 只能是清單裡的值
                                                "slug", Map.of("type", "STRING", "enum", slugs),
                                                "reason", Map.of("type", "STRING")),
                                        "required", List.of("slug", "reason")))),
                "required", List.of("message", "picks"));
    }

    /** 去掉控制字元、合併空白；長度上限由 DTO 驗證。 */
    static String normalize(String q) {
        return q == null ? "" : q.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    }

    private static String cacheKey(String query, List<RouteDto> catalog) {
        // 行程清單內容（slug、價格、天數、名稱、標籤）變了，舊答案就失效
        int version = catalog.stream()
                .map(r -> r.slug() + ":" + r.price() + ":" + r.days() + ":" + r.name() + ":" + r.tags())
                .toList().hashCode();
        return version + "|" + query.toLowerCase();
    }

    /** 寫進資料庫欄位用：硬截斷，不加「…」，確保不超過欄位長度。 */
    private static String cut(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }

    private static String abbreviate(String s) {
        return clip(s == null ? "null" : s.replaceAll("\\s+", " "), 200);
    }
}
