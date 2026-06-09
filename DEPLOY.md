# 雲端部署指南（全免費）

把這個專案部署到網路上，**不需要信用卡、不需要付任何錢**。

組合：

| 元件 | 平台 | 免費 tier 限制 |
|---|---|---|
| 前端 (Vue SPA) | Netlify | 100 GB 流量/月，個人作品集綽綽有餘 |
| 後端 (Spring Boot) | Render | 閒置 15 分鐘會睡，下次請求約 30 秒冷啟動 |
| MySQL | TiDB Cloud Serverless | 25 GB 儲存、5 GB RU/月，MySQL 相容 |

---

## 步驟 1：建立 TiDB Cloud（免費 MySQL）

1. 到 https://tidbcloud.com → 用 Google 帳號登入（免信用卡）。
2. 點 **Create Cluster** → 選 **Serverless** → Region 選 `Tokyo (Asia-Northeast)`（亞洲最近）。
3. Cluster name 填 `voyago`，按 **Create**（約 30 秒）。
4. 建好後點進 cluster → **Connect** → **General**：
   - Endpoint Type 選 **Public**
   - Branch 選 main
   - Connect With 選 **General**
   - 點 **Generate Password**（密碼會只顯示一次，**馬上複製存好**）
5. 看 **Connection String** 區塊，記下：
   - `Host`：`gateway01.xxx.prod.aws.tidbcloud.com`
   - `Port`：通常是 `4000`
   - `User`：類似 `xxxx.root`
   - `Password`：剛剛複製的
6. 進 cluster → **SQL Editor**（左邊選單）→ 把 `資料庫/01_schema.sql` 的內容貼進去，按 **Run**。再貼 `02_seed.sql` 跑一次。

---

## 步驟 2：部署後端到 Render

1. 到 https://dashboard.render.com/select-repo?type=blueprint
2. 連結你的 GitHub 帳號（如果還沒連），選 repo **`voyago-fullstack`**。
3. Render 會自動讀到 `render.yaml`，跳出 env var 表單。逐項填：

| 變數 | 值 |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://你的HOST:4000/voyago?useSSL=true&requireSSL=true&serverTimezone=Asia/Taipei&useUnicode=true&characterEncoding=utf8` |
| `SPRING_DATASOURCE_USERNAME` | TiDB 給你的 user（類似 `xxxx.root`） |
| `SPRING_DATASOURCE_PASSWORD` | 步驟 1 存下來的密碼 |
| `FRONTEND_ORIGIN` | `https://你的-netlify-app.netlify.app`（步驟 3 拿到） |
| `JWT_SECRET` | （Render 自動產生，不用動） |
| `GOOGLE_CLIENT_ID` | 留空（或填你的 Google OAuth Client ID） |

> ⚠️ `FRONTEND_ORIGIN` 可以先填 `*` 或之後再回來改。

4. 按 **Apply** → Render 開始 build（第一次約 5–10 分鐘）。
5. Build 完成後，畫面上方會顯示後端 URL，類似 `https://voyago-backend-xxxx.onrender.com`。
6. 測試： `https://你的-render-url.onrender.com/api/routes` 應該回一串 JSON。

---

## 步驟 3：設定 Netlify

### 3-A. 連結 GitHub repo（如果還沒連）

1. 到 https://app.netlify.com → 你現有的 site → **Site configuration** → **Build & deploy** → **Link repository**。
2. 選 GitHub → repo `voyago-fullstack` → branch `main`。
3. Netlify 會讀到 `netlify.toml`，自動帶入 build 設定。

### 3-B. 設定 BACKEND_ORIGIN 環境變數

1. **Site configuration** → **Environment variables** → **Add a variable**。
2. Key：`BACKEND_ORIGIN`、Value：步驟 2 拿到的 Render URL（不要結尾 `/`）。
3. 按 **Save**。

### 3-C. 觸發重新部署

1. **Deploys** → **Trigger deploy** → **Clear cache and deploy site**。
2. 等 1–2 分鐘，build 成功就好了。

---

## 步驟 4：把 Render 的 FRONTEND_ORIGIN 補正

1. Netlify 上你的網域是 `https://xxxx.netlify.app`。
2. 回 Render dashboard → 你的 service → **Environment** → 找 `FRONTEND_ORIGIN` → 改成 Netlify 網址（不要結尾 `/`）。
3. 按 **Save Changes** → Render 會自動 redeploy（約 2 分鐘）。

---

## 步驟 5：測試

開 Netlify 網址 → 註冊一個新帳號：

- 密碼要 **≥ 8 碼且含英文與數字**（前端會即時提示）
- 註冊成功會跳到 `/dashboard`
- 試試訂購一個行程 → 在 TiDB SQL Editor 跑 `SELECT * FROM booking;` 會看到剛剛的訂單

---

## 常見問題

| 問題 | 解法 |
|---|---|
| Netlify build 失敗 | 看 deploy log；通常是 `BACKEND_ORIGIN` 沒設或拼錯 |
| Render 一直 503 | 第一次冷啟動要 30 秒，刷新等等。連續 503 看 Logs，多半是 DB 連不上（檢查 URL/密碼/防火牆 IP allowlist） |
| TiDB 拒絕連線 | TiDB Serverless 預設要 SSL，JDBC URL 一定要 `useSSL=true&requireSSL=true` |
| 註冊出現「連線發生問題」 | F12 看 Network → Status code： 404 表 Netlify proxy 沒設好；500 表後端錯，看 Render Logs |
| 行程列表是空的 | TiDB SQL Editor 跑一次 `02_seed.sql` |

---

## 不想搞這麼麻煩？純前端 Demo 模式

如果只要 UI 展示、不需要真資料庫：在 Netlify 環境變數加 `VITE_DEMO_ONLY=true`，全部走前端內建示範資料，後端不用部署。

```
VITE_DEMO_ONLY=true
```

設好後 Clear cache and deploy site，就完成了。
