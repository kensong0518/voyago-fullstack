#!/usr/bin/env python3
"""依 netlify.toml 產出可直接上傳到 Netlify 的 zip。

給「沒有連結 Git、用手動上傳部署」的 Netlify 站台用。手動上傳時 Netlify 不會讀
netlify.toml，所以這支腳本把它當唯一來源：

  1. 若有 [build] command，用 [build.environment] 的環境變數執行它（目前的設定沒有建置步驟）
  2. 把 [[redirects]] 轉成 _redirects、[[headers]] 轉成 _headers，放進 publish 目錄的副本
  3. 打包成 netlify-drop.zip（不改動 repo 裡的檔案）

用法（在 repo 根目錄）：
    python3 scripts/build_netlify_drop.py
然後到 Netlify 站台的 Deploys 頁面，把 netlify-drop.zip 拖進去。
"""
import os
import shutil
import subprocess
import sys
import tempfile
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main() -> int:
    conf = tomllib.loads((ROOT / "netlify.toml").read_text(encoding="utf-8"))
    build = conf["build"]
    base = ROOT / build.get("base", ".")
    source = base / build["publish"]

    if build.get("command"):
        env = os.environ.copy()
        env.update({k: str(v) for k, v in build.get("environment", {}).items()})
        print(f"$ (cd {build.get('base', '.')} && {build['command']})")
        subprocess.run(build["command"], shell=True, cwd=base, env=env, check=True)

    # 在暫存副本上加 _redirects / _headers，repo 裡的 publish 目錄保持乾淨
    publish = Path(tempfile.mkdtemp()) / "site"
    shutil.copytree(source, publish)

    redirect_lines = []
    for r in conf.get("redirects", []):
        status = str(r.get("status", 301)) + ("!" if r.get("force") else "")
        redirect_lines.append(f"{r['from']}  {r['to']}  {status}")
    (publish / "_redirects").write_text("\n".join(redirect_lines) + "\n", encoding="utf-8")

    header_blocks = []
    for h in conf.get("headers", []):
        lines = [h["for"]] + [f"  {k}: {v}" for k, v in h.get("values", {}).items()]
        header_blocks.append("\n".join(lines))
    if header_blocks:
        (publish / "_headers").write_text("\n\n".join(header_blocks) + "\n", encoding="utf-8")

    out = ROOT / "netlify-drop.zip"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(publish.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(publish).as_posix())
    print(f"✓ {out.relative_to(ROOT)}（{out.stat().st_size // 1024} KB）")
    print("_redirects:\n" + "\n".join("  " + l for l in redirect_lines))
    return 0


if __name__ == "__main__":
    sys.exit(main())
