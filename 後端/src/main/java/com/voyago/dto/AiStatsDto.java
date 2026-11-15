package com.voyago.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 後台「AI 使用狀況」頁的資料：最近 N 天的呼叫量、失敗率、延遲、幻覺次數，以及各版提示詞的比較。 */
public record AiStatsDto(
        int days, long calls, long errors, double errorRate,
        long avgLatencyMs, long p95LatencyMs, long picks, long rejected,
        Map<String, Long> errorReasons,
        List<VersionStat> byVersion, List<DayStat> byDay, List<Recent> recent, boolean truncated) {

    public record VersionStat(String version, long calls, long errors, long avgLatencyMs, long rejected) {}
    public record DayStat(String date, long calls, long errors) {}
    public record Recent(LocalDateTime at, String version, String query, String status, String errorReason,
                         int latencyMs, String picks, int rejected) {}
}
