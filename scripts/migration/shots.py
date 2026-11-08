"""Shared helpers for the migration screenshot tools (diff_shots.py, compare_android_ios.py)."""
from __future__ import annotations

import base64
import html
import io
from pathlib import Path

import numpy as np
from PIL import Image


def list_pngs(root: Path) -> dict[str, Path]:
    """Relative POSIX name -> path for every PNG under root."""
    return {p.relative_to(root).as_posix(): p for p in sorted(root.rglob("*.png"))}


def load_rgba(path: Path) -> np.ndarray:
    return np.asarray(Image.open(path).convert("RGBA"), dtype=np.int16)


def masks_for(name: str, masks: dict | None) -> list[list[int]]:
    """Rectangles [x, y, w, h] (pixels of the full-size image) to ignore for file `name`; keys are fnmatch patterns."""
    import fnmatch
    out: list[list[int]] = []
    for pattern, rects in (masks or {}).items():
        if pattern.startswith("_"):
            continue  # documentation keys
        if fnmatch.fnmatch(name, pattern):
            out += rects
    return out


def _delta(a: np.ndarray, b: np.ndarray, rects: list[list[int]]) -> np.ndarray:
    delta = np.abs(a - b).max(axis=2)
    for x, y, w, h in rects:
        delta[y:y + h, x:x + w] = 0
    return delta


def compare(golden: Path, candidate: Path, channel_tol: int = 0, rects: list[list[int]] | None = None) -> dict:
    """Pixel comparison. `diff_ratio` = share of pixels where any channel differs by more than channel_tol
    (outside the masked rectangles, which mark known dynamic regions such as a wall-clock timestamp)."""
    a, b = load_rgba(golden), load_rgba(candidate)
    if a.shape != b.shape:
        return {"same_size": False, "golden_size": a.shape[1::-1], "candidate_size": b.shape[1::-1],
                "diff_pixels": None, "diff_ratio": 1.0, "max_channel_diff": None}
    delta = _delta(a, b, rects or [])
    changed = delta > channel_tol
    return {"same_size": True, "golden_size": a.shape[1::-1], "candidate_size": b.shape[1::-1],
            "diff_pixels": int(changed.sum()), "diff_ratio": float(changed.mean()),
            "max_channel_diff": int(delta.max())}


def diff_image(golden: Path, candidate: Path, channel_tol: int = 0, rects: list[list[int]] | None = None) -> Image.Image:
    """Faded candidate with changed pixels in red (same-size images only)."""
    a, b = load_rgba(golden), load_rgba(candidate)
    base = (b[..., :3] * 0.35 + 255 * 0.65).astype(np.uint8)
    mask = _delta(a, b, rects or []) > channel_tol
    base[mask] = (255, 0, 0)
    return Image.fromarray(base, "RGB")


def thumb_data_uri(img: Image.Image, width: int = 260) -> str:
    img = img.convert("RGB")
    if img.width > width:
        img = img.resize((width, max(1, round(img.height * width / img.width))))
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def page(title: str, body: str) -> str:
    return ("<!doctype html><meta charset=utf-8><title>%s</title>"
            "<style>body{font:14px system-ui;margin:16px;background:#f6f8fb;color:#16202f}"
            "h1{font-size:20px}table{border-collapse:collapse}td,th{border:1px solid #cfd6e0;padding:6px;"
            "vertical-align:top;background:#fff}img{display:block}.bad{color:#b00020;font-weight:600}"
            ".ok{color:#1b7f3b}</style><h1>%s</h1>%s" % (html.escape(title), html.escape(title), body))
