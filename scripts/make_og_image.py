#!/usr/bin/env python3
"""產生分享預覽圖 前端/public/og-image.jpg（1200×630，LINE／FB／104 貼連結時顯示的封面）。

用 Playwright 把下面的 HTML 截圖成 JPEG（漸層用 PNG 會超過 700 KB）。改文案後重跑即可：
    python3 scripts/make_og_image.py
需要：pip install playwright、playwright install chromium，以及系統有思源黑體（Noto Sans CJK TC）。
"""
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "前端" / "public" / "og-image.jpg"

HTML = """<!doctype html><html lang="zh-Hant"><head><meta charset="utf-8"><style>
  * { margin: 0; box-sizing: border-box; }
  body { width: 1200px; height: 630px; font-family: "Noto Sans CJK TC", "Noto Sans TC", sans-serif;
         background: radial-gradient(circle at 85% 15%, #16b27c 0, transparent 45%),
                     linear-gradient(135deg, #03291f 0%, #0a4a39 55%, #097353 100%);
         color: #fff; padding: 72px 80px; position: relative; overflow: hidden; }
  .grid { position: absolute; inset: 0; opacity: .08;
          background-image: linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px);
          background-size: 48px 48px; }
  .brand { display: flex; align-items: center; gap: 22px; position: relative; }
  .brand svg { width: 92px; height: 92px; }
  .name { font-size: 76px; font-weight: 900; letter-spacing: -1px; line-height: 1; }
  .sub { font-size: 30px; font-weight: 500; letter-spacing: 10px; color: #aff0d3; margin-top: 10px; }
  h1 { position: relative; margin-top: 64px; font-size: 54px; font-weight: 900; line-height: 1.25; }
  h1 em { font-style: normal; color: #79e2b7; }
  .chips { position: absolute; left: 80px; bottom: 64px; display: flex; gap: 14px; }
  .chip { font-size: 24px; font-weight: 500; padding: 10px 22px; border-radius: 999px;
          background: rgba(255,255,255,.12); border: 1px solid rgba(255,255,255,.25); }
</style></head><body>
  <div class="grid"></div>
  <div class="brand">
    <svg viewBox="0 0 64 64"><rect width="64" height="64" rx="16" fill="#fff"/>
      <path d="M17 19 L32 47 L47 19" fill="none" stroke="#0a9065" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>
      <circle cx="47" cy="19" r="4.5" fill="#16b27c"/></svg>
    <div><div class="name">Voyago</div><div class="sub">歐洲自助遊</div></div>
  </div>
  <h1>說出你想怎麼玩，<br><em>AI 行程顧問</em>從真實行程裡幫你挑</h1>
  <div class="chips">
    <span class="chip">Vue 3</span><span class="chip">Spring Boot 3</span>
    <span class="chip">MySQL／TiDB</span><span class="chip">Gemini AI</span><span class="chip">前台＋後台</span>
  </div>
</body></html>"""


def main() -> None:
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(viewport={"width": 1200, "height": 630})
        page.set_content(HTML, wait_until="load")
        page.screenshot(path=str(OUT), type="jpeg", quality=88)
        browser.close()
    print(f"✓ {OUT.relative_to(ROOT)}（{OUT.stat().st_size // 1024} KB）")


if __name__ == "__main__":
    main()
