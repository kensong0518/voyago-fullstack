package com.voyago.service;

import com.voyago.dto.AiStatsDto;
import com.voyago.entity.AiLog;
import com.voyago.repository.AiLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 呼叫紀錄。寫入在背景執行、失敗只記 log：紀錄寫不進去不能讓使用者的提問失敗。
 * 查詢最多讀 {@value #MAX_ROWS} 筆（免費資料庫的量不大，超過就標示 truncated，不是靜默少算）。
 */
@Service
public class AiLogService {

    private static final Logger log = LoggerFactory.getLogger(AiLogService.class);
    static final int MAX_ROWS = 5000;
    static final int RETENTION_DAYS = 90;

    private final AiLogRepository logs;

    public AiLogService(AiLogRepository logs) {
        this.logs = logs;
    }

    @Async
    public void record(AiLog entry) {
        try {
            logs.save(entry);
        } catch (RuntimeException e) {
            log.warn("AI 紀錄寫入失敗（不影響使用者）：{}", e.toString());
        }
    }

    /** 每天清掉 90 天前的紀錄，免得撐爆免費資料庫的額度。 */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOld() {
        int n = logs.deleteOlderThan(LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (n > 0) log.info("AI 紀錄清理：刪除 {} 筆超過 {} 天的紀錄", n, RETENTION_DAYS);
    }

    @Transactional(readOnly = true)
    public AiStatsDto stats(int days) {
        int d = Math.min(Math.max(days, 1), RETENTION_DAYS);
        List<AiLog> rows = logs.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                LocalDateTime.now().minusDays(d), PageRequest.of(0, MAX_ROWS));
        return summarize(d, rows);
    }

    static AiStatsDto summarize(int days, List<AiLog> rows) {
        long calls = rows.size();
        long errors = rows.stream().filter(r -> !"OK".equals(r.getStatus())).count();
        long picks = rows.stream().mapToLong(r -> r.getPickSlugs().isBlank() ? 0 : r.getPickSlugs().split(",").length).sum();
        long rejected = rows.stream().mapToLong(AiLog::getRejectedCount).sum();

        List<Integer> okLat = rows.stream().filter(r -> "OK".equals(r.getStatus()))
                .map(AiLog::getLatencyMs).sorted().toList();
        long avg = okLat.isEmpty() ? 0 : Math.round(okLat.stream().mapToInt(i -> i).average().orElse(0));
        long p95 = okLat.isEmpty() ? 0 : okLat.get(Math.min(okLat.size() - 1, (int) Math.ceil(okLat.size() * 0.95) - 1));

        Map<String, Long> reasons = rows.stream().filter(r -> r.getErrorReason() != null)
                .collect(Collectors.groupingBy(AiLog::getErrorReason, TreeMap::new, Collectors.counting()));

        Map<String, List<AiLog>> byVer = rows.stream().collect(Collectors.groupingBy(AiLog::getPromptVersion, TreeMap::new, Collectors.toList()));
        List<AiStatsDto.VersionStat> versions = byVer.entrySet().stream().map(e -> {
            List<AiLog> l = e.getValue();
            long ok = l.stream().filter(r -> "OK".equals(r.getStatus())).count();
            long avgV = ok == 0 ? 0 : Math.round(l.stream().filter(r -> "OK".equals(r.getStatus())).mapToInt(AiLog::getLatencyMs).average().orElse(0));
            return new AiStatsDto.VersionStat(e.getKey(), l.size(), l.size() - ok, avgV, l.stream().mapToLong(AiLog::getRejectedCount).sum());
        }).toList();

        // 每天一格，沒有呼叫的日子也補 0，圖才不會跳日期
        LocalDate today = LocalDate.now();
        Map<LocalDate, long[]> perDay = new TreeMap<>();
        for (int i = days - 1; i >= 0; i--) perDay.put(today.minusDays(i), new long[2]);
        for (AiLog r : rows) {
            long[] c = perDay.get(r.getCreatedAt().toLocalDate());
            if (c == null) continue;
            c[0]++;
            if (!"OK".equals(r.getStatus())) c[1]++;
        }
        List<AiStatsDto.DayStat> byDay = perDay.entrySet().stream()
                .map(e -> new AiStatsDto.DayStat(e.getKey().toString(), e.getValue()[0], e.getValue()[1])).toList();

        List<AiStatsDto.Recent> recent = rows.stream().limit(20).map(r -> new AiStatsDto.Recent(
                r.getCreatedAt(), r.getPromptVersion(), r.getQueryText(), r.getStatus(), r.getErrorReason(),
                r.getLatencyMs(), r.getPickSlugs(), r.getRejectedCount())).toList();

        return new AiStatsDto(days, calls, errors, calls == 0 ? 0 : (double) errors / calls, avg, p95, picks, rejected,
                reasons, versions, byDay, recent, rows.size() >= MAX_ROWS);
    }
}
