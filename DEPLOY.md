# 雲端部署指南（全免費、免信用卡）

這份是線上站台實際使用、驗證過的部署流程。整個網站部署成**一個網址**：Render 上的一個 Docker 服務同時提供 Vue 網頁與 Spring Boot API，資料存在 TiDB Cloud。

| 元件 | 平台 | 免費方案的限制 |
|---|---|---|
| 網頁＋API（同一個服務） | Render | 閒置 15 分鐘會休眠，下次打開約 30–60 秒喚醒 |
| 資料庫 | TiDB Cloud Starter（MySQL 相容） | 每個組織最多 5 個免費實例；每月花費上限設 $0 就只用免費額度 |

目前線上：<https://voyago-backend-c8fd.onrender.com>（`/actuator/health` 應回 `{"status":"UP"}`）。
舊網址 <https://gleeful-valkyrie-494d4e.netlify.app> 會 301 轉址過來。

---

## 步驟 1：TiDB Cloud（資料庫）

1. 到 <https://tidbcloud.com> 用 Google 或 GitHub 登入。
2. **Create Resource** → 方案選 **Starter**（預設是付費的 Essential，要手動切換）→ Compatibility Mode 選 **MySQL Compatible**。
3. Instance Name 填 `voyago`、Region 選 `Tokyo (ap-northeast-1)`、Monthly Spending Limit 維持 **$0**。右側摘要應顯示「$0.00 maximum/month、No credit card required」，按 **Create**。
4. 狀態變成 **Active** 後，左側選 **SQL Editor**，把 `資料庫/01_schema.sql` 整份貼上，按 `Shift + Ctrl + Enter` 全部執行。應該建出 5 張表：`member`、`route`、`booking`、`favorite`、`message`。
   - **不要**執行 `02_seed.sql`：裡面有密碼公開的客服帳號。行程與示範會員會由後端第一次啟動時自動寫入。
5. 左側 **Settings → Networking**：Public Endpoint 為 Enabled，Authorized Networks 要允許所有 IP（`0.0.0.0 – 255.255.255.255`）。Render 免費方案沒有固定 IP；連線一律走 TLS 並且需要密碼。
6. 回 Overview 按 **Connect**，記下 HOST、PORT、USERNAME（類似 `xxxx.root`），按 **Generate Password** 產生密碼（只顯示一次，下一步直接貼到 Render）。

## 步驟 2：Render（網頁＋API）

1. 先讓 Render 讀得到這個 repo：Render 的新增頁面 → GitHub 帳號旁的 **Configure account** → 在 GitHub 的 Render 應用程式設定裡，把 `voyago-fullstack` 加進可存取的 repo。這樣推送到 `main` 就會自動部署。
2. 到 <https://dashboard.render.com/select-repo?type=blueprint> 選 `voyago-fullstack`。Render 會讀 `render.yaml`，建立 `voyago-backend`（Docker、Free、新加坡）。填入：

| 變數 | 值 |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://<HOST>:4000/voyago?sslMode=VERIFY_IDENTITY&serverTimezone=Asia/Taipei&useUnicode=true&characterEncoding=utf8` |
| `SPRING_DATASOURCE_USERNAME` | TiDB 的 USERNAME |
| `SPRING_DATASOURCE_PASSWORD` | TiDB 剛產生的密碼 |
| `FRONTEND_ORIGIN` | 這個服務自己的網址，例如 `https://voyago-backend-c8fd.onrender.com`（WebSocket 會檢查來源；不要結尾 `/`） |
| `ADMIN_EMAILS` | 後台管理員的 Google 帳號，逗號分隔 |
| `GOOGLE_CLIENT_ID` | 跟 `前端/.env.production` 的 `VITE_GOOGLE_CLIENT_ID` 相同 |

   `JWT_SECRET` 由 Render 自動產生；`SEED_DEMO_STAFF=false`、JVM 參數、`SPRING_JPA_HIBERNATE_DDL_AUTO=none` 已寫在 `render.yaml`，不用手填。服務網址要等建立後才知道，`FRONTEND_ORIGIN` 可以先填，建立後到 Environment 頁改成正確網址。

3. 按 **Deploy Blueprint**。Docker 會先建前端、再把它打包進 Spring Boot jar；第一次建置約 5–8 分鐘。看到 Live、`/actuator/health` 回 `UP` 就完成了。

> ⚠️ 如果第一次 Blueprint 部署失敗，Render 會把上面手填的值清掉。到服務的 **Environment** 頁確認那 6 個變數都還在，缺的用 **Add variable → Import from .env** 補回去，再按 **Save, rebuild, and deploy**。

之後的更新：推送到 GitHub `main` 即可，Render 會自動重新建置部署。前端的正式設定在 `前端/.env.production`（只放公開值），改了一樣推上去就生效。

## 步驟 3：AI 行程顧問與確認信（選用）

- **AI 行程顧問**：到 <https://aistudio.google.com/apikey> 免費申請金鑰，貼到 Render 的 `GEMINI_API_KEY`。沒設定時首頁顯示「尚未啟用」，其他功能不受影響。預設模型 `gemini-3.5-flash-lite`（低延遲），可用 `GEMINI_MODEL` 換。
- **訂單確認信**：Render 免費方案封鎖 SMTP，改用 Google Apps Script 寄信，照 [`scripts/mail-relay/README.md`](scripts/mail-relay/README.md) 設定（約 5 分鐘），把網址貼到 `MAIL_RELAY_URL`。`MAIL_RELAY_SECRET` 由 Render 自動產生，複製到 Apps Script 的指令碼屬性。

資料庫結構有更新時不用手動跑 SQL：後端啟動時會自動執行 `db/migration/` 裡還沒套用過的版本。

## 步驟 4：Google 登入

到 Google Cloud Console 的 OAuth 用戶端，「已授權的 JavaScript 來源」要包含網站網址（例如 `https://voyago-backend-c8fd.onrender.com`）。前後端的 Client ID 必須是同一個。

`ADMIN_EMAILS` 裡的帳號用 Google 登入會進後台 `/admin`；其他人登入是一般會員（前台）。名單內的 Email 不能用帳密註冊，避免被人搶註冒充管理員。

## （選用）舊網址轉址

以前的網址在 Netlify。`netlify.toml` 現在只做一件事：所有路徑 301 轉址到正式站。更新方式：

```bash
python3 scripts/build_netlify_drop.py
```

把產出的 `netlify-drop.zip` 拖進 Netlify 站台的 **Deploys** 頁即可。

---

## 驗證清單

1. `https://<網站>/actuator/health` → `{"status":"UP"}`（包含資料庫連線檢查）。
2. `https://<網站>/api/routes` → 10 筆行程 JSON（服務 → TiDB 是通的）。
3. 直接打開 `https://<網站>/tours/paris-loire`、重新整理也不會 404（前端路由 fallback 正常）。
4. 頂部橫幅應該是「不要輸入真實的密碼、信用卡或個資」，而不是「所有資料僅暫存於你的瀏覽器」（後者代表還在示範模式）。
5. 註冊一個會員、下一筆訂單，到 TiDB SQL Editor 執行 `SELECT * FROM voyago.booking;` 應該看得到。
6. 用 `ADMIN_EMAILS` 裡的 Google 帳號登入 → 自動進 `/admin`；一般帳號打 `/admin` 會被導回會員中心，直接呼叫 `/api/admin/*` 會被後端擋下。

## 常見問題

| 狀況 | 原因與處理 |
|---|---|
| Render 顯示 `Invalid Dockerfile path` | 路徑含中文。`render.yaml` 應指向根目錄的 `./Dockerfile`、`dockerContext: .` |
| 部署一直停在「Waiting for internal health check」 | 看 Logs：`AuthenticationFailedException`（mail）代表沒關掉 mail 健康檢查；完全沒輸出代表啟動太慢，確認 `JAVA_TOOL_OPTIONS` 用的是 `render.yaml` 裡的 SerialGC 設定 |
| 頁面一直轉圈或第一次很慢 | 免費方案休眠中，等 30–60 秒喚醒 |
| 打開網址只看到 404 / Whitelabel Error Page | jar 裡沒有前端產物：確認 build 用的是根目錄 `Dockerfile`，且 `.dockerignore` 沒有排除 `前端/` |
| 即時客服連不上 | `FRONTEND_ORIGIN` 要等於網站自己的網址 |
| TiDB 拒絕連線 | JDBC URL 要有 `sslMode=VERIFY_IDENTITY`；確認 Authorized Networks 允許外部 IP |
| 行程列表是空的 | 後端第一次啟動時才會寫入行程；看 Render Logs 有沒有資料庫連線錯誤 |

## 只要純前端展示？

把 `前端/.env.production` 的 `VITE_DEMO_ONLY` 改成 `true` 重新部署，前端完全用內建示範資料，不需要資料庫。
