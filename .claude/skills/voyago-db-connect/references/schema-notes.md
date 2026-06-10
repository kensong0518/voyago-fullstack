# Entity ↔ 資料表 對應與型別說明

這份說明 `scripts/gen_schema.py` 的轉換規則、entity 與資料表的對應，以及 TiDB / MySQL 差異。
當需要解釋「資料模型長怎樣」或「為什麼 DDL 這樣產生」時讀這份。

## 四個 entity → 四張表

| Entity (Java) | Table | 關聯 |
|---|---|---|
| `Member`  | `member`  | 被 booking / message 參照 |
| `Route`   | `route`   | 被 booking 參照 |
| `Booking` | `booking` | `@ManyToOne` → member、route（FK，`ON DELETE CASCADE`）|
| `Message` | `message` | `member_id` 欄位 + FK → member（entity 內是純 `Long`，DB 層有 FK）|

建表順序（FK 依賴）：`member`、`route` 先，`booking`、`message` 後。
DROP 順序相反：先 `booking`、`message`，再 `member`、`route`。

## Java 型別 → MySQL 欄位型別

| Java 型別 | MySQL | 備註 |
|---|---|---|
| `Long`          | `BIGINT` | `@Id @GeneratedValue(IDENTITY)` → `AUTO_INCREMENT` + `PRIMARY KEY` |
| `Integer`       | `INT` | |
| `Double`        | `DOUBLE` | **例外**：`route.rating` 用 `DECIMAL(2,1)`（評分 0.0–9.9 夠用又省空間），由 gen_schema 的 override 指定 |
| `Boolean`       | `TINYINT(1)` | `true/false` → `1/0` |
| `String`        | `VARCHAR(length)` | 取 `@Column(length=N)`，未指定預設 255 |
| `String` + `columnDefinition="TEXT"` | `TEXT` | 如 `route.description` |
| `LocalDate`     | `DATE` | |
| `LocalDateTime` | `DATETIME` | |

## 欄位屬性轉換

- `@Column(nullable=false)` 或 `@ManyToOne(optional=false)` → `NOT NULL`
- `@Column(unique=true)` → 該表加一條 `UNIQUE KEY uq_<table>_<col>`
- `@Column(name="x")` / `@JoinColumn(name="x")` → 欄位名；沒寫就把 camelCase 欄位名轉 snake_case
- 欄位初始值 → `DEFAULT`：
  - `= "MEMBER"` → `DEFAULT 'MEMBER'`
  - `= 1` / `= 0` / `= 4.6` → `DEFAULT 1` / `0` / `4.6`
  - `= false` → `DEFAULT 0`
  - `= ""` → `DEFAULT ''`
  - `= LocalDateTime.now()` → `DEFAULT CURRENT_TIMESTAMP`
- `created_at`：`updatable=false` + 預設 now → `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at`：entity 有 `@PreUpdate` → `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`

## 外鍵（FK）

`@ManyToOne @JoinColumn(name="member_id")`（型別 `Member`）→
```sql
member_id BIGINT NOT NULL,
KEY idx_<table>_<col> (member_id),
CONSTRAINT fk_<table>_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
```
參照的表名 = 欄位的 entity 型別轉小寫（`Member`→`member`、`Route`→`route`）。

## 手調索引（entity 推不出來，gen_schema 用內建清單補上）

這些是為了查詢效能加的，**不在 entity 裡**，所以 `gen_schema.py` 用 `EXTRA_INDEXES` 字典維護：

- `route`：`idx_route_featured_reviews(featured, reviews)`、`idx_route_price`、`idx_route_rating`、
  `idx_route_days`、`idx_route_country`
- `booking`：`idx_booking_member_created(member_id, created_at DESC)`、`idx_booking_travel_date`、
  `idx_booking_status`（`idx_booking_route` 由 route FK 自動帶一條）
- `message`：`idx_message_member_created(member_id, created_at)`

> 改了 entity 要重產 schema 時，記得也檢視這份索引清單還適不適用。

## TiDB Cloud Serverless 注意事項

TiDB 與 MySQL 高度相容，這份 schema 幾乎可直接套用，但有幾點：

1. **一定要 TLS**：連線字串要帶 `sslMode=VERIFY_IDENTITY`（MySQL Connector/J 8）。少了會 handshake 失敗。
2. **使用者名稱有前綴**：TiDB Serverless 的帳號長得像 `xxxxxxxxxxx.root`（前面那串是 cluster 識別碼），
   填 `SPRING_DATASOURCE_USERNAME` 時要照抄，別只填 `root`。
3. **外鍵**：TiDB v6.6+ 支援 FK，TiDB Cloud Serverless 版本夠新，這份 schema 的 FK 可正常建立。
   萬一某些舊環境報 FK 不支援，可把 `CONSTRAINT ... FOREIGN KEY` 那幾行拿掉（保留 `KEY` 索引）——
   應用層邏輯不依賴 DB 層 FK 強制，只是少了級聯刪除的保護。
4. **`ENGINE=InnoDB` / `CHARSET=utf8mb4`**：TiDB 會忽略 ENGINE、接受 utf8mb4，可原樣保留不會報錯。
5. **埠號是 4000**，不是 3306。
