# 訂單確認信寄信服務（Google Apps Script）

Render 免費方案封鎖對外 SMTP（25/465/587），所以雲端改由 Google Apps Script 用站長自己的 Gmail 寄信，走 HTTPS、免費（一般 Gmail 帳號每天 100 封）。

## 設定（約 5 分鐘）

1. 到 <https://script.google.com> → **新專案**，把 `Code.gs` 的內容整份貼上取代預設程式碼，存檔。
2. 左側 ⚙️ **專案設定** → 最下面 **指令碼屬性** → 新增：
   - 屬性：`RELAY_SECRET`
   - 值：Render 服務 Environment 裡 `MAIL_RELAY_SECRET` 的值（Render 自動產生）
3. 右上 **部署 → 新增部署作業** → 類型選 **網頁應用程式**：
   - 執行身分：**我**
   - 誰可以存取：**任何人**（後端是從伺服器呼叫，沒有 Google 登入；安全性靠上面的密鑰）
4. 第一次部署會要求授權「以你的身分傳送電子郵件」→ 允許。
5. 複製部署完成的 **網頁應用程式網址**（`https://script.google.com/macros/s/…/exec`），貼到 Render 的 `MAIL_RELAY_URL`，存檔重新部署。

用瀏覽器打開該網址看到 `{"ok":true,"service":"voyago-mail-relay"}` 就代表部署成功。

## 安全設計

- 後端只送訂單欄位（收件人、行程名稱、日期、人數、金額），信件內容在 Apps Script 端組成，網址外流也不能拿來寄任意內容。
- 密鑰不符一律拒絕；收件人格式不對拒絕；所有欄位截斷長度、HTML 跳脫。
- 寄信失敗不影響付款（後端是非同步寄送，只記錄 log）。
