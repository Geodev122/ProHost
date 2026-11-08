#!/usr/bin/env python3
"""Android | iOS contact sheets for the parity review (no pass/fail: a person looks at them).

    python3 scripts/migration/compare_android_ios.py --android <dir> --ios <dir> --out <dir> [--only prefix,...]

Files are matched by name (`light/Explore.png` on both sides). Writes one side-by-side PNG per match plus index.html;
names present on only one side are listed so a missing iOS state is never overlooked. iOS system-owned UI
(permission dialogs, purchase sheet, Sign in with Apple) is expected to differ; see docs/migration/PARITY.md.
"""
from __future__ import annotations

import argparse
import html
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import shots  # noqa: E402
from PIL import Image  # noqa: E402


def side_by_side(a: Path, b: Path, height: int = 900, gap: int = 24) -> Image.Image:
    left, right = Image.open(a).convert("RGB"), Image.open(b).convert("RGB")

    def fit(img: Image.Image) -> Image.Image:
        return img.resize((max(1, round(img.width * height / img.height)), height))

    left, right = fit(left), fit(right)
    sheet = Image.new("RGB", (left.width + gap + right.width, height), (246, 248, 251))
    sheet.paste(left, (0, 0))
    sheet.paste(right, (left.width + gap, 0))
    return sheet


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--android", type=Path, required=True)
    ap.add_argument("--ios", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--only", default="")
    a = ap.parse_args(argv)
    only = [p for p in a.only.split(",") if p]
    pick = lambda n: not only or any(n.startswith(p) for p in only)  # noqa: E731
    android = {n: p for n, p in shots.list_pngs(a.android).items() if pick(n)}
    ios = {n: p for n, p in shots.list_pngs(a.ios).items() if pick(n)}
    a.out.mkdir(parents=True, exist_ok=True)
    rows = []
    for name in sorted(android.keys() & ios.keys()):
        target = a.out / name
        target.parent.mkdir(parents=True, exist_ok=True)
        side_by_side(android[name], ios[name]).save(target)
        rows.append(f"<tr><td>{html.escape(name)}</td><td><img src='{html.escape(name)}' width=520></td></tr>")
    only_android = sorted(android.keys() - ios.keys())
    only_ios = sorted(ios.keys() - android.keys())
    body = (f"<p>{len(rows)} matched · <b class=bad>{len(only_android)} missing on iOS</b> · {len(only_ios)} missing on Android</p>"
            + (f"<h2>Missing on iOS</h2><ul>{''.join(f'<li>{html.escape(n)}</li>' for n in only_android)}</ul>" if only_android else "")
            + (f"<h2>Missing on Android</h2><ul>{''.join(f'<li>{html.escape(n)}</li>' for n in only_ios)}</ul>" if only_ios else "")
            + "<table>" + "".join(rows) + "</table>")
    (a.out / "index.html").write_text(shots.page("Android | iOS", body), encoding="utf-8")
    print(f"{len(rows)} sheets, {len(only_android)} missing on iOS, {len(only_ios)} missing on Android -> {a.out}/index.html")
    return 0


if __name__ == "__main__":
    sys.exit(main())
