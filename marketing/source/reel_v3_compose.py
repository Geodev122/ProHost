"""Office reel v3: cuts the AI-avatar clips (marketing/avatar/clips, from scripts/marketing/avatar_video.py) to the voice,
renders the overlay layer (reel_office_v3.html, transparent) on top and scores it.

Segments: s1 and s5 carry Veo's own lip-synced speech (their speech window is detected in the clip audio); s2-s4 and the
price segment carry the v2 voice-over lines (audio/voiceovers-arabic-reel/lines). Music: audio/music/reel_office.wav,
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
OUT = ROOT / "02-reels-arabic-9x16" / "12-office-sourcing-avatar.mp4"
CHROME = os.environ.get("CHROME", "/opt/pw-browsers/chromium-1194/chrome-linux/chrome")
FPS = 30
TMP = pathlib.Path(tempfile.mkdtemp(prefix="reel3_"))

# role (the overlay keys its animations on it), clip (under avatar/clips, or "lipsync/<shot>" for a lip-synced clip that
# carries the owner's voice), speech ("clip" = that clip's own audio, "veo" = Veo's own speech, "dub" = the avatar's
# speaking footage voiced by voice-over `line`, aligned to his mouth inside `win`, else a voice-over line number), clip
# offset (s), caption. Hook + CTA are spoken on camera by the avatar (Veo's own voice and lip movement) with
# the owner's wording; the narration lines are the Lebanese voice-over.
SEGS = [
    {"role": "hook", "clip": "s1", "speech": "veo", "cap": "بدفع أجار عيادتي كامل… وهيّي <b>فاضية</b> نُصّ النهار."},
    {"role": "s2", "clip": "s2", "speech": 2, "from": 1.6, "cap": "بين جلسة وجلسة، ساعات ضايعة… و<b>مصاري</b> عم تطير."},
    {"role": "s3", "clip": "s3", "speech": 3, "from": 0.3, "cap": "هلّق عم أجّرها <b>بالساعة</b> على ProHost، لـ psychologists و coaches متلي."},
    {"role": "s4", "clip": "s4", "speech": 4, "from": 0.0, "cap": "أنا بحدّد الوقت والسعر، والطلبات بتوصلني عَال<b>واتساب</b>."},
    {"role": "cta1", "clip": "s5_a", "speech": "dub", "line": 5, "win": [0.02, 5.55], "cap": "إذا بدّك مكتب، نزّل <b>التطبيق</b>، تفتّش وتحجز كلّو <b>مجّاني</b>."},
    {"role": "cta2", "clip": "s5b", "speech": "dub", "line": 7, "win": [0.08, 5.12], "cap": "وإذا عندك مكتب بدّك تعرضو، <b>Comment أو DM</b> ليوصلك كل التفاصيل وتربح <b>شهر مجّاني</b>."},
    {"role": "price", "clip": "s4", "speech": 6, "from": 4.2, "tail": 1.5, "cap": "وبعد الشهر؟ بسّ <b>تسعة دولار</b> بالشهر… أقلّ من أجار ساعة وحدة."},
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


def stretch(x, secs):
    """Time-stretches audio to `secs` without changing pitch (ffmpeg atempo, chained when outside 0.5–2)."""
    if len(x) < 10 or secs <= 0.05:
        return np.zeros((max(1, int(secs * SR)), 2), dtype=np.float32)
    f = (len(x) / SR) / secs
    chain = []
    while f > 2.0:
        chain.append("atempo=2.0"); f /= 2.0
    while f < 0.5:
        chain.append("atempo=0.5"); f /= 0.5
    chain.append(f"atempo={f:.5f}")
    import wave
    src, dst = TMP / f"st{len(list(TMP.iterdir()))}.wav", TMP / f"st{len(list(TMP.iterdir())) + 1}_o.wav"
    with wave.open(str(src), "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())
    subprocess.run([FF, "-nostdin", "-y", "-loglevel", "error", "-i", str(src), "-af", ",".join(chain), str(dst)], check=True)
    y = load(dst)
    n = int(secs * SR)
    return np.pad(y, ((0, max(0, n - len(y))), (0, 0)))[:n]


def dub(veo, a, b, line):
    """The voice-over line laid over the avatar's own speech window [a, b]: phrase by phrase when both have the same
    number of phrases (each phrase stretched to the mouth's phrase), else as a whole. Returns audio for [a, b]."""
    la, lb = speech_window(line, 0.02)
    vp, tp = phrases(veo, a, b), phrases(line, la, lb)
    out = np.zeros((int((b - a) * SR), 2), dtype=np.float32)
    if len(vp) == len(tp) and len(vp) > 1:
        for k, ((v0, v1), (t0, t1)) in enumerate(zip(vp, tp)):
            # Each phrase on its mouth phrase, but never more than 25 % slower / faster than spoken, nor into the next one.
            room = (vp[k + 1][0] - v0 - 0.04) if k + 1 < len(vp) else (b - v0 + 0.3)
            secs = min(min(max(v1 - v0, (t1 - t0) / 1.25), (t1 - t0) / 0.8), room)
            piece = stretch(line[int(t0 * SR): int(t1 * SR)], secs)
            i = int((v0 - a) * SR)
            out[i: i + len(piece)] += piece[: len(out) - i]
        print(f"  dub: {len(vp)} phrases aligned")
    else:
        # Keep the voice natural: at most 10 % faster or slower than spoken, even if it then ends a little after the mouth.
        secs = min(max(b - a, (lb - la) / 1.1), (lb - la) / 0.9)
        piece = stretch(line[int(la * SR): int(lb * SR)], secs)
        out = np.zeros((max(len(out), len(piece)), 2), dtype=np.float32)
        out[: len(piece)] += piece
        print(f"  dub: whole line ({len(vp)} vs {len(tp)} phrases), {lb - la:.2f}s → {secs:.2f}s over {b - a:.2f}s of mouth")
    return out


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


def clip_path(name):
    return (ROOT / "avatar" / f"{name}.mp4") if "/" in name else CLIPS / f"{name}.mp4"


def timeline():
    t, segs = 0.0, []
    for s in SEGS:
        clip = clip_path(s["clip"])
        if s["speech"] == "clip":
            # A lip-synced clip already holds exactly the voice (+ a little air): use it whole.
            full = wav48(clip)
            a, b = speech_window(full, 0.08)
            frm, dur = 0.0, len(full) / SR + 0.1
            voice = fade_out(full)
            sp = (t + a, t + b)
        elif s["speech"] == "dub":
            # The avatar's speaking footage, voiced by the Lebanese voice-over line (same words), aligned to his mouth.
            full = wav48(clip)
            a, b = s.get("win") or checked_window(s["clip"]) or speech_window(full)
            frm = max(0.0, a - LEAD)
            line = wav48(LINES / f"reel_office_{s['line']:02d}.wav")
            d = dub(full, a, b, line)
            b = a + len(d) / SR
            dur = min(len(full) / SR - frm, b - frm + VEO_HOLD)
            voice = np.zeros((int(dur * SR), 2), dtype=np.float32)
            i = int((a - frm) * SR)
            voice[i: i + len(d)] = d[: len(voice) - i]
            voice = fade_out(voice)
            sp = (t + a - frm, t + b - frm)
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
        segs.append({**s, "t0": round(t, 3), "t1": round(t + dur, 3), "frm": frm, "dur": dur, "voice": level(voice),
                     "sp": [round(sp[0], 3), round(sp[1], 3)]})
        t += dur
    return segs, t


def base_video(segs, length):
    """Cut + scale every segment to 1080x1920@30 (freezing the last frame if a clip runs short) and concatenate."""
    args, chains = [], []
    for i, s in enumerate(segs):
        args += ["-ss", f"{s['frm']:.3f}", "-i", str(clip_path(s["clip"]))]
        chains.append(f"[{i}:v]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,fps={FPS},setsar=1,"
                      f"tpad=stop_mode=clone:stop_duration={s['dur']:.3f},trim=duration={s['dur']:.3f},setpts=PTS-STARTPTS[v{i}]")
    fc = ";".join(chains) + ";" + "".join(f"[v{i}]" for i in range(len(segs))) + f"concat=n={len(segs)}:v=1:a=0[v]"
    out = TMP / "base.mp4"
    subprocess.run([FF, "-nostdin", "-y", "-loglevel", "error", *args, "-filter_complex", fc, "-map", "[v]",
                    "-c:v", "libx264", "-crf", "14", "-preset", "medium", "-pix_fmt", "yuv420p", "-t", f"{length:.3f}",
                    str(out)], check=True)
    return out


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
    data = {"segs": [{"role": s["role"], "t0": s["t0"], "t1": s["t1"], "sp": s["sp"], "cap": s["cap"]} for s in segs],
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
