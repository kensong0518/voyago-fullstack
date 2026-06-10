# 路線 B：本機完整跑（local MySQL 或 Docker）

在自己電腦上把「前端 → 後端 → 真實 MySQL」整條跑起來，資料存在本機 MySQL。
適合開發/驗證；別人連不到（要給別人看用路線 A）。

兩種子方案：**B1 全用 Docker Compose（最省事）** 或 **B2 各自手動跑**。

---

## B1：一鍵 Docker Compose（推薦）

專案根目錄有 `docker-compose.yml`，已設定好 MySQL（utf8mb4）、自動掛載 `資料庫/` 做初始化、
healthcheck 鏈，以及前後端容器。

1. 裝 Docker Desktop（個人/小公司免費）。
2. 複製環境變數範本並填值：
   ```bash
   cp .env.example .env
   ```
   編輯 `.env`，至少設：
   - `MYSQL_ROOT_PASSWORD`（本機隨意設一組）
   - `JWT_SECRET`（32+ 字元隨機字串）
   - `GOOGLE_CLIENT_ID`（選填）
3. 啟動：
   ```bash
   docker-compose up -d --build
   ```
   MySQL 容器第一次啟動會自動執行 `資料庫/01_schema.sql`（和 `02_seed.sql`，依 compose 掛載設定）建表灌資料。
4. 開瀏覽器：
   - 前端：`http://localhost`（nginx 容器，已把 `/api` 反代到後端容器）
   - 後端健康檢查：`http://localhost:8080/actuator/health` → `UP`
5. 驗證：註冊帳號後進 MySQL 查資料是否寫入（見下方「驗證」）。

> 這條路前端是容器內 build 的正式版，預設**不是** demo 模式（compose 不帶 `VITE_DEMO_ONLY=true`），
> 所以直接打後端、存進真 DB，無需改 netlify.toml。

停掉：`docker-compose down`（加 `-v` 連資料 volume 一起清掉、回到全新狀態）。

---

## B2：手動分開跑（不裝 Docker）

### 1. 準備 MySQL
- 裝 MySQL 8（或用既有的）。
- 載入 schema（必要時先 `gen_schema.py` 重建）：
  ```bash
  mysql -u root -p < 資料庫/01_schema.sql
  mysql -u root -p < 資料庫/02_seed.sql      # 選用：範例行程
  ```
- 確認：`mysql -u root -p -e "USE voyago; SHOW TABLES;"` 有四張表。

### 2. 跑後端
後端預設就連 `localhost:3306/voyago`（見 `application.yml`），只要 DB 帳密對得上即可。
帳密非預設（root / 123456）時用環境變數覆蓋：

PowerShell：
```powershell
$env:DB_USER="root"; $env:DB_PASSWORD="你的密碼"
$env:JWT_SECRET="本機隨意一組32字以上的字串"
cd 後端; .\mvnw.cmd spring-boot:run
```
bash：
```bash
export DB_USER=root DB_PASSWORD=你的密碼 JWT_SECRET=本機隨意一組32字以上的字串
cd 後端 && ./mvnw spring-boot:run
```
後端起在 `http://localhost:8080`。驗證 `/actuator/health` → `UP`。

### 3. 跑前端（dev 模式，打本機後端）
前端 dev server 要把 API 指到本機後端。確認**沒有**啟用 demo（dev 不吃 netlify.toml，
但若你之前在本機設過 `VITE_DEMO_ONLY` 要清掉）：

PowerShell：
```powershell
$env:VITE_API_BASE="http://localhost:8080/api"
$env:VITE_DEMO_ONLY="false"
cd 前端; npm install; npm run dev
```
開 `http://localhost:5173`。

### 4. 驗證
1. `http://localhost:8080/actuator/health` → `{"status":"UP"}`。
2. 前端註冊帳號 → MySQL 查：
   ```sql
   USE voyago; SELECT id,name,email,created_at FROM member ORDER BY id DESC LIMIT 5;
   ```
   看到新帳號 ＝ 真的寫進 DB。
3. 下訂單 → `SELECT b.id,b.member_id,b.route_id,b.total_price FROM booking b;` 對得起來 ＝ 關聯正確。
4. 重啟前端/重整，資料還在 ＝ 持久化成功（不像 demo 模式重整就還原）。

## 常見錯誤

| 症狀 | 原因 | 解法 |
|---|---|---|
| 後端啟動報 `Access denied for user` | DB 帳密不符 | 設對 `DB_USER` / `DB_PASSWORD` |
| 後端報 `Unknown database 'voyago'` | 沒載 schema | 先 `mysql < 資料庫/01_schema.sql` |
| 前端註冊「連線發生問題」 | 後端沒起 / API base 設錯 | 確認 8080 有起、`VITE_API_BASE` 指對 |
| 中文存進去變亂碼 | 連線非 utf8mb4 | schema 已是 utf8mb4；URL 保留 `characterEncoding=utf8` |
| `docker-compose up` 沒自動建表 | volume 已存在（不會重跑 init） | `docker-compose down -v` 清掉 volume 再 up |
