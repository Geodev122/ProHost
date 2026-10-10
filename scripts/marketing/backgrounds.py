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
FALLBACK = ["imagen-4.0-ultra-generate-001", "imagen-4.0-generate-001", "imagen-3.0-generate-002", "gemini-2.5-flash-image"]
DEAD = set()

creds, project = google.auth.default(scopes=["https://www.googleapis.com/auth/cloud-platform"])
project = project or "prohost-f766f"
session = google.auth.transport.requests.AuthorizedSession(creds)


def catalogue():
    """Image models from Vertex AI's model catalogue, newest first (Imagen, then Gemini image models)."""
    import re
    names, token = [], None
    for _ in range(20):
        r = session.get("https://us-central1-aiplatform.googleapis.com/v1beta1/publishers/google/models",
                        params={"pageSize": 200, **({"pageToken": token} if token else {})},
                        headers={"x-goog-user-project": project}, timeout=60)
        if not r.ok:
            print(f"::notice::model catalogue {r.status_code} {r.text[:200]}")
            break
        js = r.json()
        names += [m["name"].rsplit("/", 1)[-1] for m in js.get("publisherModels", [])]
        token = js.get("nextPageToken")
        if not token:
            break
    print(f"::notice::catalogue image models: {', '.join(n for n in names if 'imag' in n)}")
    ver = lambda m: tuple(int(x) for x in re.findall(r"\d+", m)[:2] or [0])
    imagen = [n for n in names if n.startswith("imagen") and "generate" in n and "edit" not in n and "capab" not in n]
    imagen.sort(key=lambda m: (ver(m), "ultra" in m, "fast" not in m, "preview" not in m), reverse=True)
    gem = [n for n in names if n.startswith("gemini") and "image" in n]
    gem.sort(key=lambda m: (ver(m), "pro" in m, "preview" not in m), reverse=True)
    out = []
    for m in imagen + gem + FALLBACK:
        if m not in out:
            out.append(m)
    print(f"::notice::trying image models: {', '.join(out)}")
    return out


MODELS = []


def call(model, prompt):
    for loc in ("us-central1", "global"):
        host = "aiplatform.googleapis.com" if loc == "global" else f"{loc}-aiplatform.googleapis.com"
        base = f"https://{host}/v1/projects/{project}/locations/{loc}/publishers/google/models/{model}"
        if model.startswith("imagen"):
            params = {"sampleCount": 1, "aspectRatio": "9:16", "personGeneration": "dont_allow"}
            if "imagen-4" in model or "imagen-5" in model:
                params["sampleImageSize"] = "2K"
            for retry in range(4):
                r = session.post(base + ":predict", json={"instances": [{"prompt": prompt}], "parameters": params}, timeout=180)
                if r.status_code == 400 and "sampleImageSize" in params:
                    del params["sampleImageSize"]
                    continue
                if r.status_code in (429, 500, 503):
                    time.sleep(10 * (retry + 1))
                    continue
                break
            if r.ok and r.json().get("predictions"):
                return base64.b64decode(r.json()["predictions"][0]["bytesBase64Encoded"]), r
        else:
            body = {"contents": [{"role": "user", "parts": [{"text": prompt}]}],
                    "generationConfig": {"responseModalities": ["IMAGE"], "imageConfig": {"aspectRatio": "9:16"}}}
            for retry in range(4):
                r = session.post(base + ":generateContent", json=body, timeout=180)
                if r.status_code in (429, 500, 503):
                    time.sleep(10 * (retry + 1))
                    continue
                break
            if r.ok:
                for part in r.json().get("candidates", [{}])[0].get("content", {}).get("parts", []):
                    if "inlineData" in part:
                        return base64.b64decode(part["inlineData"]["data"]), r
        if r.status_code != 404:
            return None, r
    return None, r


def generate(prompt):
    if not MODELS:
        MODELS.extend(catalogue())
    errors = []
    for model in [m for m in MODELS if m not in DEAD]:
        raw, r = call(model, prompt)
        if raw:
            return raw, model
        errors.append(f"{model}: {r.status_code} {r.text[:160]}")
        if r.status_code in (400, 403, 404):
            DEAD.add(model)
    sys.exit("::error::Image generation failed | " + " | ".join(errors))


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
