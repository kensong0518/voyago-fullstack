-- ============================================================
-- 升級：真實評價、優惠碼（已上線的資料庫執行一次；新資料庫直接跑 01_schema.sql 即可）
-- 可重複執行：每一步都先判斷是否已做過。
-- ============================================================
USE voyago;

-- 1. 評分從 0 開始：清掉原本的示範評分，之後由會員評價自動計算
ALTER TABLE route ALTER COLUMN rating SET DEFAULT 0;
UPDATE route r SET rating = 0, reviews = 0
WHERE NOT EXISTS (SELECT 1 FROM information_schema.tables
                  WHERE table_schema = DATABASE() AND table_name = 'review');

-- 2. 訂單加上優惠碼欄位
SET @has_coupon_col = (SELECT COUNT(*) FROM information_schema.columns
                       WHERE table_schema = DATABASE() AND table_name = 'booking' AND column_name = 'coupon_code');
SET @sql = IF(@has_coupon_col = 0,
  'ALTER TABLE booking ADD COLUMN coupon_code VARCHAR(30) NULL AFTER total_price, ADD COLUMN discount_amount INT NOT NULL DEFAULT 0 AFTER coupon_code, ADD KEY idx_booking_member_coupon (member_id, coupon_code)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 評價
CREATE TABLE IF NOT EXISTS review (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  member_id   BIGINT       NOT NULL,
  route_id    BIGINT       NOT NULL,
  rating      INT          NOT NULL,
  content     VARCHAR(500) NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_review_member_route (member_id, route_id),
  KEY idx_review_route_updated (route_id, updated_at),
  CONSTRAINT fk_review_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
  CONSTRAINT fk_review_route  FOREIGN KEY (route_id)  REFERENCES route(id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 優惠碼
CREATE TABLE IF NOT EXISTS coupon (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  code            VARCHAR(30)  NOT NULL,
  description     VARCHAR(100) NOT NULL DEFAULT '',
  discount_type   VARCHAR(10)  NOT NULL,
  discount_value  INT          NOT NULL,
  min_amount      INT          NOT NULL DEFAULT 0,
  max_uses        INT          NULL,
  used_count      INT          NOT NULL DEFAULT 0,
  expires_at      DATETIME     NULL,
  active          TINYINT(1)   NOT NULL DEFAULT 1,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_coupon_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO coupon (code, description, discount_type, discount_value, min_amount, max_uses) VALUES
('WELCOME10', '新會員 9 折', 'PERCENT', 10, 0, NULL),
('EURO3000', '滿 NT$50,000 折 NT$3,000', 'AMOUNT', 3000, 50000, 200);
