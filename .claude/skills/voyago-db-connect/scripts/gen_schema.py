#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
依 voyago 後端的 JPA entity 重新產生對應的 MySQL 建表 SQL。

設計取捨：
- 「欄位」完全從 entity 解析（型別、長度、nullable、unique、default）—— 這樣 entity 一改，
  重跑就能抓到漂移，是這支腳本的主要價值。
- 「關聯中繼資料」（外鍵、手調索引）entity 表達不完整（例如 Message.memberId 是純 Long、
  route 的效能索引不在 entity 裡），所以用底下的 FK_CONFIG / EXTRA_INDEXES 字典維護，
  確保產出與手調 schema 一致。改資料模型時這兩個字典也要一起檢視。

用法：
  python gen_schema.py --entities 後端/src/main/java/com/voyago/entity \
                       --out 資料庫/01_schema.sql [--drop] [--database voyago]
  python gen_schema.py --entities <dir> --check 資料庫/01_schema.sql   # 只比對漂移、不寫檔
不給 --out / --check 時印到 stdout。
"""
import argparse
import os
import re
import sys

# ---- 維護區：entity 推不出來的東西放這 ----

# 特定欄位型別覆蓋（Java 型別映射不夠精準時）
TYPE_OVERRIDES = {
    ("route", "rating"): "DECIMAL(2,1)",   # Double 預設會給 DOUBLE，評分用 DECIMAL(2,1) 較省
}

# 外鍵：table -> [(欄位, 參照表, on_delete)]
FK_CONFIG = {
    "booking": [("member_id", "member", "CASCADE"), ("route_id", "route", "CASCADE")],
    "message": [("member_id", "member", "CASCADE")],
}

# 手調索引（效能用，不在 entity 裡）：table -> [完整索引定義字串]
EXTRA_INDEXES = {
    "route": [
        "KEY idx_route_featured_reviews (featured, reviews)",
        "KEY idx_route_price (price)",
        "KEY idx_route_rating (rating)",
        "KEY idx_route_days (days)",
        "KEY idx_route_country (country)",
    ],
    "booking": [
        "KEY idx_booking_member_created (member_id, created_at DESC)",
        "KEY idx_booking_travel_date (travel_date)",
        "KEY idx_booking_status (status)",
    ],
    "message": [
        "KEY idx_message_member_created (member_id, created_at)",
    ],
}

# Java 型別 -> MySQL 型別
TYPE_MAP = {
    "Long": "BIGINT",
    "Integer": "INT",
    "Double": "DOUBLE",
    "Boolean": "TINYINT(1)",
    "LocalDate": "DATE",
    "LocalDateTime": "DATETIME",
}

# ---- 解析 ----

FIELD_RE = re.compile(
    r"((?:@\w+(?:\([^)]*\))?\s*)*)"          # 前置註解（每個可帶參數），可跨行
    r"private\s+([A-Za-z0-9_<>]+)\s+([A-Za-z0-9_]+)\s*"  # private 型別 欄位名
    r"(?:=\s*(.+?))?\s*;",                   # 可選的初始值
    re.DOTALL,
)


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)   # 區塊註解
    text = re.sub(r"//[^\n]*", "", text)                      # 行註解
    return text


def camel_to_snake(name):
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def anno_get(blob, anno, key):
    """從註解字串取出某註解的某個屬性值，如 @Column(length = 50) 的 length。"""
    m = re.search(anno + r"\(([^)]*)\)", blob)
    if not m:
        return None
    args = m.group(1)
    mk = re.search(key + r"\s*=\s*\"?([^,\")]+)\"?", args)
    return mk.group(1).strip() if mk else None


def anno_has(blob, anno):
    return re.search(r"(?<![A-Za-z])" + anno + r"\b", blob) is not None


def sql_default(expr):
    e = expr.strip()
    if e == "true":
        return "1"
    if e == "false":
        return "0"
    if e.startswith('"') and e.endswith('"'):
        return "'" + e[1:-1].replace("'", "''") + "'"
    if re.fullmatch(r"-?\d+(\.\d+)?", e):
        return e
    return None   # 方法呼叫等（如 LocalDateTime.now()）交給特殊處理


def parse_entity(path):
    text = strip_comments(open(path, encoding="utf-8").read())
    tm = re.search(r'@Table\(\s*name\s*=\s*"([^"]+)"', text)
    if not tm:
        return None
    table = tm.group(1)
    cols, pk, uniques = [], None, []

    for blob, jtype, fname, default in FIELD_RE.findall(text):
        is_id = anno_has(blob, "@Id")
        is_rel = anno_has(blob, "@ManyToOne") or anno_has(blob, "@JoinColumn")

        # 欄位名
        col = anno_get(blob, "@Column", "name") or anno_get(blob, "@JoinColumn", "name")
        if not col:
            col = camel_to_snake(fname)

        # 型別
        if is_rel:
            sqltype = "BIGINT"
        elif (table, col) in TYPE_OVERRIDES:
            sqltype = TYPE_OVERRIDES[(table, col)]
        else:
            coldef = anno_get(blob, "@Column", "columnDefinition")
            if coldef and coldef.upper().startswith("TEXT"):
                sqltype = "TEXT"
            elif jtype == "String":
                length = anno_get(blob, "@Column", "length") or "255"
                sqltype = "VARCHAR(%s)" % length
            else:
                sqltype = TYPE_MAP.get(jtype, "VARCHAR(255)")

        # nullable
        nn_col = anno_get(blob, "@Column", "nullable") == "false"
        nn_rel = is_rel and anno_get(blob, "@ManyToOne", "optional") == "false"
        not_null = is_id or nn_col or nn_rel or col in ("created_at", "updated_at")

        # 組欄位定義
        if is_id:
            definition = "%s NOT NULL AUTO_INCREMENT" % sqltype
            pk = col
        elif col == "created_at":
            definition = "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
        elif col == "updated_at":
            definition = "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
        else:
            definition = sqltype + (" NOT NULL" if not_null else "")
            if default:
                d = sql_default(default)
                if d is not None:
                    definition += " DEFAULT " + d

        cols.append((col, definition))
        if anno_get(blob, "@Column", "unique") == "true":
            uniques.append(col)

    return {"table": table, "cols": cols, "pk": pk, "uniques": uniques}


# ---- 產生 SQL ----

def order_tables(parsed):
    names = [p["table"] for p in parsed]
    emitted = []
    while names:
        progressed = False
        for n in list(names):
            deps = [ref for (_, ref, _) in FK_CONFIG.get(n, [])]
            if all(d in emitted or d == n for d in deps):
                emitted.append(n)
                names.remove(n)
                progressed = True
        if not progressed:           # 萬一有環，剩下的直接接上
            emitted.extend(names)
            break
    return emitted


def render_table(p):
    table = p["table"]
    lines = []
    for col, definition in p["cols"]:
        lines.append("  %-12s %s," % (col, definition))
    lines.append("  PRIMARY KEY (%s)" % p["pk"])
    for col in p["uniques"]:
        lines[-1] += ","
        lines.append("  UNIQUE KEY uq_%s_%s (%s)" % (table, col, col))

    fks = FK_CONFIG.get(table, [])
    extra = EXTRA_INDEXES.get(table, [])
    # 外鍵欄位若已有「以該欄位開頭」的手調索引就不重複建單欄索引
    extra_leads = {re.search(r"\(([A-Za-z0-9_]+)", ix).group(1) for ix in extra}
    for ix in extra:
        lines[-1] += ","
        lines.append("  " + ix)
    for col, ref, on_del in fks:
        if col not in extra_leads:
            lines[-1] += ","
            lines.append("  KEY idx_%s_%s (%s)" % (table, ref, col))
    for col, ref, on_del in fks:
        lines[-1] += ","
        lines.append("  CONSTRAINT fk_%s_%s FOREIGN KEY (%s) REFERENCES %s(id) ON DELETE %s"
                     % (table, ref, col, ref, on_del))

    return "CREATE TABLE IF NOT EXISTS %s (\n%s\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;" % (
        table, "\n".join(lines))


def generate(entities_dir, database, drop):
    parsed = []
    for fn in sorted(os.listdir(entities_dir)):
        if fn.endswith(".java"):
            p = parse_entity(os.path.join(entities_dir, fn))
            if p:
                parsed.append(p)
    by_name = {p["table"]: p for p in parsed}
    order = order_tables(parsed)

    out = []
    out.append("-- ============================================================")
    out.append("-- Voyago 資料庫結構 — 由 .claude/skills/voyago-db-connect/scripts/gen_schema.py 產生")
    out.append("-- 來源：後端 JPA entity。請勿手改本檔；要改資料模型請改 entity 後重跑此腳本。")
    out.append("-- 執行： mysql -u root -p < 01_schema.sql")
    out.append("-- ============================================================")
    out.append("")
    out.append("CREATE DATABASE IF NOT EXISTS %s" % database)
    out.append("  DEFAULT CHARACTER SET utf8mb4")
    out.append("  COLLATE utf8mb4_unicode_ci;")
    out.append("")
    out.append("USE %s;" % database)
    out.append("")
    if drop:
        out.append("SET FOREIGN_KEY_CHECKS = 0;")
        for t in reversed(order):
            out.append("DROP TABLE IF EXISTS %s;" % t)
        out.append("SET FOREIGN_KEY_CHECKS = 1;")
        out.append("")
    for t in order:
        out.append(render_table(by_name[t]))
        out.append("")
    return "\n".join(out).rstrip() + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--entities", required=True, help="entity 目錄")
    ap.add_argument("--out", help="輸出 SQL 檔路徑（不給就印到 stdout）")
    ap.add_argument("--database", default="voyago")
    ap.add_argument("--drop", action="store_true", help="加上 FK-safe 的 DROP TABLE（乾淨重建）")
    ap.add_argument("--check", help="與此檔比對漂移，不寫檔；一致回 0、不一致回 1")
    args = ap.parse_args()

    sql = generate(args.entities, args.database, args.drop)

    if args.check:
        existing = open(args.check, encoding="utf-8").read()
        norm = lambda s: "\n".join(l.rstrip() for l in s.strip().splitlines())
        if norm(existing) == norm(sql):
            print("OK：產生結果與 %s 一致，entity 與 schema 沒有漂移。" % args.check)
            return 0
        print("漂移：產生結果與 %s 不同。" % args.check, file=sys.stderr)
        print("--- 產生的版本 ---")
        print(sql)
        return 1

    if args.out:
        with open(args.out, "w", encoding="utf-8", newline="\n") as f:
            f.write(sql)
        print("已寫入 %s" % args.out)
    else:
        sys.stdout.write(sql)
    return 0


if __name__ == "__main__":
    sys.exit(main())
