package com.voyago.service;

import com.voyago.ai.AiException;
import com.voyago.ai.LlmClient;
import com.voyago.dto.RouteDto;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.voyago.service.AdvisorEval.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 評估「工具」本身的測試（不呼叫真的模型，每次 PR 都跑）：
 * 題目資料有沒有壞、判分規則對不對、用假模型跑完整流程時數字合不合理。
 */
class AdvisorEvalHarnessTest {

    private final List<RouteDto> catalog = loadCatalog();
    private final List<Case> cases = loadCases();

    private static LlmClient fake(String answer) {
        return new LlmClient() {
            @Override public boolean isConfigured() { return true; }
            @Override public String generateJson(String s, String u, Map<String, Object> sc) { return answer; }
        };
    }

    // ---------- 題目資料 ----------

    @Test
    void dataset_has30UniqueCases_acrossTheExpectedCategories() {
        assertEquals(30, cases.size());
        Set<String> ids = new HashSet<>();
        cases.forEach(c -> assertTrue(ids.add(c.id()), "題目編號重複：" + c.id()));
        assertEquals(Set.of("match", "budget", "duration", "unsupported", "injection", "vague"),
                new HashSet<>(cases.stream().map(Case::category).toList()));
        cases.forEach(c -> assertTrue(!c.query().isBlank() && c.query().length() <= 200, c.id() + " 問題長度要在 1~200 字"));
    }

    @Test
    void dataset_everySlugExists_andEveryCaseIsSatisfiable() {
        Set<String> slugs = new HashSet<>(catalog.stream().map(RouteDto::slug).toList());
        for (Case c : cases) {
            c.expect().anyOf().forEach(s -> assertTrue(slugs.contains(s), c.id() + " 指定了不存在的行程 " + s));
            c.expect().noneOf().forEach(s -> assertTrue(slugs.contains(s), c.id() + " 指定了不存在的行程 " + s));
            assertTrue(satisfiable(c, catalog), c.id() + " 的規則在這份行程清單裡不可能達成，題目寫錯了");
        }
    }

    @Test
    void catalog_matchesTheSeedData() {
        assertEquals(10, catalog.size());
        assertTrue(catalog.stream().anyMatch(r -> r.slug().equals("norway-fjord") && r.price() == 132000 && r.days() == 9));
    }

    // ---------- 判分規則 ----------

    private Case rule(Expect e) { return new Case("t", "x", "q", e); }
    private Expect expect(List<String> anyOf, Integer maxPrice, Integer maxDays, Integer minDays, Boolean empty, List<String> forbid) {
        return new Expect(anyOf, null, maxPrice, maxDays, minDays, empty, forbid);
    }
    private RouteDto r(String slug) { return catalog.stream().filter(x -> x.slug().equals(slug)).findFirst().orElseThrow(); }

    @Test
    void check_emptyRule() {
        Case c = rule(expect(null, null, null, null, true, null));
        assertTrue(check(c, List.of(), "").isEmpty());
        assertFalse(check(c, List.of(r("norway-fjord")), "").isEmpty());
    }

    @Test
    void check_anyOfRule() {
        Case c = rule(expect(List.of("swiss-jungfrau"), null, null, null, null, null));
        assertTrue(check(c, List.of(r("norway-fjord"), r("swiss-jungfrau")), "").isEmpty());
        assertFalse(check(c, List.of(r("norway-fjord")), "").isEmpty());
        assertFalse(check(c, List.of(), "").isEmpty(), "指定要推薦某行程時，回空就是沒答對");
    }

    @Test
    void check_numericRules_useDatabaseValues() {
        Case price = rule(expect(null, 50000, null, null, null, null));
        assertTrue(check(price, List.of(r("czech-prague")), "").isEmpty());        // 42,800
        assertFalse(check(price, List.of(r("paris-loire")), "").isEmpty());        // 52,800
        Case days = rule(expect(null, null, 6, 6, null, null));
        assertTrue(check(days, List.of(r("paris-loire")), "").isEmpty());          // 6 天
        assertFalse(check(days, List.of(r("austria-vienna")), "").isEmpty());      // 7 天
        Case min = rule(expect(null, null, null, 8, null, null));
        assertTrue(check(min, List.of(r("italy-classic")), "").isEmpty());         // 8 天
        assertFalse(check(min, List.of(r("czech-prague")), "").isEmpty());         // 6 天
    }

    @Test
    void check_forbidMessageRule() {
        Case c = rule(expect(null, null, null, null, null, List.of("PWNED")));
        assertTrue(check(c, List.of(), "我只能推薦旅遊行程").isEmpty());
        assertFalse(check(c, List.of(), "PWNED").isEmpty());
    }

    // ---------- 用假模型跑完整流程 ----------

    @Test
    void run_modelThatAlwaysSaysNothingFits_passesOnlyTheCasesWhereEmptyIsAcceptable() {
        var outcomes = run(fake("{\"message\":\"目前沒有適合的行程\",\"picks\":[]}"), catalog, cases, 0);
        assertEquals(30, outcomes.size());
        long mustPick = cases.stream().filter(c -> !c.expect().anyOf().isEmpty()).count();   // 要求有指定行程的題目：10 題主題 + 1 題預算
        assertEquals(30 - mustPick, outcomes.stream().filter(Outcome::passed).count());
        assertTrue(outcomes.stream().noneMatch(o -> o.error() != null));
    }

    @Test
    void run_hallucinatingModel_isCountedButNeverReachesTheUser() {
        var outcomes = run(fake("{\"message\":\"m\",\"picks\":[{\"slug\":\"atlantis\",\"reason\":\"編的\"}]}"), catalog, cases, 0);
        assertTrue(outcomes.stream().allMatch(o -> o.rejected() == 1), "每題都要記到一次編造");
        assertTrue(outcomes.stream().allMatch(o -> o.picks().isEmpty()), "編造的行程不能出現在推薦裡");
    }

    @Test
    void run_failingModel_isReportedAsFailure_notCrash() {
        LlmClient broken = new LlmClient() {
            @Override public boolean isConfigured() { return true; }
            @Override public String generateJson(String s, String u, Map<String, Object> sc) {
                throw new AiException(AiException.Reason.TIMEOUT, "逾時");
            }
        };
        var outcomes = run(broken, catalog, cases.subList(0, 3), 0);
        assertTrue(outcomes.stream().noneMatch(Outcome::passed));
        assertTrue(outcomes.stream().allMatch(o -> "TIMEOUT".equals(o.error())));
    }

    @Test
    void report_containsTheNumbersAndTheFailures() throws Exception {
        var outcomes = run(fake("{\"message\":\"沒有\",\"picks\":[]}"), catalog, cases, 0);
        String md = markdown("fake", outcomes);
        assertTrue(md.contains("通過題數"));
        assertTrue(md.contains("沒通過的題目"));
        assertTrue(md.contains(TripAdvisorService.PROMPT_VERSION));
        assertTrue(json("fake", outcomes).contains("\"passRate\""));
    }
}
