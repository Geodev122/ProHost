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


def generate(prompt, seed, model_list):
    errors = []
    for model in model_list:
        url = (f"https://us-central1-aiplatform.googleapis.com/v1/projects/{project}/locations/us-central1/"
               f"publishers/google/models/{model}:predict")
        body = {"instances": [{"prompt": prompt, "negative_prompt": CFG["negative"], "seed": seed}], "parameters": {}}
        for retry in range(4):
            r = session.post(url, json=body, timeout=300)
            if r.status_code in (429, 500, 503):
                time.sleep(15 * (retry + 1))
                continue
            break
        if r.ok:
            preds = r.json().get("predictions", [])
            for pr in preds:
                data = pr.get("bytesBase64Encoded") or pr.get("audioContent")
                if data:
                    return base64.b64decode(data), model
        errors.append(f"{model}: {r.status_code} {r.text[:200]}")
    sys.exit("::error::Lyria failed | " + " | ".join(errors))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    only = set(sys.argv[1].split(",")) if len(sys.argv) > 1 and sys.argv[1] else None
    ml = models()
    for i, (pid, prompt) in enumerate(CFG["tracks"].items()):
        if only and pid not in only:
            continue
        raw, model = generate(prompt + ", instrumental only, seamless steady groove from the first second", 1000 + i, ml)
        (OUT / f"{pid}.wav").write_bytes(raw)
        print(f"{pid}: {model} {len(raw)} bytes", flush=True)


main()
