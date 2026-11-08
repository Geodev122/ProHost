#!/usr/bin/env python3
"""Download a stored screenshot set (refs/design-shots/<name>, written by the Design Screenshots workflow) into a folder.

    python3 scripts/migration/fetch_ref.py <name> <out-dir> [--only prefix,prefix] [--workers 8]

e.g. `fetch_ref.py golden-v1047 golden` (all 280) or `--only splash_` (just the splash renders).
Needs the GitHub CLI (`gh`, authenticated: GH_TOKEN in CI). Reads ref -> commit -> tree -> blobs through the API, so it works where
artifacts are not downloadable; blobs are fetched in parallel. Prints the commit and file count.
"""
import argparse
import base64
import json
import pathlib
import subprocess
from concurrent.futures import ThreadPoolExecutor

REPO = "geodev122/prohost"


def gh(path: str):
    return json.loads(subprocess.check_output(["gh", "api", path]))


def fetch(entry, dest: pathlib.Path) -> str:
    (dest / entry["path"]).write_bytes(base64.b64decode(gh(f"repos/{REPO}/git/blobs/{entry['sha']}")["content"]))
    return entry["path"]


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("name")
    ap.add_argument("out")
    ap.add_argument("--only", default="", help="comma-separated file-name prefixes")
    ap.add_argument("--workers", type=int, default=8)
    a = ap.parse_args()
    only = [p for p in a.only.split(",") if p]
    dest = pathlib.Path(a.out)
    dest.mkdir(parents=True, exist_ok=True)
    commit = gh(f"repos/{REPO}/git/ref/design-shots/{a.name}")["object"]["sha"]
    tree = gh(f"repos/{REPO}/git/trees/{gh(f'repos/{REPO}/git/commits/{commit}')['tree']['sha']}?recursive=1")["tree"]
    wanted = [e for e in tree if e["type"] == "blob" and (not only or any(e["path"].startswith(p) for p in only))]
    with ThreadPoolExecutor(max_workers=a.workers) as pool:
        done = list(pool.map(lambda e: fetch(e, dest), wanted))
    print(f"{a.name} {commit[:8]} {len(done)} files -> {dest}")


if __name__ == "__main__":
    main()
