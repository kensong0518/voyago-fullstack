package com.voyago.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyago.ai.GeminiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用「真的 Gemini」跑 30 題評估。會消耗免費額度，所以預設不跑：
 * 只有設定 AI_EVAL=1 且有 GEMINI_API_KEY 才執行（GitHub 上的「AI 評估」工作手動觸發）。
 *
 *   AI_EVAL=1 GEMINI_API_KEY=... mvn -Dtest=AdvisorLiveEvalTest test
 *
 * 可調：GEMINI_MODEL、AI_EVAL_DELAY_MS（題與題之間等多久，避免撞到每分鐘次數上限，預設 4000）、
 * AI_EVAL_MIN_PASS（最低通過率，預設 0.8）。報告寫到 target/ai-eval/。
 */
@EnabledIfEnvironmentVariable(named = "AI_EVAL", matches = "1|true")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class AdvisorLiveEvalTest {

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v.trim();
    }

    @Test
    void liveModelMeetsTheBar() throws Exception {
        String model = env("GEMINI_MODEL", "gemini-3.5-flash-lite");
        var llm = new GeminiClient(new ObjectMapper(), System.getenv("GEMINI_API_KEY"), model,
                "https://generativelanguage.googleapis.com", 40, "low");
        var outcomes = AdvisorEval.run(llm, AdvisorEval.loadCatalog(), AdvisorEval.loadCases(),
                Long.parseLong(env("AI_EVAL_DELAY_MS", "4000")));

        String md = AdvisorEval.markdown(model, outcomes);
        Path dir = Path.of("target", "ai-eval");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("report.md"), md);
        Files.writeString(dir.resolve("report.json"), AdvisorEval.json(model, outcomes));
        System.out.println(md);
        String summary = System.getenv("GITHUB_STEP_SUMMARY");
        if (summary != null && !summary.isBlank()) {
            Files.writeString(Path.of(summary), md + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        double min = Double.parseDouble(env("AI_EVAL_MIN_PASS", "0.8"));
        double rate = AdvisorEval.passRate(outcomes);
        assertTrue(rate >= min, "通過率 " + Math.round(rate * 100) + "% 低於門檻 " + Math.round(min * 100) + "%，見 target/ai-eval/report.md");
    }
}
