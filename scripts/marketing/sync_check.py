"""Lip-sync judge: SyncNet (the standard audio–lip sync model, via LatentSync's eval code) scores each clip — the offset
between his mouth and the voice (frames at 25 fps; 0 = in sync, positive = the voice is late) and the confidence (how
clearly the mouth follows the voice; native talking video scores about 6–9, unsynced < 3). Whole clip and 2 s windows,
so a drifting phrase shows up. Runs in .github/workflows/marketing-sync-check.yml (needs torch + the LatentSync repo).

  python sync_check.py <latentsync_repo> <report.json> <clip.mp4> [<clip.mp4> …]
"""
import json, os, pathlib, shutil, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
WINDOW, STEP = 2.0, 1.0


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                         capture_output=True, text=True, check=True).stdout
    return float(out.strip())


def score(video, syncnet, detector, work):
    """(offset, confidence) of one video, or None when no face track was found."""
    det = work / "detect"
    shutil.rmtree(det, ignore_errors=True)
    detector(video_path=str(video), min_track=40)
    crops = sorted((det / "crop").glob("*")) if (det / "crop").exists() else []
    if not crops:
        return None
    off, _, conf = syncnet.evaluate(video_path=str(crops[0]), temp_dir=str(work / "tmp"))
    return int(off), round(float(conf), 2)


def main(repo, report, clips):
    sys.path.insert(0, repo)
    os.chdir(repo)
    import torch
    from eval.syncnet import SyncNetEval
    from eval.syncnet_detect import SyncNetDetector
    device = "cuda" if torch.cuda.is_available() else "cpu"
    syncnet = SyncNetEval(device=device)
    syncnet.loadParameters("checkpoints/auxiliary/syncnet_v2.model")
    work = pathlib.Path(tempfile.mkdtemp())
    detector = SyncNetDetector(device=device, detect_results_dir=str(work / "detect"))
    results = {}
    for clip in clips:
        clip = pathlib.Path(clip).resolve()
        d = duration(clip)
        res = {"duration": round(d, 2), "whole": score(clip, syncnet, detector, work), "windows": []}
        t = 0.0
        while t + WINDOW <= d + 0.01:
            cut = work / "win.mp4"
            subprocess.run(["ffmpeg", "-nostdin", "-y", "-loglevel", "error", "-ss", f"{t:.2f}", "-i", str(clip), "-t",
                            f"{WINDOW:.2f}", "-r", "25", "-c:v", "libx264", "-crf", "12", "-c:a", "aac", str(cut)],
                           check=True)
            res["windows"].append({"t": round(t, 2), "score": score(cut, syncnet, detector, work)})
            t += STEP
        results[clip.relative_to(ROOT).as_posix()] = res
        w = ", ".join(f"{x['t']:.0f}s {x['score']}" for x in res["windows"])
        print(f"sync {clip.name}: whole {res['whole']} (offset frames, confidence) · windows {w}", flush=True)
    pathlib.Path(report).write_text(json.dumps(results, indent=1))


if __name__ == "__main__":
    main(sys.argv[1], str(pathlib.Path(sys.argv[2]).resolve()), [str(pathlib.Path(c).resolve()) for c in sys.argv[3:]])
