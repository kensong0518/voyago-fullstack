# 雲端部署指南（全免費、免信用卡）

這份是線上站台實際使用、驗證過的部署流程。

| 元件 | 平台 | 免費方案的限制 |
|---|---|---|
| 前端（Vue SPA） | Netlify | 每月流量額度對作品集綽綽有餘 |
| 後端（Spring Boot） | Render | 閒置 15 分鐘會休眠，下次請求約 30–60 秒喚醒 |
| 資料庫 | TiDB Cloud Starter（MySQL 相容） | 每個組織最多 5 個免費實例；每月花費上限設 $0 就只用免費額度 |

目前線上：

- 前端：<https://gleeful-valkyrie-494d4e.netlify.app>
- 後端：<https://voyago-backend-c8fd.onrender.com>（`/actuator/health` 應回 `{"status":"UP"}`）

---

## 步驟 1：TiDB Cloud（資料庫）

1. 到 <https://tidbcloud.com> 用 Google 或 GitHub 登入。
2. **Create Resource** → 方案選 **Starter**（預設是付費的 Essential，要手動切換）→ Compatibility Mode 選 **MySQL Compatible**。
3. Instance Name 填 `voyago`、Region 選 `Tokyo (ap-northeast-1)`、Monthly Spending Limit 維持 **$0**。右側摘要應顯示「$0.00 maximum/month、No credit card required」，按 **Create**。
4. 狀態變成 **Active** 後，左側選 **SQL Editor**，把 `資料庫/01_schema.sql` 整份貼上，按 `Shift + Ctrl + Enter` 全部執行。應該建出 5 張表：`member`、`route`、`booking`、`favorite`、`message`。
   - **不要**執行 `02_seed.sql`：裡面有密碼公開的客服帳號。行程與示範會員會由後端第一次啟動時自動寫入。
5. 左側 **Settings → Networking**：Public Endpoint 為 Enabled，Authorized Networks 要允許所有 IP（`0.0.0.0 – 255.255.255.255`）。Render 免費方案沒有固定 IP；連線一律走 TLS 並且需要密碼。
6. 回 Overview 按 **Connect**，記下 HOST、PORT、USERNAME（類似 `xxxx.root`），按 **Generate Password** 產生密碼（只顯示一次，下一步直接貼到 Render）。

## 步驟 2：Render（後端）

1. 到 <https://dashboard.render.com/select-repo?type=blueprint>。
   - 如果 Render 的 GitHub 授權沒有開到這個 repo，在下方 **Public Git Repository** 貼上 `https://github.com/kensong0518/voyago-fullstack` 即可（repo 是公開的）。差別只在推送程式碼後不會自動重新部署，要到服務頁按 **Manual Deploy → Deploy latest commit**。
2. Render 會讀 `render.yaml`，建立 `voyago-backend`（Docker、Free、新加坡）。填入：

| 變數 | 值 |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://<HOST>:4000/voyago?sslMode=VERIFY_IDENTITY&serverTimezone=Asia/Taipei&useUnicode=true&characterEncoding=utf8` |
| `SPRING_DATASOURCE_USERNAME` | TiDB 的 USERNAME |
| `SPRING_DATASOURCE_PASSWORD` | TiDB 剛產生的密碼 |
| `FRONTEND_ORIGIN` | `https://gleeful-valkyrie-494d4e.netlify.app`（給 CORS 與 WebSocket 用，不要結尾 `/`） |
| `ADMIN_EMAILS` | 後台管理員的 Google 帳號，逗號分隔 |
| `GOOGLE_CLIENT_ID` | 跟前端 `VITE_GOOGLE_CLIENT_ID` 相同 |

   `JWT_SECRET` 由 Render 自動產生；`SEED_DEMO_STAFF=false`、JVM 參數、`SPRING_JPA_HIBERNATE_DDL_AUTO=none` 已寫在 `render.yaml`，不用手填。

3. 按 **Deploy Blueprint**。第一次建置約 5–8 分鐘，看到服務狀態 Live、`/actuator/health` 回 `UP` 就完成了。

> ⚠️ 如果第一次 Blueprint 部署失敗，Render 會把上面手填的值清掉。到服務的 **Environment** 頁確認那 6 個變數都還在，缺的用 **Add variable → Import from .env** 補回去，再按 **Save, rebuild, and deploy**。

## 步驟 3：Netlify（前端）

前端的所有部署設定都在 `netlify.toml`（建置環境變數、`/api` 反代到 Render、SPA fallback、安全標頭）。換了後端網址的話，改這份檔案的 `VITE_WS_BASE` 和 `/api/*` 那條 redirect。

**A. 手動上傳（目前線上用的方式）**

```bash
python3 scripts/build_netlify_drop.py
```

腳本會依 `netlify.toml` 建置，把 redirects / headers 轉成 `dist/_redirects`、`dist/_headers`，打包成根目錄的 `netlify-drop.zip`。到 Netlify 站台的 **Deploys** 頁，把這個 zip 拖進去就上線了。

**B. 連結 Git 自動部署**

Netlify 站台 → **Project configuration → Build & deploy → Link repository** → 選這個 repo 的 `main`。Netlify 會直接讀 `netlify.toml`，不需要再設任何環境變數。

## 步驟 4：Google 登入

到 Google Cloud Console 的 OAuth 用戶端，確認「已授權的 JavaScript 來源」包含前端網址 `https://gleeful-valkyrie-494d4e.netlify.app`。前後端的 Client ID 必須是同一個。

`ADMIN_EMAILS` 裡的帳號用 Google 登入會進後台 `/admin`；其他人登入是一般會員（前台）。名單內的 Email 不能用帳密註冊，避免被人搶註冒充管理員。

---

## 驗證清單

1. `https://<後端>/actuator/health` → `{"status":"UP"}`（包含資料庫連線檢查）。
2. `https://<前端>/api/routes` → 10 筆行程 JSON（證明 Netlify → Render → TiDB 整條通）。
3. 前端頂部橫幅應該是「不要輸入真實的密碼、信用卡或個資」，而不是「所有資料僅暫存於你的瀏覽器」（後者代表還在示範模式）。
4. 註冊一個會員、下一筆訂單，到 TiDB SQL Editor 執行 `SELECT * FROM voyago.booking;` 應該看得到。
5. 用 `ADMIN_EMAILS` 裡的 Google 帳號登入 → 自動進 `/admin`；用一般帳號打 `/admin` 會被導回會員中心，直接呼叫 `/api/admin/*` 會被後端擋下。

## 常見問題

| 狀況 | 原因與處理 |
|---|---|
| Render 顯示 `Invalid Dockerfile path` | 路徑含中文。`render.yaml` 應指向根目錄的 `./backend.Dockerfile`、`dockerContext: .` |
| 部署一直停在「Waiting for internal health check」 | 看 Logs：`AuthenticationFailedException`（mail）代表沒關掉 mail 健康檢查；完全沒輸出代表啟動太慢，確認 `JAVA_TOOL_OPTIONS` 用的是 `render.yaml` 裡的 SerialGC 設定 |
| 頁面一直轉圈或第一次很慢 | 免費方案休眠中，等 30–60 秒喚醒 |
| 前端 `/api/...` 回 404 | `_redirects` 沒進到上傳的 zip；用 `scripts/build_netlify_drop.py` 重建 |
| TiDB 拒絕連線 | JDBC URL 要有 `sslMode=VERIFY_IDENTITY`；確認 Authorized Networks 允許外部 IP |
| 行程列表是空的 | 後端第一次啟動時才會寫入行程；看 Render Logs 有沒有資料庫連線錯誤 |

## 只要純前端展示？

把 `netlify.toml` 的 `VITE_DEMO_ONLY` 改成 `"true"` 重新部署，前端完全用內建示範資料，不需要後端與資料庫。
