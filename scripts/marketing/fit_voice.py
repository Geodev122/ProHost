"""Fit voice 1 onto a talking take's own mouth — the first office reel's lip-sync, with the Lebanese voice.

Why: the first office reel looked natural because Veo made the face and the voice together — every jaw drop, lip
rounding, nod and brow landed on a syllable. Redrawing the mouth (LatentSync on the CPU runners, v6) gave a damped,
half-open "mumble" on top of a jaw still moving to Veo's own sentence. Veo's speech isn't Lebanese, but its MOUTH is
perfect for the words it said — and a take of the same line says the same words. So keep the take's frames untouched
and bend the voice instead: align the take's own audio to voice 1 (DTW on MFCCs + voice activity), then time-stretch
the voice syllable by syllable onto it (Rubber Band R3 with a keyframe map). Inside words the voice moves at most ±25 %
(it stays natural); pauses absorb the rest.

  python fit_voice.py <voice.wav> <name> <take.mp4>[,<take.mp4>…]
    → <FIT_OUT>/<name>.mp4: the take's native frames over its spoken part, with the fitted voice
  Several takes (other Veo takes of the same shot): the one whose rhythm needs the least bending wins — Veo's audio is
  thrown away, so only its words and rhythm matter, never its accent.

FIT_OUT defaults to marketing/avatar/dubbed (the composer's "dubbed/<name>"); Reel Studio passes <reel>/dubbed.
Needs the rubberband CLI (apt rubberband-cli) and librosa. With SYNCNET_REPO (a LatentSync checkout with
checkpoints/auxiliary/syncnet_v2.model, as in the CI workflows) SyncNet — the lip-sync judge — picks the take whose
fitted clip is best in sync and then corrects the timing where it still finds the voice early or late (sync_check.py).
"""
import os, pathlib, shutil, subprocess, sys, tempfile, wave

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from lipsync import ROOT, ff, mono  # noqa: E402

OUT = ROOT / os.environ.get("FIT_OUT", "marketing/avatar/dubbed")
SR, HOP = 16000, 160           # 10 ms analysis frames
LEAD, TAIL = 0.15, 0.30        # seconds kept before the first word / after the last
SPEECH_RATE = (0.8, 1.25)      # take seconds per voice second inside words (the voice's stretch)
PAUSE_RATE = (0.25, 4.0)       # … inside the voice's pauses
VAD_WEIGHT = 4.0               # how strongly silence must pair with silence in the alignment


class Track:
    """One audio track at 16 kHz with its speech activity per 10 ms frame. TTS is near-silent between words, so its
    broadband level is enough. A Veo take carries ambience and steady tones (louder than his pauses, and they run on
    after the line), so its speech is found by syllable-rate modulation: voice-band log energy pulsing at 3–9 Hz."""

    def __init__(self, x, tts):
        import librosa
        from scipy.ndimage import uniform_filter1d
        from scipy.signal import butter, sosfiltfilt
        self.x, n = x, len(x) // HOP
        if tts:
            r = np.sqrt((x[: n * HOP].reshape(n, HOP) ** 2).mean(axis=1))
            self.speech = 20 * np.log10(r / (r.max() + 1e-9) + 1e-9) > -22
        else:
            s = np.abs(librosa.stft(x, n_fft=512, hop_length=HOP))
            f = librosa.fft_frequencies(sr=SR, n_fft=512)
            e = np.log(s[(f > 250) & (f < 3800)].sum(axis=0) + 1e-6)[:n]
            mod = uniform_filter1d(sosfiltfilt(butter(2, [3, 9], btype="band", fs=100, output="sos"), e) ** 2, size=40)
            edge = 30  # the filters ring at the clip's end
            mod[-edge:] = 0
            # The 0.4 s smoothing widens every stretch of speech by ~0.2 s on each side: erode it back, or the voice's
            # first word lands before his lips open.
            from scipy.ndimage import binary_erosion
            self.speech = binary_erosion(mod > 0.08 * mod.max(), iterations=18)

    def bounds(self):
        on = np.where(self.speech)[0]
        return (int(on[0]), int(on[-1]) + 1) if len(on) else (0, len(self.speech))

    def features(self, a=0, b=None):
        """MFCCs + deltas (close enough between two voices saying the same words) + a voice-activity row."""
        import librosa
        b = len(self.speech) if b is None else b
        m = librosa.feature.mfcc(y=self.x[a * HOP: b * HOP], sr=SR, n_mfcc=20, hop_length=HOP, n_fft=512)[1:]
        m = np.vstack([m, librosa.feature.delta(m)])[:, : b - a]
        m = (m - m.mean(axis=1, keepdims=True)) / (m.std(axis=1, keepdims=True) + 1e-6)
        return np.vstack([m, VAD_WEIGHT * (self.speech[a:b].astype(float) - 0.5)])


def fit(voice_wav, take_mp4):
    """Align one take to the voice: voice 10 ms frame → take 10 ms frame (monotonic, rate-limited) + scores."""
    import librosa
    v = Track(mono(voice_wav, SR)[0], tts=True)
    a, b = v.bounds()
    v0, v1 = max(0, a - int(LEAD * 100)), min(len(v.speech), b + int(TAIL * 100))
    t = Track(mono(take_mp4, SR)[0], tts=False)
    ta, tb = t.bounds()
    t0, t1 = max(0, ta - int(LEAD * 100)), min(len(t.speech), tb + int(TAIL * 100))
    _, wp = librosa.sequence.dtw(X=v.features(v0, v1), Y=t.features(t0, t1), backtrack=True)
    wp = wp[::-1]
    wp[:, 1] += t0
    n = v1 - v0
    raw = np.array([np.median(wp[wp[:, 0] == i, 1]) if (wp[:, 0] == i).any() else wp[-1, 1] for i in range(n)])
    k = 6  # smooth over 130 ms
    sm = np.convolve(np.pad(raw, k, mode="edge"), np.ones(2 * k + 1) / (2 * k + 1), mode="valid")
    talking = v.speech[v0:v1]
    m = np.empty(n)
    m[0] = sm[0]
    for i in range(1, n):
        lo, hi = SPEECH_RATE if talking[i] else PAUSE_RATE
        m[i] = np.clip(sm[i], m[i - 1] + lo, m[i - 1] + hi)
    m = np.minimum(m, len(t.speech) - 1)
    agree = float((t.speech[np.round(m).astype(int)] == talking).mean())
    rate = np.diff(m)[talking[1:]]
    bend = float(np.abs(np.log(np.clip(rate, 1e-3, None))).mean()) if len(rate) else 0.0
    return {"take": pathlib.Path(take_mp4), "v0": v0, "m": m, "agree": agree, "bend": bend,
            "score": agree - 0.5 * bend, "said": (ta / 100, tb / 100)}


def render(best, voice_wav, out):
    m, v0 = best["m"], best["v0"]
    T0, T1 = float(m[0]) / 100, float(m[-1]) / 100 + 0.01
    with tempfile.TemporaryDirectory() as d:
        d = pathlib.Path(d)
        src, mapf, fitted = d / "voice.wav", d / "map.txt", d / "fitted.wav"
        full, _ = mono(voice_wav, 48000)
        seg = full[v0 * 480: (v0 + len(m)) * 480]
        with wave.open(str(src), "wb") as w:
            w.setnchannels(1), w.setsampwidth(2), w.setframerate(48000)
            w.writeframes((np.clip(seg, -1, 1) * 32767).astype("<i2").tobytes())
        # Keyframes every 100 ms (denser ones make Rubber Band drop segments): voice sample → take-relative sample,
        # strictly increasing.
        rows, last = [], -1
        for i in range(10, len(m) - 5, 10):
            tgt = int(round((m[i] / 100 - T0) * 48000))
            if tgt > last:
                rows.append(f"{i * 480} {tgt}")
                last = tgt
        mapf.write_text("\n".join(rows) + "\n")
        subprocess.run(["rubberband", "-q", "--fine", "-M", str(mapf), "-D", f"{T1 - T0:.4f}", str(src), str(fitted)],
                       check=True)
        pathlib.Path(out).parent.mkdir(parents=True, exist_ok=True)
        ff("-ss", f"{T0:.3f}", "-i", str(best["take"]), "-i", str(fitted), "-t", f"{T1 - T0:.3f}", "-map", "0:v:0",
           "-map", "1:a:0", "-c:v", "libx264", "-crf", "12", "-preset", "slow", "-pix_fmt", "yuv420p", "-c:a", "aac",
           "-b:a", "256k", "-ar", "48000", str(out))
    return T0, T1


def judged(judge, clip):
    """SyncNet over 2 s windows every 0.5 s: (mean confidence, [(window centre in clip seconds, offset, confidence)])."""
    pts = [(t + 1.0, sc[0], sc[1]) for t, sc in judge.windows(clip, 2.0, 0.5) if sc]
    return (float(np.mean([c for _, _, c in pts])) if pts else 0.0), pts


def corrected(r, pts, min_conf=2.5):
    """The alignment shifted by SyncNet's offsets: where the voice is early by k frames (offset +k), it is delayed by
    k × 40 ms there (and advanced where it is late). Only confident windows count; the correction is interpolated
    between window centres and kept monotonic."""
    good = [(c, o) for c, o, conf in pts if conf >= min_conf and abs(o) <= 8]
    if not good:
        return None
    m = r["m"]
    clip_t = (m - m[0]) / 100
    xs, ys = zip(*sorted(good))
    delta = np.interp(clip_t, xs, ys) * 4.0                      # frames at 25 fps → 10 ms units
    m2 = m + delta
    for i in range(1, len(m2)):
        m2[i] = max(m2[i], m2[i - 1] + 0.5)                        # never backwards, never frozen
    m2 = np.clip(m2, 0, None)
    return {**r, "m": m2}


def main(voice_wav, name, takes):
    out = OUT / f"{name}.mp4"
    results = []
    for tk in takes:
        r = fit(voice_wav, tk)
        results.append(r)
        print(f"fit {name}: {r['take'].stem} (speech {r['said'][0]:.2f}-{r['said'][1]:.2f}s): rhythm match "
              f"{r['agree']:.0%}, voice bend {r['bend']:.2f} (score {r['score']:.2f})")
    repo = os.environ.get("SYNCNET_REPO")
    if not repo:
        best = max(results, key=lambda r: r["score"])
        T0, T1 = render(best, voice_wav, out)
        print(f"fit {name}: chose {best['take'].stem} (no SyncNet: rhythm score), its frames {T0:.2f}-{T1:.2f}s → {out}")
        return
    # SyncNet decides: the take whose fitted clip it finds best in sync, then up to two timing corrections.
    from sync_check import Judge
    judge = Judge(repo)
    tmp = pathlib.Path(tempfile.mkdtemp())
    scored = []
    for r in results:
        clip = tmp / f"{r['take'].stem}.mp4"
        render(r, voice_wav, clip)
        conf, pts = judged(judge, clip)
        scored.append((conf, r, pts, clip))
        print(f"fit {name}: {r['take'].stem} fitted → SyncNet {conf:.2f} "
              f"(offsets {' '.join(f'{o:+d}' for _, o, _ in pts)})", flush=True)
    conf, best, pts, clip = max(scored, key=lambda x: x[0])
    for k in range(2):
        nxt = corrected(best, pts)
        if nxt is None:
            break
        clip2 = tmp / f"{best['take'].stem}_fix{k}.mp4"
        render(nxt, voice_wav, clip2)
        conf2, pts2 = judged(judge, clip2)
        print(f"fit {name}: correction {k + 1} → SyncNet {conf2:.2f} (offsets {' '.join(f'{o:+d}' for _, o, _ in pts2)})",
              flush=True)
        if conf2 <= conf:
            break
        conf, best, pts, clip = conf2, nxt, pts2, clip2
    out.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy(clip, out)
    print(f"fit {name}: chose {best['take'].stem}, SyncNet {conf:.2f} → {out}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], [t for t in sys.argv[3].split(",") if t])
