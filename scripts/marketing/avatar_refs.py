"""Crops the AI-avatar character sheet (marketing/avatar/reference/character_sheet.jpg, 768x1376, 3x2 labelled panels)
into label-free reference images for avatar_video.py: marketing/avatar/reference/<name>.png."""
import pathlib
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
REF = ROOT / "marketing/avatar/reference"
COLS = [(4, 250), (261, 507), (518, 764)]
ROWS = [(12, 628), (695, 1304)]
NAMES = [["full_front", "three_quarter", "full_back"], ["close_neutral", "close_smile", "desk_mid"]]

sheet = Image.open(REF / "character_sheet.jpg").convert("RGB")
for r, (y0, y1) in enumerate(ROWS):
    for c, (x0, x1) in enumerate(COLS):
        sheet.crop((x0, y0, x1, y1)).save(REF / f"{NAMES[r][c]}.png")
        print(NAMES[r][c], x1 - x0, y1 - y0)
