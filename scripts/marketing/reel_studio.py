"""Reel Studio: a brief-driven Instagram reel with the ProHost avatar, its two voices and the reel montage.

One spec (marketing/reels/<id>/spec.json, usually drafted from a brief in the Reel Studio artifact) → one reel, through the
same tools the office reel used (.claude/skills/prohost-reel/SKILL.md documents the whole chain):

  prepare   validates the spec; writes shots.json (new avatar shots → avatar_video.py) and voice.json (both voices →
            voiceover.py) into the reel's folder; prints the plan and the Veo cost estimate
  footage   avatar_video.py: first frames (a library keyframe, or Gemini image for a new frame) + Veo clips. A voice-1
            scene is filmed with Veo SAYING the scene's line (2 takes, with audio): its mouth then articulates exactly
            those words, naturally — the first office reel's lip-sync. Narrator scenes are silent, lips closed
  voices    voiceover.py: voice 1 = Gemini-TTS Orus (the owner's pick), voice 2 = Charon (narrator), both with the Beirut
            dialect prompt that the owner approved — Veo's own Arabic is never heard (not Lebanese)
  voice1    fit_voice.py: voice 1 fitted syllable by syllable onto the best take's own mouth (Veo's audio thrown away)
            → dubbed/<role>.mp4 (voice1/<role>.wav keeps the line)
  compose   reel_v3_compose.py with REEL_SPEC: montage.py + reel_studio.html overlay + music/sfx → reel.mp4 + cover.jpg
            (the frame holds still while he talks: no punches or reframing mid-line)

usage: reel_studio.py <stage> marketing/reels/<id>/spec.json
"""
import json, os, pathlib, re, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).parent
MAX_NEW_SHOTS, PRICE = 6, {False: 0.10, True: 0.15}  # Veo fast, US$/s silent / with speech
TALK_TAKES = 2  # Veo takes per voice-1 shot; fit_voice keeps the one whose rhythm fits the voice best
# Library shots where Veo SAYS a line (takes <shot>_a…_d): a voice-1 scene may reuse one only for that same line, since
# fit_voice keeps his native mouth and only bends the voice onto it. Any other line needs a new talking shot.
SPEAKING = {
    "s1v": "بدفع أجار عيادتي كامل… وبتضل فاضية نُصّ النهار.",
    "s5v": "إذا بدّك مكتب، نزّل التطبيق، تفتّش وتحجز كِلّو مجّاني.",
}
V1_VOICE, V2_VOICE = "Orus", "Charon"  # the owner's picks (Oct 2026): on-camera voice, narrator
LIBRARY = ROOT / "marketing/avatar/clips"
# What the shared takes show, so a spec can reuse one instead of paying for a new shot.
LIBRARY_NOTES = {
    "s1v": "seated at the desk, says the office hook to camera, a little tired (voice 1 only with that exact line)",
    "s5v": "standing, confident, says the 'download the app' CTA with open palms (voice 1 only with that exact line)",
    "s5c": "with his espresso at the counter, sips, smiles, small toast, points down (silent, lips closed)",
    "s1": "seated at the desk, looks up and talks to camera, slightly tired (talks: never under the narrator)",
    "s2": "glances at the empty couch and the wall clock, exhales, stands up (silent)",
    "s3": "makes an espresso, sips, small satisfied smile (silent)",
    "s4": "reads a message on his phone, smiles, types a reply (silent)",
    "s5_a": "standing, confident, talks to camera with open-palm gestures (talks: never under the narrator)",
    "s5b": "standing, smiles, points down to the comments, 'your turn' gesture (silent)",
}
ROLE_RE = re.compile(r"^[a-z][a-z0-9_]{0,23}$")
TRANSITIONS = {"cut", "whip_l", "whip_r", "zoom"}


def words(text):
    """A line reduced to its letters, so punctuation, diacritics and spacing don't count."""
    return re.sub(r"[^\w]|[\u064B-\u0652_]", "", text or "")


def group(reuse):
    """s1v_d → s1v: a shot id without its take letter."""
    return re.sub(r"_[a-z]$", "", reuse)


def takes_of(folder, shot):
    """The takes of a shot in a clips folder: <shot>_a.mp4, <shot>_b.mp4… (or <shot>.mp4 alone)."""
    found = sorted(folder.glob(f"{shot}_[a-z].mp4"))
    return found or [p for p in [folder / f"{shot}.mp4"] if p.exists()]


def load(spec_path):
    p = ROOT / spec_path
    return p, json.loads(p.read_text(encoding="utf-8"))


def validate(spec):
    errs = []
    sc = spec.get("scenes") or []
    if not 2 <= len(sc) <= 9:
        errs.append("2 to 9 scenes")
    roles = [s.get("role", "") for s in sc]
    if len(set(roles)) != len(roles) or not all(ROLE_RE.match(r) for r in roles):
        errs.append("roles must be unique lowercase ids (a-z, 0-9, _)")
    new = 0
    for s in sc:
        r = s.get("role")
        if s.get("voice") not in (1, 2):
            errs.append(f"{r}: voice must be 1 (on camera, lip-synced) or 2 (narrator)")
        if not (s.get("line") or "").strip() or not (s.get("cap") or "").strip():
            errs.append(f"{r}: line and cap are required")
        shot = s.get("shot") or {}
        if shot.get("reuse"):
            if not (LIBRARY / f"{shot['reuse']}.mp4").exists():
                errs.append(f"{r}: unknown library shot {shot['reuse']}")
            g = group(shot["reuse"])
            if s.get("voice") == 1 and words(SPEAKING.get(g)) != words(s.get("line")):
                errs.append(f"{r}: voice 1 can reuse a talking shot only for the line Veo says in it "
                            f"({'; '.join(f'{k}: {v}' for k, v in SPEAKING.items())}); give this line a new shot")
            if s.get("voice") == 2 and g in SPEAKING:
                errs.append(f"{r}: {g} shows him talking; the narrator needs footage with his lips closed")
        elif not (shot.get("frame") and shot.get("motion")):
            errs.append(f"{r}: a new shot needs frame and motion")
        else:
            new += 1
        if s.get("in", "cut") not in TRANSITIONS:
            errs.append(f"{r}: in must be one of {sorted(TRANSITIONS)}")
        # Prices only as offerClaims.ts states them: the overlay's card shows $8.99/month; no other amounts in words.
        for txt in (s.get("line", ""), s.get("cap", ""), s.get("chip", ""), s.get("stamp", "")):
            for m in re.findall(r"\$\s?\d+(?:\.\d+)?|\d+(?:\.\d+)?\s?\$|\d+\s?%", txt or ""):
                if m.replace(" ", "") not in ("$8.99", "8.99$"):
                    errs.append(f"{r}: price/discount {m!r} is not an offer ProHost runs (offerClaims.ts)")
    if new > MAX_NEW_SHOTS:
        errs.append(f"at most {MAX_NEW_SHOTS} new shots per reel")
    return errs, new


def prepare(spec_path):
    p, spec = load(spec_path)
    errs, new = validate(spec)
    if errs:
        sys.exit("::error::spec: " + "; ".join(errs))
    d = p.parent
    rel = d.relative_to(ROOT)
    base = json.loads((HERE / "avatar_shots.json").read_text(encoding="utf-8"))
    shots, cost = [], 0.0
    for s in spec["scenes"]:
        shot = s.get("shot") or {}
        if shot.get("reuse"):
            continue  # a library take, used as it is (a talking one gets voice 1 fitted onto its mouth)
        talking = s["voice"] == 1
        entry = {"id": s["role"], "refs": shot.get("refs") or
                 (["close_neutral", "close_smile", "full_front"] if talking else ["three_quarter", "desk_mid", "close_smile"]),
                 "frame": shot["frame"], "audio": talking, "takes": TALK_TAKES if talking else 1}
        if talking:  # Veo says the line itself, so the mouth articulates these words; its audio is replaced by voice 1
            entry.update({"motion": shot["motion"] + (" He says the whole line straight to the camera, as if to one friend:"
                                                      " natural blinking and small head movements, and he finishes the"
                                                      " sentence before the clip ends. Static camera, slow subtle push-in."),
                          "speech": s["line"],
                          "voice": "a warm, deep male voice with a Lebanese (Beirut) accent, relaxed and conversational"})
        else:
            entry.update({"motion": shot["motion"] + " His lips stay closed, he is not talking; a narrator speaks over this shot.",
                          "silent": True})
        cost += 8 * PRICE[talking] * entry["takes"]
        shots.append(entry)
    (d / "shots.json").write_text(json.dumps({**{k: v for k, v in base.items() if k != "shots"}, "shots": shots},
                                             ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    vo = json.loads((HERE / "voiceover_reel.json").read_text(encoding="utf-8"))
    posts = []
    for vid, voice, n in (("v1", V1_VOICE, 1), ("v2", V2_VOICE, 2)):
        lines = [s for s in spec["scenes"] if s["voice"] == n]
        if lines:
            posts.append({"id": vid, "voice": voice, "gender": "male", "length": 9.0 * len(lines) + 1,
                          "persona": vo["posts"][0]["persona"] + (" Speaking ON CAMERA to the viewer." if n == 1 else ""),
                          "lines": [{"at": 9.0 * i + 0.2, "end": 9.0 * i + 8.8, "style": s.get("style", ""), "text": s["line"]}
                                    for i, s in enumerate(lines)]})
    (d / "voice.json").write_text(json.dumps({"_about": f"Reel Studio voices for {spec.get('title', d.name)}",
                                              "dialect": vo["dialect"], "length": 9.0 * len(spec["scenes"]) + 1,
                                              "output": str(rel / "audio"), "posts": posts}, ensure_ascii=False, indent=2) + "\n",
                                  encoding="utf-8")
    print(f"studio prepare {d.name}: {len(spec['scenes'])} scenes, {len(shots)} shot(s) to film (~US${cost:.2f} of Veo), "
          f"{sum(s['voice'] == 1 for s in spec['scenes'])} on camera (voice 1 fitted to his mouth), "
          f"{sum(s['voice'] == 2 for s in spec['scenes'])} narrated")
    return d, spec


def run(cmd, env=None):
    print("+", " ".join(cmd), flush=True)
    subprocess.run(cmd, cwd=ROOT, check=True, env={**os.environ, **(env or {})})


def main():
    stage, spec_path = sys.argv[1], sys.argv[2]
    d, spec = prepare(spec_path)
    rel = str(d.relative_to(ROOT))
    v1 = [s for s in spec["scenes"] if s["voice"] == 1]
    if stage in ("footage", "all") and (json.loads((d / "shots.json").read_text())["shots"]):
        env = {"AVATAR_SHOTS": str(d / "shots.json"), "AVATAR_OUT": rel}
        todo = [x["id"] for x in json.loads((d / "shots.json").read_text())["shots"] if not (d / "keyframes" / f"{x['id']}.png").exists()]
        if todo:
            run([sys.executable, "scripts/marketing/avatar_video.py", "keyframes", ",".join(todo)], env)
        run([sys.executable, "scripts/marketing/avatar_video.py", "clips", ""], env)
    if stage in ("voices", "all"):
        run([sys.executable, "scripts/marketing/voiceover.py", "/tmp/voiceover"], {"VO_CONFIG": str(d / "voice.json")})
    if stage in ("voice1", "voices", "all") and v1:
        (d / "voice1").mkdir(parents=True, exist_ok=True)
        for i, s in enumerate(v1, 1):
            src = d / "audio" / "lines" / f"v1_{i:02d}.wav"
            if not src.exists():
                sys.exit(f"::error::{s['role']}: voice 1 line {src.name} is missing (run stage voices)")
            (d / "voice1" / f"{s['role']}.wav").write_bytes(src.read_bytes())
            reuse = (s.get("shot") or {}).get("reuse")
            takes = takes_of(LIBRARY, group(reuse)) if reuse else takes_of(d / "clips", s["role"])
            if not takes:
                sys.exit(f"::error::{s['role']}: no talking take to fit voice 1 onto (run stage footage)")
            run([sys.executable, "scripts/marketing/fit_voice.py", str(d / "voice1" / f"{s['role']}.wav"), s["role"],
                 ",".join(str(t) for t in takes)], {"FIT_OUT": str(pathlib.Path(rel) / "dubbed")})
    if stage in ("compose", "all"):
        run([sys.executable, "marketing/source/reel_v3_compose.py"], {"REEL_SPEC": str(d / "spec.json")})


if __name__ == "__main__":
    main()
