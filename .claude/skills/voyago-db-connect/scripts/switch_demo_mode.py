#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
切換前端的「示範模式 / 真實模式」—— 改 netlify.toml。

real：關掉 demo、把 /api 反代指到真實後端
  python switch_demo_mode.py --mode real --backend https://voyago-backend-xxxx.onrender.com
demo：純前端示範（不打後端，全用 localStorage mock）
  python switch_demo_mode.py --mode demo

預設改專案根的 netlify.toml；可用 --file 指定。改完記得重新部署前端。
"""
import argparse
import os
import re
import sys


def find_default_toml():
    here = os.path.dirname(os.path.abspath(__file__))
    # scripts -> voyago-db-connect -> skills -> .claude -> 專案根
    root = os.path.abspath(os.path.join(here, "..", "..", "..", ".."))
    return os.path.join(root, "netlify.toml")


def upsert_in_build_env(text, key, value):
    """在 [build.environment] 區塊內 設定/新增 key = "value"。"""
    line = '  %s = "%s"' % (key, value)
    if re.search(r'(?m)^\s*%s\s*=' % re.escape(key), text):
        return re.sub(r'(?m)^\s*%s\s*=.*$' % re.escape(key), line, text)
    # 沒有就插在 [build.environment] 標頭後第一行
    m = re.search(r'(?m)^\[build\.environment\]\s*$', text)
    if not m:
        raise SystemExit("找不到 [build.environment] 區塊，請確認 netlify.toml 格式。")
    idx = m.end()
    return text[:idx] + "\n" + line + text[idx:]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", required=True, choices=["real", "demo"])
    ap.add_argument("--backend", help="real 模式的後端網址，如 https://voyago-backend-xxxx.onrender.com")
    ap.add_argument("--file", help="netlify.toml 路徑（預設自動找專案根）")
    args = ap.parse_args()

    path = args.file or find_default_toml()
    if not os.path.exists(path):
        raise SystemExit("找不到 netlify.toml：%s" % path)
    text = open(path, encoding="utf-8").read()
    original = text

    if args.mode == "real":
        if not args.backend:
            raise SystemExit("real 模式需要 --backend <後端網址>")
        backend = args.backend.rstrip("/")
        text = upsert_in_build_env(text, "VITE_DEMO_ONLY", "false")
        text = upsert_in_build_env(text, "BACKEND_ORIGIN", backend)
        msg = "已切換為『真實模式』：VITE_DEMO_ONLY=false、BACKEND_ORIGIN=%s" % backend
    else:
        text = upsert_in_build_env(text, "VITE_DEMO_ONLY", "true")
        msg = "已切換為『示範模式』：VITE_DEMO_ONLY=true（前端不打後端）"

    if text == original:
        print("netlify.toml 已是目標狀態，無需變更。")
        return 0
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print(msg)
    print("→ 記得重新部署前端：netlify deploy --prod 或 git push 觸發 rebuild。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
