"""Helpers for the avatar lip-sync (gpu_lipsync.py runs LatentSync 1.6 on a cloud GPU): job parsing, ffmpeg, frame
reading and the review sheets.

A job is "name:audio:clip:video_from", e.g. hook:line_1:s1:0.1 — the voice marketing/audio/voice1/<audio>.wav laid on
marketing/avatar/clips/<clip>.mp4 from video_from (the avatar's own speaking take, so head and jaw already move naturally).
LatentSync on the free CPU runners was tried and dropped: 2 CPUs / 7 GB ran it out of memory at 512 px.
"""
import json, pathlib, subprocess, sys, wave

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
VOICE, CLIPS, OUT = ROOT / "marketing/audio/voice1", ROOT / "marketing/avatar/clips", ROOT / "marketing/avatar/lipsync"
FPS = 25


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
