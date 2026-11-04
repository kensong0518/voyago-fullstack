-- V5：帳號安全——登入憑證版本（可撤銷所有裝置的登入）、Email 是否驗證過所有權
ALTER TABLE member ADD COLUMN token_version INT NOT NULL DEFAULT 0;
ALTER TABLE member ADD COLUMN email_verified TINYINT(1) NOT NULL DEFAULT 0;
-- 既有的 Google 帳號，Email 都由 Google 驗證過
UPDATE member SET email_verified = 1 WHERE provider = 'GOOGLE';
