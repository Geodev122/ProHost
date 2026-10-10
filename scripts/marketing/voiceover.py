"""Generate the Lebanese Arabic voiceovers for marketing/*.mp4 with Google Cloud Text-to-Speech.

Runs in CI (.github/workflows/voiceover.yml) with the CI service account (GOOGLE_APPLICATION_CREDENTIALS).
For every post in voiceover.json it synthesizes each line with that post's voice, fits it into its scene
(speeds it up a little if needed), places it at its start time and writes:
  marketing/voice/<post>_voice.mp3   the full 15.0 s track, ready to merge with the video
  marketing/voice/<post>_voice.wav   the same track, lossless
  marketing/voice/lines/<post>_NN.wav each line on its own (to re-time by hand)
  marketing/voice/report.json        engine, voice, durations and tempo per line

Engine: Gemini-TTS (style prompt with Lebanese dialect + acting direction); falls back to Chirp 3 HD (ar-XA).
"""
import base64, json, pathlib, subprocess, sys, time, wave

import google.auth
import google.auth.transport.requests
import imageio_ffmpeg
import requests

ROOT = pathlib.Path(__file__).resolve().parents[2]
CFG = json.loads((pathlib.Path(__file__).parent / "voiceover.json").read_text(encoding="utf-8"))
OUT = ROOT / "marketing" / "voice"
LINES = OUT / "lines"
TMP = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/voiceover")
FF = imageio_ffmpeg.get_ffmpeg_exe()
LENGTH = 15.0
RATE = 24000
MAX_TEMPO = 1.3
WARNED = []

creds, project = google.auth.default(scopes=["https://www.googleapis.com/auth/cloud-platform"])
project = project or "prohost-f766f"
session = google.auth.transport.requests.AuthorizedSession(creds)


def post(url, body):
    r = session.post(url, json=body, headers={"x-goog-user-project": project}, timeout=120)
    return r.status_code, (r.json() if r.content else {})


def ensure_api(service="texttospeech.googleapis.com", required=True):
    url = f"https://serviceusage.googleapis.com/v1/projects/{project}/services/{service}"
    r = session.get(url, timeout=60)
    if r.ok and r.json().get("state") == "ENABLED":
        return
    print(f"Enabling {service} …")
    session.post(url + ":enable", timeout=60)
    for _ in range(30):
        time.sleep(10)
        r = session.get(url, timeout=60)
        if r.ok and r.json().get("state") == "ENABLED":
            time.sleep(30)  # propagation
            return
    if required:
        sys.exit(f"::error::Could not enable {service}")
    print(f"::warning::Could not enable {service}")


def synth(p, line):
    """Returns (pcm bytes, engine). Tries Gemini-TTS, then Chirp 3 HD."""
    prompt = f"{CFG['dialect']} Character: {p['persona']} Delivery: {line['style']}"
    attempts = []
    for model in ("gemini-2.5-pro-tts", "gemini-2.5-flash-tts"):
        for lang in ("ar-EG",):  # Gemini-TTS has no ar-LB; the prompt asks for Lebanese
            attempts.append((f"{model}/{lang}", {
                "input": {"text": line["text"], "prompt": prompt},
                "voice": {"languageCode": lang, "name": p["voice"], "modelName": model}}))
    attempts.append(("chirp3-hd/ar-XA", {
        "input": {"text": line["text"]},
        "voice": {"languageCode": "ar-XA", "name": f"ar-XA-Chirp3-HD-{p['voice']}"}}))
    errors = []
    for engine, body in attempts:
        body["audioConfig"] = {"audioEncoding": "LINEAR16", "sampleRateHertz": RATE}
        if engine.startswith("chirp"):
            if errors and not WARNED:
                WARNED.append(1)
                print("::warning::Gemini-TTS unavailable, using Chirp 3 HD | " + " | ".join(errors)[:900])
            body["audioConfig"]["speakingRate"] = p.get("rate", 1.1)
        for retry in range(3):
            code, js = post("https://texttospeech.googleapis.com/v1/text:synthesize", body)
            if code == 200:
                return base64.b64decode(js["audioContent"]), engine
            if code == 400 and "speakingRate" in body["audioConfig"] and "peak" in json.dumps(js):
                del body["audioConfig"]["speakingRate"]
                continue
            if code in (429, 500, 503):
                time.sleep(5 * (retry + 1))
                continue
            break
        errors.append(f"{engine}: {code} {json.dumps(js)[:200]}")
    sys.exit("::error::TTS failed for " + p["id"] + " | " + " | ".join(errors))


def wav_seconds(path):
    with wave.open(str(path)) as w:
        return w.getnframes() / w.getframerate()


def ff(*args):
    subprocess.run([FF, "-nostdin", "-y", "-loglevel", "error", *args], check=True,
                   stdin=subprocess.DEVNULL, timeout=120)


def as_wav(audio):
    """Chirp returns a WAV file; Gemini-TTS may return bare 16-bit PCM. Always hand ffmpeg a WAV."""
    if audio[:4] == b"RIFF":
        return audio
    import io
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(audio)
    return buf.getvalue()


def iam_report():
    """Prints which project roles this service account holds and whether it may call Gemini-TTS."""
    email = getattr(creds, "service_account_email", "?")
    code, js = post(f"https://cloudresourcemanager.googleapis.com/v1/projects/{project}:getIamPolicy", {})
    if code == 200:
        mine = sorted(b["role"] for b in js.get("bindings", []) if f"serviceAccount:{email}" in b.get("members", []))
        holders = sorted({m for b in js.get("bindings", []) if b["role"] == "roles/aiplatform.user" for m in b.get("members", [])})
        print(f"::notice::IAM {email}: {', '.join(mine)}")
        print(f"::notice::roles/aiplatform.user holders: {', '.join(holders) or 'none'}")
    else:
        print(f"::notice::getIamPolicy {code}")
    code, js = post(f"https://cloudresourcemanager.googleapis.com/v1/projects/{project}:testIamPermissions",
                    {"permissions": ["aiplatform.endpoints.predict"]})
    print(f"::notice::aiplatform.endpoints.predict allowed: {bool(js.get('permissions'))} ({code})")


def main():
    ensure_api()
    iam_report()
    ensure_api("aiplatform.googleapis.com", required=False)  # Gemini-TTS (style prompts) runs on Vertex AI
    TMP.mkdir(parents=True, exist_ok=True)
    LINES.mkdir(parents=True, exist_ok=True)
    report = []
    only = set(sys.argv[2].split(",")) if len(sys.argv) > 2 and sys.argv[2] else None
    for p in CFG["posts"]:
        if only and p["id"] not in only:
            continue
        placed, rows = [], []
        for i, line in enumerate(p["lines"], 1):
            pcm, engine = synth(p, line)
            raw = TMP / f"{p['id']}_{i:02d}_raw.wav"
            raw.write_bytes(as_wav(pcm))
            print(f"  {p['id']} line {i}: {engine}, {len(pcm)} bytes", flush=True)
            # trim leading/trailing silence and shorten long pauses inside the line so timing is exact
            trimmed = TMP / f"{p['id']}_{i:02d}_trim.wav"
            ff("-i", str(raw), "-af",
               "silenceremove=start_periods=1:start_threshold=-42dB:stop_periods=-1:stop_duration=0.22:"
               "stop_threshold=-42dB:stop_silence=0.16,areverse,"
               "silenceremove=start_periods=1:start_threshold=-42dB,areverse",
               "-ar", str(RATE), "-ac", "1", str(trimmed))
            dur = wav_seconds(trimmed)
            slot = line["end"] - line["at"]
            tempo = 1.0 if dur <= slot else min(MAX_TEMPO, dur / slot)
            final = LINES / f"{p['id']}_{i:02d}.wav"
            ff("-i", str(trimmed), "-af", f"atempo={tempo:.3f}", "-ar", "48000", "-ac", "1", str(final))
            fdur = wav_seconds(final)
            overflow = round(line["at"] + fdur - line["end"], 2)
            if overflow > 0:
                print(f"::warning::{p['id']} line {i} runs {overflow}s into the next scene")
            placed.append((final, line["at"]))
            rows.append({"line": i, "engine": engine, "start": line["at"], "end": round(line["at"] + fdur, 2),
                         "sceneEnd": line["end"], "spoken": round(dur, 2), "tempo": round(tempo, 3),
                         "text": line["text"]})
        # mix every line at its start time into one 15.0 s track
        inputs, chains = [], []
        for k, (f, at) in enumerate(placed):
            inputs += ["-i", str(f)]
            ms = int(at * 1000)
            chains.append(f"[{k}:a]adelay={ms}|{ms}[d{k}]")
        mix = "".join(f"[d{k}]" for k in range(len(placed)))
        graph = ";".join(chains) + f";{mix}amix=inputs={len(placed)}:normalize=0,apad," \
                f"atrim=0:{LENGTH},loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000,atrim=0:{LENGTH}[out]"
        wav_out = OUT / f"{p['id']}_voice.wav"
        ff(*inputs, "-filter_complex", graph, "-map", "[out]", "-ac", "2", "-ar", "48000", str(wav_out))
        ff("-i", str(wav_out), "-c:a", "libmp3lame", "-b:a", "192k", str(OUT / f"{p['id']}_voice.mp3"))
        report.append({"post": p["id"], "voice": p["voice"], "gender": p["gender"], "lines": rows,
                       "trackSeconds": round(wav_seconds(wav_out), 2)})
        print(f"{p['id']}: {p['voice']} · " + ", ".join(f"{r['engine']} {r['start']}–{r['end']}s x{r['tempo']}" for r in rows))
    old = {}
    rp = OUT / "report.json"
    if rp.exists():
        old = {r["post"]: r for r in json.loads(rp.read_text(encoding="utf-8"))}
    old.update({r["post"]: r for r in report})
    rp.write_text(json.dumps(list(old.values()), ensure_ascii=False, indent=2), encoding="utf-8")


main()
