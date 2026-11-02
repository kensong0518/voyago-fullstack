package com.voyago.controller;

import com.voyago.ai.AiException;
import com.voyago.ai.AiUsageLimiter;
import com.voyago.dto.AdvisorRequest;
import com.voyago.dto.AdvisorResponse;
import com.voyago.security.ClientIpResolver;
import com.voyago.service.TripAdvisorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** AI 行程顧問：免登入可用，用量由 AiUsageLimiter 控制（每位訪客每天、全站每天）。 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final TripAdvisorService advisor;
    private final AiUsageLimiter limiter;
    private final ClientIpResolver clientIp;

    public AiController(TripAdvisorService advisor, AiUsageLimiter limiter, ClientIpResolver clientIp) {
        this.advisor = advisor;
        this.limiter = limiter;
        this.clientIp = clientIp;
    }

    /** 前端用來決定要不要顯示輸入框（未設定金鑰時顯示「尚未啟用」而不是讓人送出後才失敗）。 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("enabled", advisor.isEnabled(), "dailyLimit", limiter.perClientDaily());
    }

    @PostMapping("/advisor")
    public AdvisorResponse advise(@Valid @RequestBody AdvisorRequest req, HttpServletRequest http) {
        if (!advisor.isEnabled()) {
            throw new AiException(AiException.Reason.NOT_CONFIGURED, "GEMINI_API_KEY 未設定");
        }
        // 同樣的問題已經回答過：直接回快取，不扣次數、不呼叫 AI
        var hit = advisor.cached(req.query());
        if (hit.isPresent()) {
            return new AdvisorResponse(hit.get().message(), hit.get().recommendations(), -1, true);
        }
        String client = clientIp.resolve(http);
        int remaining = limiter.acquire(client);
        try {
            var result = advisor.advise(req.query());
            return new AdvisorResponse(result.message(), result.recommendations(), remaining, false);
        } catch (AiException e) {
            // Google 端拒絕（額度用完、金鑰問題）代表沒有真的產生回答，次數還給訪客
            if (e.reason() == AiException.Reason.QUOTA_EXCEEDED || e.reason() == AiException.Reason.NOT_CONFIGURED) {
                limiter.release(client);
            }
            throw e;
        }
    }
}
