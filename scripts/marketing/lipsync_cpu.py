"""Free lip-sync: LatentSync 1.6 (512 px face) on GitHub's CPU runners, one line split into ~1–2 s chunks at its pauses,
each chunk on its own runner (.github/workflows/marketing-lipsync-cpu.yml; 24 GB swap + VAE slicing keep the 7 GB runners
alive). Slow (about an hour per chunk, chunks in parallel) but costs nothing; gpu_lipsync.py is the paid / GPU path.

  plan <jobs>     cuts every line into chunks of about 0.8–2.2 s at its pauses (frame-exact, 25 fps) → matrix JSON
  sync <chunk>    one chunk: cut video + audio, LatentSync redraws the mouth (512 px face, 20 steps, DeepCache)
  join <jobs>     chunks in order → <LIPSYNC_OUT>/<name>.mp4 (25 fps, voice 1) + <name>_sheet.jpg + <name>_mouth.jpg

Jobs and folders as in lipsync.py ("name:audio:clip:video_from"; LIPSYNC_VOICE / LIPSYNC_CLIPS / LIPSYNC_OUT).
Chunks are cut in the pauses, where the mouth is at rest in the source and in the result, so the joins don't show.
"""
import json, pathlib, subprocess, sys

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from lipsync import FPS, OUT, ROOT, VOICE, clip_file, ff, frames, mono, mouth_sheet, parse  # noqa: E402

MIN_CHUNK, MAX_CHUNK = 0.8, 2.2  # seconds per runner (cut in a pause whenever one is in reach)


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
    ff("-ss", f"{c['vfrom'] + c['a0']:.3f}", "-i", str(clip_file(c["clip"])), "-t", f"{dur:.3f}", "-an",
       "-vf", f"fps={FPS},tpad=stop_mode=clone:stop_duration=2", "-t", f"{dur:.3f}", "-c:v", "libx264", "-crf", "10", str(video))
    ff("-ss", f"{c['a0']:.3f}", "-t", f"{dur:.3f}", "-i", str(VOICE / f"{c['audio']}.wav"), "-ac", "1", "-ar", "16000", str(audio))
    subprocess.run([sys.executable, str(pathlib.Path(__file__).resolve().with_name("latentsync_cpu.py")), "--video", str(video),
                    "--audio", str(audio), "--out", str(out_dir / f"{c['chunk']}.mp4"), "--steps", str(steps)],
                   cwd=repo, check=True)
    print(f"lipsync sync {c['chunk']}: {dur:.2f}s done")


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
