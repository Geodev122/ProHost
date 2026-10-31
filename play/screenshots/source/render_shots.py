"""Render the Google Play screenshots in every format from shots.html.

usage: python render_shots.py [formats=phone,tab7,tab10,desk,xr]
  phone  → ../phone/        1080x1920  (phone screenshots)
  tab7   → ../tablet-7in/   1080x1920  (7-inch tablet, app at 600 dp)
  tab10  → ../tablet-10in/  1440x2560  (10-inch tablet, app at 800 dp)
  desk   → ../desktop/      1920x1080  (desktop / ChromeOS window)
  xr     → ../android-xr/   1920x1080  (Android XR home-space panel)
All PNG, 24-bit, no alpha, 9:16 or 16:9.
"""
import os, pathlib, sys
from playwright.sync_api import sync_playwright
from PIL import Image

HERE = pathlib.Path(__file__).parent
CHROME = os.environ.get("CHROME", "/opt/pw-browsers/chromium-1194/chrome-linux/chrome")
NAMES = ["01-explore-map", "02-search-and-filter", "03-room-details", "04-book-slots",
         "05-my-rentals", "06-list-your-space", "07-accept-requests", "08-occupancy-and-revenue"]
FORMATS = {  # format: (out dir, css width, css height, device scale factor)
    "phone": ("phone", 1080, 1920, 1), "tab7": ("tablet-7in", 1080, 1920, 1), "tab10": ("tablet-10in", 1080, 1920, 4 / 3),
    "desk": ("desktop", 1920, 1080, 1), "xr": ("android-xr", 1920, 1080, 1),
}

wanted = sys.argv[1].split(",") if len(sys.argv) > 1 else list(FORMATS)
with sync_playwright() as p:
    b = p.chromium.launch(executable_path=CHROME, args=["--force-color-profile=srgb", "--allow-file-access-from-files"])
    for f in wanted:
        sub, w, h, dsf = FORMATS[f]
        out = HERE.parent / sub
        out.mkdir(parents=True, exist_ok=True)
        pg = b.new_page(viewport={"width": w, "height": h}, device_scale_factor=dsf)
        for i, name in enumerate(NAMES, 1):
            pg.goto((HERE / "shots.html").as_uri() + f"?f={f}#{i}")
            pg.reload()
            pg.wait_for_function("document.fonts.ready.then(() => true)")
            pg.wait_for_function("Array.from(document.images).every(i => i.complete)")
            pg.wait_for_timeout(500)
            tmp = out / f"{name}.tmp.png"
            pg.screenshot(path=str(tmp))
            Image.open(tmp).convert("RGB").save(out / f"{name}.png", optimize=True)
            tmp.unlink()
        pg.close()
        print(f, out, Image.open(out / f"{NAMES[0]}.png").size)
    b.close()
