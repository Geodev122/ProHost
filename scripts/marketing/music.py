"""Generate instrumental background music for marketing posts with Vertex AI Lyria (CI service account).

Writes marketing/music/<post>.wav for every track in music.json (or only the ids in argv[1]).
"""
import base64, json, pathlib, re, sys, time

import google.auth
import google.auth.transport.requests

ROOT = pathlib.Path(__file__).resolve().parents[2]
CFG = json.loads((pathlib.Path(__file__).parent / "music.json").read_text(encoding="utf-8"))
OUT = ROOT / "marketing" / "music"

creds, project = google.auth.default(scopes=["https://www.googleapis.com/auth/cloud-platform"])
project = project or "prohost-f766f"
session = google.auth.transport.requests.AuthorizedSession(creds)


def models():
    names, token = [], None
    for _ in range(20):
        r = session.get("https://us-central1-aiplatform.googleapis.com/v1beta1/publishers/google/models",
                        params={"pageSize": 200, **({"pageToken": token} if token else {})},
                        headers={"x-goog-user-project": project}, timeout=60)
        if not r.ok:
            break
        js = r.json()
        names += [m["name"].rsplit("/", 1)[-1] for m in js.get("publisherModels", [])]
        token = js.get("nextPageToken")
        if not token:
            break
    ly = [n for n in names if "lyria" in n]
    ver = lambda m: tuple(int(x) for x in re.findall(r"\d+", m)[:2] or [0])
    ly.sort(key=lambda m: (ver(m), "preview" not in m), reverse=True)
    out = []
    for m in ly + ["lyria-002"]:
        if m not in out:
            out.append(m)
    print(f"::notice::Lyria models (newest first): {', '.join(out)}")
    return out


def attempt(model, loc, style, prompt, seed):
    host = "aiplatform.googleapis.com" if loc == "global" else f"{loc}-aiplatform.googleapis.com"
    base = f"https://{host}/v1/projects/{project}/locations/{loc}/publishers/google/models/{model}"
    if style == "predict":
        url, body = base + ":predict", {"instances": [{"prompt": prompt, "negative_prompt": CFG["negative"], "seed": seed}], "parameters": {}}
    else:
        url, body = base + ":generateContent", {
            "contents": [{"role": "user", "parts": [{"text": f"{prompt}. Avoid: {CFG['negative']}."}]}],
            "generationConfig": {"responseModalities": ["AUDIO"], "seed": seed}}
    for retry in range(4):
        r = session.post(url, json=body, timeout=300)
        if r.status_code in (429, 500, 503):
            time.sleep(15 * (retry + 1))
            continue
        break
    if r.ok:
        js = r.json()
        for pr in js.get("predictions", []):
            data = pr.get("bytesBase64Encoded") or pr.get("audioContent")
            if data:
                return base64.b64decode(data), pr.get("mimeType", "audio/wav")
        for c in js.get("candidates", []):
            for part in c.get("content", {}).get("parts", []):
                if "inlineData" in part:
                    return base64.b64decode(part["inlineData"]["data"]), part["inlineData"].get("mimeType", "")
    return None, f"{r.status_code} {r.text[:300]}"


WORKING = []


def generate(prompt, seed, model_list):
    combos = WORKING or [(m, loc, style) for m in model_list for loc in ("global", "us-central1")
                         for style in ("generateContent", "predict")]
    errors = []
    for combo in combos:
        for k in range(6):  # "blocked by recitation checks" is per sample: retry with other seeds
            raw, info = attempt(*combo, prompt, seed + 7919 * k)
            if raw or "recitation" not in str(info):
                break
        if not raw and combo[0].startswith("lyria-3") and combo[1] == "global":
            print(f"::notice::{combo} -> {str(info)[:600]}")
        if raw:
            if not WORKING:
                WORKING.append(combo)
                print(f"::notice::music engine: {combo} ({info})")
            return raw, combo[0], info
        errors.append(f"{combo}: {info}")
    print("::error::" + " || ".join(e[:260] for e in errors))
    sys.exit("Lyria failed")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    only = set(sys.argv[1].split(",")) if len(sys.argv) > 1 and sys.argv[1] else None
    ml = models()
    for i, (pid, prompt) in enumerate(CFG["tracks"].items()):
        if only and pid not in only:
            continue
        raw, model, mime = generate(prompt + ", instrumental only, seamless steady groove from the first second", 1000 + i, ml)
        ext = "mp3" if "mp3" in mime or "mpeg" in mime else ("wav" if raw[:4] == b"RIFF" else "raw")
        (OUT / f"{pid}.{ext}").write_bytes(raw)
        print(f"{pid}: {model} {len(raw)} bytes", flush=True)


main()
