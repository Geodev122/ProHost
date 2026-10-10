"""Render an animated HTML post (window.render(t)) to an Instagram-ready MP4 + a cover PNG.

usage: python render.py post1.html [seconds=15] [fps=30] [preview]
"""
import os, subprocess, sys, pathlib
from playwright.sync_api import sync_playwright
import imageio_ffmpeg

HERE = pathlib.Path(__file__).parent
CHROME = os.environ.get("CHROME", "/opt/pw-browsers/chromium-1194/chrome-linux/chrome")

def main():
    name = sys.argv[1]
    secs = float(sys.argv[2]) if len(sys.argv) > 2 else 15
    fps = int(sys.argv[3]) if len(sys.argv) > 3 else 30
    preview = len(sys.argv) > 4
    stem = pathlib.Path(name).stem
    out_dir = HERE / "out"
    out_dir.mkdir(exist_ok=True)
    with sync_playwright() as p:
        b = p.chromium.launch(executable_path=CHROME, args=["--force-color-profile=srgb"])
        pg = b.new_page(viewport={"width": 1080, "height": 1920}, device_scale_factor=1)
        pg.goto((HERE / name).as_uri())
        pg.wait_for_function("document.fonts.ready.then(() => true)")
        pg.wait_for_function("Array.from(document.images).every(i => i.complete)")
        pg.wait_for_timeout(400)
        if preview:  # contact frames only
            for t in [float(x) for x in sys.argv[4].split(",")]:
                pg.evaluate(f"render({t})")
                pg.screenshot(path=str(out_dir / f"{stem}_t{t:05.2f}.png"))
            b.close()
            return
        ff = imageio_ffmpeg.get_ffmpeg_exe()
        mp4 = out_dir / f"{stem}.mp4"
        proc = subprocess.Popen(
            [ff, "-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", str(fps), "-c:v", "png", "-i", "-",
             "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "17", "-preset", "slow", "-profile:v", "high",
             "-movflags", "+faststart", "-r", str(fps), str(mp4)],
            stdin=subprocess.PIPE)
        n = int(secs * fps)
        for f in range(n):
            pg.evaluate(f"render({f / fps})")
            proc.stdin.write(pg.screenshot(type="png"))
        proc.stdin.close()
        proc.wait()
        pg.evaluate(f"render({secs - 0.05})")
        pg.screenshot(path=str(out_dir / f"{stem}_cover.png"))
        # Instagram profile grid shows Reels as a 3:4 crop of the cover (1080x1440, centred)
        from PIL import Image
        Image.open(out_dir / f"{stem}_cover.png").crop((0, 240, 1080, 1680)).save(out_dir / f"{stem}_grid.png")
        b.close()
        print(f"{mp4} ({n} frames)")

main()
