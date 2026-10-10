"""Score each post with its Lyria track, synced to the animation.

For every post: reads events/<post>_events.json (written by render.py), finds the window of music/<post>.wav whose beats
best match the post's major animation events, cuts LENGTH seconds, adds light sound effects on the animation cues,
dips the music just before the closing offer and muxes it with the silent video.

usage: python mix.py [post ids...]      → ../posts/<post>.mp4
"""
import json, pathlib, subprocess, sys, wave

import imageio_ffmpeg
import numpy as np

HERE = pathlib.Path(__file__).parent
ROOT = HERE.parent
SR, LENGTH = 48000, 16.0
VIDEOS = pathlib.Path(__import__("os").environ.get("VIDEOS", HERE / "out"))  # silent renders from render.py
FF = imageio_ffmpeg.get_ffmpeg_exe()
rng = np.random.default_rng(7)


def load(path):
    with wave.open(str(path)) as w:
        ch, width = w.getnchannels(), w.getsampwidth()
        raw = w.readframes(10 ** 9)  # Lyria's header can misstate the length: trust the data
    a = np.frombuffer(raw[: len(raw) // (ch * width) * ch * width], dtype="<i2").astype(np.float32) / 32768
    return a.reshape(-1, ch) if ch > 1 else np.repeat(a[:, None], 2, axis=1)


def onset_strength(x, hop=512):
    mono = x.mean(axis=1)
    n = len(mono) // hop
    frames = mono[: n * hop].reshape(n, hop)
    spec = np.abs(np.fft.rfft(frames * np.hanning(hop), axis=1))
    flux = np.maximum(0, np.diff(np.log1p(spec), axis=0)).sum(axis=1)
    flux = np.concatenate([[0], flux])
    return (flux - flux.mean()) / (flux.std() + 1e-9), hop / SR


def best_offset(music, events):
    """Offset (s) into the track that puts the strongest beats on the major animation events."""
    o, dt = onset_strength(music)
    major = [e["t"] for e in events if e.get("major")] or [e["t"] for e in events]
    win = 2  # ±2 hops ≈ ±21 ms
    best, best_d = -1e9, 0.0
    max_d = len(music) / SR - LENGTH - 0.5
    for d in np.arange(0.0, max(0.0, max_d), dt):
        score = 0.0
        for t in major:
            i = int(round((t + d) / dt))
            score += o[max(0, i - win): i + win + 1].max(initial=0)
        if score > best:
            best, best_d = score, float(d)
    return best_d, best / max(1, len(major))


# ---- sound effects -------------------------------------------------------
def env(n, attack, decay):
    t = np.arange(n) / SR
    return np.minimum(1, t / max(attack, 1e-4)) * np.exp(-t / decay)


def tone(freq, dur, decay, attack=0.002):
    n = int(dur * SR)
    t = np.arange(n) / SR
    return np.sin(2 * np.pi * freq * t) * env(n, attack, decay)


def noise(dur):
    return rng.standard_normal(int(dur * SR))


def lowpass(x, k):
    return np.convolve(x, np.ones(k) / k, mode="same")


def sfx(kind):
    if kind == "whoosh":
        n = int(0.45 * SR)
        x = lowpass(noise(0.45), 9) - lowpass(noise(0.45), 60)
        shape = np.sin(np.linspace(0, np.pi, n)) ** 2
        return x * shape * 0.22
    if kind == "pop":
        return tone(880, 0.12, 0.03) * 0.25 + tone(1320, 0.12, 0.02) * 0.12
    if kind == "tick":
        return tone(2200, 0.05, 0.008) * 0.12
    if kind == "tap":
        return tone(1400, 0.06, 0.012) * 0.2 + lowpass(noise(0.06), 3) * env(int(0.06 * SR), 0.001, 0.006) * 0.08
    if kind == "notify":
        a, b = tone(988, 0.25, 0.09), tone(1319, 0.35, 0.12)
        out = np.zeros(int(0.5 * SR)); out[: len(a)] += a; out[int(0.11 * SR): int(0.11 * SR) + len(b)] += b
        return out * 0.2
    if kind == "success":
        out = np.zeros(int(0.7 * SR))
        for i, f in enumerate([784, 988, 1175, 1568]):
            s = tone(f, 0.45, 0.14); k = int(i * 0.06 * SR); out[k: k + len(s)] += s
        return out * 0.13
    if kind == "impact":
        n = int(0.9 * SR)
        t = np.arange(n) / SR
        boom = np.sin(2 * np.pi * (55 + 40 * np.exp(-t / 0.05)) * t) * np.exp(-t / 0.28)
        crack = lowpass(noise(0.9), 4) * np.exp(-t / 0.03)
        return boom * 0.45 + crack * 0.12
    return np.zeros(1)


def score(post):
    music = load(ROOT / "music" / f"{post}.wav")
    events = json.loads((HERE / "events" / f"{post}_events.json").read_text())
    d, s = best_offset(music, events)
    n = int(LENGTH * SR)
    m = music[int(d * SR): int(d * SR) + n].copy()
    t = np.arange(len(m)) / SR
    gain = np.minimum(1, t / 0.25) * np.clip((LENGTH - t) / 1.4, 0, 1)  # fade in / out
    closing = next((e["t"] for e in events if e["k"] == "impact" and e["t"] > 10), None)
    if closing:  # breath before the offer: dip, then back up on the impact
        dip = np.clip(1 - 0.55 * np.exp(-((t - (closing - 0.25)) / 0.22) ** 2), 0, 1)
        gain = gain * dip
    m *= gain[:, None]
    m /= max(1e-6, np.sqrt((m ** 2).mean())) / 0.12  # music bed level
    fx = np.zeros(n)
    for e in events:
        x = sfx(e["k"])
        i = int(e["t"] * SR)
        if e["k"] == "whoosh":
            i -= int(0.2 * SR)  # whooshes peak on the cut
        i = max(0, i)
        x = x[: max(0, n - i)]
        fx[i: i + len(x)] += x
    mix = m * 0.8 + fx[:, None] * 0.9
    mix /= max(1.0, np.abs(mix).max() / 0.95)
    tmp = HERE / "out" / f"{post}_score.wav"
    with wave.open(str(tmp), "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((mix * 32767).astype("<i2").tobytes())
    out = ROOT / "posts" / f"{post}.mp4"
    out.parent.mkdir(exist_ok=True)
    subprocess.run([FF, "-nostdin", "-y", "-loglevel", "error", "-i", str(VIDEOS / f"{post}.mp4"), "-i", str(tmp),
                    "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-af", "loudnorm=I=-14:TP=-1.5:LRA=11,aresample=48000",
                    "-c:a", "aac", "-b:a", "192k", "-t", str(LENGTH), "-movflags", "+faststart", str(out)], check=True)
    print(f"{post}: music offset {d:.2f}s, beat match {s:.2f} → {out.relative_to(ROOT)}")


for p in sys.argv[1:] or [f.stem for f in sorted((ROOT / "music").glob("*.wav"))]:
    score(p)
