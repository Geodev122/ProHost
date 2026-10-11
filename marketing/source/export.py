"""Copy finished videos and covers into the numbered delivery folders of marketing/ with readable names.

usage: python export.py KEY=DIR ...   (any subset)
  MIX_EN=…        mixed 9:16 Reels, British English   → 01-reels-english-9x16/
  MIX_AR=…        mixed 9:16 Reels, Lebanese Arabic   → 02-reels-arabic-9x16/
  LAND_EN=… LAND_AR=…  mixed 16:9 Reel 1             → 03-landscape-16x9/
  MIX_FEED=…      mixed 4:5 feed posts (music only)   → 04-feed-posts-4x5-music/
  RENDERS_916=…   silent 9:16 renders + covers        → 05-covers/…, 06-silent-videos-9x16/
  RENDERS_45=…    4:5 renders (covers)                → 05-covers/feed-posts-4x5/
  RENDERS_169=…   16:9 render (cover)                 → 03-landscape-16x9/
"""
import pathlib, shutil, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
NAMES = {
    "post1_hosts": "01-space-owners", "post2_app": "02-the-app", "post3_psychologist": "03-psychologist",
    "post4_trainer": "04-corporate-trainer", "post5_lifecoach": "05-life-coach", "post6_dietitian": "06-dietitian",
    "post7_hotel": "07-hotel", "post8_medical": "08-medical-center", "post9_institute": "09-training-center",
    "post10_studio": "10-yoga-studio",
}
args = dict(a.split("=", 1) for a in sys.argv[1:])


def put(src: pathlib.Path, dest: pathlib.Path):
    if src.exists():
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dest)
        print(f"{dest.relative_to(ROOT)}")


for post, name in NAMES.items():
    for key, folder in [("MIX_EN", "01-reels-english-9x16"), ("MIX_AR", "02-reels-arabic-9x16"), ("MIX_FEED", "04-feed-posts-4x5-music")]:
        if key in args:
            put(pathlib.Path(args[key]) / f"{post}.mp4", ROOT / folder / f"{name}.mp4")
    if "RENDERS_916" in args:
        d = pathlib.Path(args["RENDERS_916"])
        put(d / f"{post}.mp4", ROOT / "06-silent-videos-9x16" / f"{name}.mp4")
        put(d / f"{post}_cover.png", ROOT / "05-covers" / "reels-9x16" / f"{name}.png")
        put(d / f"{post}_grid.png", ROOT / "05-covers" / "grid-previews-3x4" / f"{name}.png")
    if "RENDERS_45" in args:
        put(pathlib.Path(args["RENDERS_45"]) / f"{post}_cover.png", ROOT / "05-covers" / "feed-posts-4x5" / f"{name}.png")

for key, lang in [("LAND_EN", "english"), ("LAND_AR", "arabic")]:
    if key in args:
        put(pathlib.Path(args[key]) / "post1_hosts.mp4", ROOT / "03-landscape-16x9" / f"01-space-owners-{lang}.mp4")
if "RENDERS_169" in args:
    put(pathlib.Path(args["RENDERS_169"]) / "post1_hosts_cover.png", ROOT / "03-landscape-16x9" / "01-space-owners-cover.png")
