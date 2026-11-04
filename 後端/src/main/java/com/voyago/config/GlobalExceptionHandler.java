package com.voyago.config;

import com.voyago.ai.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/** 統一把錯誤轉成 { "error": "訊息" } 的 JSON，方便前端 AJAX 處理。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final com.voyago.service.AuditService audit;

    public GlobalExceptionHandler(com.voyago.service.AuditService audit) {
        this.audit = audit;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(f -> f.getDefaultMessage()).orElse("輸入資料有誤");
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason() == null ? "發生錯誤" : ex.getReason()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadInput(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "輸入資料有誤"));
    }

    /**
     * 防禦性處理：業務層已先擋過（例如刪會員前檢查有效訂單），這裡接住的是
     * 漏檢查或併發情況下仍撞到 DB 外鍵/唯一鍵限制的情形，避免洩漏成 500 + stack trace。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "這筆資料仍被其他紀錄參照，無法刪除或修改"));
    }

    /**
     * @PreAuthorize 在方法層拒絕時拋出的例外會先抵達這裡，而不是 Spring Security 的
     * ExceptionTranslationFilter；若讓下面的 Exception 處理器接住會變成 500。
     * 已登入但權限不足 → 403。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex,
                                                                  jakarta.servlet.http.HttpServletRequest req) {
        // 已登入的人去打沒權限的 API（例如一般會員打後台），記成資安事件：可能是有人在試探
        try {
            audit.securityForCurrentUser("ACCESS_DENIED", req.getMethod() + " " + req.getRequestURI());
        } catch (RuntimeException e) {
            log.warn("記錄 ACCESS_DENIED 失敗：{}", e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "權限不足"));
    }

    /** AI 功能的錯誤：給使用者看得懂的訊息，細節（Google 回應、逾時原因）只寫 log。 */
    @ExceptionHandler(AiException.class)
    public ResponseEntity<Map<String, String>> handleAi(AiException ex) {
        if (ex.reason() == AiException.Reason.RATE_LIMITED) {
            log.info("AI rate limited: {}", ex.getMessage());
        } else {
            log.warn("AI error: {}", ex.getMessage());
        }
        return ResponseEntity.status(ex.reason().status).body(Map.of("error", ex.reason().userMessage));
    }

    /**
     * Spring 6.1 起，找不到的網址（打錯的路徑、不存在的檔案）會丟 NoResourceFoundException；
     * 若讓下面的 Exception 處理器接住，任何打錯的網址都會變成 500「伺服器發生錯誤」。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "找不到這個網址"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "伺服器發生錯誤，請稍後再試"));
    }
}
