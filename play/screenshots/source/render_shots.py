"""Render the 8 Google Play phone screenshots (1080x1920 PNG, no alpha) from shots.html.

usage: python render_shots.py [out_dir=../phone]
"""
import os, pathlib, sys
from playwright.sync_api import sync_playwright
from PIL import Image

HERE = pathlib.Path(__file__).parent
CHROME = os.environ.get("CHROME", "/opt/pw-browsers/chromium-1194/chrome-linux/chrome")
NAMES = ["01-explore-map", "02-search-and-filter", "03-room-details", "04-book-slots",
         "05-my-rentals", "06-list-your-space", "07-accept-requests", "08-occupancy-and-revenue"]

out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else HERE.parent / "phone"
out.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    b = p.chromium.launch(executable_path=CHROME, args=["--force-color-profile=srgb", "--allow-file-access-from-files"])
    pg = b.new_page(viewport={"width": 1080, "height": 1920}, device_scale_factor=1)
    for i, name in enumerate(NAMES, 1):
        pg.goto((HERE / "shots.html").as_uri() + f"#{i}")
        pg.reload()
        pg.wait_for_function("document.fonts.ready.then(() => true)")
        pg.wait_for_function("Array.from(document.images).every(i => i.complete)")
        pg.wait_for_timeout(500)
        tmp = out / f"{name}.tmp.png"
        pg.screenshot(path=str(tmp))
        Image.open(tmp).convert("RGB").save(out / f"{name}.png", optimize=True)
        tmp.unlink()
        print(out / f"{name}.png")
    b.close()
