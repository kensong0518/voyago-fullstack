# 路線 A：雲端免費上線（TiDB Cloud Serverless + Render）

把後端 + 真實資料庫免費部署上線，再讓既有的 Netlify 前端打到它。全程免費、免綁信用卡。
專案已備好 `render.yaml`（Render Blueprint）與 `資料庫/01_schema.sql`，這份是把它們串起來的操作流程。

架構：
```
Netlify 前端 (gleeful-valkyrie-494d4e)
   └─ /api/* proxy ─→ Render 後端 (Spring Boot, Docker)
                          └─ JDBC + SSL ─→ TiDB Cloud Serverless (MySQL 相容)
```

> 有些步驟要在網頁上點（註冊、授權、複製連線字串），那部分請引導**使用者**操作；
> 能用 CLI / 改檔案自動化的部分（改 netlify.toml、重新部署、驗證）由你來做。

---

## 1. 開 TiDB Cloud Serverless（免費資料庫）

1. 到 https://tidbcloud.com → 用 Google/GitHub 註冊（免費，免綁卡）。
2. 建立一個 **Serverless** cluster（不是 Dedicated）。region 選離台灣近的，如 `Singapore` 或
   `Tokyo`。免費額度：5GB 儲存 + 每月一定 RU，作品集綽綽有餘。
3. cluster 建好後點 **Connect**：
   - **Connect With** 選 `General` / `Java`。
   - 它會給你 **Host**（像 `gateway01.<region>.prod.aws.tidbcloud.com`）、**Port** `4000`、
     **User**（像 `xxxxxxxxxxx.root`，前綴別漏）、以及要你設定的 **Password**（按 *Generate Password*
     並複製存好，只顯示一次）。
   - 資料庫名稱：建立一個叫 `voyago` 的 database（Connect 視窗或 SQL 編輯器執行 `CREATE DATABASE voyago;`，
     或直接用我們 schema 裡的 `CREATE DATABASE IF NOT EXISTS voyago`）。

## 2. 載入 schema（建表）

先確認 `資料庫/01_schema.sql` 是最新的（必要時先跑 `gen_schema.py` 重建，見 SKILL.md 步驟 0）。
兩種載入方式擇一：

**方式 1：TiDB Cloud 網頁 SQL 編輯器（最簡單）**
- 在 cluster 頁面打開 **SQL Editor / Chat2Query**，把 `01_schema.sql` 內容整段貼上執行。
- 想要範例資料就再貼 `資料庫/02_seed.sql` 執行。

**方式 2：本機 mysql client**
```bash
mysql --host gateway01.<region>.prod.aws.tidbcloud.com --port 4000 \
      -u 'xxxxxxxxxxx.root' -p --ssl-mode=VERIFY_IDENTITY \
      < 資料庫/01_schema.sql
```
（Windows 沒裝 mysql client 的話用方式 1 最省事。）

載完後驗證：`SHOW TABLES;` 應看到 `member route booking message` 四張表。

## 3. 把後端部署到 Render

1. 到 https://render.com → 用 GitHub 註冊登入（免費）。
2. **New → Blueprint** → 選 repo `kensong0518/voyago-fullstack`。Render 會讀到 `render.yaml`，
   自動建立一個叫 `voyago-backend` 的 Docker web service（free plan、Singapore、健康檢查
   `/actuator/health`）。
3. Render 會要你填 `render.yaml` 裡 `sync:false` 的環境變數——照 TiDB 的連線資料填：

   | 環境變數 | 值 |
   |---|---|
   | `SPRING_DATASOURCE_URL` | `jdbc:mysql://gateway01.<region>.prod.aws.tidbcloud.com:4000/voyago?sslMode=VERIFY_IDENTITY&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Taipei` |
   | `SPRING_DATASOURCE_USERNAME` | `xxxxxxxxxxx.root`（TiDB 給的完整帳號，含前綴）|
   | `SPRING_DATASOURCE_PASSWORD` | 你在步驟 1 產生的密碼 |
   | `FRONTEND_ORIGIN` | `https://gleeful-valkyrie-494d4e.netlify.app`（給 CORS 白名單）|
   | `GOOGLE_CLIENT_ID` | 選填；留空 Google 登入按鈕會自動隱藏 |

   `JWT_SECRET` 不用填——`render.yaml` 設了 `generateValue: true`，Render 會自動產生隨機強密鑰。

4. 按部署。第一次 build（Docker 多階段 + Maven）約 3–6 分鐘。
5. 完成後拿到後端網址，像 `https://voyago-backend-xxxx.onrender.com`。
6. 驗證後端活著且連得到 DB：開 `https://voyago-backend-xxxx.onrender.com/actuator/health`
   應回 `{"status":"UP"}`。若是 `DOWN` 多半是 DB 連線字串/密碼/SSL 有誤，看 Render 的 Logs。

## 4. 把前端切回真實模式 + 指到後端

用 skill 內建腳本改 `netlify.toml`：
```bash
python .claude/skills/voyago-db-connect/scripts/switch_demo_mode.py \
  --mode real --backend https://voyago-backend-xxxx.onrender.com
```
它會：
- `VITE_DEMO_ONLY` → `"false"`（前端開始打真 API）
- 在 `[build.environment]` 設 `BACKEND_ORIGIN`，讓 `/api/*` proxy 轉到 Render 後端

然後重新部署前端（擇一）：
```bash
# 方式 1：CLI 直接部署（會跑 npm ci && npm run build）
netlify deploy --prod --dir="前端/dist" --site=13e03acf-1fd4-4c69-8823-5e7889f1a3f8

# 方式 2：commit + push，讓 Netlify 自動 rebuild
git add netlify.toml && git commit -m "chore: connect frontend to real backend" && git push
```

## 5. 端到端驗證

1. `GET <backend>/actuator/health` → `UP`。
2. 開前端網站（強制重整 Ctrl+Shift+R）。頂部 demo 橫幅文字應變成「⚠️ 本站為展示網站，非真實旅遊服務。
   不要輸入真實的密碼、信用卡或個資。」（v-else 分支）＝ demo 已關。
3. 註冊一個新帳號 → 到 TiDB SQL 編輯器 `SELECT id,name,email FROM member;` 應看到那筆 ＝ 真的寫進 DB。
4. 登出、換瀏覽器再登入，資料還在 ＝ 持久化成功。
5. 下一張訂單 → `SELECT * FROM booking;` 應看到，且 `member_id` / `route_id` 對得起來 ＝ 關聯正確。

## 常見錯誤排查

| 症狀 | 多半原因 | 解法 |
|---|---|---|
| health 回 `DOWN`、Render log 有 `Communications link failure` / SSL | 連線字串少了 `sslMode` 或帳號漏前綴 | URL 補 `sslMode=VERIFY_IDENTITY`；USERNAME 用 TiDB 完整帳號 |
| 前端 console 出現 CORS 錯誤 | `FRONTEND_ORIGIN` 沒設或設錯 | Render 環境變數 `FRONTEND_ORIGIN` 設成前端正式網址，重新部署後端 |
| 第一個請求等很久才回 | Render free 冷啟動（閒置 15 分鐘休眠） | 正常現象；醒來後約 30 秒內恢復 |
| 註冊回 5xx | DB 沒載 schema / 表不存在 | 回步驟 2 確認 `SHOW TABLES;` 有四張表 |
| 仍走 mock、橫幅沒變 | 前端還是舊 bundle 或 demo 沒關 | 確認 `switch_demo_mode.py --mode real` 有跑、已重新部署、瀏覽器強制重整 |
