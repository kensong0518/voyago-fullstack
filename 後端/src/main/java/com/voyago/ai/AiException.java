package com.voyago.ai;

import org.springframework.http.HttpStatus;

/** AI 功能的錯誤，帶著要回給使用者的訊息與 HTTP 狀態；細節只寫進 log，不外洩給使用者。 */
public class AiException extends RuntimeException {

    public enum Reason {
        NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "AI 行程顧問尚未啟用，請先瀏覽下方的行程列表"),
        QUOTA_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE, "AI 今天的免費額度用完了，明天再來試試，或直接瀏覽行程列表"),
        RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "你今天的 AI 提問次數已用完，明天再來試試"),
        TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "AI 暫時沒有回應，請稍後再試"),
        BAD_RESPONSE(HttpStatus.BAD_GATEWAY, "AI 這次的回答格式不正確，請換個說法再試一次");

        public final HttpStatus status;
        public final String userMessage;

        Reason(HttpStatus status, String userMessage) {
            this.status = status;
            this.userMessage = userMessage;
        }
    }

    private final Reason reason;

    public AiException(Reason reason, String detail) {
        super(reason + ": " + detail);
        this.reason = reason;
    }

    public AiException(Reason reason, String detail, Throwable cause) {
        super(reason + ": " + detail, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
