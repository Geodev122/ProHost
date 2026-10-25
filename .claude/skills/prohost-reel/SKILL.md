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
- **Voice 1 — on camera, lip-synced**: Gemini-TTS `Orus` with the Beirut dialect prompt, then **converted to the
  hook voice** (Veo's own speech in `marketing/avatar/clips/s1.mp4`, 0.1–5.4 s) with Seed-VC (`scripts/marketing/voice1.py`).
  Only voice-1 scenes show his mouth moving.
- **Voice 2 — the narrator**: Gemini-TTS `Charon`, same Beirut dialect prompt (`scripts/marketing/voiceover_reel.json`
  `dialect`). Plays only over footage where his lips are closed.
- Accent gate: Gemini (`avatar_video.py` `listen()`) must hear **Lebanese ≥ 4/5 and the scripted words**; Veo's own
  Arabic speech is NOT reliable for accent (it drifted to Syrian/Jordanian) — that is why voice 1 is TTS + conversion.

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

- 2–9 scenes; `role` = unique lowercase id. `voice: 1` needs a **speaking take** (`reuse` `s1` / `s5_a`, or a new shot,
  which `prepare` tells to talk); `voice: 2` needs a silent take (new shots are told to keep the lips closed).
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
2. Dispatch **Marketing Reel Studio** (`spec`, `stage` = all, `lipsync_backend`). Stages, each re-runnable alone:
   `footage` (Gemini keyframes → Veo clips) · `voices` (both TTS voices) · `voice1` (Seed-VC to the hook voice) ·
   `lipsync` (LatentSync 1.6, 40 steps) · `compose` (montage + overlay + music, −14 LUFS) → `reel.mp4`, `cover.jpg`.
3. Review before anyone posts: `lipsync/<role>_mouth.jpg` (mouth close-ups), a frame sheet of `reel.mp4`, the accent
   verdicts in the log. Never ship a soft or out-of-sync mouth — redo `lipsync` or change the take.

## Lip-sync: where it can run

- `vertex`: LatentSync on a Vertex AI custom job (L4 → T4 → A100). **The project has 0 GPU training quota by default**
  (429 on every GPU) — the owner must request quota (Console › IAM & Admin › Quotas: "Custom model training Nvidia L4
  GPUs per region", us-central1, 1) before this works.
- `hf`: the same model on a public Hugging Face Space (ZeroGPU) via `gradio_client` — free, queue-dependent; an
  `HF_TOKEN` secret raises the daily GPU allowance.
- Never on the free GitHub runners (2 CPUs / 7 GB: out of memory at 512 px) and never Wav2Lip (the owner rejected it).

## Gotchas learned the hard way

- `workflow_dispatch` pins the commit at dispatch time: push first, then dispatch. New workflow files must exist on
  `main` before they can be dispatched.
- Parallel marketing runs push to the same branch: every workflow commits with a pull-rebase retry loop.
- Veo with `takes > 1` writes `<shot>_a/_b.mp4`; check the last frames (takes sometimes glitch in the final 0.5 s).
- Caption words light up by character share of the spoken window; anchors `"w:<word>"` use the same rule, so cuts
  land on the highlighted word.
