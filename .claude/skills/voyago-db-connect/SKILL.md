---
name: voyago-db-connect
description: >-
  重建並串聯 voyago-fullstack 專案的資料庫，把網站從「demo 示範模式」(前端 localStorage 假資料、不連後端)
  切換成「真實持久化資料庫」。依後端 JPA entity (Member / Route / Booking / Message) 重新產生對應的
  建表 SQL，並把 Spring Boot 後端接到真實 DB——雲端免費方案 (TiDB Cloud Serverless + Render) 或本機
  MySQL / Docker。Use this skill WHENEVER the user mentions「資料庫沒串起來 / 沒接上 / 連不到」、
  「把資料庫接起來 / 串起來」、「重建資料表 / 重新建表 / rebuild schema」、「切成真實 / 關掉 demo /
  真的存得進去」、「資料不會持久 / 重整就不見」、「部署資料庫 / connect database / TiDB / Render」，
  even if they don't name the project or a specific service. 只要意圖是「讓 voyago 用真實資料庫」就觸發。
---

# Voyago 資料庫重建 + 串聯

把 voyago-fullstack 從 demo 模式切換成連真實資料庫。這個 skill 同時負責兩件事：

1. **重建 (rebuild)**：依後端 4 個 JPA entity 產生對應的建表 SQL（與資料庫保持一致）。
2. **串聯 (connect)**：把 Spring Boot 後端接上真實資料庫，並把前端從 demo 模式切回真實 API。

專案根目錄：`C:\Users\ken\Desktop\桌面檔案\voyago-fullstack-main`
（檔案工具也可用 `C:\Users\ken\Desktop\voyago-fullstack-main`，兩者指向同一份。）

---

## 先搞懂現況，再動手

這個專案**故意**被設成 demo 模式，所以「沒串起來」是設計使然、不是壞掉：

- 前端 `netlify.toml` 設了 `VITE_DEMO_ONLY = "true"` → `前端/src/api/api.js` 的 `withFallback`
  會**直接回傳 mock**，完全不打後端。資料只存在瀏覽器 localStorage，重整換裝置就還原。
- 後端 `application.yml` 預設連 `localhost:3306/voyago`，但線上根本沒有這個後端在跑。

**要「串起來」= 讓前端打到一個真的有資料庫的後端。** 兩條路，先讓使用者選（除非他已指定）：

| 路線 | 適合 | 代價 | 細節檔 |
|---|---|---|---|
| **A. 雲端免費上線** | 想要真的網址、別人連得到、資料永久存 | 需註冊 TiDB Cloud + Render（免費、免綁卡） | `references/cloud-tidb-render.md` |
| **B. 本機完整跑** | 只在自己電腦驗證全端 | 需本機 MySQL 或 Docker | `references/local-mysql.md` |

> 不確定差異時，讀 `references/schema-notes.md` 給使用者解釋，再選。

---

## 步驟總覽

### 步驟 0：重建對應的建表 SQL（兩條路都先做）

用 `scripts/gen_schema.py` 依 entity 重新產生 DDL，確保資料表跟程式碼一致：

```bash
python .claude/skills/voyago-db-connect/scripts/gen_schema.py \
  --entities 後端/src/main/java/com/voyago/entity \
  --out 資料庫/01_schema.sql --drop
```

- `--drop` 會在前面加上 FK-safe 的 `DROP TABLE`，產生「乾淨重建」腳本。要保留既有資料就拿掉。
- 產生後**和現有 `資料庫/01_schema.sql` 比對差異**再覆蓋；entity 沒改的話應該幾乎一致。
- 型別/索引對應與 TiDB 注意事項見 `references/schema-notes.md`。

後端 `application.yml` 是 `ddl-auto: update`，連到空 DB 時 Hibernate 也會自動補表；但**先手動載入這份
SQL 比較好**，因為它含手調的索引（`idx_route_*`、`idx_booking_member_created` 等），ddl-auto 不會建那些。

可選：載入 `資料庫/02_seed.sql` 灌入範例行程資料，前台才有東西可逛。

### 步驟 1：建立資料庫 + 載入 schema

- **路線 A**：在 TiDB Cloud 開一個免費 Serverless cluster，用它的 SQL 編輯器或 `mysql` client 載入
  `01_schema.sql`（+ 選用 `02_seed.sql`）。詳見 `references/cloud-tidb-render.md`。
- **路線 B**：本機 `mysql -u root -p < 資料庫/01_schema.sql`，或用 `docker-compose up`（compose 已掛載
  `資料庫/` 自動初始化）。詳見 `references/local-mysql.md`。

### 步驟 2：把後端接上資料庫

後端用環境變數接 DB，**不用改 code**：

- `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`
  （Spring relaxed-binding 會覆蓋 `application.yml` 的 localhost 預設）。
- `JWT_SECRET`（32+ 字元）、`FRONTEND_ORIGIN`（前端網址，給 CORS）、選用 `GOOGLE_CLIENT_ID`。

路線 A 把這些填到 Render（`render.yaml` 已宣告好欄位，部署時會問）。
路線 B 在本機 shell `export` / `$env:` 設定後再啟動後端。

### 步驟 3：把前端切回真實模式

用 `scripts/switch_demo_mode.py` 改 `netlify.toml`：

```bash
# 切成真實：關掉 demo、設定後端網址
python .claude/skills/voyago-db-connect/scripts/switch_demo_mode.py \
  --mode real --backend https://voyago-backend-xxxx.onrender.com

# 要切回 demo（純前端展示）：
python .claude/skills/voyago-db-connect/scripts/switch_demo_mode.py --mode demo
```

`--mode real` 會把 `VITE_DEMO_ONLY` 設成 `"false"`、把 `/api/*` proxy 的 `BACKEND_ORIGIN` 指到後端。
改完重新部署前端（`netlify deploy --prod`，或 push 觸發 Netlify rebuild）。

### 步驟 4：驗證真的串起來了

1. 後端健康檢查：`GET <backend>/actuator/health` 應回 `{"status":"UP"}`（含 DB 連線）。
2. 開前端，**頂部 demo 橫幅文字應從「所有資料僅暫存於你的瀏覽器…」變回「不要輸入真實的密碼…」**
   （因為 `VITE_DEMO_ONLY` 變 false，`DemoBanner.vue` 走 v-else 分支）——這是 demo 是否關掉的快速指標。
3. 註冊一個帳號 → 去 TiDB/MySQL 查 `SELECT * FROM member;` 應看到那筆資料 = 真的寫進 DB 了。
4. 重整頁面 / 換瀏覽器登入，資料還在 = 持久化成功。

完整驗證清單與常見錯誤（SSL handshake、CORS、Render 冷啟動、FK 報錯）見對應 reference 檔末段。

---

## 重要前提與雷區

- **改的是設定/SQL，不是 entity**：entity 與 `01_schema.sql` 已經對齊，不要為了「串起來」去亂改欄位。
  真要改資料模型，先改 entity → 再跑 `gen_schema.py` 重建 SQL → 同步更新兩邊。
- **不要把密碼進版控**：DB 密碼、JWT secret 只放在 Render/本機環境變數，別寫進 `application.yml` 或 commit。
- **TiDB 一定要 SSL**：JDBC URL 要帶 `sslMode=VERIFY_IDENTITY`（或至少 `useSSL=true`），否則連不上。
- **Render 免費會冷啟動**：閒置 15 分鐘後第一個請求約 30 秒才醒，前端會像「卡住」，屬正常。
- **demo 橫幅要留著**：就算切成真實 DB，作品集仍建議保留「展示網站」聲明（只是文字會自動換句）。
