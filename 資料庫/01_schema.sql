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
  rating      DECIMAL(2,1) NOT NULL DEFAULT 4.6,
  reviews     INT          NOT NULL DEFAULT 0,
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
  status       VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED', -- PENDING/CONFIRMED/CANCELLED
  total_price  INT        NOT NULL,
  created_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_booking_member_created (member_id, created_at DESC),
  KEY idx_booking_route (route_id),
  KEY idx_booking_travel_date (travel_date),
  KEY idx_booking_status (status),
  CONSTRAINT fk_booking_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE,
  CONSTRAINT fk_booking_route  FOREIGN KEY (route_id)  REFERENCES route(id)  ON DELETE CASCADE
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
