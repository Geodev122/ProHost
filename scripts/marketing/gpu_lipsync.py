"""Lip-sync voice 1 onto the avatar on a cloud GPU: LatentSync 1.6 as a Vertex AI custom job in the Firebase project.

The free GitHub runners (2 CPUs, 7 GB) can't run LatentSync at 512 px; one L4 GPU does a 5 s line in minutes (~US$1).
Runs in CI (.github/workflows/marketing-lipsync.yml) with the CI service account.

  1. per job "name:audio:clip:video_from" (e.g. hook:line_1:s1:0.1): the avatar's speaking take from video_from, 25 fps,
     a little longer than the voice (marketing/audio/voice1/<audio>.wav), + the voice at 16 kHz → Cloud Storage
     gs://<bucket>/marketing-gpu/<run>/in/
  2. a custom job (L4, else T4 / A100, in a few regions — whichever has quota) installs LatentSync (pinned), reads the
     inputs through Vertex's Cloud Storage mount (/gcs/<bucket>) and writes out/<name>.mp4 there
  3. the results come back → marketing/avatar/lipsync/<name>.mp4 (the 48 kHz voice muxed back in) + <name>_sheet.jpg +
     <name>_mouth.jpg (mouth close-ups every 4 frames, for review)

usage: gpu_lipsync.py <jobs> [steps] [guidance]
"""
import json, os, pathlib, subprocess, sys, tempfile, time, urllib.parse

import google.auth
import google.auth.transport.requests

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from lipsync import CLIPS, OUT, ROOT, VOICE, ff, frames, mouth_sheet, parse  # noqa: E402

BUCKET = os.environ.get("GPU_BUCKET", "prohost-f766f.firebasestorage.app")
LATENTSYNC_SHA = "a229c3948406bc2cf6eaf4873e662e70c6a04746"
IMAGE = "us-docker.pkg.dev/vertex-ai/training/pytorch-gpu.2-2.py310:latest"
# (region, machine, accelerator) in order of preference; the first with quota wins.
TARGETS = [("us-central1", "g2-standard-8", "NVIDIA_L4"), ("europe-west4", "g2-standard-8", "NVIDIA_L4"),
           ("us-central1", "n1-standard-8", "NVIDIA_TESLA_T4"), ("europe-west4", "n1-standard-8", "NVIDIA_TESLA_T4"),
           ("us-central1", "a2-highgpu-1g", "NVIDIA_TESLA_A100")]

creds, project = google.auth.default(scopes=["https://www.googleapis.com/auth/cloud-platform"])
project = project or "prohost-f766f"
session = google.auth.transport.requests.AuthorizedSession(creds)
HDR = {"x-goog-user-project": project}

RUN_SH = r"""
set -euo pipefail
B=/gcs/{bucket}/{prefix}
mkdir -p "$B/out"
exec > >(tee "$B/out/job.log") 2>&1
nvidia-smi || true
pip install -q --no-cache-dir torch==2.5.1 torchvision==0.20.1 --index-url https://download.pytorch.org/whl/cu121
pip install -q --no-cache-dir diffusers==0.32.2 transformers==4.48.0 decord==0.6.0 accelerate==0.26.1 einops==0.7.0 \
  omegaconf==2.3.0 opencv-python-headless==4.9.0.80 python_speech_features==0.6 librosa==0.10.1 ffmpeg-python==0.2.0 \
  imageio==2.31.1 imageio-ffmpeg==0.5.1 "huggingface-hub[cli]==0.30.2" numpy==1.26.4 kornia==0.8.0 insightface==0.7.3 \
  onnxruntime-gpu==1.21.0 DeepCache==0.1.1 soundfile matplotlib more-itertools regex
command -v ffmpeg || ln -sf "$(python -c 'import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())')" /usr/local/bin/ffmpeg
cd /tmp && git clone -q https://github.com/bytedance/LatentSync.git && cd LatentSync && git checkout -q {sha}
# The face detector falls back to the CPU if onnxruntime can't use this image's CUDA.
sed -i 's/providers=\["CUDAExecutionProvider"\]/providers=["CUDAExecutionProvider", "CPUExecutionProvider"]/' latentsync/utils/face_detector.py
huggingface-cli download ByteDance/LatentSync-1.6 whisper/tiny.pt --local-dir checkpoints > /dev/null
huggingface-cli download ByteDance/LatentSync-1.6 latentsync_unet.pt --local-dir checkpoints > /dev/null
for name in {names}; do
  echo "=== $name"
  python -m scripts.inference --unet_config_path configs/unet/stage2_512.yaml --inference_ckpt_path checkpoints/latentsync_unet.pt \
    --inference_steps {steps} --guidance_scale {guidance} --seed 1247 \
    --video_path "$B/in/$name.mp4" --audio_path "$B/in/$name.wav" --video_out_path "/tmp/$name.mp4"
  cp "/tmp/$name.mp4" "$B/out/$name.mp4"
done
echo done > "$B/out/DONE"
"""


def gcs_put(local, name):
    url = f"https://storage.googleapis.com/upload/storage/v1/b/{BUCKET}/o?uploadType=media&name={urllib.parse.quote(name, safe='')}"
    r = session.post(url, data=pathlib.Path(local).read_bytes(), headers={**HDR, "Content-Type": "application/octet-stream"}, timeout=300)
    r.raise_for_status()


def gcs_get(name):
    r = session.get(f"https://storage.googleapis.com/storage/v1/b/{BUCKET}/o/{urllib.parse.quote(name, safe='')}?alt=media",
                    headers=HDR, timeout=300)
    return r.content if r.ok else None


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                         capture_output=True, text=True, check=True).stdout
    return float(out.strip())


def submit(region, machine, acc, prefix, names, steps, guidance):
    script = RUN_SH.format(bucket=BUCKET, prefix=prefix, sha=LATENTSYNC_SHA, names=" ".join(names), steps=steps, guidance=guidance)
    sa = getattr(creds, "service_account_email", None)
    body = {"displayName": f"prohost-lipsync-{prefix.split('/')[-1]}",
            "jobSpec": {"workerPoolSpecs": [{"machineSpec": {"machineType": machine, "acceleratorType": acc, "acceleratorCount": 1},
                                             "replicaCount": 1, "diskSpec": {"bootDiskType": "pd-ssd", "bootDiskSizeGb": 200},
                                             "containerSpec": {"imageUri": IMAGE, "command": ["bash", "-c", script]}}],
                        "scheduling": {"timeout": "5400s"}, **({"serviceAccount": sa} if sa else {})}}
    r = session.post(f"https://{region}-aiplatform.googleapis.com/v1/projects/{project}/locations/{region}/customJobs",
                     json=body, headers=HDR, timeout=120)
    if not r.ok:
        print(f"::notice::lipsync gpu {region} {acc}: not accepted ({r.status_code}) {r.text[:300]}")
        return None
    return r.json()["name"]


def wait(region, job):
    """Final state + error; a job still waiting for a GPU after 20 min is cancelled (no capacity there) → next target."""
    t0, last = time.time(), ""
    while True:
        j = session.get(f"https://{region}-aiplatform.googleapis.com/v1/{job}", headers=HDR, timeout=60).json()
        st = j.get("state", "")
        if st != last:
            print(f"lipsync gpu: {st} after {int(time.time() - t0)} s", flush=True)
            last = st
        if st in ("JOB_STATE_SUCCEEDED", "JOB_STATE_FAILED", "JOB_STATE_CANCELLED", "JOB_STATE_EXPIRED"):
            return st, j.get("error", {}).get("message", "")
        if st in ("JOB_STATE_QUEUED", "JOB_STATE_PENDING") and time.time() - t0 > 1200:
            session.post(f"https://{region}-aiplatform.googleapis.com/v1/{job}:cancel", headers=HDR, timeout=60)
            return "NO_CAPACITY", "no GPU capacity within 20 min (resource unavailable)"
        if time.time() - t0 > 6000:
            return "TIMEOUT", ""
        time.sleep(30)


def main():
    jobs = parse(sys.argv[1])
    steps = int(sys.argv[2]) if len(sys.argv) > 2 else 30
    guidance = float(sys.argv[3]) if len(sys.argv) > 3 else 1.5
    prefix = f"marketing-gpu/{time.strftime('%Y%m%d-%H%M%S')}"
    tmp = pathlib.Path(tempfile.mkdtemp(prefix="gpuls_"))
    for j in jobs:
        voice = VOICE / f"{j['audio']}.wav"
        secs = duration(voice)
        v, a = tmp / f"{j['name']}.mp4", tmp / f"{j['name']}.wav"
        # The take must be at least as long as the voice (LatentSync would otherwise play it back and forth).
        ff("-ss", f"{j['vfrom']}", "-i", str(CLIPS / f"{j['clip']}.mp4"), "-an", "-vf", "fps=25,tpad=stop_mode=clone:stop_duration=3",
           "-t", f"{secs + 0.2:.3f}", "-c:v", "libx264", "-crf", "10", str(v))
        ff("-i", str(voice), "-ac", "1", "-ar", "16000", str(a))
        gcs_put(v, f"{prefix}/in/{j['name']}.mp4")
        gcs_put(a, f"{prefix}/in/{j['name']}.wav")
        print(f"lipsync gpu {j['name']}: {secs:.2f}s of {j['audio']} on {j['clip']} from {j['vfrom']}s uploaded")
    names = [j["name"] for j in jobs]
    for region, machine, acc in TARGETS:
        job = submit(region, machine, acc, prefix, names, steps, guidance)
        if not job:
            continue
        print(f"lipsync gpu: {acc} job in {region} ({job.split('/')[-1]})", flush=True)
        st, err = wait(region, job)
        log = (gcs_get(f"{prefix}/out/job.log") or b"").decode(errors="replace")
        print(f"lipsync gpu: {st} {err[:400]}")
        if st == "JOB_STATE_SUCCEEDED":
            break
        print("\n".join(log.splitlines()[-40:]))
        if "quota" not in (err + log).lower() and "resource" not in err.lower() and "unavailable" not in err.lower():
            sys.exit(f"::error::lip-sync job failed: {err[:500]}")
    else:
        sys.exit("::error::no GPU target accepted the job (quota)")
    OUT.mkdir(parents=True, exist_ok=True)
    for j in jobs:
        data = gcs_get(f"{prefix}/out/{j['name']}.mp4")
        if not data:
            sys.exit(f"::error::no output for {j['name']}")
        raw = tmp / f"{j['name']}_gpu.mp4"
        raw.write_bytes(data)
        out = OUT / f"{j['name']}.mp4"
        ff("-i", str(raw), "-i", str(VOICE / f"{j['audio']}.wav"), "-map", "0:v", "-map", "1:a", "-c:v", "libx264", "-crf", "14",
           "-preset", "slow", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-shortest", str(out))
        fr = frames(out)
        import cv2, numpy as np
        h, w = fr[0].shape[:2]
        idx = np.linspace(0, len(fr) - 1, 6).astype(int)
        cv2.imwrite(str(OUT / f"{j['name']}_sheet.jpg"), np.hstack([cv2.resize(fr[i], (w // 4, h // 4)) for i in idx]),
                    [cv2.IMWRITE_JPEG_QUALITY, 88])
        mouth_sheet(fr, [], OUT / f"{j['name']}_mouth.jpg")
        print(f"lipsync gpu {j['name']}: {len(fr)} frames → {out.relative_to(ROOT)}")
    (OUT / "gpu_run.json").write_text(json.dumps({"prefix": prefix, "jobs": names, "steps": steps, "guidance": guidance}) + "\n")


if __name__ == "__main__":
    main()
