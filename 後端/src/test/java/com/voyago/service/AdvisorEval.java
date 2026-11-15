package com.voyago.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyago.ai.AiException;
import com.voyago.ai.LlmClient;
import com.voyago.dto.RouteDto;
import com.voyago.entity.AiLog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 行程顧問的評估集：30 個問題，每題寫明「什麼算答對」，跑完得到通過率、失敗率、延遲、幻覺次數。
 *
 * 判分規則是看資料庫的真實欄位（價格、天數、行程代號），不是請另一個 AI 評分：結果可重現、可以放進 CI。
 * 資料放在 src/test/resources/ai-eval/：catalog.json（固定的行程清單，跟示範資料一致）、cases.json（題目與規則）。
 * 放在 com.voyago.service 套件下是為了呼叫 TripAdvisorService 的套件內可見方法。
 */
public final class AdvisorEval {

    private AdvisorEval() {}

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 一題的判分規則。沒寫的欄位不檢查。 */
    public record Expect(List<String> anyOf, List<String> noneOf, Integer maxPricePerPerson,
                         Integer maxDays, Integer minDays, Boolean empty, List<String> forbidMessage) {
        public Expect {
            anyOf = anyOf == null ? List.of() : anyOf;
            noneOf = noneOf == null ? List.of() : noneOf;
            forbidMessage = forbidMessage == null ? List.of() : forbidMessage;
        }
    }

    public record Case(String id, String category, String query, Expect expect) {}

    public record Outcome(Case testCase, boolean passed, List<String> failures, String error,
                          List<String> picks, String message, int rejected, int latencyMs) {}

    // ---------- 載入資料 ----------

    public static List<Case> loadCases() {
        try (InputStream in = AdvisorEval.class.getResourceAsStream("/ai-eval/cases.json")) {
            return List.of(JSON.readValue(in, Case[].class));
        } catch (IOException e) {
            throw new IllegalStateException("讀不到 ai-eval/cases.json", e);
        }
    }

    public static List<RouteDto> loadCatalog() {
        try (InputStream in = AdvisorEval.class.getResourceAsStream("/ai-eval/catalog.json")) {
            JsonNode root = JSON.readTree(in);
            List<RouteDto> out = new ArrayList<>();
            long id = 1;
            for (JsonNode n : root) {
                out.add(new RouteDto(id++, n.get("slug").asText(), n.get("name").asText(), n.get("summary").asText(),
                        n.get("description").asText(), n.get("location").asText(), n.get("country").asText(),
                        n.get("days").asInt(), n.get("price").asInt(), 0.0, 0, n.get("imageUrl").asText(),
                        n.get("tags").asText(), n.get("featured").asBoolean()));
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("讀不到 ai-eval/catalog.json", e);
        }
    }

    // ---------- 判分 ----------

    /** 回傳沒通過的原因；空清單＝通過。picks 是模型推薦的行程（已由後端用資料庫值還原）。 */
    public static List<String> check(Case c, List<RouteDto> picks, String message) {
        Expect e = c.expect();
        List<String> f = new ArrayList<>();
        if (picks.size() > TripAdvisorService.MAX_PICKS) f.add("推薦了 " + picks.size() + " 個，上限是 " + TripAdvisorService.MAX_PICKS);
        if (Boolean.TRUE.equals(e.empty()) && !picks.isEmpty()) {
            f.add("清單裡沒有符合的行程，應該回空，卻推薦了 " + slugs(picks));
        }
        if (!e.anyOf().isEmpty() && picks.stream().noneMatch(p -> e.anyOf().contains(p.slug()))) {
            f.add("應該至少包含 " + e.anyOf() + " 其中一個，實際是 " + slugs(picks));
        }
        for (RouteDto p : picks) {
            if (e.noneOf().contains(p.slug())) f.add("不該推薦 " + p.slug());
            if (e.maxPricePerPerson() != null && p.price() > e.maxPricePerPerson()) {
                f.add(p.slug() + " 每人 " + p.price() + " 超過上限 " + e.maxPricePerPerson());
            }
            if (e.maxDays() != null && p.days() > e.maxDays()) f.add(p.slug() + " " + p.days() + " 天，超過 " + e.maxDays() + " 天");
            if (e.minDays() != null && p.days() < e.minDays()) f.add(p.slug() + " " + p.days() + " 天，少於 " + e.minDays() + " 天");
        }
        for (String bad : e.forbidMessage()) {
            if (message != null && message.contains(bad)) f.add("回覆裡不該出現「" + bad + "」");
        }
        return f;
    }

    /** 題目本身有沒有解：要求「至少包含某行程」的題目，那個行程必須存在、且符合同一題的數字條件。 */
    public static boolean satisfiable(Case c, List<RouteDto> catalog) {
        Expect e = c.expect();
        if (e.anyOf().isEmpty()) return true;   // 沒有指定行程：回空或任何符合條件的行程都算對
        return catalog.stream().anyMatch(r -> e.anyOf().contains(r.slug())
                && (e.maxPricePerPerson() == null || r.price() <= e.maxPricePerPerson())
                && (e.maxDays() == null || r.days() <= e.maxDays())
                && (e.minDays() == null || r.days() >= e.minDays())
                && !e.noneOf().contains(r.slug()));
    }

    private static String slugs(List<RouteDto> picks) {
        return picks.stream().map(RouteDto::slug).toList().toString();
    }

    // ---------- 執行 ----------

    /** 用真正的 TripAdvisorService（含防幻覺三層）跑完所有題目；llm 可以是真的模型，也可以是測試用的假模型。 */
    public static List<Outcome> run(LlmClient llm, List<RouteDto> catalog, List<Case> cases, long delayMs) {
        RouteService routes = mock(RouteService.class);
        when(routes.list(any(), any(), any())).thenReturn(catalog);
        List<AiLog> sink = new ArrayList<>();
        AiLogService log = mock(AiLogService.class);
        doAnswer(inv -> { sink.add(inv.getArgument(0)); return null; }).when(log).record(any());
        TripAdvisorService service = new TripAdvisorService(llm, routes, JSON, log);

        List<Outcome> out = new ArrayList<>();
        for (Case c : cases) {
            int before = sink.size();
            try {
                TripAdvisorService.Result r = service.advise(c.query());
                List<RouteDto> picks = r.recommendations().stream().map(x -> x.route()).toList();
                AiLog entry = sink.size() > before ? sink.get(sink.size() - 1) : null;
                List<String> failures = check(c, picks, r.message());
                out.add(new Outcome(c, failures.isEmpty(), failures, null, picks.stream().map(RouteDto::slug).toList(),
                        r.message(), entry == null ? 0 : entry.getRejectedCount(), entry == null ? 0 : entry.getLatencyMs()));
            } catch (AiException e) {
                AiLog entry = sink.size() > before ? sink.get(sink.size() - 1) : null;
                out.add(new Outcome(c, false, List.of("呼叫失敗：" + e.reason()), e.reason().name(), List.of(), "",
                        0, entry == null ? 0 : entry.getLatencyMs()));
            }
            if (delayMs > 0) {
                try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        return out;
    }

    // ---------- 統計與報告 ----------

    public static double passRate(List<Outcome> o) {
        return o.isEmpty() ? 0 : (double) o.stream().filter(Outcome::passed).count() / o.size();
    }

    public static Map<String, double[]> byCategory(List<Outcome> o) {
        Map<String, double[]> m = new TreeMap<>();
        for (Outcome x : o) {
            double[] v = m.computeIfAbsent(x.testCase().category(), k -> new double[2]);
            v[0]++;
            if (x.passed()) v[1]++;
        }
        return m;
    }

    public static String markdown(String model, List<Outcome> o) {
        long errors = o.stream().filter(x -> x.error() != null).count();
        long halluc = o.stream().filter(x -> x.rejected() > 0).count();
        List<Integer> lat = o.stream().filter(x -> x.error() == null).map(Outcome::latencyMs).sorted().toList();
        long avg = lat.isEmpty() ? 0 : Math.round(lat.stream().mapToInt(i -> i).average().orElse(0));
        long p95 = lat.isEmpty() ? 0 : lat.get(Math.min(lat.size() - 1, (int) Math.ceil(lat.size() * 0.95) - 1));
        StringBuilder sb = new StringBuilder();
        sb.append("## AI 行程顧問評估\n\n");
        sb.append("- 提示詞版本：`").append(TripAdvisorService.PROMPT_VERSION).append("`　模型：`").append(model).append("`\n\n");
        sb.append("| 指標 | 結果 |\n|---|---|\n");
        sb.append("| 通過題數 | ").append(o.stream().filter(Outcome::passed).count()).append(" / ").append(o.size())
                .append("（").append(Math.round(passRate(o) * 100)).append("%） |\n");
        sb.append("| 呼叫失敗 | ").append(errors).append(" |\n");
        sb.append("| 模型編造行程的題數（被後端過濾） | ").append(halluc).append(" |\n");
        sb.append("| 平均延遲 / p95 | ").append(avg).append(" ms / ").append(p95).append(" ms |\n\n");
        sb.append("| 類別 | 通過 |\n|---|---|\n");
        byCategory(o).forEach((k, v) -> sb.append("| ").append(k).append(" | ").append((int) v[1]).append(" / ").append((int) v[0]).append(" |\n"));
        List<Outcome> bad = o.stream().filter(x -> !x.passed()).toList();
        if (!bad.isEmpty()) {
            sb.append("\n### 沒通過的題目\n\n");
            for (Outcome x : bad) {
                sb.append("- **").append(x.testCase().id()).append("**「").append(x.testCase().query()).append("」→ ")
                        .append(String.join("；", x.failures())).append("\n");
            }
        }
        return sb.toString();
    }

    public static String json(String model, List<Outcome> o) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("promptVersion", TripAdvisorService.PROMPT_VERSION);
        m.put("model", model);
        m.put("total", o.size());
        m.put("passed", o.stream().filter(Outcome::passed).count());
        m.put("passRate", passRate(o));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Outcome x : o) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", x.testCase().id());
            r.put("category", x.testCase().category());
            r.put("query", x.testCase().query());
            r.put("passed", x.passed());
            r.put("failures", x.failures());
            r.put("picks", x.picks());
            r.put("message", x.message());
            r.put("rejected", x.rejected());
            r.put("latencyMs", x.latencyMs());
            rows.add(r);
        }
        m.put("cases", rows);
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(m);
    }
}
