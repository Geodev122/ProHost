#!/usr/bin/env python3
"""Compare a candidate set of screenshots with the golden baseline (migration parity gate).

    python3 scripts/migration/diff_shots.py --golden <dir> --candidate <dir> [--only <prefix>[,<prefix>]]
        [--channel-tol 0] [--max-ratio 0.0] [--masks docs/migration/golden-masks.json] [--report out/diff.html] [--json out/diff.json]

The golden set is the untouched Android app (refs/design-shots/golden-v1047, rendered by the Design Screenshots
workflow). Exit code 0 = every selected golden file exists in the candidate and differs by no more than
--max-ratio of its pixels; 1 = at least one missing/changed/resized file. `--only` limits the comparison to file
names starting with the given prefixes (one unit at a time, see docs/migration/PARITY.md). `--masks` ignores known
dynamic regions (JSON: file-name pattern -> [[x, y, w, h], ...]); a mask hides real regressions inside it, so keep them tiny.
"""
from __future__ import annotations

import argparse
import html
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import shots  # noqa: E402


def run(golden: Path, candidate: Path, only: list[str], channel_tol: int, max_ratio: float, masks: dict | None = None) -> dict:
    g, c = shots.list_pngs(golden), shots.list_pngs(candidate)
    names = [n for n in g if not only or any(n.startswith(p) for p in only)]
    results, extra = [], sorted(n for n in c if n not in g and (not only or any(n.startswith(p) for p in only)))
    for name in names:
        if name not in c:
            results.append({"name": name, "status": "missing"})
            continue
        r = shots.compare(g[name], c[name], channel_tol, shots.masks_for(name, masks))
        r["name"] = name
        r["status"] = "same" if r["same_size"] and r["diff_ratio"] <= max_ratio else ("resized" if not r["same_size"] else "changed")
        results.append(r)
    bad = [r for r in results if r["status"] != "same"]
    return {"golden": str(golden), "candidate": str(candidate), "compared": len(results), "failed": len(bad),
            "extra_in_candidate": extra, "results": results, "ok": not bad and bool(results)}


def write_report(summary: dict, golden: Path, candidate: Path, channel_tol: int, path: Path, masks: dict | None = None) -> None:
    g, c = shots.list_pngs(golden), shots.list_pngs(candidate)
    rows = []
    for r in summary["results"]:
        if r["status"] == "same":
            continue
        name = r["name"]
        cells = [f"<td>{html.escape(name)}<br><span class=bad>{r['status']}</span>"
                 + (f"<br>{r['diff_pixels']} px ({r['diff_ratio']:.4%}), max Δ {r['max_channel_diff']}" if r.get("diff_pixels") is not None else "")
                 + "</td>"]
        cells.append(f"<td><img src='{shots.thumb_data_uri(shots.Image.open(g[name]))}'></td>")
        if name in c:
            cells.append(f"<td><img src='{shots.thumb_data_uri(shots.Image.open(c[name]))}'></td>")
            cells.append(f"<td><img src='{shots.thumb_data_uri(shots.diff_image(g[name], c[name], channel_tol, shots.masks_for(name, masks)))}'></td>"
                         if r.get("same_size") else "<td>size differs</td>")
        else:
            cells += ["<td>missing</td>", "<td></td>"]
        rows.append("<tr>" + "".join(cells) + "</tr>")
    head = (f"<p>{summary['compared']} compared, <b class={'ok' if summary['ok'] else 'bad'}>{summary['failed']} not identical</b>."
            f" Extra in candidate: {len(summary['extra_in_candidate'])}.</p>")
    table = "<table><tr><th>file</th><th>golden</th><th>candidate</th><th>diff</th></tr>" + "".join(rows) + "</table>" if rows else ""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(shots.page("Screenshot parity", head + table), encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--golden", type=Path, required=True)
    ap.add_argument("--candidate", type=Path, required=True)
    ap.add_argument("--only", default="", help="comma-separated file-name prefixes")
    ap.add_argument("--channel-tol", type=int, default=0, help="ignore per-channel differences up to this value (anti-aliasing noise)")
    ap.add_argument("--max-ratio", type=float, default=0.0, help="allowed share of changed pixels per file")
    ap.add_argument("--masks", type=Path, help="JSON of known dynamic regions to ignore")
    ap.add_argument("--report", type=Path)
    ap.add_argument("--json", type=Path)
    a = ap.parse_args(argv)
    only = [p for p in a.only.split(",") if p]
    masks = json.loads(a.masks.read_text(encoding="utf-8")) if a.masks else None
    summary = run(a.golden, a.candidate, only, a.channel_tol, a.max_ratio, masks)
    if a.report:
        write_report(summary, a.golden, a.candidate, a.channel_tol, a.report, masks)
    if a.json:
        a.json.parent.mkdir(parents=True, exist_ok=True)
        a.json.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    for r in summary["results"]:
        if r["status"] != "same":
            print(f"{r['status'].upper():8} {r['name']}" + (f"  {r['diff_ratio']:.4%}" if r.get("diff_pixels") is not None else ""))
    print(f"{summary['compared']} compared, {summary['failed']} not identical, {len(summary['extra_in_candidate'])} extra")
    if not summary["results"]:
        print("nothing compared: check --golden / --only", file=sys.stderr)
    return 0 if summary["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
