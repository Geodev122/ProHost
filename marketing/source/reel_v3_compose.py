"""Office reel v3: cuts the AI-avatar clips (marketing/avatar/clips, from scripts/marketing/avatar_video.py) to the voice,
renders the overlay layer (reel_office_v3.html, transparent) on top and scores it.

Two voices: voice 1, the avatar on camera (hook + CTA 1), lip-synced by scripts/marketing/lipsync.py; voice 2, the
narrator (audio/voiceovers-arabic-reel/lines), over footage where he doesn't talk. The footage is cut and moved by
montage.py. Music: audio/music/reel_office.wav,
ducked under speech, with the overlay's sound cues (mix.py's sfx).

usage: python reel_v3_compose.py [preview t,t,...]
  → ../02-reels-arabic-9x16/12-office-sourcing-avatar.mp4 + 12-office-sourcing-avatar_cover.jpg
"""
import json, os, pathlib, subprocess, sys, tempfile

import numpy as np
from playwright.sync_api import sync_playwright

from mix import FF, SR, best_offset, load, sfx

HERE = pathlib.Path(__file__).parent
ROOT = HERE.parent
CLIPS = ROOT / "avatar" / "clips"
LINES = ROOT / "audio" / "voiceovers-arabic-reel" / "lines"
OUT = pathlib.Path(os.environ.get("REEL_OUT") or ROOT / "02-reels-arabic-9x16" / "12-office-sourcing-avatar.mp4")
CHROME = os.environ.get("CHROME", "/opt/pw-browsers/chromium-1194/chrome-linux/chrome")
FPS = 30
TMP = pathlib.Path(tempfile.mkdtemp(prefix="reel3_"))

# role (the overlay keys its animations on it), clip (under avatar/clips, or "lipsync/<name>" for a lip-synced clip that
# carries voice 1; until it exists, "fallback" = (raw clip, from, voice1 file, reel_v1 line) is used unsynced), speech
# ("clip" = that clip's own audio, "veo" = Veo's own speech, else a voice-over line number), clip offset (s), caption;
# cam / punch / ramp / in / jcut drive the montage (montage.py).
SEGS = [
    # Voice 1 (on camera, lip-synced; marketing/avatar/lipsync from scripts/marketing/lipsync.py) — hook and CTA 1.
    {"role": "hook", "clip": "lipsync/hook", "speech": "clip", "fallback": ("s1", 0.1, "line_1", 1),
     "cap": "بدفع أجار عيادتي كامل… وبتضل <b>فاضية</b> نُصّ النهار.",
     "cam": [(0, 1.38, 0.05), ("w:وبتضل", 1.12, 0.05)], "punch": ["w:فاضية"]},
    # Voice 2 (the narrator, voice-over on footage where he doesn't talk).
    {"role": "s2", "clip": "s2", "speech": 2, "from": 1.6, "in": "zoom", "jcut": 0.35,
     "cap": "بين جلسة وجلسة، ساعات ضايعة… و<b>مصاري</b> عم تطير.",
     "cam": [(0, 1.3, 0.06), (2.1, 1.04, 0.05)]},
    {"role": "s3", "clip": "s3", "speech": 3, "from": 0.3, "in": "whip_l", "jcut": 0.35,
     "ramp": [(1.6, 0.6), (99, 1.12)], "cap": "هلّق عم أجّرها <b>بالساعة</b> على ProHost، لـ psychologists و coaches متلي.",
     "cam": [(0, 1.16, 0.06), (2.6, 1.32, 0.04)]},
    {"role": "s4", "clip": "s4", "speech": 4, "from": 0.0, "in": "whip_r", "jcut": 0.35,
     "cap": "أنا بحدّد الوقت والسعر، والطلبات بتوصلني عَال<b>واتساب</b>.",
     "cam": [(0, 1.08, 0.06), (2.0, 1.3, 0.05)]},
    {"role": "cta1", "clip": "lipsync/cta1", "speech": "clip", "fallback": ("s5_a", 0.0, "line_5", 2), "in": "zoom",
     "cap": "إذا بدّك مكتب، نزّل <b>التطبيق</b>، تفتّش وتحجز كلّو <b>مجّاني</b>.",
     "cam": [(0, 1.1, 0.05), ("w:تفتّش", 1.32, 0.04)], "punch": ["w:مجّاني"]},
    {"role": "cta2", "clip": "s5b", "speech": 7, "from": 0.2, "in": "whip_l", "jcut": 0.35,
     "cap": "وإذا عندك مكتب بدّك تعرضو، <b>Comment أو DM</b> ليوصلك كل التفاصيل وتربح <b>شهر مجّاني</b>.",
     "cam": [(0, 1.04, 0.07), ("w:ليوصلك", 1.16, 0.05)], "punch": ["w:شهر"]},
    {"role": "price", "clip": "s4", "speech": 6, "from": 4.2, "tail": 1.5, "in": "cut",
     "cap": "وبعد الشهر؟ بسّ <b>تسعة دولار</b> بالشهر… أقلّ من أجار ساعة وحدة.",
     "cam": [(0, 1.0, 0.1)]},
]
LEAD, HOLD = 0.15, 0.25  # silence before / after each spoken line
VEO_HOLD, FADE = 0.45, 0.12  # after a lip-synced line: hold, then fade the audio out (never clip the last word)


def wav48(src, ss=None, t=None):
    """Any audio (or a clip's audio track) → 48 kHz stereo float array."""
    dst = TMP / f"a{len(list(TMP.iterdir()))}.wav"
    cmd = [FF, "-nostdin", "-y", "-loglevel", "error"] + (["-ss", f"{ss:.3f}"] if ss is not None else []) + \
          (["-t", f"{t:.3f}"] if t is not None else []) + ["-i", str(src), "-vn", "-ac", "2", "-ar", str(SR), str(dst)]
    subprocess.run(cmd, check=True)
    return load(dst)


def speech_window(x, thr=0.12):
    """First / last 20 ms frame louder than thr × the loudest one (seconds)."""
    hop = SR // 50
    n = len(x) // hop
    rms = np.sqrt((x[: n * hop].mean(axis=1).reshape(n, hop) ** 2).mean(axis=1))
    on = np.where(rms > thr * rms.max())[0]
    return (on[0] * hop / SR, (on[-1] + 1) * hop / SR) if len(on) else (0.0, len(x) / SR)


def level(x, target=0.2):
    active = np.abs(x).mean(axis=1) > 0.01
    return x / (max(1e-6, np.sqrt((x[active] ** 2).mean())) / target) if active.any() else x


def last_voiced(x, around, thr=0.15):
    """End of the last 20 ms frame above thr × peak, searched from 0.3 s before `around` to 0.6 s after it (room tone
    after the line must not stretch the shot)."""
    hop = SR // 50
    n = len(x) // hop
    rms = np.sqrt((x[: n * hop].mean(axis=1).reshape(n, hop) ** 2).mean(axis=1))
    lo, hi = max(0, int((around - 0.3) * 50)), min(n, int((around + 0.6) * 50))
    on = [i for i in range(lo, hi) if rms[i] > thr * rms.max()]
    return max(around, (on[-1] + 1) * hop / SR) if on else around


def fade_out(x, secs=FADE):
    k = min(len(x), int(secs * SR))
    if k:
        x = x.copy()
        x[-k:] *= np.linspace(1, 0, k)[:, None]
    return x


def phrases(x, a, b, gap=0.16, thr=0.10):
    """[(start, end)] of the phrases between a and b: stretches of voice split at pauses of at least `gap` seconds."""
    hop = SR // 100
    i0, i1 = int(a * SR) // hop, int(b * SR) // hop
    seg = x[i0 * hop: i1 * hop].mean(axis=1)
    n = len(seg) // hop
    rms = np.sqrt((seg[: n * hop].reshape(n, hop) ** 2).mean(axis=1))
    on = rms > thr * rms.max()
    out, start, quiet = [], None, 0
    for k, v in enumerate(on):
        if v:
            if start is None:
                start = k
            quiet = 0
        elif start is not None:
            quiet += 1
            if quiet * hop / SR >= gap:
                out.append((a + start * hop / SR, a + (k - quiet + 1) * hop / SR))
                start, quiet = None, 0
    if start is not None:
        out.append((a + start * hop / SR, b))
    return [p for p in out if p[1] - p[0] > 0.12]


def checked_window(shot):
    """Speech start/end Gemini heard in the clip (avatar_video.py check stage), else None."""
    rep = ROOT / "avatar" / "report.json"
    history = (json.loads(rep.read_text()) if rep.exists() else []) + \
        [json.loads(f.read_text()) for f in sorted((ROOT / "avatar" / "runs").glob("*.json"))]
    for run in reversed(sorted(history, key=lambda r: r.get("at", ""))):
        res = run.get("shots", {}).get(shot) if run.get("stage") == "check" else None
        if isinstance(res, dict) and "speech_start_s" in res:
            return float(res["speech_start_s"]), float(res["speech_end_s"])
    return None


def word_time(seg, word):
    """When `word` of the caption is said (seconds from the segment start): the caption's words spread over the speech by
    length, the same rule the overlay uses to light them up, so cuts and punches land with the highlighted word."""
    import re
    ws = re.sub(r"</?b>", "", seg["cap"]).split()
    total, acc = sum(len(w) + 1 for w in ws), 0
    for w in ws:
        if word in w:
            return seg["sp"][0] - seg["t0"] + (seg["sp"][1] - seg["sp"][0]) * acc / total
        acc += len(w) + 1
    raise ValueError(f"{word!r} is not in the caption of {seg['role']}")


def anchors(seg):
    """Resolves "w:<word>" times in cam / punch to seconds."""
    at = lambda x: word_time(seg, x[2:]) if isinstance(x, str) and x.startswith("w:") else x
    return {**seg, "cam": [(at(b[0]), b[1], b[2]) for b in seg.get("cam", [(0, 1.0, 0.04)])],
            "punch": [at(p) for p in seg.get("punch", [])]}


def clip_path(name):
    return (ROOT / "avatar" / f"{name}.mp4") if "/" in name else CLIPS / f"{name}.mp4"


def timeline():
    t, segs = 0.0, []
    for s in SEGS:
        clip = clip_path(s["clip"])
        if s["speech"] == "clip" and not clip.exists() and s.get("fallback"):
            raw, vfrom, v1, n = s["fallback"]
            src = ROOT / "audio" / "voice1" / f"{v1}.wav"
            src = src if src.exists() else LINES / f"reel_v1_{n:02d}.wav"
            print(f"  ::warning:: {clip.name} not lip-synced yet: {raw} from {vfrom}s + {src.name}, unsynced")
            line = wav48(src)
            a, b = speech_window(line, 0.02)
            line = fade_out(line[int(a * SR): min(len(line), int((b + 0.12) * SR))])
            s = {**s, "clip": raw, "from": vfrom}
            frm, dur = vfrom, LEAD + len(line) / SR + HOLD
            voice = np.zeros((int(dur * SR), 2), dtype=np.float32)
            voice[int(LEAD * SR): int(LEAD * SR) + len(line)] = line
            sp = (t + LEAD, t + LEAD + len(line) / SR)
        elif s["speech"] == "clip":
            # A lip-synced clip already holds exactly the voice (+ a little air): use it whole.
            full = wav48(clip)
            a, b = speech_window(full, 0.08)
            frm, dur = 0.0, len(full) / SR + 0.1
            voice = fade_out(full)
            sp = (t + a, t + b)
        elif s["speech"] == "veo":
            full = wav48(clip)
            a, b = checked_window(s["clip"]) or speech_window(full)
            b = last_voiced(full, b)
            frm = max(0.0, a - LEAD)
            dur = min(len(full) / SR - frm, b - frm + VEO_HOLD)
            voice = fade_out(full[int(frm * SR): int((frm + dur) * SR)])
            sp = (t + a - frm, t + b - frm)
        else:
            line = wav48(LINES / f"reel_office_{s['speech']:02d}.wav")
            a, b = speech_window(line, 0.02)
            line = fade_out(line[int(a * SR): min(len(line), int((b + 0.12) * SR))])
            frm = s.get("from", 0.0)
            dur = LEAD + len(line) / SR + HOLD + s.get("tail", 0.0)
            voice = np.zeros((int(dur * SR), 2), dtype=np.float32)
            voice[int(LEAD * SR): int(LEAD * SR) + len(line)] = line
            sp = (t + LEAD, t + LEAD + len(line) / SR)
        v = level(voice)
        segs.append({**s, "t0": round(t, 3), "t1": round(t + dur, 3), "frm": frm, "dur": dur, "voice": v,
                     "sp": [round(sp[0], 3), round(sp[1], 3)],
                     "phrases": [round(t + a, 3) for a, _ in phrases(v, 0.0, len(v) / SR)]})
        t += dur
    return segs, t


def base_video(segs, length):
    """The footage, cut and moved by the montage engine (face-tracked framing, punches, whips, grade, grain)."""
    from montage import render_base
    return render_base([anchors(s) for s in segs], length, clip_path, TMP / "base.mp4", FF)


def score(segs, length, events):
    n = int(length * SR)
    voice = np.zeros((n, 2), dtype=np.float32)
    for s in segs:
        i = int(s["t0"] * SR)
        v = s["voice"][: n - i]
        voice[i: i + len(v)] += v
    music = load(ROOT / "audio" / "music" / "reel_office.wav")
    d, _ = best_offset(music, [{"t": e["t"], "major": True} for e in events] + [{"t": 0.0}]) if len(music) / SR > length + 1 else (0.0, 0)
    m = music[int(d * SR): int(d * SR) + n].copy()
    if len(m) < n:
        m = np.pad(m, ((0, n - len(m)), (0, 0)))
    t = np.arange(n) / SR
    m *= (np.minimum(1, t / 0.4) * np.clip((length - t) / 1.2, 0, 1))[:, None]
    m /= max(1e-6, np.sqrt((m ** 2).mean())) / 0.12
    active = np.abs(voice).mean(axis=1) > 0.01
    env, e, step = np.zeros(n, dtype=np.float32), 0.0, 240
    up, dn = 1 - np.exp(-1 / (0.06 * SR)), 1 - np.exp(-1 / (0.4 * SR))
    for i in range(0, n, step):
        target = 1.0 if active[i: i + step].mean() > 0.2 else 0.0
        k = up if target > e else dn
        e += (target - e) * (1 - (1 - k) ** step)
        env[i: i + step] = e
    m *= (0.6 * (1 - 0.45 * env))[:, None]
    fx = np.zeros(n)
    for ev in events:
        x = sfx(ev["k"])
        i = max(0, int(ev["t"] * SR) - (int(0.2 * SR) if ev["k"] == "whoosh" else 0))
        x = x[: max(0, n - i)]
        fx[i: i + len(x)] += x
    mix = m * 0.8 + fx[:, None] * 0.7 + voice
    mix /= max(1.0, np.abs(mix).max() / 0.95)
    out = TMP / "score.wav"
    import wave
    with wave.open(str(out), "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((mix * 32767).astype("<i2").tobytes())
    return out


def main():
    segs, length = timeline()
    data = {"segs": [{"role": s["role"], "t0": s["t0"], "t1": s["t1"], "sp": s["sp"], "cap": s["cap"], "phrases": s["phrases"],
                      "in": s.get("in", "cut"), "cut": round(s["t0"] + s.get("jcut", 0.0), 3)}
                     for s in segs],
            "end": round(length - SEGS[-1].get("tail", 0) + 0.15, 3), "length": round(length, 3)}
    (HERE / "reel_office_v3_data.js").write_text("window.REEL3 = " + json.dumps(data, ensure_ascii=False) + ";\n", encoding="utf-8")
    print("segments:", ", ".join(f"{s['clip']}@{s['frm']:.2f} {s['t0']:.2f}-{s['t1']:.2f} speech {s['sp']}" for s in segs),
          f"length {length:.2f}s")
    base = base_video(segs, length)
    preview = sys.argv[2].split(",") if len(sys.argv) > 2 and sys.argv[1] == "preview" else None
    with sync_playwright() as p:
        b = p.chromium.launch(executable_path=CHROME, args=["--force-color-profile=srgb"])
        pg = b.new_page(viewport={"width": 1080, "height": 1920}, device_scale_factor=1)
        pg.goto((HERE / "reel_office_v3.html").as_uri())
        pg.wait_for_function("document.fonts.ready.then(() => true)")
        pg.wait_for_function("Array.from(document.images).every(i => i.complete)")
        pg.wait_for_timeout(400)
        events = pg.evaluate("window.EVENTS")
        if preview:
            out_dir = HERE / "out"
            out_dir.mkdir(exist_ok=True)
            for ts in preview:
                tt = float(ts)
                pg.evaluate(f"render({tt})")
                ov = TMP / "ov.png"
                pg.screenshot(path=str(ov), omit_background=True)
                subprocess.run([FF, "-nostdin", "-y", "-loglevel", "error", "-ss", f"{tt:.3f}", "-i", str(base), "-i", str(ov),
                                "-filter_complex", "[0:v][1:v]overlay=0:0", "-frames:v", "1",
                                str(out_dir / f"reel3_t{tt:05.2f}.png")], check=True)
            b.close()
            return
        silent = TMP / "video.mp4"
        proc = subprocess.Popen([FF, "-nostdin", "-y", "-loglevel", "error", "-i", str(base), "-f", "image2pipe",
                                 "-framerate", str(FPS), "-c:v", "png", "-i", "-", "-filter_complex",
                                 "[0:v][1:v]overlay=0:0:format=auto,format=yuv420p[v]", "-map", "[v]", "-c:v", "libx264",
                                 "-crf", "17", "-preset", "slow", "-profile:v", "high", "-r", str(FPS), str(silent)],
                                stdin=subprocess.PIPE)
        for f in range(int(round(length * FPS))):
            pg.evaluate(f"render({f / FPS})")
            proc.stdin.write(pg.screenshot(type="png", omit_background=True))
        proc.stdin.close()
        proc.wait()
        b.close()
    audio = score(segs, length, events)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([FF, "-nostdin", "-y", "-loglevel", "error", "-i", str(silent), "-i", str(audio), "-map", "0:v:0",
                    "-map", "1:a:0", "-c:v", "copy", "-af", "loudnorm=I=-14:TP=-1.5:LRA=11,aresample=48000",
                    "-c:a", "aac", "-b:a", "192k", "-t", f"{length:.3f}", "-movflags", "+faststart", str(OUT)], check=True)
    cover_t = next(s for s in segs if s["role"] == "cta2")["sp"][1] - 0.4  # big smile + free-month stamp
    subprocess.run([FF, "-nostdin", "-y", "-loglevel", "error", "-ss", f"{cover_t:.2f}", "-i", str(OUT), "-frames:v", "1",
                    "-q:v", "2", str(OUT.with_name(OUT.stem + "_cover.jpg"))], check=True)
    print(f"{OUT} ({length:.2f}s)")


if __name__ == "__main__":
    main()
