"""Render the feed posts (1080x1350) to PNG + JPEG (Instagram's API takes JPEG) and run the layout checks.

usage: python render_posts.py [post11_rent_your_way post12_add_price_publish ...]   (default: all post1*.html here)
Output: out/<name>.png, out/<name>.jpg; prints geometry problems (text closer than 70 px to a side, text overflowing the canvas).
"""
import json, os, pathlib, sys
from playwright.sync_api import sync_playwright
from PIL import Image

HERE = pathlib.Path(__file__).parent
CHROME = os.environ.get("CHROME", "/opt/pw-browsers/chromium-1194/chrome-linux/chrome")
names = sys.argv[1:] or sorted(p.stem for p in HERE.glob("post1*.html"))
(HERE / "out").mkdir(exist_ok=True)
problems = 0
with sync_playwright() as p:
    b = p.chromium.launch(executable_path=CHROME, args=["--force-color-profile=srgb"])
    pg = b.new_page(viewport={"width": 1080, "height": 1350}, device_scale_factor=1)
    for n in names:
        pg.goto((HERE / f"{n}.html").as_uri())
        pg.wait_for_function("document.fonts.ready.then(() => true)")
        pg.wait_for_function("Array.from(document.images).every(i => i.complete)")
        boxes = pg.evaluate("""() => [...document.querySelectorAll('.logo, .eyebrow, .h, .sub, .cta, .band .t, .band .s, .tags')].map(e => {
            const r = document.createRange(); r.selectNodeContents(e); const b = r.getBoundingClientRect();
            return {cls: e.className || e.tagName, l: b.left, r: b.right, t: b.top, b: b.bottom}; })""")
        for x in boxes:
            if x["l"] < 70 or x["r"] > 1010 or x["b"] > 1300 or x["t"] < 60:
                print(f"::warning::{n} {x['cls']} outside the safe area: left {x['l']:.0f} right {x['r']:.0f} top {x['t']:.0f} bottom {x['b']:.0f}")
                problems += 1
        png = HERE / "out" / f"{n}.png"
        pg.screenshot(path=str(png))
        Image.open(png).convert("RGB").save(HERE / "out" / f"{n}.jpg", quality=95, subsampling=0)
        print(f"{n}: rendered, {len(boxes)} text boxes checked")
    b.close()
sys.exit(1 if problems else 0)
