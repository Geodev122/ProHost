#!/usr/bin/env python3
"""Fetch the Design Screenshots of a commit and build one contact sheet per screen.

Usage: python3 fetch_shots.py <commit-sha> <out-dir>

Artifacts aren't downloadable from the cloud sandbox, so the workflow stores the PNGs as a git commit under
refs/design-shots/<branch> and prints its sha in a `design-shots` notice. This reads that notice from the job's
annotations, then pulls each PNG through the git trees/blobs API (api.github.com only). Sheets need Pillow
(`pip install pillow`).
"""
import base64, json, os, subprocess, sys

REPO = "geodev122/prohost"
VARIANTS = ["light", "dark", "small", "rtl", "ar"]


def gh(path):
    return json.loads(subprocess.check_output(["gh", "api", path]))


def main(sha, out):
    os.makedirs(out, exist_ok=True)
    runs = [r for r in gh(f"repos/{REPO}/actions/runs?head_sha={sha}")["workflow_runs"] if r["name"] == "Design Screenshots"]
    if not runs:
        sys.exit("no Design Screenshots run for that commit (did the push touch ui/, res/ or the test?)")
    run = runs[0]
    if run["status"] != "completed":
        sys.exit(f"run {run['id']} is {run['status']}; wait for it")
    job = gh(f"repos/{REPO}/actions/runs/{run['id']}/jobs")["jobs"][0]["id"]
    notes = [a["message"] for a in gh(f"repos/{REPO}/check-runs/{job}/annotations") if a.get("title") == "design-shots"]
    if not notes:
        sys.exit(f"run {run['id']} ({run['conclusion']}) stored no screenshots")
    commit = notes[0].split()[0]
    tree = gh(f"repos/{REPO}/git/trees/{gh(f'repos/{REPO}/git/commits/{commit}')['tree']['sha']}")["tree"]
    for entry in tree:
        data = base64.b64decode(gh(f"repos/{REPO}/git/blobs/{entry['sha']}")["content"])
        with open(os.path.join(out, entry["path"]), "wb") as f:
            f.write(data)
    errors = [e["path"] for e in tree if e["path"].endswith(".error.txt")]
    print(f"{len(tree)} files from run {run['id']} into {out}; errors: {errors or 'none'}")

    try:
        from PIL import Image, ImageDraw
    except ImportError:
        print("Pillow missing: no contact sheets")
        return
    sheets = os.path.join(out, "sheets")
    os.makedirs(sheets, exist_ok=True)
    screens = sorted({e["path"].rsplit("_", 1)[0] for e in tree if e["path"].endswith(".png")})
    for screen in screens:
        ims = [Image.open(os.path.join(out, f"{screen}_{v}.png")).convert("RGB")
               for v in VARIANTS if os.path.exists(os.path.join(out, f"{screen}_{v}.png"))]
        w = 360
        hs = [int(i.height * w / i.width) for i in ims]
        sheet = Image.new("RGB", ((w + 10) * len(ims), max(hs) + 24), "white")
        d = ImageDraw.Draw(sheet)
        for k, (im, h) in enumerate(zip(ims, hs)):
            sheet.paste(im.resize((w, h)), (k * (w + 10), 24))
            d.text((k * (w + 10) + 4, 6), VARIANTS[k], fill="black")
        sheet.save(os.path.join(sheets, f"{screen}.png"))
    print(f"contact sheets: {sheets}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
