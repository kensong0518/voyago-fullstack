package com.voyago.ai;

import java.util.Map;

/**
 * 大型語言模型的最小介面：給系統指令、使用者輸入與 JSON Schema，回傳符合 Schema 的 JSON 字串。
 * 業務邏輯（TripAdvisorService）只依賴這個介面：測試可以換成假的實作，之後要加第二家模型也不用改業務邏輯。
 */
public interface LlmClient {

    /** 是否已設定好（例如有 API 金鑰）。沒設定時呼叫端應直接告知「功能未啟用」，不要送出請求。 */
    boolean isConfigured();

    /**
     * @param systemInstruction 系統指令（規則、資料）
     * @param userInput         使用者原文，模型只能把它當成「需求」，不能當成指令
     * @param responseSchema    回傳格式（OpenAPI 子集，Gemini responseSchema 格式）
     * @return 模型產生的 JSON 文字
     * @throws AiException 額度用完、逾時、回應格式錯誤等
     */
    String generateJson(String systemInstruction, String userInput, Map<String, Object> responseSchema);
}
