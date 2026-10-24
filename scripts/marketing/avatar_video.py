"""AI-avatar clips for the office reel v3 (shots in avatar_shots.json, references from avatar_refs.py).

Runs in CI (.github/workflows/marketing-avatar.yml) with the Firebase service account (GOOGLE_APPLICATION_CREDENTIALS),
on Vertex AI in the Firebase project:
  stage keyframes  Gemini image: references + scene prompt -> marketing/avatar/keyframes/<shot>.png (9:16 first frame).
                   Shots after s1 also get s1's keyframe so the room stays the same.
  stage check      Gemini listens to every speaking clip: verbatim transcript, speech start/end, dialect, voice, and
                   whether it says the scripted line (cents; result in report.json).
  stage clips      Veo (newest 3.x fast): keyframe -> marketing/avatar/clips/<shot>.mp4 (8 s, 9:16); audio (lip-synced
                   speech) only for shots with "audio": true.
Spend guard: at most MAX_SECONDS generated video seconds per run; every run is appended to marketing/avatar/report.json
with its estimated cost.

usage: avatar_video.py <stage> [shot,shot,...]
"""
import base64, datetime, json, os, pathlib, re, sys, time

import google.auth
import google.auth.transport.requests

ROOT = pathlib.Path(__file__).resolve().parents[2]
CFG = json.loads((pathlib.Path(__file__).parent / "avatar_shots.json").read_text(encoding="utf-8"))
AV = ROOT / "marketing/avatar"
REF, KEY, CLIPS = AV / "reference", AV / "keyframes", AV / "clips"
MAX_SECONDS = 48
DURATION = 8
PRICE = {True: 0.15, False: 0.10}  # Veo 3.x Fast, US$ per generated second (with / without audio)

creds, project = google.auth.default(scopes=["https://www.googleapis.com/auth/cloud-platform"])
project = project or "prohost-f766f"
session = google.auth.transport.requests.AuthorizedSession(creds)
HDR = {"x-goog-user-project": project}


def catalogue():
    names, token = [], None
    for _ in range(20):
        r = session.get("https://us-central1-aiplatform.googleapis.com/v1beta1/publishers/google/models",
                        params={"pageSize": 200, **({"pageToken": token} if token else {})}, headers=HDR, timeout=60)
        if not r.ok:
            print(f"::notice::model catalogue {r.status_code}")
            break
        js = r.json()
        names += [m["name"].rsplit("/", 1)[-1] for m in js.get("publisherModels", [])]
        token = js.get("nextPageToken")
        if not token:
            break
    return names


def ver(m):
    return tuple(int(x) for x in re.findall(r"\d+", m)[:2] or [0])


def pick(found, test, fallback, prefer):
    cands = sorted([m for m in found if test(m)], key=lambda m: (ver(m), prefer(m), "preview" not in m), reverse=True)
    return [m for m in dict.fromkeys(cands + fallback)]


def b64(path):
    return base64.b64encode(path.read_bytes()).decode()


def keyframe(shot, models):
    parts = [{"inlineData": {"mimeType": "image/png", "data": b64(REF / f"{r}.png")}} for r in shot["refs"]]
    room_ref = KEY / "s1.png"
    same_room = ""
    if shot["id"] != "s1" and room_ref.exists():
        parts.append({"inlineData": {"mimeType": "image/png", "data": b64(room_ref)}})
        same_room = " The last image shows the same room: keep its furniture, light and colours identical."
    prompt = (f"Create one new photograph of this exact man. {CFG['person']} Setting: {CFG['room']}. {shot['frame']}"
              f"{same_room} {CFG['look']}")
    parts.append({"text": prompt})
    body = {"contents": [{"role": "user", "parts": parts}],
            "generationConfig": {"responseModalities": ["TEXT", "IMAGE"], "imageConfig": {"aspectRatio": "9:16"}}}
    for model in models:
        r = session.post(f"https://aiplatform.googleapis.com/v1/projects/{project}/locations/global/publishers/google/"
                         f"models/{model}:generateContent", json=body, headers=HDR, timeout=300)
        if not r.ok:
            print(f"::notice::{shot['id']} {model}: HTTP {r.status_code} {r.text[:300]}")
            continue
        for c in r.json().get("candidates", []):
            for p in c.get("content", {}).get("parts", []):
                if "inlineData" in p:
                    KEY.mkdir(parents=True, exist_ok=True)
                    (KEY / f"{shot['id']}.png").write_bytes(base64.b64decode(p["inlineData"]["data"]))
                    return model
        print(f"::notice::{shot['id']} {model}: no image ({json.dumps(r.json())[:300]})")
    return None


def veo_prompt(shot):
    p = f"{CFG['person']} Setting: {CFG['room']}. {shot['motion']} {CFG['look']}"
    if shot.get("speech"):
        p += (f' He says, in {shot["voice"]}: "{shot["speech"]}" Only his voice and quiet room tone; no music, '
              "no subtitles. His lips move exactly with the words.")
    return p


def clip(shot, models):
    frame = KEY / f"{shot['id']}.png"
    if not frame.exists():
        print(f"::warning::{shot['id']}: no keyframe, skipped")
        return None
    params = {"aspectRatio": "9:16", "durationSeconds": DURATION, "sampleCount": 1, "generateAudio": shot["audio"],
              "negativePrompt": CFG["negative"], "personGeneration": "allow_adult", "resolution": "1080p"}
    inst = {"prompt": veo_prompt(shot), "image": {"bytesBase64Encoded": b64(frame), "mimeType": "image/png"}}
    for model in models:
        base = f"https://us-central1-aiplatform.googleapis.com/v1/projects/{project}/locations/us-central1/publishers/google/models/{model}"
        for attempt_params in (params, {k: v for k, v in params.items() if k != "resolution"}):
            r = session.post(f"{base}:predictLongRunning", json={"instances": [inst], "parameters": attempt_params},
                             headers=HDR, timeout=120)
            if r.ok:
                break
            print(f"::notice::{shot['id']} {model}: HTTP {r.status_code} {r.text[:300]}")
            if r.status_code != 400:
                break
        if not r.ok:
            continue
        op = r.json()["name"]
        for _ in range(60):
            time.sleep(15)
            s = session.post(f"{base}:fetchPredictOperation", json={"operationName": op}, headers=HDR, timeout=120)
            js = s.json() if s.content else {}
            if js.get("done"):
                break
        else:
            print(f"::warning::{shot['id']}: Veo timed out")
            return None
        if "error" in js:
            print(f"::warning::{shot['id']}: {json.dumps(js['error'])[:400]}")
            return None
        res = js.get("response", {})
        vids = res.get("videos", [])
        if not vids or "bytesBase64Encoded" not in vids[0]:
            print(f"::warning::{shot['id']}: no video (filtered={res.get('raiMediaFilteredCount')} "
                  f"{res.get('raiMediaFilteredReasons')})")
            return model  # billed or not, the attempt counts toward the cap
        CLIPS.mkdir(parents=True, exist_ok=True)
        (CLIPS / f"{shot['id']}.mp4").write_bytes(base64.b64decode(vids[0]["bytesBase64Encoded"]))
        return model
    return None


def check(shot, models):
    import subprocess, imageio_ffmpeg
    clip_path = CLIPS / f"{shot['id']}.mp4"
    if not clip_path.exists():
        return {"error": "no clip"}
    mp3 = pathlib.Path(os.environ.get("RUNNER_TEMP", "/tmp")) / f"{shot['id']}.mp3"
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-i", str(clip_path), "-vn", "-ac", "1",
                    "-b:a", "96k", str(mp3)], check=True)
    prompt = ("Listen to this 8-second clip of a man speaking. Reply with JSON only: {\"transcript\": verbatim Arabic as "
              "spoken, \"speech_start_s\": number, \"speech_end_s\": number, \"cut_off\": true if the last word is cut "
              "off by the end of the clip, \"dialect\": which Arabic dialect it sounds like, \"voice\": gender and tone, "
              "\"matches_script\": true if it says the same words as the script, \"differences\": what differs, "
              f"\"intelligible\": 1-5}}. Script: {shot['speech']}")
    body = {"contents": [{"role": "user", "parts": [{"inlineData": {"mimeType": "audio/mpeg", "data": b64(mp3)}},
                                                     {"text": prompt}]}],
            "generationConfig": {"responseMimeType": "application/json", "temperature": 0}}
    for model in models:
        r = session.post(f"https://aiplatform.googleapis.com/v1/projects/{project}/locations/global/publishers/google/"
                         f"models/{model}:generateContent", json=body, headers=HDR, timeout=300)
        if not r.ok:
            print(f"::notice::check {model}: HTTP {r.status_code} {r.text[:200]}")
            continue
        txt = "".join(p.get("text", "") for c in r.json().get("candidates", []) for p in c.get("content", {}).get("parts", []))
        try:
            return {"model": model, **json.loads(txt)}
        except ValueError:
            return {"model": model, "raw": txt[:800]}
    return {"error": "no model answered"}


def main():
    stage = sys.argv[1] if len(sys.argv) > 1 else "keyframes"
    only = [s for s in (sys.argv[2] if len(sys.argv) > 2 else "").split(",") if s.strip()]
    shots = [s for s in CFG["shots"] if not only or s["id"] in only]
    found = catalogue()
    run = {"at": datetime.datetime.utcnow().isoformat(timespec="seconds") + "Z", "stage": stage, "shots": {}}
    if stage == "keyframes":
        models = pick(found, lambda m: m.startswith("gemini") and "image" in m and "embedding" not in m,
                      ["gemini-3-pro-image-preview", "gemini-2.5-flash-image"], lambda m: "pro" in m)
        print(f"::notice::image models: {', '.join(models[:4])}")
        for s in shots:
            m = keyframe(s, models)
            run["shots"][s["id"]] = m or "failed"
            print(f"keyframe {s['id']}: {m or 'FAILED'}")
    elif stage == "clips":
        models = pick(found, lambda m: m.startswith("veo-3") and "fast" in m,
                      ["veo-3.1-fast-generate-001", "veo-3.1-fast-generate-preview", "veo-3.0-fast-generate-001"],
                      lambda m: True)
        print(f"::notice::video models: {', '.join(models[:4])}")
        seconds, cost = 0, 0.0
        for s in shots:
            if seconds + DURATION > MAX_SECONDS:
                print(f"::warning::{s['id']}: skipped, the {MAX_SECONDS} s cap per run is reached")
                continue
            m = clip(s, models)
            if m:
                seconds += DURATION
                cost += DURATION * PRICE[s["audio"]]
            run["shots"][s["id"]] = m or "failed"
            print(f"clip {s['id']}: {m or 'FAILED'} audio={s['audio']} ~${DURATION * PRICE[s['audio']]:.2f}")
        run["seconds"], run["estimatedUsd"] = seconds, round(cost, 2)
        print(f"clips total: {seconds} s, estimated US${cost:.2f}")
    elif stage == "check":
        models = pick(found, lambda m: re.fullmatch(r"gemini-\d+(\.\d+)?-(pro|flash)", m) is not None,
                      ["gemini-2.5-pro", "gemini-2.5-flash"], lambda m: "pro" in m)
        for s in [s for s in shots if s.get("speech")]:
            res = check(s, models)
            run["shots"][s["id"]] = res
            print(f"check {s['id']}: {json.dumps(res, ensure_ascii=False)}")
    else:
        sys.exit(f"unknown stage {stage}")
    rep = AV / "report.json"
    hist = json.loads(rep.read_text()) if rep.exists() else []
    rep.write_text(json.dumps(hist + [run], indent=2, ensure_ascii=False) + "\n")
    if any(v == "failed" for v in run["shots"].values() if isinstance(v, str)):
        print("::warning::some shots failed")


if __name__ == "__main__":
    main()
