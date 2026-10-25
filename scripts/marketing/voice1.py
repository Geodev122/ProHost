"""Voice 1 of the office reel: the on-camera avatar's lines (1 and 5) in the hook voice, with a controlled Lebanese accent.

Runs in CI (.github/workflows/marketing-voice1.yml). The words and the accent come from Gemini-TTS (post reel_v1 in
voiceover_reel.json: lines/reel_v1_01.wav, reel_v1_02.wav); Seed-VC (open-source zero-shot voice conversion, CPU) then
gives them the timbre of the avatar's hook voice, i.e. Veo's own speech in marketing/avatar/clips/s1.mp4. Conversion keeps
the TTS timing and pronunciation, so the accent survives. → marketing/audio/voice1/line_1.wav, line_5.wav (48 kHz mono).
The lip-sync (lipsync.py) is driven by these files; avatar_video.py stage voice1 checks them with Gemini.

usage: voice1.py <seed-vc repo dir> [diffusion steps]
"""
import pathlib, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "marketing/audio/voiceovers-arabic-reel/lines"
OUT = ROOT / "marketing/audio/voice1"
REF_CLIP, REF_FROM, REF_TO = ROOT / "marketing/avatar/clips/s1.mp4", 0.1, 5.4  # the hook as Veo spoke it (Lebanese 5/5)
JOBS = {"line_1": "reel_v1_01", "line_5": "reel_v1_02"}


def ff(*args):
    subprocess.run(["ffmpeg", "-nostdin", "-y", "-loglevel", "error", *args], check=True)


def main():
    repo, steps = pathlib.Path(sys.argv[1]), int(sys.argv[2]) if len(sys.argv) > 2 else 50
    tmp = pathlib.Path(tempfile.mkdtemp(prefix="voice1_"))
    ref = tmp / "hook_voice.wav"
    # The reference: the hook's speech only, mono, gently cleaned of room tone (no loudness games: timbre is what counts).
    ff("-ss", f"{REF_FROM}", "-to", f"{REF_TO}", "-i", str(REF_CLIP), "-vn", "-ac", "1", "-ar", "44100",
       "-af", "highpass=f=70,afftdn=nf=-30", str(ref))
    OUT.mkdir(parents=True, exist_ok=True)
    for name, src in JOBS.items():
        out_dir = tmp / name
        subprocess.run([sys.executable, "inference.py", "--source", str(SRC / f"{src}.wav"), "--target", str(ref),
                        "--output", str(out_dir), "--diffusion-steps", str(steps), "--length-adjust", "1.0",
                        "--inference-cfg-rate", "0.7", "--f0-condition", "False", "--fp16", "False"], cwd=repo, check=True)
        wav = next(out_dir.glob("vc_*.wav"))
        ff("-i", str(wav), "-ac", "1", "-ar", "48000", "-af", "loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000",
           str(OUT / f"{name}.wav"))
        print(f"voice1 {name}: {src} → {(OUT / f'{name}.wav').relative_to(ROOT)} ({steps} diffusion steps)")


if __name__ == "__main__":
    main()
