"""Montage engine for the office reel: renders the base video (footage only, no overlays) frame by frame.

Per segment (reel_v3_compose.SEGS → "cam", "punch", "ramp", "in", "jcut"):
  cam    framing beats [(t, zoom, push)] — t in seconds from the segment start (reel_v3_compose resolves "w:<word>" to
         when that caption word is said); zoom 1.0 = full frame, 1.4 = close-up; push = extra zoom gained by the end of the beat (eased dolly).
         Crops are centred on the face (OpenCV track, smoothed), so punch-ins never cut his head off.
  punch  zoom punches (+10 %, 80 ms attack, 0.6 s release) on key words, same time syntax as cam.
  steady for a line he says on camera: the frame holds on his median face position (no tracking), no punches, a third
         of the handheld shake — only the scene's slow push moves. The first office reel's lip-sync read as natural
         partly because nothing moved the frame while he spoke; zooms and reframes mid-sentence fight the mouth.
  ramp   speed ramp [(until, rate)] for footage without lip-sync (e.g. the coffee pour at 0.6x, then 1.1x).
  in     transition into the segment: whip_l / whip_r (whip-pan, directional motion blur), zoom (zoom-blur), cut.
  jcut   the picture changes this many seconds after the segment's audio starts (voice leads the cut).
Everything gets handheld micro-shake, one grade (gentle S-curve, warm highlights, cool shadows), film grain and a
vignette, so the Veo clips read as one camera.
"""
import math, subprocess

import cv2
import numpy as np

W, H, FPS = 1080, 1920, 30
BASE_ZOOM = 1.035  # headroom for the shake and transitions


def ease(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


class Source:
    """Sequential frame reader with a small cache; frame(t) blends the two nearest source frames."""

    def __init__(self, path):
        self.path = str(path)
        cap = cv2.VideoCapture(self.path)
        self.fps = cap.get(cv2.CAP_PROP_FPS) or 24
        self.n = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        cap.release()
        self.cap, self.pos, self.cache = None, 0, {}
        self.faces = None

    def raw(self, i):
        i = max(0, min(self.n - 1, i))
        if i in self.cache:
            return self.cache[i]
        if self.cap is None or i < self.pos:
            if self.cap is not None:
                self.cap.release()
            self.cap, self.pos = cv2.VideoCapture(self.path), 0
        f = None
        while self.pos <= i:
            ok, f = self.cap.read()
            if not ok:
                self.n = self.pos
                return self.raw(self.n - 1)
            f = cv2.resize(f, (W, H)) if f.shape[1] != W else f
            self.cache[self.pos] = f
            self.pos += 1
        for k in [k for k in self.cache if k < i - 6]:
            del self.cache[k]
        return self.cache[i]

    def frame(self, t):
        x = max(0.0, t) * self.fps
        i, a = int(x), x - int(x)
        f0 = self.raw(i)
        if a < 0.08 or i + 1 >= self.n:
            return f0
        return cv2.addWeighted(f0, 1 - a, self.raw(i + 1), a, 0)

    def face(self, t):
        """Smoothed face centre (x, y) and height at clip time t."""
        if self.faces is None:
            det = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
            cap, pts, k = cv2.VideoCapture(self.path), [], 0
            while True:
                ok, f = cap.read()
                if not ok:
                    break
                if k % 4 == 0:
                    g = cv2.cvtColor(cv2.resize(f, (W // 3, H // 3)), cv2.COLOR_BGR2GRAY)
                    found = det.detectMultiScale(g, 1.1, 6, minSize=(60, 60))
                    if len(found):
                        x, y, w, h = max(found, key=lambda b: b[2] * b[3])
                        pts.append((k / self.fps, 3 * (x + w / 2), 3 * (y + h / 2), 3 * h))
                k += 1
            cap.release()
            if not pts:
                pts = [(0.0, W / 2, H * 0.38, H * 0.18)]
            a = np.array(pts)
            # Median-smoothed over ~0.7 s so the crop glides instead of jittering with the detector.
            for c in (1, 2, 3):
                a[:, c] = [np.median(a[max(0, i - 4): i + 5, c]) for i in range(len(a))]
            self.faces = a
        a = self.faces
        return tuple(np.interp(t, a[:, 0], a[:, c]) for c in (1, 2, 3))


def shake(t, seed, amp=1.0):
    """Handheld micro-motion: a few incommensurate sines (px, px, degrees)."""
    r = np.random.default_rng(seed).uniform(0, 2 * math.pi, 6)
    dx = 4.5 * math.sin(2 * math.pi * 0.43 * t + r[0]) + 2.2 * math.sin(2 * math.pi * 1.27 * t + r[1])
    dy = 3.5 * math.sin(2 * math.pi * 0.37 * t + r[2]) + 1.8 * math.sin(2 * math.pi * 1.61 * t + r[3])
    rot = 0.22 * math.sin(2 * math.pi * 0.29 * t + r[4]) + 0.08 * math.sin(2 * math.pi * 1.1 * t + r[5])
    return dx * amp, dy * amp, rot * amp


class Shot:
    """One segment of the timeline: its source, camera beats, punches and speed ramp, rendered at local time u."""

    def __init__(self, seg, src, idx):
        self.s, self.src, self.idx = seg, src, idx
        self.phr = [p - seg["t0"] for p in seg.get("phrases", [])]
        self.beats = sorted((self.when(b[0]), b[1], b[2]) for b in seg.get("cam", [(0, 1.0, 0.04)]))
        self.steady = bool(seg.get("steady"))
        self.punches = [] if self.steady else [self.when(p) for p in seg.get("punch", [])]
        self.ramp = seg.get("ramp")
        self.hold = None  # steady: one face position for the whole line

    def when(self, x):
        if isinstance(x, str) and x.startswith("p"):
            n = int(x[1:])
            return self.phr[min(n, len(self.phr) - 1)] if self.phr else 0.0
        return float(x)

    def src_time(self, u):
        if not self.ramp:
            return self.s["frm"] + u
        t, prev = 0.0, 0.0
        for until, rate in self.ramp:
            if u <= until:
                return self.s["frm"] + t + (u - prev) * rate
            t += (until - prev) * rate
            prev = until
        return self.s["frm"] + t + (u - prev) * self.ramp[-1][1]

    def zoom(self, u):
        i = max([k for k, b in enumerate(self.beats) if b[0] <= u] or [0])
        b0, z, push = self.beats[i]
        b1 = self.beats[i + 1][0] if i + 1 < len(self.beats) else self.s["dur"]
        z = z + push * ease((u - b0) / max(0.3, b1 - b0))
        for p in self.punches:
            d = u - p
            if 0 <= d < 0.7:
                z *= 1 + 0.10 * (min(1, d / 0.08) if d < 0.08 else math.exp(-(d - 0.08) / 0.22))
        return max(1.0, z)

    def render(self, u, extra_zoom=1.0, dx_extra=0.0):
        st = self.src_time(u)
        f = self.src.frame(st)
        z = self.zoom(u) * BASE_ZOOM * extra_zoom
        if self.steady:
            if self.hold is None:
                pts = np.array([self.src.face(self.src_time(x)) for x in np.linspace(0, self.s["dur"], 24)])
                self.hold = tuple(np.median(pts, axis=0))
            fx, fy, fh = self.hold
        else:
            fx, fy, fh = self.src.face(st)
        # Centre: frame centre at full frame, the face (with headroom) as we push in.
        k = min(1.0, (z - BASE_ZOOM) / 0.25)
        cx = W / 2 + (fx - W / 2) * k
        cy = H / 2 + (fy + 0.12 * fh - H / 2) * k  # face a little above centre: headroom kept
        hw, hh = W / (2 * z), H / (2 * z)
        cx, cy = min(max(cx, hw), W - hw), min(max(cy, hh), H - hh)
        sx, sy, rot = shake(self.s["t0"] + u, self.idx, 0.33 if self.steady else 1.0)
        m = cv2.getRotationMatrix2D((cx, cy), rot, z)
        m[0, 2] += W / 2 - cx + sx + dx_extra
        m[1, 2] += H / 2 - cy + sy
        return cv2.warpAffine(f, m, (W, H), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT)


def hblur(img, length):
    n = int(length)
    if n < 3:
        return img
    k = np.zeros((1, n), np.float32)
    k[0, :] = 1.0 / n
    return cv2.filter2D(img, -1, k)


def zblur(img, amount):
    """Radial zoom blur: average of a few slightly scaled copies."""
    if amount < 0.01:
        return img
    acc = img.astype(np.float32)
    for s in (1 + amount * 0.33, 1 + amount * 0.66, 1 + amount):
        m = cv2.getRotationMatrix2D((W / 2, H / 2), 0, s)
        acc += cv2.warpAffine(img, m, (W, H), borderMode=cv2.BORDER_REFLECT)
    return (acc / 4).astype(np.uint8)


def grade_lut():
    x = np.arange(256, dtype=np.float32) / 255
    s = np.clip(x - 0.045 * np.sin(2 * math.pi * x), 0, 1)  # gentle S: deeper shadows, brighter highlights
    hi, lo = np.clip((x - 0.55) / 0.45, 0, 1), np.clip((0.45 - x) / 0.45, 0, 1)
    b = np.clip(s - 0.025 * hi + 0.03 * lo, 0, 1)
    g = np.clip(s + 0.008 * hi + 0.006 * lo, 0, 1)
    r = np.clip(s + 0.03 * hi - 0.012 * lo, 0, 1)
    return [(c * 255).astype(np.uint8) for c in (b, g, r)]


class Finish:
    def __init__(self):
        self.lut = grade_lut()
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        d = np.sqrt(((xx - W / 2) / (W * 0.62)) ** 2 + ((yy - H * 0.45) / (H * 0.62)) ** 2)
        self.vig = (1 - 0.28 * np.clip(d - 0.55, 0, 1) ** 1.6)[..., None].astype(np.float32)
        rng = np.random.default_rng(7)
        self.grain = [cv2.resize(rng.normal(0, 5.5, (H // 2, W // 2)).astype(np.float32), (W, H))[..., None] for _ in range(6)]

    def __call__(self, img, i):
        img = cv2.merge([cv2.LUT(c, l) for c, l in zip(cv2.split(img), self.lut)]).astype(np.float32)
        luma = img.mean(axis=2, keepdims=True)
        img = luma + (img - luma) * 1.05
        img = img * self.vig + self.grain[i % len(self.grain)]
        return np.clip(img, 0, 255).astype(np.uint8)


def render_base(segs, length, clip_path, out, ff):
    shots = [Shot(s, Source(clip_path(s["clip"])), i) for i, s in enumerate(segs)]
    # Visual cut times: a J-cut moves the picture change later than the audio boundary.
    cuts = [s["t0"] + s.get("jcut", 0.0) if i else 0.0 for i, s in enumerate(segs)]
    fin = Finish()
    proc = subprocess.Popen([ff, "-nostdin", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "bgr24", "-s",
                             f"{W}x{H}", "-r", str(FPS), "-i", "-", "-c:v", "libx264", "-crf", "14", "-preset", "medium",
                             "-pix_fmt", "yuv420p", str(out)], stdin=subprocess.PIPE)
    TR = 5  # transition half-length in frames
    for fi in range(int(round(length * FPS))):
        t = fi / FPS
        k = max(i for i, c in enumerate(cuts) if c <= t + 1e-6)
        sh = shots[k]
        img = None
        # The nearest cut (before or after) decides whether we're inside a transition.
        for j in (k, k + 1):
            if 0 < j < len(shots):
                d = (t - cuts[j]) * FPS  # frames from the cut (-TR..TR)
                kind = segs[j].get("in", "cut")
                if kind == "cut" or not -TR <= d < TR:
                    continue
                a, b = shots[j - 1], shots[j]
                p = (d + TR) / (2 * TR)  # 0..1 across the transition
                ua, ub = t - segs[j - 1]["t0"], t - segs[j]["t0"]
                if kind.startswith("whip"):
                    sgn = -1 if kind == "whip_l" else 1
                    e = ease(p)
                    off = W * e
                    speed = W * (ease(min(1, p + 0.1)) - ease(max(0, p - 0.1))) / 0.2 / (2 * TR)
                    fa = a.render(ua, dx_extra=sgn * off)
                    fb = b.render(max(0.0, ub), dx_extra=sgn * (off - W))
                    m = np.zeros((H, W), np.float32)
                    edge = int(W - off) if sgn < 0 else int(off)
                    if sgn < 0:
                        m[:, :max(0, min(W, edge))] = 1
                    else:
                        m[:, max(0, min(W, edge)):] = 1
                    img = (fa * m[..., None] + fb * (1 - m[..., None])).astype(np.uint8)
                    img = hblur(img, min(260, speed * 0.9))
                else:  # zoom: A punches in and blurs out, B lands from a push with radial blur
                    if p < 0.5:
                        img = zblur(a.render(ua, extra_zoom=1 + 0.35 * ease(p * 2)), 0.12 * ease(p * 2))
                    else:
                        img = zblur(b.render(max(0.0, ub), extra_zoom=1 + 0.35 * (1 - ease((p - 0.5) * 2))),
                                    0.12 * (1 - ease((p - 0.5) * 2)))
                break
        if img is None:
            img = sh.render(t - segs[k]["t0"])
        proc.stdin.write(fin(img, fi).tobytes())
    proc.stdin.close()
    proc.wait()
    return out
