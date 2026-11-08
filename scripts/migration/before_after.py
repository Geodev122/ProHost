#!/usr/bin/env python3
"""Before | After | Difference sheets for the owner's review (golden baseline vs the candidate build).

    python3 scripts/migration/before_after.py --golden <dir> --candidate <dir> --out <dir>
        [--from-json parity/diff.json] [--show prefix,prefix] [--masks docs/migration/golden-masks.json] [--height 900]

Which files get a sheet: every file `--from-json` (the diff_shots.py result) lists as not identical, plus every golden file whose name starts
with one of the `--show` prefixes (identical files are shown too, so a UI unit that must look the same still gets a visible
"nothing changed" sheet). Each sheet is three panels labelled Before (golden) / After (candidate) / Difference (changed pixels in red,
masked dynamic regions ignored). Writes <out>/<name> plus index.html; prints the number of sheets.
"""
from __future__ import annotations

import argparse
import html
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import shots  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

BG, INK = (246, 248, 251), (22, 32, 47)


def panel(img: Image.Image, label: str, height: int) -> Image.Image:
    img = img.convert("RGB")
    img = img.resize((max(1, round(img.width * height / img.height)), height))
    out = Image.new("RGB", (img.width, height + 22), BG)
    ImageDraw.Draw(out).text((4, 4), label, fill=INK)
    out.paste(img, (0, 22))
    return out


def sheet(golden: Path, candidate: Path, rects: list[list[int]] | None, height: int = 900, gap: int = 16) -> Image.Image:
    before, after = Image.open(golden), Image.open(candidate)
    panels = [panel(before, "BEFORE (golden)", height), panel(after, "AFTER (candidate)", height)]
    if before.size == after.size:
        panels.append(panel(shots.diff_image(golden, candidate, 0, rects), "DIFFERENCE (red = changed)", height))
    else:
        panels.append(panel(Image.new("RGB", (40, 40), BG), f"SIZE DIFFERS {before.size} vs {after.size}", height))
    out = Image.new("RGB", (sum(p.width for p in panels) + gap * (len(panels) - 1), panels[0].height), BG)
    x = 0
    for p in panels:
        out.paste(p, (x, 0))
        x += p.width + gap
    return out


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--golden", type=Path, required=True)
    ap.add_argument("--candidate", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--from-json", type=Path)
    ap.add_argument("--show", default="")
    ap.add_argument("--masks", type=Path)
    ap.add_argument("--height", type=int, default=900)
    a = ap.parse_args(argv)
    g, c = shots.list_pngs(a.golden), shots.list_pngs(a.candidate)
    masks = json.loads(a.masks.read_text(encoding="utf-8")) if a.masks else None
    wanted: list[str] = []
    if a.from_json and a.from_json.exists():
        wanted += [r["name"] for r in json.loads(a.from_json.read_text(encoding="utf-8"))["results"] if r["status"] != "same"]
    prefixes = [p for p in a.show.split(",") if p]
    wanted += [n for n in sorted(g) if prefixes and any(n.startswith(p) for p in prefixes)]
    names = [n for n in dict.fromkeys(wanted) if n in g and n in c]
    a.out.mkdir(parents=True, exist_ok=True)
    rows = []
    for name in names:
        target = a.out / name
        target.parent.mkdir(parents=True, exist_ok=True)
        sheet(g[name], c[name], shots.masks_for(name, masks), a.height).save(target)
        rows.append(f"<tr><td>{html.escape(name)}</td><td><img src='{html.escape(name)}' style='max-width:100%'></td></tr>")
    missing = [n for n in dict.fromkeys(wanted) if n not in c]
    body = (f"<p>{len(rows)} sheets.</p>" + (f"<p class=bad>No candidate render for: {html.escape(', '.join(missing))}</p>" if missing else "")
            + "<table>" + "".join(rows) + "</table>")
    (a.out / "index.html").write_text(shots.page("Before | After | Difference", body), encoding="utf-8")
    print(f"{len(rows)} sheets -> {a.out}/index.html" + (f" ({len(missing)} without candidate)" if missing else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
