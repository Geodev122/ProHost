"""Voice 1 of the reels: the on-camera avatar always sounds like the hook voice (marketing/avatar/reference/hook_voice.wav,
Veo's own Lebanese speech in the first hook take), whatever produced the words.

Runs in CI (.github/workflows/marketing-voice1.yml). Seed-VC (open-source zero-shot voice conversion, CPU) changes only
the timbre; timing and pronunciation stay, so:
  - a Gemini-TTS line (reel_v1 in voiceover_reel.json) keeps its controlled Lebanese accent (then needs lip-sync), or
  - a Veo take where he speaks the line himself ("clip:<path>") keeps its native lip-sync — the mouth is already right,
    only the voice changes (V1_MUX writes the take with voice 1). This is the path that needs no GPU.
avatar_video.py stage voice1 checks the results with Gemini (words, Lebanese accent).

usage: voice1.py <seed-vc repo dir> [diffusion steps]
"""
import os, pathlib, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
# V1_SRC / V1_OUT / V1_JOBS ("out=src,..."): another reel's lines (Reel Studio); the reference voice never changes.
SRC = ROOT / os.environ.get("V1_SRC", "marketing/audio/voiceovers-arabic-reel/lines")
OUT = ROOT / os.environ.get("V1_OUT", "marketing/audio/voice1")
# The hook as Veo first spoke it (Lebanese 5/5), kept as a fixed reference so new takes never change voice 1.
REF_VOICE = ROOT / "marketing/avatar/reference/hook_voice.wav"
# A source "clip:<path>" is a Veo take in which he speaks the line himself (native lip-sync): its voice is converted
# with the timing untouched, so the mouth stays in sync; V1_MUX=<dir> then writes <dir>/<out>.mp4 (the take + voice 1).
JOBS = dict(j.split("=", 1) for j in os.environ.get("V1_JOBS", "line_1=reel_v1_01,line_5=reel_v1_02").split(","))
MUX = os.environ.get("V1_MUX")
# V1_F0=True: Seed-VC's 44 kHz model (BigVGAN, pitch-conditioned, pitch moved to the reference) — clearer consonants
# than the 22 kHz speech model when a take's words come out blurred.
F0 = os.environ.get("V1_F0", "False")


def ff(*args):
    subprocess.run(["ffmpeg", "-nostdin", "-y", "-loglevel", "error", *args], check=True)


def main():
    repo, steps = pathlib.Path(sys.argv[1]), int(sys.argv[2]) if len(sys.argv) > 2 else 50
    tmp = pathlib.Path(tempfile.mkdtemp(prefix="voice1_"))
    ref = tmp / "hook_voice.wav"
    ff("-i", str(REF_VOICE), "-ac", "1", "-ar", "44100", "-af", "highpass=f=70,afftdn=nf=-30", str(ref))
    OUT.mkdir(parents=True, exist_ok=True)
    for name, src in JOBS.items():
        out_dir = tmp / name
        if src.startswith("clip:"):
            source = tmp / f"{name}_take.wav"
            ff("-i", str(ROOT / src[5:]), "-vn", "-ac", "1", "-ar", "44100", str(source))
        else:
            source = SRC / f"{src}.wav"
        subprocess.run([sys.executable, "inference.py", "--source", str(source), "--target", str(ref),
                        "--output", str(out_dir), "--diffusion-steps", str(steps), "--length-adjust", "1.0",
                        "--inference-cfg-rate", os.environ.get("V1_CFG", "0.7"), "--f0-condition", F0,
                        "--auto-f0-adjust", F0, "--fp16", "False"], cwd=repo, check=True)
        wav = next(out_dir.glob("vc_*.wav"))
        ff("-i", str(wav), "-ac", "1", "-ar", "48000", "-af", "loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000",
           str(OUT / f"{name}.wav"))
        print(f"voice1 {name}: {src} → {(OUT / f'{name}.wav').relative_to(ROOT)} ({steps} diffusion steps)")
        if MUX and src.startswith("clip:"):
            (ROOT / MUX).mkdir(parents=True, exist_ok=True)
            ff("-i", str(ROOT / src[5:]), "-i", str(OUT / f"{name}.wav"), "-map", "0:v", "-map", "1:a", "-c:v", "copy",
               "-c:a", "aac", "-b:a", "192k", "-shortest", str(ROOT / MUX / f"{name}.mp4"))
            print(f"voice1 {name}: take + voice 1 → {MUX}/{name}.mp4 (lip-sync from the take itself)")


if __name__ == "__main__":
    main()
