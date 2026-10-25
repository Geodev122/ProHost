"""AI-avatar clips for the office reel v3 (shots in avatar_shots.json, references from avatar_refs.py).

Runs in CI (.github/workflows/marketing-avatar.yml) with the Firebase service account (GOOGLE_APPLICATION_CREDENTIALS),
on Vertex AI in the Firebase project:
  stage keyframes  Gemini image: references + scene prompt -> marketing/avatar/keyframes/<shot>.png (9:16 first frame).
                   Shots after s1 also get s1's keyframe so the room stays the same.
  stage check      Gemini listens to every take of every speaking clip (and the voice-over lines): verbatim transcript,
                   speech start/end, dialect (Lebanese vs Syrian...), whether it says the scripted line; the best take
                   (script, Lebanese, score) becomes clips/<shot>.mp4 (cents; verdicts in report.json).
  stage voice      the owner's recordings (marketing/audio/owner/raw/line_N.m4a, several takes each): takes are found
                   from the pauses, Gemini transcribes and rates every take, the best one is cut at real pauses, cleaned and written to marketing/audio/owner/line_N.wav.
  stage clips      Veo (newest 3.x fast): keyframe -> marketing/avatar/clips/<shot>.mp4 (8 s, 9:16); audio (lip-synced
                   speech) only for shots with "audio": true; "takes": n > 1 writes <shot>_a.mp4, <shot>_b.mp4, ...
Spend guard: at most MAX_SECONDS generated video seconds per run; every run is written to marketing/avatar/runs/<time>_<stage>.json
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
MAX_SECONDS = 64
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
        p += (f' He says, in {shot["voice"]}, accent: {CFG["accent"]}. His exact words: "{shot["speech"]}" Only his voice '
              "and quiet room tone; no music, no subtitles. His lips move exactly with the words, and he finishes the "
              "last word well before the clip ends.")
    return p


def clip(shot, models):
    frame = KEY / f"{shot['id']}.png"
    if not frame.exists():
        print(f"::warning::{shot['id']}: no keyframe, skipped")
        return None
    takes = int(shot.get("takes", 1))
    params = {"aspectRatio": "9:16", "durationSeconds": DURATION, "sampleCount": takes, "generateAudio": shot["audio"],
              "negativePrompt": CFG["negative"] + (", " + CFG["negative_silent"] if shot.get("silent") else ""),
              "personGeneration": "allow_adult", "resolution": "1080p"}
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
        for i, v in enumerate(vids):
            if "bytesBase64Encoded" in v:
                name = f"{shot['id']}_{'abcdefgh'[i]}" if takes > 1 else shot["id"]
                (CLIPS / f"{name}.mp4").write_bytes(base64.b64decode(v["bytesBase64Encoded"]))
        return model
    return None


LINES = ROOT / "marketing/audio/voiceovers-arabic-reel/lines"
VO_CFG = pathlib.Path(__file__).parent / "voiceover_reel.json"


def ask_audio(path, prompt, models):
    """One Gemini audio question about a file (converted to mono mp3); returns the parsed JSON answer."""
    import subprocess, imageio_ffmpeg
    mp3 = pathlib.Path(os.environ.get("RUNNER_TEMP", "/tmp")) / f"{path.stem}.mp3"
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-i", str(path), "-vn", "-ac", "1",
                    "-b:a", "96k", str(mp3)], check=True)
    body = {"contents": [{"role": "user", "parts": [{"inlineData": {"mimeType": "audio/mpeg", "data": b64(mp3)}},
                                                     {"text": prompt}]}],
            "generationConfig": {"responseMimeType": "application/json", "temperature": 0}}
    for model in models:
        for attempt in range(4):  # 429 = per-minute quota: wait and retry the same model
            r = session.post(f"https://aiplatform.googleapis.com/v1/projects/{project}/locations/global/publishers/google/"
                             f"models/{model}:generateContent", json=body, headers=HDR, timeout=300)
            if r.status_code != 429:
                break
            time.sleep(15 * (attempt + 1))
        if not r.ok:
            print(f"::notice::check {model}: HTTP {r.status_code} {r.text[:200]}")
            continue
        txt = "".join(p.get("text", "") for c in r.json().get("candidates", []) for p in c.get("content", {}).get("parts", []))
        try:
            return {"model": model, **json.loads(txt)}
        except ValueError:
            return {"model": model, "raw": txt[:800]}
    return {"error": "no model answered"}


def listen(path, script, models):
    """Gemini's verdict on one audio/video file against its script (transcript, timing, dialect)."""
    return ask_audio(path, (
        "You are a Lebanese dialect coach. Listen to this clip of a man speaking Arabic. Reply with JSON only: "
        "{\"transcript\": verbatim as spoken, \"speech_start_s\": number, \"speech_end_s\": number (end of the last "
        "word), \"cut_off\": true if the last word is cut off, \"dialect\": one of Lebanese, Syrian, Palestinian, "
        "Jordanian, Egyptian, Gulf, MSA, other, \"lebanese_score\": 1-5 (5 = clearly a native Beiruti, 1 = clearly "
        "not Lebanese), \"cues\": the pronunciation or intonation cues that decided it, \"matches_script\": true if "
        "it says the same words as the script (spelling and English words may differ), \"differences\": string, "
        f"\"intelligible\": 1-5}}. Script: {script}"), models)


# ---- The owner's own recordings (marketing/audio/owner/raw/line_N.m4a, several takes per file) ----
OWNER = ROOT / "marketing/audio/owner"


def silences(path, noise="-38dB", min_len=0.25):
    """[(start, end)] of the pauses in a file (ffmpeg silencedetect)."""
    import subprocess, imageio_ffmpeg
    out = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-nostdin", "-hide_banner", "-i", str(path), "-af",
                          f"silencedetect=n={noise}:d={min_len}", "-f", "null", "-"], capture_output=True, text=True).stderr
    starts = [float(x) for x in re.findall(r"silence_start: ([\d.]+)", out)]
    ends = [float(x) for x in re.findall(r"silence_end: ([\d.]+)", out)]
    return list(zip(starts, ends + [starts[-1] + 99] * (len(starts) - len(ends)))) if starts else []


def snap(start, end, pauses, total):
    """Moves a take's edges out to the surrounding pauses, so no word is clipped (0.15 s of air each side)."""
    before = [e for s, e in pauses if e <= start + 0.35]
    after = [s for s, e in pauses if s >= end - 0.35]
    a = max(0.0, (max(before) if before else start) - 0.15)
    b = min(total, (min(after) if after else end) + 0.15)
    return round(a, 3), round(b, 3)


ROLES = {"1": "the opening HOOK of the ad: an honest, relatable confession that makes the viewer stop scrolling",
         "5": "the CALL TO ACTION at the end of the ad: confident, warm, energetic, makes the viewer want to comment or DM now"}


def speech_takes(path, total, gap=1.2):
    """Takes = stretches of speech separated by pauses longer than `gap` seconds (from silencedetect)."""
    pauses = silences(path, "-35dB", 0.45)
    speech, t = [], 0.0
    for s0, e0 in pauses:
        if s0 - t > 0.15:
            speech.append([t, s0])
        t = e0
    if total - t > 0.15:
        speech.append([t, total])
    takes = []
    for seg in speech:
        if takes and seg[0] - takes[-1][1] < gap:
            takes[-1][1] = seg[1]
        else:
            takes.append(seg)
    return [(round(max(0, x - 0.12), 3), round(min(total, y + 0.18), 3)) for x, y in takes if y - x > 0.8]


def owner_voice(line, models):
    """Finds every take in the owner's recording (pauses), has Gemini transcribe and rate each one for its role in the
    ad, then cuts and cleans the best one."""
    import subprocess, imageio_ffmpeg
    raw = OWNER / "raw" / f"line_{line}.m4a"
    if not raw.exists():
        return {"error": f"{raw.name} missing"}
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    total = float(re.search(r"Duration: (\d+):(\d+):([\d.]+)", subprocess.run([ff, "-hide_banner", "-i", str(raw)],
                    capture_output=True, text=True).stderr).group(3))
    tmp = pathlib.Path(os.environ.get("RUNNER_TEMP", "/tmp"))
    rated = []
    for i, (a, b) in enumerate(speech_takes(raw, total)):
        cut = tmp / f"line{line}_take{i}.wav"
        subprocess.run([ff, "-nostdin", "-y", "-loglevel", "error", "-ss", f"{a}", "-to", f"{b}", "-i", str(raw), str(cut)], check=True)
        v = ask_audio(cut, (
            f"You are a Lebanese voice director for an Instagram ad. This is one take of {ROLES.get(line, 'a line of the ad')}. "
            "Reply with JSON only: {\"transcript\": verbatim Arabic as spoken (English words in Latin letters), \"complete\": "
            "true if the whole sentence is said with no stumble, restart or cut word, \"clarity\": 1-5, \"emotion\": 1-5 (how "
            "well the delivery fits that role and makes the viewer feel it), \"lebanese_score\": 1-5, \"notes\": string}"), models)
        rated.append({"start_s": a, "end_s": b, **v})
    if not rated:
        return {"error": "no speech found"}
    score = lambda t: (bool(t.get("complete")), float(t.get("emotion") or 0) + float(t.get("clarity") or 0) + 0.5 * float(t.get("lebanese_score") or 0))
    best = max(range(len(rated)), key=lambda i: score(rated[i]))
    t = rated[best]
    out = OWNER / f"line_{line}.wav"
    # Phone recording → clean ad voice: rumble cut, gentle denoise, de-harsh, even level.
    subprocess.run([ff, "-nostdin", "-y", "-loglevel", "error", "-ss", f"{t['start_s']}", "-to", f"{t['end_s']}", "-i", str(raw),
                    "-af", "highpass=f=80,afftdn=nf=-28,equalizer=f=6500:t=q:w=2:g=-2,"
                           "acompressor=threshold=-22dB:ratio=3:attack=8:release=120,loudnorm=I=-16:TP=-1.5:LRA=7",
                    "-ac", "1", "-ar", "48000", str(out)], check=True)
    return {"best": best, "cut": [t["start_s"], t["end_s"]], "transcript": t.get("transcript"), "takes": rated,
            "file": str(out.relative_to(ROOT))}


def take_score(v):
    return (bool(v.get("matches_script")), v.get("dialect") == "Lebanese", float(v.get("lebanese_score") or 0),
            not v.get("cut_off"), float(v.get("intelligible") or 0))


def check(shot, models):
    """Checks every take of a speaking shot; the best one becomes clips/<shot>.mp4."""
    import shutil
    # Only takes generated together (takes > 1) compete; otherwise the clip itself is checked (old take files never win).
    takes = (sorted(CLIPS.glob(f"{shot['id']}_?.mp4")) if int(shot.get("takes", 1)) > 1 else []) or [CLIPS / f"{shot['id']}.mp4"]
    takes = [t for t in takes if t.exists()]
    if not takes:
        return {"error": "no clip"}
    verdicts = {t.stem: listen(t, shot["speech"], models) for t in takes}
    best = max(verdicts, key=lambda k: take_score(verdicts[k]))
    if best != shot["id"]:
        shutil.copyfile(CLIPS / f"{best}.mp4", CLIPS / f"{shot['id']}.mp4")
    return {"chosen": best, **verdicts[best], "takes": verdicts}


def check_voiceover(models):
    cfg = json.loads(VO_CFG.read_text(encoding="utf-8"))
    out = {}
    for i, line in enumerate(cfg["posts"][0]["lines"], 1):
        wav = LINES / f"reel_office_{i:02d}.wav"
        if wav.exists():
            out[f"vo{i}"] = listen(wav, line["text"], models)
    return out


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
            n = int(s.get("takes", 1))
            if seconds + DURATION * n > MAX_SECONDS:
                print(f"::warning::{s['id']}: skipped, the {MAX_SECONDS} s cap per run is reached")
                continue
            m = clip(s, models)
            if m:
                seconds += DURATION * n
                cost += DURATION * n * PRICE[s["audio"]]
            run["shots"][s["id"]] = m or "failed"
            print(f"clip {s['id']}: {m or 'FAILED'} takes={n} audio={s['audio']} ~${DURATION * n * PRICE[s['audio']]:.2f}")
        run["seconds"], run["estimatedUsd"] = seconds, round(cost, 2)
        print(f"clips total: {seconds} s, estimated US${cost:.2f}")
    elif stage == "check":
        models = pick(found, lambda m: re.fullmatch(r"gemini-\d+(\.\d+)?-(pro|flash)", m) is not None,
                      ["gemini-2.5-pro", "gemini-2.5-flash"], lambda m: "pro" in m)
        for s in [s for s in shots if s.get("speech")]:
            res = check(s, models)
            run["shots"][s["id"]] = res
            short = {k: res.get(k) for k in ("chosen", "transcript", "dialect", "lebanese_score", "matches_script", "speech_end_s", "cut_off")}
            print(f"check {s['id']}: {json.dumps(short, ensure_ascii=False)} takes=" +
                  json.dumps({k: [v.get("dialect"), v.get("lebanese_score"), v.get("matches_script")] for k, v in res.get("takes", {}).items()}, ensure_ascii=False))
        if not only or "vo" in only:
            for k, v in check_voiceover(models).items():
                run["shots"][k] = v
                print(f"check {k}: {json.dumps({x: v.get(x) for x in ('transcript', 'dialect', 'lebanese_score', 'cues', 'matches_script')}, ensure_ascii=False)}")
    elif stage == "voice":
        models = pick(found, lambda m: re.fullmatch(r"gemini-\d+(\.\d+)?-(pro|flash)", m) is not None,
                      ["gemini-2.5-pro", "gemini-2.5-flash"], lambda m: "pro" in m)
        for line in only or ["1", "5"]:
            res = owner_voice(line.replace("line", "").strip("_"), models)
            run["shots"][f"line_{line}"] = res
            print(f"voice line {line}: best take {res.get('best')} cut {res.get('cut')} \"{res.get('transcript')}\" {res.get('error', '')}")
            for i, t in enumerate(res.get("takes", [])):
                print(f"voice line {line} take {i}: {t.get('start_s')}-{t.get('end_s')} complete={t.get('complete')} "
                      f"clarity={t.get('clarity')} emotion={t.get('emotion')} leb={t.get('lebanese_score')} \"{t.get('transcript')}\"")
    else:
        sys.exit(f"unknown stage {stage}")
    # One file per run (marketing/avatar/runs/), so two runs never conflict when they commit; report.json is the old log.
    runs = AV / "runs"
    runs.mkdir(parents=True, exist_ok=True)
    (runs / f"{run['at'].replace(':', '')}_{stage}.json").write_text(json.dumps(run, indent=2, ensure_ascii=False) + "\n")
    if any(v == "failed" for v in run["shots"].values() if isinstance(v, str)):
        print("::warning::some shots failed")


if __name__ == "__main__":
    main()
