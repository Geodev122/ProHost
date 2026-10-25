"""Lip-sync voice 1 (the on-camera avatar) with LatentSync 1.6 on CPU, split over parallel CI runners
(.github/workflows/marketing-lipsync.yml).

A job is "name:audio:clip:video_from", e.g. hook:line_1:s1:0.1 — the voice marketing/audio/voice1/<audio>.wav laid on
marketing/avatar/clips/<clip>.mp4 from video_from (the avatar's own speaking take, so head and jaw already move naturally).

  plan <jobs>     cuts every line into chunks of about 0.8–2.2 s at its pauses (frame-exact, 25 fps) → matrix JSON
  sync <chunk>    one chunk: cut video + audio, LatentSync redraws the mouth (512 px face, 20+ steps, DeepCache)
  join <jobs>     chunks in order → marketing/avatar/lipsync/<name>.mp4 (25 fps, voice 1) + <name>_sheet.jpg (frames)
                  + <name>_mouth.jpg (mouth close-ups every 4 frames, seams marked)

Chunks are cut in the pauses, where the mouth is at rest in the source and in the result, so the joins don't show.
"""
import json, pathlib, subprocess, sys, wave

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
VOICE, CLIPS, OUT = ROOT / "marketing/audio/voice1", ROOT / "marketing/avatar/clips", ROOT / "marketing/avatar/lipsync"
FPS = 25
MIN_CHUNK, MAX_CHUNK = 0.8, 2.2  # seconds per runner (cut in a pause whenever one is in reach)


def ff(*args):
    subprocess.run(["ffmpeg", "-nostdin", "-y", "-loglevel", "error", *args], check=True)


def mono(path, sr=16000):
    raw = subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-i", str(path), "-ac", "1", "-ar", str(sr), "-f",
                          "s16le", "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768, sr


def parse(jobs):
    out = []
    for j in [j.strip() for j in jobs.split(",") if j.strip()]:
        name, audio, clip, vfrom = j.split(":")
        out.append({"name": name, "audio": audio, "clip": clip, "vfrom": float(vfrom)})
    return out


def cuts(x, sr):
    """Chunk boundaries (seconds, on the 25 fps grid): the quietest frame inside pauses, so each chunk is 0.8–2.2 s."""
    hop = sr // FPS
    n = len(x) // hop
    rms = np.sqrt((x[: n * hop].reshape(n, hop) ** 2).mean(axis=1))
    quiet = rms < 0.08 * rms.max()
    total = n
    bounds, start = [0], 0
    while total - start > MAX_CHUNK * FPS:
        lo, hi = start + int(MIN_CHUNK * FPS), min(total - int(0.5 * FPS), start + int(MAX_CHUNK * FPS))
        window = range(lo, hi + 1)
        # Prefer the middle of a pause (a quiet frame with quiet neighbours), else the quietest frame.
        best = max(window, key=lambda k: (quiet[max(0, k - 2): k + 3].all(), quiet[k], -rms[k]))
        bounds.append(best)
        start = best
    bounds.append(total)
    return [(bounds[i] / FPS, bounds[i + 1] / FPS) for i in range(len(bounds) - 1)]


def plan(jobs):
    matrix = []
    for j in parse(jobs):
        x, sr = mono(VOICE / f"{j['audio']}.wav")
        for k, (a0, a1) in enumerate(cuts(x, sr)):
            matrix.append({**j, "chunk": f"{j['name']}_{k:02d}", "a0": round(a0, 3), "a1": round(a1, 3)})
    print(json.dumps({"include": matrix}))
    for m in matrix:
        print(f"lipsync plan {m['chunk']}: {m['a0']:.2f}-{m['a1']:.2f}s of {m['audio']} on {m['clip']}", file=sys.stderr)


def sync(chunk, repo, out_dir, steps):
    c = json.loads(chunk)
    out_dir = pathlib.Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    dur = c["a1"] - c["a0"]
    video, audio = out_dir / f"{c['chunk']}_src.mp4", out_dir / f"{c['chunk']}_voice.wav"
    ff("-ss", f"{c['vfrom'] + c['a0']:.3f}", "-i", str(CLIPS / f"{c['clip']}.mp4"), "-t", f"{dur:.3f}", "-an",
       "-vf", f"fps={FPS},tpad=stop_mode=clone:stop_duration=2", "-t", f"{dur:.3f}", "-c:v", "libx264", "-crf", "10", str(video))
    ff("-ss", f"{c['a0']:.3f}", "-t", f"{dur:.3f}", "-i", str(VOICE / f"{c['audio']}.wav"), "-ac", "1", "-ar", "16000", str(audio))
    subprocess.run([sys.executable, str(pathlib.Path(__file__).resolve().with_name("latentsync_cpu.py")), "--video", str(video),
                    "--audio", str(audio), "--out", str(out_dir / f"{c['chunk']}.mp4"), "--steps", str(steps)],
                   cwd=repo, check=True)
    print(f"lipsync sync {c['chunk']}: {dur:.2f}s done")


def frames(path):
    import cv2
    cap, out = cv2.VideoCapture(str(path)), []
    while True:
        ok, f = cap.read()
        if not ok:
            return out
        out.append(f)


def mouth_sheet(frs, seams, path):
    """Close-ups of the mouth (Haar face → lower half) every 4 frames; a red bar marks a chunk seam."""
    import cv2
    det = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
    tiles, box = [], None
    for i in range(0, len(frs), 4):
        f = frs[i]
        faces = det.detectMultiScale(cv2.cvtColor(f, cv2.COLOR_BGR2GRAY), 1.1, 5, minSize=(200, 200))
        if len(faces):
            box = max(faces, key=lambda b: b[2] * b[3])
        if box is None:
            continue
        x, y, w, h = box
        t = cv2.resize(f[y + h // 2: y + h + h // 8, x: x + w], (240, 150))
        if any(abs(i - s) < 4 for s in seams):
            cv2.rectangle(t, (0, 0), (239, 149), (0, 0, 255), 4)
        tiles.append(t)
    rows = [np.hstack(tiles[i: i + 8] + [np.zeros_like(tiles[0])] * (8 - len(tiles[i: i + 8]))) for i in range(0, len(tiles), 8)]
    if rows:
        cv2.imwrite(str(path), np.vstack(rows), [cv2.IMWRITE_JPEG_QUALITY, 90])


def join(jobs, chunk_dir):
    import cv2
    chunk_dir = pathlib.Path(chunk_dir)
    OUT.mkdir(parents=True, exist_ok=True)
    for j in parse(jobs):
        parts = sorted(chunk_dir.rglob(f"{j['name']}_[0-9][0-9].mp4"))
        if not parts:
            print(f"::warning::no chunks for {j['name']}")
            continue
        allf, seams = [], []
        for p in parts:
            fr = frames(p)
            if allf:
                seams.append(len(allf))
            allf += fr
        h, w = allf[0].shape[:2]
        raw = chunk_dir / f"{j['name']}_frames.mp4"
        vw = cv2.VideoWriter(str(raw), cv2.VideoWriter_fourcc(*"mp4v"), FPS, (w, h))
        for f in allf:
            vw.write(f)
        vw.release()
        out = OUT / f"{j['name']}.mp4"
        ff("-i", str(raw), "-i", str(VOICE / f"{j['audio']}.wav"), "-map", "0:v", "-map", "1:a", "-c:v", "libx264", "-crf", "14",
           "-preset", "slow", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-shortest", str(out))
        idx = np.linspace(0, len(allf) - 1, 6).astype(int)
        cv2.imwrite(str(OUT / f"{j['name']}_sheet.jpg"), np.hstack([cv2.resize(allf[i], (w // 4, h // 4)) for i in idx]),
                    [cv2.IMWRITE_JPEG_QUALITY, 88])
        mouth_sheet(allf, seams, OUT / f"{j['name']}_mouth.jpg")
        print(f"lipsync join {j['name']}: {len(parts)} chunks, {len(allf)} frames ({len(allf) / FPS:.2f}s), seams at {seams} → "
              f"{out.relative_to(ROOT)}")


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "plan":
        plan(sys.argv[2])
    elif cmd == "sync":
        sync(sys.argv[2], sys.argv[3], sys.argv[4], int(sys.argv[5]) if len(sys.argv) > 5 else 20)
    elif cmd == "join":
        join(sys.argv[2], sys.argv[3])
    else:
        sys.exit(f"unknown command {cmd}")
