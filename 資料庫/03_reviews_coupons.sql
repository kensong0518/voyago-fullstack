-- ============================================================
-- 升級：真實評價、優惠碼
-- 後端啟動時會自動執行（後端/src/main/resources/db/migration/V3__reviews_coupons.sql，見 SchemaMigrator），
-- 一般不需要手動跑。要手動執行時，內容與該檔相同；新資料庫直接跑 01_schema.sql 即可。
-- ============================================================
USE voyago;
-- 評分從 0 開始：清掉原本的示範評分，之後由會員評價自動計算（這個版本只會執行一次）
ALTER TABLE route ALTER COLUMN rating SET DEFAULT 0;
UPDATE route SET rating = 0, reviews = 0;

-- 訂單加上優惠碼欄位
ALTER TABLE booking ADD COLUMN coupon_code VARCHAR(30) NULL;
ALTER TABLE booking ADD COLUMN discount_amount INT NOT NULL DEFAULT 0;
ALTER TABLE booking ADD KEY idx_booking_member_coupon (member_id, coupon_code);

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
