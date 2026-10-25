---
name: prohost-reel
description: How to make a ProHost Instagram/TikTok reel with the AI presenter — same avatar, same office, the two Lebanese voices, GPU lip-sync, the reel montage and overlay — from a written brief (Reel Studio). Use for any new reel, a change to a reel's script/voice/lip-sync/montage, or a Reel Studio request.
---

# ProHost reels (Reel Studio)

A reel is one **spec** (`marketing/reels/<id>/spec.json`) run through one chain of tools. The office reel
(`marketing/02-reels-arabic-9x16/12-office-sourcing-avatar.mp4`) was built with the same tools by hand; a new
brief goes through `scripts/marketing/reel_studio.py` + `.github/workflows/marketing-reel-studio.yml`.

Precedence: the owner's words → `CLAUDE.md` (offers, prices, no countries on the website…) → this skill.

## The cast (never change without the owner)

- **Avatar**: the man in `marketing/avatar/reference/` (character sheet), the office in
  `marketing/avatar/keyframes/s1.png`; identity text in `scripts/marketing/avatar_shots.json` (`person`, `room`,
  `look`, `negative`). New shots always get these references + the office keyframe.
- **Voice 1 — on camera**: Veo films him **saying the line himself** (native, perfect lip-sync; 2 takes); Gemini picks
  the take with the scripted words and the most Lebanese accent; Seed-VC then converts that take's speech to the
  **hook voice** (`marketing/avatar/reference/hook_voice.wav`, Veo's first Lebanese hook take) with the timing
  untouched, so mouth and voice stay in sync (`scripts/marketing/voice1.py`, `clip:` sources + `V1_MUX`). Only
  voice-1 scenes show his mouth moving. No GPU is needed.
- **Voice 2 — the narrator**: Gemini-TTS `Charon`, same Beirut dialect prompt (`scripts/marketing/voiceover_reel.json`
  `dialect`). Plays only over footage where his lips are closed.
- Accent gate: Gemini (`avatar_video.py` `listen()`) must hear **Lebanese ≥ 4/5 and the scripted words**. Veo's
  Arabic sometimes drifts to Syrian/Jordanian: the speaking shots carry Beirut pronunciation cues (`avatar_shots.json`
  `accent` + the shot's `voice`) and two takes; if no take reaches 4/5, film more takes (stage footage) before composing.

## The spec

```json
{ "id": "free-month-owners", "title": "…", "brief": "the owner's words",
  "scenes": [
    { "role": "hook", "voice": 1, "line": "Lebanese Arabic, as spoken", "style": "acting direction for the voice",
      "cap": "caption with <b>key words</b>", "shot": { "reuse": "s1", "from": 0.1 },
      "cam": [[0, 1.38, 0.05], ["w:وبتضل", 1.12, 0.05]], "punch": ["w:فاضية"], "in": "cut" },
    { "role": "pain", "voice": 2, "line": "…", "cap": "…", "in": "zoom", "jcut": 0.35,
      "shot": { "frame": "Medium shot. …", "motion": "He … (what happens, camera move)" }, "chip": "optional pill text" },
    { "role": "cta", "voice": 2, "line": "…", "cap": "…", "shot": { "reuse": "s5b", "from": 0.2 },
      "comment": "office", "stamp": "شهر مجّاني", "stamp_sub": "علّق office أو ابعتلنا DM" },
    { "role": "price", "voice": 2, "line": "…", "cap": "…", "shot": { "reuse": "s4", "from": 4.2 }, "tail": 1.5,
      "price": { "label": "بعد الشهر الأوّل", "note": "أقلّ من أجار ساعة وحدة" } } ],
  "end": { "title": "أوّل شهر <b>مجّاني</b>", "sub": "…", "url": "pro-host.tech/…" } }
```

- 2–9 scenes; `role` = unique lowercase id. `voice: 1` scenes are always filmed new (he says that exact line):
  `shot.reuse` = a library shot whose first frame and action to start from (`s1` desk, `s5_a` standing) — no new image
  — or `frame` + `motion` for a new one (US$2.40: 2 takes × 8 s with speech). `voice: 2` scenes use a library take as
  is (`reuse`, free) or a new silent shot (US$0.80; told to keep the lips closed).
- Library takes (reuse costs nothing): `s1` desk, talks · `s2` couch/clock glance, stands up · `s3` espresso, sips ·
  `s4` phone message, types · `s5_a` standing, talks with open palms · `s5b` standing, smiles, points down (silent).
  Notes in `reel_studio.py` `LIBRARY_NOTES`. At most 6 new shots per reel (~US$0.80 each, Veo fast silent 8 s).
- `cam` beats `[time, zoom, push]` (time in s or `"w:<caption word>"`), `punch` on key words, `in` = `cut | whip_l |
  whip_r | zoom`, `jcut` ≈ 0.35 for narrator scenes (voice leads the picture), `ramp` `[[until, rate], …]` for silent
  slow-motion. `montage.py` does face-tracked framing, punches, whips/zoom-blur, grade, grain, vignette.
- Overlays per scene (`reel_studio.html`): `chip`, `comment` (Instagram comment bar typing the keyword + arrow),
  `stamp`/`stamp_sub`, `price` (card shows **$8.99/month** only). End card from `end`.
- **Offers**: only what `functions/src/billing/offerClaims.ts` states (monthly first month free, yearly 50 % off year
  one, $8.99/month). `prepare` rejects any other price or percentage.

## Running it

1. Write the spec (Reel Studio drafts it from a brief; check lines are natural **Beiruti** Arabic, English words in
   English, captions mirror the line). Commit it under `marketing/reels/<id>/spec.json`.
2. Dispatch **Marketing Reel Studio** (`spec`, `stage` = all). Stages, each re-runnable alone: `prepare` (validate) ·
   `footage` (first frames → Veo clips) · `check` (Gemini picks the voice-1 takes) · `voices` (the narrator) · `voice1`
   (each take's speech → the hook voice, muxed into `lipsync/<role>.mp4`) · `compose` (montage + overlay + music,
   −14 LUFS) → `reel.mp4`, `cover.jpg`.
   - Picking a take: `check` ranks words-match first, then Lebanese. When the only 5/5 Lebanese take has a slip (a
     repeated word, a long pause), keep it and jump-cut the slip out at a silence with ffmpeg (trim+concat, 20 ms audio
     crossfade), then add a framing change (`cam` beat) at the cut so it reads as an ad edit; record the speech window
     in a `runs/*_check_edit.json` (the office reel's s1v / s5v are the example).
3. Review before anyone posts: `lipsync/<role>_mouth.jpg` (mouth close-ups), a frame sheet of `reel.mp4`, the accent
   verdicts in the log. Never ship a soft or out-of-sync mouth — redo `lipsync` or change the take.

## Lip-sync: why native takes

- Wav2Lip (rejected by the owner: soft mouth), LatentSync on the free GitHub runners (2 CPUs / 7 GB: out of memory at
  512 px), Vertex AI GPU jobs (the project has **0 GPU training quota** — 429 on L4/T4/A100) and the public LatentSync
  Space (anonymous jobs capped below what a 5 s line needs) all failed. `scripts/marketing/gpu_lipsync.py` keeps the
  Vertex and Hugging Face paths for the day a GPU quota or an `HF_TOKEN` secret exists.
- Native takes + voice conversion needs neither, and the mouth is Veo's own: always in sync.

## Gotchas learned the hard way

- `workflow_dispatch` pins the commit at dispatch time: push first, then dispatch. New workflow files must exist on
  `main` before they can be dispatched.
- Parallel marketing runs push to the same branch: every workflow commits with a pull-rebase retry loop.
- Veo with `takes > 1` writes `<shot>_a/_b.mp4`; check the last frames (takes sometimes glitch in the final 0.5 s).
- Caption words light up by character share of the spoken window; anchors `"w:<word>"` use the same rule, so cuts
  land on the highlighted word.
