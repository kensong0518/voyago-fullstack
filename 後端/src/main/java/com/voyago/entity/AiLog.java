package com.voyago.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** AI 呼叫紀錄：只新增不修改。記的是「哪一版提示詞、哪個模型、花多久、有沒有出錯、模型有沒有亂編行程」。 */
@Entity
@Table(name = "ai_log")
public class AiLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "prompt_version", nullable = false, length = 30)
    private String promptVersion;

    @Column(nullable = false, length = 60)
    private String model;

    @Column(name = "query_text", nullable = false, length = 200)
    private String queryText;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "error_reason", length = 30)
    private String errorReason;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "pick_slugs", nullable = false, length = 200)
    private String pickSlugs = "";

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String errorReason) { this.errorReason = errorReason; }
    public int getLatencyMs() { return latencyMs; }
    public void setLatencyMs(int latencyMs) { this.latencyMs = latencyMs; }
    public String getPickSlugs() { return pickSlugs; }
    public void setPickSlugs(String pickSlugs) { this.pickSlugs = pickSlugs; }
    public int getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }
}
