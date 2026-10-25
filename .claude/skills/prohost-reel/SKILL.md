---
name: prohost-reel
description: How to make a ProHost Instagram/TikTok reel with the AI presenter — same avatar, same office, the two Lebanese voices, voice 1 fitted onto his native mouth, the reel montage and overlay — from a written brief (Reel Studio). Use for any new reel, a change to a reel's script/voice/lip-sync/montage, or a Reel Studio request.
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
- **Voice 1 — on camera**: Gemini-TTS **`Orus`** (the owner's pick from 4 samples, Oct 2026) with the same Beirut
  `dialect` prompt as the narrator, used as is — no voice conversion (Seed-VC blurred the Lebanese consonants).
  **His mouth is never redrawn.** Veo films him SAYING the scene's line (with audio, 2 takes), so his own mouth, jaw,
  brows and nods articulate exactly those words; `scripts/marketing/fit_voice.py` throws Veo's audio away and stretches
  Orus syllable by syllable onto that mouth (DTW alignment + Rubber Band keyframe map, ±25 % inside words, pauses
  absorb the rest) → `dubbed/<role>.mp4`. This is why the first office reel (Veo's native speech) looked natural and the
  LatentSync v6 did not: a redrawn mouth on CPU was a damped, half-open mumble over a jaw still moving to Veo's own
  sentence (owner, Oct 2026: "not natural"). Library talking shots (`s1v`, `s5v`) can be reused only for the exact line
  Veo says in them (`SPEAKING` in `reel_studio.py`).
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

- 2–9 scenes; `role` = unique lowercase id. `voice: 1` needs a take where Veo says that line: a new shot (`frame` +
  `motion`; Veo speaks `line`, 2 takes with audio, about US$2.40), or `reuse` `s1v` / `s5v` only for the line those
  takes say. `voice: 2` uses a library take as is (`reuse`, free) or a new silent shot (US$0.80, lips closed).
- Library takes (reuse costs nothing): `s1v` desk, says the office hook · `s5v` standing, says the app CTA · `s2` couch/clock
  glance, stands up · `s3` espresso, sips · `s4` phone message, types · `s5c` espresso at the counter, sip, smile, toast,
  points down (lips closed). Notes in `reel_studio.py` `LIBRARY_NOTES`. At most 6 new shots per reel.
- `cam` beats `[time, zoom, push]` (time in s or `"w:<caption word>"`), `punch` on key words, `in` = `cut | whip_l |
  whip_r | zoom`, `jcut` ≈ 0.35 for narrator scenes (voice leads the picture), `ramp` `[[until, rate], …]` for silent
  slow-motion. `montage.py` does face-tracked framing, punches, whips/zoom-blur, grade, grain, vignette.
- Voice-1 scenes are `steady` (set automatically): the frame holds on his face for the whole line — one slow push at
  most, no punches, no reframing, light shake. Zooms and reframes mid-sentence fight the mouth (owner, Oct 2026). Put
  the energy in the cuts between lines and in the narrator scenes.
- Overlays per scene (`reel_studio.html`): `chip`, `comment` (Instagram comment bar typing the keyword + arrow),
  `stamp`/`stamp_sub`, `price` (card shows **$8.99/month** only). End card from `end`.
- **Offers**: only what `functions/src/billing/offerClaims.ts` states (monthly first month free, yearly 50 % off year
  one, $8.99/month). `prepare` rejects any other price or percentage.

## Running it

1. Write the spec (Reel Studio drafts it from a brief; check lines are natural **Beiruti** Arabic, English words in
   English, captions mirror the line). Commit it under `marketing/reels/<id>/spec.json`.
2. Dispatch **Marketing Reel Studio** (`spec`, `stage` = all): `prepare` (validate) · `footage` (first frames → Veo
   clips; voice-1 shots speak their line) · `voices` (voice 1 Orus + voice 2 Charon) · `voice1` (fit_voice → `dubbed/`)
   · `compose` (montage + overlay + music, −14 LUFS, ~12 Mbps) → `reel.mp4`, `cover.jpg`. One run, no lip-sync job.
3. Check the sync before anyone posts: dispatch **Marketing lip-sync check** with the `dubbed/` clips (SyncNet: offset
   in frames, 0 = in sync; confidence, native talking video ≈ 6–9). Look at a mouth strip too (mouth crop per frame with
   the voice level under it): jaw drops and lip rounding must land on the loud syllables.
4. Review: a frame sheet of `reel.mp4`, captions in the safe zone, the frame still while he talks.

## Lip-sync: what works and what didn't

- Works: Veo says the line + `fit_voice.py` (his native mouth, the voice bent onto it). Free, seconds per line.
- Rejected by the owner: Wav2Lip (soft mouth), LatentSync 1.6 on CPU (damped, mumbling mouth; the head kept Veo's
  rhythm), dubbing TTS over Veo's mouth with phrase-level shifts only (out of sync — fit_voice aligns per syllable),
  Veo's own Arabic audio (not Lebanese), voice conversion (Seed-VC blurred the consonants).
- Paid / GPU mouth redraws (`gpu_lipsync.py`: fal Sync lipsync-2-pro, Vertex) stay available but aren't the default;
  the project has 0 Vertex GPU quota.

## Gotchas learned the hard way

- `workflow_dispatch` pins the commit at dispatch time: push first, then dispatch. New workflow files must exist on
  `main` before they can be dispatched.
- Parallel marketing runs push to the same branch: every workflow commits with a pull-rebase retry loop.
- Veo with `takes > 1` writes `<shot>_a/_b.mp4`; check the last frames (takes sometimes glitch in the final 0.5 s).
- Caption words light up by character share of the spoken window; anchors `"w:<word>"` use the same rule, so cuts
  land on the highlighted word.
