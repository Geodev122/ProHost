"""Reel Studio: a brief-driven Instagram reel with the ProHost avatar, its two voices and the reel montage.

One spec (marketing/reels/<id>/spec.json, usually drafted from a brief in the Reel Studio artifact) → one reel, through the
same tools the office reel used (.claude/skills/prohost-reel/SKILL.md documents the whole chain):

  prepare   validates the spec; writes shots.json (new avatar shots → avatar_video.py) and voice.json (both voices →
            voiceover.py) into the reel's folder; prints the plan and the Veo cost estimate
  footage   avatar_video.py: first frames (a library keyframe, or Gemini image for a new frame) + silent Veo clips. A
            voice-1 scene is filmed with him talking to camera (mouth moving, no sound); the lip-sync redraws the mouth
  voices    voiceover.py: voice 1 = Gemini-TTS Orus (the owner's pick), voice 2 = Charon (narrator), both with the Beirut
            dialect prompt that the owner approved — never Veo's own Arabic speech (not Lebanese)
  voice1    copies each voice-1 line to voice1/<role>.wav for the lip-sync
  lipsync   NOT in this job: dispatch "Marketing lip-sync CPU" with reel=<folder> (free, LatentSync 1.6, parallel
            runners, ~1–2 h) or "Marketing lip-sync" (fal / vertex) — both write lipsync/<role>.mp4
  compose   reel_v3_compose.py with REEL_SPEC: montage.py + reel_studio.html overlay + music/sfx → reel.mp4 + cover.jpg
            (a voice-1 scene without its lip-synced clip falls back to the take + voice, unsynced, with a warning)

usage: reel_studio.py <stage> marketing/reels/<id>/spec.json
"""
import json, os, pathlib, re, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).parent
MAX_NEW_SHOTS, PRICE = 6, {False: 0.10, True: 0.15}  # Veo fast, US$/s silent / with speech
SPEAKING = {"s1v_d", "s5v_d", "s1", "s5_a"}  # library takes where he talks to camera: sources for voice-1 lip-sync
V1_VOICE, V2_VOICE = "Orus", "Charon"  # the owner's picks (Oct 2026): on-camera voice, narrator
LIBRARY = ROOT / "marketing/avatar/clips"
# What the shared takes show, so a spec can reuse one instead of paying for a new shot.
LIBRARY_NOTES = {
    "s1v_d": "seated at the desk, talks to camera, a little tired (speaking take, best for a hook)",
    "s5v_d": "standing, confident, talks to camera with open palms (speaking take, best for a call to action)",
    "s5c": "with his espresso at the counter, sips, smiles, small toast, points down (silent, lips closed)",
    "s1": "seated at the desk, looks up and talks to camera, slightly tired (speaking take)",
    "s2": "glances at the empty couch and the wall clock, exhales, stands up (silent)",
    "s3": "makes an espresso, sips, small satisfied smile (silent)",
    "s4": "reads a message on his phone, smiles, types a reply (silent)",
    "s5_a": "standing, confident, talks to camera with open-palm gestures (speaking take)",
    "s5b": "standing, smiles, points down to the comments, 'your turn' gesture (silent)",
}
ROLE_RE = re.compile(r"^[a-z][a-z0-9_]{0,23}$")
TRANSITIONS = {"cut", "whip_l", "whip_r", "zoom"}


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
            if s.get("voice") == 1 and shot["reuse"] not in SPEAKING:
                errs.append(f"{r}: a voice-1 scene needs a take where he talks ({', '.join(sorted(SPEAKING))}) or a new shot")
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
            continue  # a library take, used as it is (a speaking take gets its mouth redrawn by the lip-sync)
        talking = s["voice"] == 1
        entry = {"id": s["role"], "refs": shot.get("refs") or
                 (["close_neutral", "close_smile", "full_front"] if talking else ["three_quarter", "desk_mid", "close_smile"]),
                 "frame": shot["frame"], "audio": False, "takes": 1}
        if talking:  # the lip-sync redraws the mouth: he only has to talk naturally, no sound needed
            entry["motion"] = shot["motion"] + (" He talks straight to the camera as if saying one sentence to a friend, natural"
                                                " mouth movement, blinking and small head movements.")
        else:
            entry.update({"motion": shot["motion"] + " His lips stay closed, he is not talking; a narrator speaks over this shot.",
                          "silent": True})
        cost += 8 * PRICE[False]
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
          f"{sum(s['voice'] == 1 for s in spec['scenes'])} on camera (lip-synced), "
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
            if src.exists():
                (d / "voice1" / f"{s['role']}.wav").write_bytes(src.read_bytes())
        jobs = ",".join(f"{s['role']}:{s['role']}:{(s.get('shot') or {}).get('reuse') or s['role']}:"
                        f"{(s.get('shot') or {}).get('from', 0.0)}" for s in v1)
        print(f"studio lipsync next: dispatch marketing-lipsync-cpu.yml with jobs={jobs} reel={rel}")
    if stage == "compose":  # after the lip-sync run (stage "all" stops before it)
        run([sys.executable, "marketing/source/reel_v3_compose.py"], {"REEL_SPEC": str(d / "spec.json")})


if __name__ == "__main__":
    main()
