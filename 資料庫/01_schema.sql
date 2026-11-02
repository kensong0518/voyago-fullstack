-- ============================================================
-- Voyago 旅遊平台 — MySQL 資料庫結構
-- 執行： mysql -u root -p < 01_schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS voyago
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE voyago;

-- 會員
CREATE TABLE IF NOT EXISTS member (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(50)  NOT NULL,
  email       VARCHAR(120) NOT NULL,
  phone       VARCHAR(30),
  address     VARCHAR(255),
  birthday    DATE,
  password    VARCHAR(100) NULL,            -- BCrypt 雜湊；Google 註冊者可為 NULL
  role        VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',  -- MEMBER / STAFF
  provider    VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',   -- LOCAL / GOOGLE
  avatar_url  VARCHAR(500),
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_member_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 行程
CREATE TABLE IF NOT EXISTS route (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  slug        VARCHAR(80)  NOT NULL,
  name        VARCHAR(120) NOT NULL,
  summary     VARCHAR(255) NOT NULL,
  description TEXT         NOT NULL,
  location    VARCHAR(120) NOT NULL,
  country     VARCHAR(60)  NOT NULL,
  days        INT          NOT NULL,
  price       INT          NOT NULL,
  rating      DECIMAL(2,1) NOT NULL DEFAULT 0,   -- 由會員評價自動計算，沒有評價時為 0
  reviews     INT          NOT NULL DEFAULT 0,   -- 評價數，同上
  image_url   VARCHAR(500) NOT NULL,
  tags        VARCHAR(255) NOT NULL DEFAULT '',
  featured    TINYINT(1)   NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_route_slug (slug),
  KEY idx_route_featured_reviews (featured, reviews),
  KEY idx_route_price (price),
  KEY idx_route_rating (rating),
  KEY idx_route_days (days),
  KEY idx_route_country (country)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 訂單
CREATE TABLE IF NOT EXISTS booking (
  id           BIGINT     NOT NULL AUTO_INCREMENT,
  member_id    BIGINT     NOT NULL,
  route_id     BIGINT     NOT NULL,
  people       INT        NOT NULL DEFAULT 1,
  travel_date  DATE       NOT NULL,
  status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING/CONFIRMED/CANCELLED，新訂單一律待付款
  total_price  INT        NOT NULL,                       -- 實付金額（已扣優惠）
  coupon_code  VARCHAR(30) NULL,
  discount_amount INT     NOT NULL DEFAULT 0,
  created_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_booking_member_created (member_id, created_at DESC),
  KEY idx_booking_route (route_id),
  KEY idx_booking_travel_date (travel_date),
  KEY idx_booking_status (status),
  KEY idx_booking_member_coupon (member_id, coupon_code),
  -- RESTRICT：訂單是交易紀錄，不能因為刪會員或刪行程就連帶消失；
  -- 要刪會員／行程前，業務邏輯（MemberServiceImpl）會先檢查是否還有未取消的訂單。
  CONSTRAINT fk_booking_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE RESTRICT,
  CONSTRAINT fk_booking_route  FOREIGN KEY (route_id)  REFERENCES route(id)  ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 收藏行程（會員 × 行程，收藏跟刪帳號/刪行程一起清掉沒關係，用 CASCADE）
CREATE TABLE IF NOT EXISTS favorite (
  id          BIGINT     NOT NULL AUTO_INCREMENT,
  member_id   BIGINT     NOT NULL,
  route_id    BIGINT     NOT NULL,
  created_at  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_favorite_member_route (member_id, route_id),
  KEY idx_favorite_member (member_id),
  CONSTRAINT fk_favorite_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
  CONSTRAINT fk_favorite_route  FOREIGN KEY (route_id)  REFERENCES route(id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 客服訊息
CREATE TABLE IF NOT EXISTS message (
  id          BIGINT     NOT NULL AUTO_INCREMENT,
  member_id   BIGINT     NOT NULL,
  sender      VARCHAR(20) NOT NULL,   -- MEMBER / STAFF
  content     VARCHAR(1000) NOT NULL,
  created_at  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_message_member_created (member_id, created_at),
  CONSTRAINT fk_message_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 行程評價（只有付過款的會員能寫，每人每行程一則；route.rating / reviews 由這張表重算）
CREATE TABLE IF NOT EXISTS review (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  member_id   BIGINT       NOT NULL,
  route_id    BIGINT       NOT NULL,
  rating      INT          NOT NULL,             -- 1~5 星
  content     VARCHAR(500) NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_review_member_route (member_id, route_id),
  KEY idx_review_route_updated (route_id, updated_at),
  CONSTRAINT fk_review_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
  CONSTRAINT fk_review_route  FOREIGN KEY (route_id)  REFERENCES route(id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 優惠碼（used_count 只用條件式 UPDATE 遞增，避免超賣）
CREATE TABLE IF NOT EXISTS coupon (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  code            VARCHAR(30)  NOT NULL,
  description     VARCHAR(100) NOT NULL DEFAULT '',
  discount_type   VARCHAR(10)  NOT NULL,          -- PERCENT / AMOUNT
  discount_value  INT          NOT NULL,
  min_amount      INT          NOT NULL DEFAULT 0,
  max_uses        INT          NULL,              -- NULL = 不限次數
  used_count      INT          NOT NULL DEFAULT 0,
  expires_at      DATETIME     NULL,              -- NULL = 不會過期
  active          TINYINT(1)   NOT NULL DEFAULT 1,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_coupon_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 後台操作紀錄（只新增不修改；不設外鍵，操作者帳號刪掉後紀錄仍保留）
CREATE TABLE IF NOT EXISTS audit_log (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  actor_id     BIGINT       NULL,
  actor_email  VARCHAR(120) NOT NULL,
  action       VARCHAR(40)  NOT NULL,
  target_type  VARCHAR(20)  NOT NULL,
  target_id    BIGINT       NULL,
  detail       VARCHAR(500) NOT NULL DEFAULT '',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
