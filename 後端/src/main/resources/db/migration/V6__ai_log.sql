-- V6：AI 呼叫紀錄（每次實際呼叫模型記一筆；快取命中不記）
CREATE TABLE IF NOT EXISTS ai_log (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  prompt_version  VARCHAR(30)  NOT NULL,
  model           VARCHAR(60)  NOT NULL,
  query_text      VARCHAR(200) NOT NULL,
  status          VARCHAR(10)  NOT NULL,          -- OK / ERROR
  error_reason    VARCHAR(30)  NULL,
  latency_ms      INT          NOT NULL,
  pick_slugs      VARCHAR(200) NOT NULL DEFAULT '',
  rejected_count  INT          NOT NULL DEFAULT 0, -- 模型回傳、但資料庫沒有而被過濾掉的行程數（幻覺次數）
  PRIMARY KEY (id),
  KEY idx_ai_log_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
