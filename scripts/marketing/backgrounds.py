"""Generate photo backgrounds for marketing videos with Vertex AI Imagen (CI service account).

Writes marketing/bg/<name>.jpg (1080x1920) for every entry in backgrounds.json (or only the names in argv[1]).
Tries the newest Imagen model first and falls back to older ones.
"""
import base64, io, json, pathlib, sys, time

import google.auth
import google.auth.transport.requests
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
CFG = json.loads((pathlib.Path(__file__).parent / "backgrounds.json").read_text(encoding="utf-8"))
OUT = ROOT / "marketing" / "bg"
MODELS = ["imagen-4.0-ultra-generate-001", "imagen-4.0-generate-001", "imagen-3.0-generate-002"]

creds, project = google.auth.default(scopes=["https://www.googleapis.com/auth/cloud-platform"])
project = project or "prohost-f766f"
session = google.auth.transport.requests.AuthorizedSession(creds)


def generate(prompt):
    errors = []
    for model in MODELS:
        url = (f"https://us-central1-aiplatform.googleapis.com/v1/projects/{project}/locations/us-central1/"
               f"publishers/google/models/{model}:predict")
        params = {"sampleCount": 1, "aspectRatio": "9:16", "personGeneration": "dont_allow", "addWatermark": False}
        if model.startswith("imagen-4"):
            params["sampleImageSize"] = "2K"
        for retry in range(3):
            r = session.post(url, json={"instances": [{"prompt": prompt}], "parameters": params}, timeout=180)
            if r.status_code == 400 and "sampleImageSize" in params:
                del params["sampleImageSize"]
                continue
            if r.status_code == 400 and "addWatermark" in params:
                del params["addWatermark"]
                continue
            if r.status_code in (429, 500, 503):
                time.sleep(10 * (retry + 1))
                continue
            break
        if r.ok and r.json().get("predictions"):
            return base64.b64decode(r.json()["predictions"][0]["bytesBase64Encoded"]), model
        errors.append(f"{model}: {r.status_code} {r.text[:200]}")
    sys.exit("::error::Imagen failed | " + " | ".join(errors))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    only = set(sys.argv[1].split(",")) if len(sys.argv) > 1 and sys.argv[1] else None
    for name, desc in CFG["images"].items():
        if only and name not in only:
            continue
        raw, model = generate(f"{desc} {CFG['style']}")
        im = Image.open(io.BytesIO(raw)).convert("RGB")
        w, h = im.size
        scale = max(1080 / w, 1920 / h)
        im = im.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
        x, y = (im.width - 1080) // 2, (im.height - 1920) // 2
        im.crop((x, y, x + 1080, y + 1920)).save(OUT / f"{name}.jpg", quality=88, optimize=True)
        print(f"{name}: {model} {w}x{h}", flush=True)


main()
