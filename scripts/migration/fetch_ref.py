#!/usr/bin/env python3
"""Download a stored screenshot set (refs/design-shots/<name>, written by the Design Screenshots workflow) into a folder.

    python3 scripts/migration/fetch_ref.py <name> <out-dir>      e.g. golden-v1047 golden

Needs the GitHub CLI (`gh`, authenticated: GH_TOKEN in CI). Reads the ref -> commit -> tree -> blobs through the API, so it
works where artifacts are not downloadable. Prints the commit and file count.
"""
import base64
import json
import pathlib
import subprocess
import sys

REPO = "geodev122/prohost"


def gh(path: str):
    return json.loads(subprocess.check_output(["gh", "api", path]))


def main(ref: str, out: str) -> None:
    dest = pathlib.Path(out)
    dest.mkdir(parents=True, exist_ok=True)
    commit = gh(f"repos/{REPO}/git/ref/design-shots/{ref}")["object"]["sha"]
    tree_sha = gh(f"repos/{REPO}/git/commits/{commit}")["tree"]["sha"]
    tree = gh(f"repos/{REPO}/git/trees/{tree_sha}?recursive=1")["tree"]
    count = 0
    for entry in tree:
        if entry["type"] == "blob":
            (dest / entry["path"]).write_bytes(base64.b64decode(gh(f"repos/{REPO}/git/blobs/{entry['sha']}")["content"]))
            count += 1
    print(f"{ref} {commit[:8]} {count} files -> {dest}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
