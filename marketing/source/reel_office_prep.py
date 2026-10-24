"""Line timings + mouth envelope for reel_office.html, from the generated voiceover.

usage: python reel_office_prep.py   → reel_office_data.js (window.REEL = { lines: [{at, end}], lip: [0..1 per 1/30 s] })
Reads ../audio/voiceovers-arabic-reel/report.json (start/end of every spoken line) and reel_office_voice.wav (the RMS of
each 1/30 s frame, gated, compressed and smoothed, drives the presenter's mouth).
"""
import json, pathlib, wave
import numpy as np

HERE = pathlib.Path(__file__).parent
VOICE = HERE.parent / "audio" / "voiceovers-arabic-reel"
FPS = 30

rep = next(r for r in json.loads((VOICE / "report.json").read_text(encoding="utf-8")) if r["post"] == "reel_office")
lines = [{"at": r["start"], "end": r["end"]} for r in rep["lines"]]

with wave.open(str(VOICE / "reel_office_voice.wav")) as w:
    sr, ch = w.getframerate(), w.getnchannels()
    x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float32) / 32768
x = x.reshape(-1, ch).mean(axis=1)
hop = sr // FPS
n = len(x) // hop
rms = np.sqrt((x[: n * hop].reshape(n, hop) ** 2).mean(axis=1))
ref = np.percentile(rms[rms > 1e-4], 95) if (rms > 1e-4).any() else 1.0
lip = np.clip((rms / ref - 0.06) / 0.94, 0, 1) ** 0.7          # gate the breath noise, open quieter syllables a bit more
lip = np.convolve(lip, [0.25, 0.5, 0.25], mode="same")         # 3-frame smoothing so the jaw doesn't flicker
(HERE / "reel_office_data.js").write_text(
    "window.REEL = " + json.dumps({"lines": lines, "lip": [round(float(v), 3) for v in lip]}) + ";\n", encoding="utf-8")
print(f"{len(lines)} lines, {len(lip)} lip frames ({len(lip) / FPS:.1f} s): " + ", ".join(f"{l['at']}–{l['end']}" for l in lines))
