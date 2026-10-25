"""Lip-sync the AI avatar to the owner's recorded voice (free, CPU, in CI: .github/workflows/marketing-lipsync.yml).

For each job "shot:audio:video_from[:audio_from-audio_to]" (e.g. s1:line_1:0 or s5:line_5:0:0-5.6):
  1. cut the Veo clip (marketing/avatar/clips/<shot>.mp4) from video_from for the audio's length, 25 fps (Wav2Lip's rate);
  2. Wav2Lip GAN redraws the mouth from the audio (marketing/audio/owner/<audio>.wav);
  3. GFPGAN restores the face of every frame (Wav2Lip's mouth is 96 px, soft at full HD);
  4. only the mouth/jaw area (an ellipse from GFPGAN's 5 landmarks, feathered) is blended back into the ORIGINAL frame,
     so eyes, hair, skin texture, background and grain stay Veo's;
  5. → marketing/avatar/lipsync/<shot>.mp4 (with the voice) + <shot>_sheet.jpg (6 frames to review).

usage: lipsync.py <wav2lip repo dir> <wav2lip_gan.pth> <GFPGANv1.4.pth> job[,job...]
"""
import pathlib, subprocess, sys, tempfile

import cv2
import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
CLIPS, VOICE, OUT = ROOT / "marketing/avatar/clips", ROOT / "marketing/audio/owner", ROOT / "marketing/avatar/lipsync"
FPS = 25


def ff(*args):
    subprocess.run(["ffmpeg", "-nostdin", "-y", "-loglevel", "error", *args], check=True)


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                         capture_output=True, text=True, check=True).stdout
    return float(out.strip())


def mouth_mask(shape, lm5):
    """Feathered ellipse over mouth + jaw from 5 landmarks (eyes, nose, mouth corners)."""
    h, w = shape[:2]
    le, re, nose, ml, mr = [np.array(p, dtype=np.float32) for p in lm5]
    mouth = (ml + mr) / 2
    eye_dist = np.linalg.norm(re - le)
    cx, cy = mouth[0], (nose[1] + mouth[1]) / 2 + 0.45 * (mouth[1] - nose[1]) + 0.18 * eye_dist
    ax, ay = 0.95 * eye_dist, 0.78 * eye_dist
    angle = float(np.degrees(np.arctan2(mr[1] - ml[1], mr[0] - ml[0])))
    m = np.zeros((h, w), np.float32)
    cv2.ellipse(m, (int(cx), int(cy)), (int(ax), int(ay)), angle, 0, 360, 1.0, -1)
    k = max(3, int(eye_dist * 0.35) | 1)
    return cv2.GaussianBlur(m, (k, k), 0)[..., None]


def run(job, w2l_dir, w2l_ckpt, restorer, tmp):
    parts = job.split(":")
    shot, audio, vfrom = parts[0], parts[1], float(parts[2] or 0)
    wav = VOICE / f"{audio}.wav"
    a_in, a_hq = tmp / f"{shot}_voice16k.wav", tmp / f"{shot}_voice.wav"  # 16 kHz for Wav2Lip, 48 kHz for the output
    rng = ["-ss", parts[3].split("-")[0], "-to", parts[3].split("-")[1]] if len(parts) > 3 and parts[3] else []
    ff(*rng, "-i", str(wav), "-ac", "1", "-ar", "16000", str(a_in))
    ff(*rng, "-i", str(wav), "-ac", "1", "-ar", "48000", str(a_hq))
    secs = duration(a_in)
    src = tmp / f"{shot}_src.mp4"
    # The clip, from vfrom, for the voice's length (+0.3 s), at 25 fps; a short clip is held on its last frame.
    ff("-ss", f"{vfrom}", "-i", str(CLIPS / f"{shot}.mp4"), "-vf",
       f"fps={FPS},tpad=stop_mode=clone:stop_duration=8", "-t", f"{secs + 0.3:.3f}", "-an", "-c:v", "libx264", "-crf", "12", str(src))
    w2l = tmp / f"{shot}_w2l.mp4"
    subprocess.run([sys.executable, "inference.py", "--checkpoint_path", str(w2l_ckpt), "--face", str(src), "--audio", str(a_in),
                    "--outfile", str(w2l), "--pads", "0", "18", "0", "0", "--face_det_batch_size", "4", "--wav2lip_batch_size", "64"],
                   cwd=w2l_dir, check=True)
    a = cv2.VideoCapture(str(src)); b = cv2.VideoCapture(str(w2l))
    frames = []
    while True:
        ok1, orig = a.read(); ok2, lips = b.read()
        if not (ok1 and ok2):
            break
        restorer.face_helper.clean_all()
        _, _, restored = restorer.enhance(lips, has_aligned=False, only_center_face=True, paste_back=True)
        lms = restorer.face_helper.all_landmarks_5
        if restored is None or not len(lms):
            frames.append(lips)  # no face found: keep Wav2Lip's frame
            continue
        m = mouth_mask(orig.shape, lms[0])
        frames.append((orig * (1 - m) + restored * m).astype(np.uint8))
    OUT.mkdir(parents=True, exist_ok=True)
    raw = tmp / f"{shot}_frames.mp4"
    h, w = frames[0].shape[:2]
    vw = cv2.VideoWriter(str(raw), cv2.VideoWriter_fourcc(*"mp4v"), FPS, (w, h))
    for f in frames:
        vw.write(f)
    vw.release()
    out = OUT / f"{shot}.mp4"
    ff("-i", str(raw), "-i", str(a_hq), "-map", "0:v", "-map", "1:a", "-c:v", "libx264",
       "-crf", "15", "-preset", "slow", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-shortest", str(out))
    idx = np.linspace(0, len(frames) - 1, 6).astype(int)
    sheet = np.hstack([cv2.resize(frames[i], (w // 4, h // 4)) for i in idx])
    cv2.imwrite(str(OUT / f"{shot}_sheet.jpg"), sheet, [cv2.IMWRITE_JPEG_QUALITY, 88])
    print(f"lipsync {shot}: {len(frames)} frames, {secs:.2f} s of {audio} → {out.relative_to(ROOT)}")


def main():
    w2l_dir, w2l_ckpt, gfp_ckpt, jobs = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3], sys.argv[4]
    from gfpgan import GFPGANer
    restorer = GFPGANer(model_path=gfp_ckpt, upscale=1, arch="clean", channel_multiplier=2, bg_upsampler=None)
    tmp = pathlib.Path(tempfile.mkdtemp(prefix="lipsync_"))
    for job in [j.strip() for j in jobs.split(",") if j.strip()]:
        run(job, w2l_dir, w2l_ckpt, restorer, tmp)


if __name__ == "__main__":
    main()
