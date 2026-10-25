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

usage: gpu_lipsync.py <jobs> [steps] [guidance] [backend: fal | vertex | hf | fal-probe]

backend fal (default, the owner's choice): Sync's lipsync-2-pro on fal.ai (FAL_KEY secret) — minutes, no GPU quota
needed; model ids are tried in order and the log names the one that answered. fal-probe only checks the key (a tiny
upload, nothing generated).

backend hf: when the project has no GPU quota for Vertex training (the default for a new project: 429 on every GPU
type), the same LatentSync runs on a public Hugging Face Space (ZeroGPU) through gradio_client.
"""
import json, os, pathlib, subprocess, sys, tempfile, time, urllib.parse

import google.auth
import google.auth.transport.requests

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from lipsync import OUT, ROOT, VOICE, clip_file, ff, frames, mouth_sheet, parse  # noqa: E402

BUCKET = os.environ.get("GPU_BUCKET", "prohost-f766f.firebasestorage.app")
LATENTSYNC_SHA = "a229c3948406bc2cf6eaf4873e662e70c6a04746"
IMAGE = "us-docker.pkg.dev/vertex-ai/training/pytorch-gpu.2-2.py310:latest"
# (region, machine, accelerator) in order of preference; the first with quota wins.
TARGETS = [("us-central1", "g2-standard-8", "NVIDIA_L4"), ("europe-west4", "g2-standard-8", "NVIDIA_L4"),
           ("us-central1", "n1-standard-8", "NVIDIA_TESLA_T4"), ("europe-west4", "n1-standard-8", "NVIDIA_TESLA_T4"),
           ("us-central1", "a2-highgpu-1g", "NVIDIA_TESLA_A100")]

try:  # only the vertex backend needs Google credentials
    creds, project = google.auth.default(scopes=["https://www.googleapis.com/auth/cloud-platform"])
    session = google.auth.transport.requests.AuthorizedSession(creds)
except Exception:  # noqa: BLE001
    creds, project, session = None, None, None
project = project or "prohost-f766f"
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


FAL_MODELS = [("fal-ai/sync-lipsync/v2/pro", {}), ("fal-ai/sync-lipsync/v2", {"model": "lipsync-2-pro"}),
              ("fal-ai/sync-lipsync/v2", {}), ("fal-ai/latentsync", {})]


def fal_key_ok():
    if not os.environ.get("FAL_KEY"):
        sys.exit("::error::FAL_KEY is missing: add it in GitHub › Settings › Secrets and variables › Actions")
    import fal_client
    probe = pathlib.Path(tempfile.mkdtemp()) / "probe.txt"
    probe.write_text("prohost")
    try:
        fal_client.upload_file(str(probe))
    except Exception as e:  # noqa: BLE001
        sys.exit(f"::error::fal.ai refused the key ({str(e)[:200]})")
    print("lipsync fal: key OK")


def fal_run(video, audio, out):
    """One job on fal.ai; returns the model id that produced it, or None."""
    import fal_client, requests as rq
    v_url, a_url = fal_client.upload_file(str(video)), fal_client.upload_file(str(audio))
    for model, extra in FAL_MODELS:
        args = {"video_url": v_url, "audio_url": a_url, "sync_mode": "cut_off", **extra}
        try:
            res = fal_client.subscribe(model, arguments=args, with_logs=False)
        except Exception as e:  # noqa: BLE001 — an unknown model / field: try the next
            print(f"::notice::lipsync fal {model} {extra or ''}: {str(e)[:240]}")
            continue
        url = (res.get("video") or {}).get("url") if isinstance(res, dict) else None
        if not url:
            print(f"::notice::lipsync fal {model}: no video in {str(res)[:200]}")
            continue
        r = rq.get(url, timeout=300)
        r.raise_for_status()
        pathlib.Path(out).write_bytes(r.content)
        return model + (f" {extra}" if extra else "")
    return None


HF_SPACES = ["fffiloni/LatentSync", "ByteDance/LatentSync", "jbilcke-hf/LatentSync", "TMElyralab/MuseTalk"]


def hf_run(video, audio, out):
    """Runs one job on the first public Space whose API takes a video and an audio; returns the Space id or None."""
    from gradio_client import Client, handle_file
    token = os.environ.get("HF_TOKEN") or None
    for space in HF_SPACES:
        try:
            try:
                c = Client(space, token=token, verbose=False)  # gradio_client >= 1.0
            except TypeError:
                c = Client(space, hf_token=token, verbose=False)
            api = c.view_api(return_format="dict", print_info=False)
        except Exception as e:  # noqa: BLE001 — a Space that is down or private: try the next
            print(f"::notice::lipsync hf {space}: unavailable ({str(e)[:160]})")
            continue
        for ep, info in (api.get("named_endpoints") or {}).items():
            params = info.get("parameters", [])
            kinds = [str(p.get("component", "")).lower() + " " + str(p.get("label", "")).lower() for p in params]
            if not any("video" in k for k in kinds) or not any("audio" in k for k in kinds):
                continue
            print(f"lipsync hf {space}{ep}: {[(p.get('label'), p.get('component')) for p in params]}", flush=True)
            res = None
            # Gradio versions differ on how a Video input is passed: the file itself, or {"video": file}.
            for wrap in (False, True):
                args = []
                for p, k in zip(params, kinds):
                    if "video" in k:
                        args.append({"video": handle_file(str(video))} if wrap else handle_file(str(video)))
                    elif "audio" in k:
                        args.append(handle_file(str(audio)))
                    else:
                        args.append(p.get("parameter_default") if p.get("parameter_has_default") else None)
                try:
                    res = c.predict(*args, api_name=ep)
                    break
                except Exception as e:  # noqa: BLE001
                    print(f"::notice::lipsync hf {space}{ep} (wrapped={wrap}): failed ({str(e)[:300]})")
            if res is None:
                break
            path = res.get("video") if isinstance(res, dict) else (res[0] if isinstance(res, (list, tuple)) else res)
            if isinstance(path, dict):
                path = path.get("video") or path.get("path")
            if path and pathlib.Path(path).exists():
                pathlib.Path(out).write_bytes(pathlib.Path(path).read_bytes())
                return space
            print(f"::notice::lipsync hf {space}{ep}: no video in {str(res)[:200]}")
            break
    return None


def main():
    jobs = parse(sys.argv[1])
    steps = int(sys.argv[2]) if len(sys.argv) > 2 else 30
    guidance = float(sys.argv[3]) if len(sys.argv) > 3 else 1.5
    prefix = f"marketing-gpu/{time.strftime('%Y%m%d-%H%M%S')}"
    tmp = pathlib.Path(tempfile.mkdtemp(prefix="gpuls_"))
    backend = sys.argv[4] if len(sys.argv) > 4 else "fal"
    if backend.startswith("fal"):
        fal_key_ok()
        if backend == "fal-probe":
            return
    for j in jobs:
        voice = VOICE / f"{j['audio']}.wav"
        secs = duration(voice)
        v, a = tmp / f"{j['name']}.mp4", tmp / f"{j['name']}.wav"
        # The take must be at least as long as the voice (LatentSync would otherwise play it back and forth).
        ff("-ss", f"{j['vfrom']}", "-i", str(clip_file(j['clip'])), "-an", "-vf", "fps=25,tpad=stop_mode=clone:stop_duration=3",
           "-t", f"{secs + 0.3:.3f}", "-c:v", "libx264", "-crf", "10", str(v))
        ff("-i", str(voice), "-ac", "1", "-ar", "48000" if backend == "fal" else "16000", str(a))
        if backend == "vertex":
            gcs_put(v, f"{prefix}/in/{j['name']}.mp4")
            gcs_put(a, f"{prefix}/in/{j['name']}.wav")
        print(f"lipsync gpu {j['name']}: {secs:.2f}s of {j['audio']} on {j['clip']} from {j['vfrom']}s ready")
    names = [j["name"] for j in jobs]
    if backend == "fal":
        OUT.mkdir(parents=True, exist_ok=True)
        for j in jobs:
            model = fal_run(tmp / f"{j['name']}.mp4", tmp / f"{j['name']}.wav", tmp / f"{j['name']}_gpu.mp4")
            if not model:
                sys.exit(f"::error::fal.ai lip-synced nothing for {j['name']} (see the notices above)")
            print(f"lipsync fal {j['name']}: done with {model}")
        return finish(jobs, tmp, {"backend": "fal"})
    if backend == "hf":
        OUT.mkdir(parents=True, exist_ok=True)
        for j in jobs:
            raw = tmp / f"{j['name']}_gpu.mp4"
            space = hf_run(tmp / f"{j['name']}.mp4", tmp / f"{j['name']}.wav", raw)
            if not space:
                sys.exit(f"::error::no Hugging Face Space lip-synced {j['name']}")
            print(f"lipsync hf {j['name']}: done on {space}")
        return finish(jobs, tmp, {"backend": "hf"})
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
        (tmp / f"{j['name']}_gpu.mp4").write_bytes(data)
    finish(jobs, tmp, {"backend": "vertex", "prefix": prefix, "steps": steps, "guidance": guidance})


def finish(jobs, tmp, meta):
    """The lip-synced takes → marketing/avatar/lipsync/<name>.mp4 with the 48 kHz voice, + review sheets."""
    import cv2, numpy as np
    for j in jobs:
        raw = tmp / f"{j['name']}_gpu.mp4"
        out = OUT / f"{j['name']}.mp4"
        ff("-i", str(raw), "-i", str(VOICE / f"{j['audio']}.wav"), "-map", "0:v", "-map", "1:a", "-c:v", "libx264", "-crf", "14",
           "-preset", "slow", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-shortest", str(out))
        fr = frames(out)
        h, w = fr[0].shape[:2]
        idx = np.linspace(0, len(fr) - 1, 6).astype(int)
        cv2.imwrite(str(OUT / f"{j['name']}_sheet.jpg"), np.hstack([cv2.resize(fr[i], (w // 4, h // 4)) for i in idx]),
                    [cv2.IMWRITE_JPEG_QUALITY, 88])
        mouth_sheet(fr, [], OUT / f"{j['name']}_mouth.jpg")
        print(f"lipsync gpu {j['name']}: {len(fr)} frames → {out.relative_to(ROOT)}")
    (OUT / "gpu_run.json").write_text(json.dumps({**meta, "jobs": [j["name"] for j in jobs]}) + "\n")


if __name__ == "__main__":
    main()
