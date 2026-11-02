-- V4：後台操作紀錄
CREATE TABLE IF NOT EXISTS audit_log (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  actor_id     BIGINT       NULL,                 -- 不設外鍵：操作者帳號刪掉後紀錄仍要保留
  actor_email  VARCHAR(120) NOT NULL,
  action       VARCHAR(40)  NOT NULL,
  target_type  VARCHAR(20)  NOT NULL,
  target_id    BIGINT       NULL,
  detail       VARCHAR(500) NOT NULL DEFAULT '',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
