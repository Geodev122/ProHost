"""Lip-sync judge: SyncNet (the standard audio–lip sync model, via LatentSync's eval code) scores a clip — the offset
between his mouth and the voice in frames at 25 fps (0 = in sync; POSITIVE = the voice is EARLY, ahead of the lips) and
the confidence (how clearly the mouth follows the voice: native talking video ≈ 4–7, weak < 3). Whole clip and sliding
windows, so a drifting phrase shows up. fit_voice.py uses the same judge to pick the take and correct the timing.

  python sync_check.py <latentsync_repo> <report.json> <clip.mp4> [<clip.mp4> …]
  (.github/workflows/marketing-sync-check.yml; needs torch + the LatentSync repo and its auxiliary/syncnet_v2.model)
"""
import json, os, pathlib, shutil, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
WINDOW, STEP = 2.0, 1.0


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                         capture_output=True, text=True, check=True).stdout
    return float(out.strip())


class Judge:
    """SyncNet + its face detector, loaded once."""

    def __init__(self, repo):
        sys.path.insert(0, str(repo))
        cwd = os.getcwd()
        os.chdir(repo)  # LatentSync's eval code opens its checkpoints relative to the repo
        try:
            import torch
            from eval.syncnet import SyncNetEval
            from eval.syncnet_detect import SyncNetDetector
            device = "cuda" if torch.cuda.is_available() else "cpu"
            self.syncnet = SyncNetEval(device=device)
            self.syncnet.loadParameters(str(pathlib.Path(repo) / "checkpoints/auxiliary/syncnet_v2.model"))
            self.work = pathlib.Path(tempfile.mkdtemp())
            self.detector = SyncNetDetector(device=device, detect_results_dir=str(self.work / "detect"))
        finally:
            os.chdir(cwd)
        self.repo = str(repo)

    def score(self, video):
        """(offset frames, confidence) of one video, or None when no face track was found."""
        det = self.work / "detect"
        shutil.rmtree(det, ignore_errors=True)
        cwd = os.getcwd()
        os.chdir(self.repo)
        try:
            self.detector(video_path=str(video), min_track=40)
            crops = sorted((det / "crop").glob("*")) if (det / "crop").exists() else []
            if not crops:
                return None
            off, _, conf = self.syncnet.evaluate(video_path=str(crops[0]), temp_dir=str(self.work / "tmp"))
        finally:
            os.chdir(cwd)
        return int(off), round(float(conf), 2)

    def windows(self, video, window=WINDOW, step=STEP):
        """[(start s, (offset, confidence) | None)] over sliding windows."""
        d, out, t = duration(video), [], 0.0
        while t + window <= d + 0.01:
            cut = self.work / "win.mp4"
            subprocess.run(["ffmpeg", "-nostdin", "-y", "-loglevel", "error", "-ss", f"{t:.2f}", "-i", str(video), "-t",
                            f"{window:.2f}", "-r", "25", "-c:v", "libx264", "-crf", "12", "-c:a", "aac", str(cut)],
                           check=True)
            out.append((round(t, 2), self.score(cut)))
            t += step
        return out


def main(repo, report, clips):
    judge = Judge(repo)
    results = {}
    for clip in clips:
        clip = pathlib.Path(clip).resolve()
        res = {"duration": round(duration(clip), 2), "whole": judge.score(clip),
               "windows": [{"t": t, "score": s} for t, s in judge.windows(clip)]}
        results[clip.relative_to(ROOT).as_posix()] = res
        w = ", ".join(f"{x['t']:.0f}s {x['score']}" for x in res["windows"])
        print(f"sync {clip.name}: whole {res['whole']} (offset frames, confidence) · windows {w}", flush=True)
    pathlib.Path(report).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(report).write_text(json.dumps(results, indent=1))


if __name__ == "__main__":
    main(sys.argv[1], str(pathlib.Path(sys.argv[2]).resolve()), [str(pathlib.Path(c).resolve()) for c in sys.argv[3:]])
