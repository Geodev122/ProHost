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
- **Voice 1 — on camera, lip-synced**: Gemini-TTS **`Orus`** (the owner's pick from 4 samples, Oct 2026) with the same
  Beirut `dialect` prompt as the narrator, used as is — no voice conversion (Seed-VC blurred the Lebanese consonants).
  His mouth is redrawn on a take where he talks to camera (`s1v_d` desk, `s5v_d` standing, or a new talking shot) by
  LatentSync 1.6 (`scripts/marketing/lipsync_cpu.py`, free) or `gpu_lipsync.py` (fal / Vertex, paid). Only voice-1 scenes
  show his mouth moving.
- **Voice 2 — the narrator**: Gemini-TTS `Charon`, same Beirut dialect prompt (`scripts/marketing/voiceover_reel.json`
  `dialect`). Plays only over footage where his lips stay closed (check the frames: smiles are fine, speech shapes are not).
- **Never use Veo's own Arabic speech for dialogue**: the owner hears it as not Lebanese at all, even when Gemini's
  dialect check scored it 5/5. Gemini accent scores are advisory; the owner's ear decides.

## The spec

```json
{ "id": "free-month-owners", "title": "…", "brief": "the owner's words",
  "scenes": [
    { "role": "hook", "voice": 1, "line": "Lebanese Arabic, as spoken", "style": "acting direction for the voice",
      "cap": "caption with <b>key words</b>", "shot": { "reuse": "s1v_d" },
      "cam": [[0, 1.38, 0.05], ["w:وبتضل", 1.12, 0.05]], "punch": ["w:فاضية"], "in": "cut" },
    { "role": "pain", "voice": 2, "line": "…", "cap": "…", "in": "zoom", "jcut": 0.35,
      "shot": { "frame": "Medium shot. …", "motion": "He … (what happens, camera move)" }, "chip": "optional pill text" },
    { "role": "cta", "voice": 2, "line": "…", "cap": "…", "shot": { "reuse": "s5c", "from": 1.0 },
      "comment": "office", "stamp": "شهر مجّاني", "stamp_sub": "علّق office أو ابعتلنا DM" },
    { "role": "price", "voice": 2, "line": "…", "cap": "…", "shot": { "reuse": "s4", "from": 4.2 }, "tail": 1.5,
      "price": { "label": "بعد الشهر الأوّل", "note": "أقلّ من أجار ساعة وحدة" } } ],
  "end": { "title": "أوّل شهر <b>مجّاني</b>", "sub": "…", "url": "pro-host.tech/…" } }
```

- 2–9 scenes; `role` = unique lowercase id. `voice: 1` needs a take where he talks: `reuse` `s1v_d` (desk) / `s5v_d`
  (standing), or `frame` + `motion` for a new talking shot (US$0.80, silent Veo — the lip-sync adds the words).
  `voice: 2` uses a library take as is (`reuse`, free) or a new silent shot (US$0.80, told to keep the lips closed).
- Library takes (reuse costs nothing): `s1v_d` desk, talks · `s5v_d` standing, talks, open palms · `s2` couch/clock
  glance, stands up · `s3` espresso, sips · `s4` phone message, types · `s5c` espresso at the counter, sip, smile, toast,
  points down (lips closed). Notes in `reel_studio.py` `LIBRARY_NOTES`. At most 6 new shots per reel.
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
2. Dispatch **Marketing Reel Studio** (`spec`, `stage` = all): `prepare` (validate) · `footage` (first frames → silent
   Veo clips) · `voices` (voice 1 Orus + voice 2 Charon) · `voice1` (lines → `voice1/<role>.wav`). Its log ends with the
   lip-sync jobs to run.
3. Dispatch **Marketing lip-sync CPU** (free) with those `jobs` and `reel` = the reel folder: LatentSync 1.6 at 512 px,
   each line split into ~1–2 s chunks at its pauses, one runner per chunk (24 GB swap; about 1–2 h) →
   `lipsync/<role>.mp4` + `_mouth.jpg`. (Paid, minutes: **Marketing lip-sync**, backend `fal` with a `FAL_KEY` secret.)
4. Dispatch **Marketing Reel Studio** again with `stage` = compose (montage + overlay + music, −14 LUFS, ~12 Mbps) →
   `reel.mp4`, `cover.jpg`. A voice-1 scene whose lip-sync is missing falls back to the take + voice, unsynced, with a
   warning — never ship that.
5. Review before anyone posts: `lipsync/<role>_mouth.jpg` (mouth close-ups), a frame sheet of `reel.mp4`, the accent
   verdicts in the log. Never ship a soft or out-of-sync mouth — redo `lipsync` or change the take.

## Lip-sync: what works and what didn't

- Free: LatentSync 1.6 on the GitHub runners only with 24 GB swap + VAE slicing + chunks in parallel (without them the
  7 GB runners died). Paid: fal.ai (Sync lipsync-2-pro, `FAL_KEY`). Vertex AI GPU jobs need GPU training quota (the
  project has 0 — 429 on L4/T4/A100); the public LatentSync Space caps anonymous jobs below a 5 s line.
- Rejected by the owner: Wav2Lip (soft mouth), dubbing TTS over Veo's speaking mouth (not in sync), Veo's own Arabic
  (not Lebanese).

## Gotchas learned the hard way

- `workflow_dispatch` pins the commit at dispatch time: push first, then dispatch. New workflow files must exist on
  `main` before they can be dispatched.
- Parallel marketing runs push to the same branch: every workflow commits with a pull-rebase retry loop.
- Veo with `takes > 1` writes `<shot>_a/_b.mp4`; check the last frames (takes sometimes glitch in the final 0.5 s).
- Caption words light up by character share of the spoken window; anchors `"w:<word>"` use the same rule, so cuts
  land on the highlighted word.
