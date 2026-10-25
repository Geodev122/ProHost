"""Reel Studio: a brief-driven Instagram reel with the ProHost avatar, its two voices and the reel montage.

One spec (marketing/reels/<id>/spec.json, usually drafted from a brief in the Reel Studio artifact) → one reel, through the
same tools the office reel used (.claude/skills/prohost-reel/SKILL.md documents the whole chain):

  prepare   validates the spec; writes shots.json (avatar shots → avatar_video.py) and voice.json (the narrator →
            voiceover.py) into the reel's folder; prints the plan and the Veo cost estimate
  footage   avatar_video.py: first frames (a library keyframe, or Gemini image for a new frame) + Veo clips. Voice-1
            scenes are filmed with him SAYING the line (Veo audio, 2 takes): the lip-sync is native, no GPU needed
  check     avatar_video.py check: Gemini picks the take with the scripted words and the most Lebanese accent
  voices    voiceover.py: voice 2 (the narrator, Charon, Beirut dialect prompt)
  voice1    voice1.py: each voice-1 take's own speech converted to the hook voice (Seed-VC, timing untouched) and
            muxed back → lipsync/<role>.mp4 (mouth and voice in sync by construction)
  compose   reel_v3_compose.py with REEL_SPEC: montage.py + reel_studio.html overlay + music/sfx → reel.mp4 + cover.jpg

usage: reel_studio.py <stage> marketing/reels/<id>/spec.json
"""
import json, os, pathlib, re, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).parent
MAX_NEW_SHOTS, PRICE = 6, {False: 0.10, True: 0.15}  # Veo fast, US$/s silent / with speech
SPEAK_TAKES = 2
LIBRARY = ROOT / "marketing/avatar/clips"
# What the shared takes show, so a spec can reuse one instead of paying for a new shot.
LIBRARY_NOTES = {
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
            if s.get("voice") == 1:
                new += 1  # filmed again from that shot's first frame, now saying this line
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
    lib = {x["id"]: x for x in base["shots"]}
    speaking_voice = lib["s5v"]["voice"]
    shots, cost = [], 0.0
    (d / "keyframes").mkdir(parents=True, exist_ok=True)
    for s in spec["scenes"]:
        shot = s.get("shot") or {}
        talking = s["voice"] == 1
        if shot.get("reuse") and not talking:
            continue  # a library take, used as it is
        src = lib.get(shot.get("reuse", "")) or lib.get({"s5_a": "s5"}.get(shot.get("reuse", ""), ""), {})
        frame, motion = shot.get("frame") or src.get("frame", ""), shot.get("motion") or src.get("motion", "")
        if shot.get("reuse"):  # same first frame as the library shot: no new image, same framing
            key = ROOT / "marketing/avatar/keyframes" / f"{src.get('id', shot['reuse'])}.png"
            if key.exists():
                (d / "keyframes" / f"{s['role']}.png").write_bytes(key.read_bytes())
        entry = {"id": s["role"], "refs": shot.get("refs") or src.get("refs") or
                 (["close_neutral", "close_smile", "full_front"] if talking else ["three_quarter", "desk_mid", "close_smile"]),
                 "frame": frame}
        if talking:
            entry.update({"motion": motion + " He says the line straight to the camera, natural blinking and head movement.",
                          "audio": True, "takes": SPEAK_TAKES, "speech": s["line"], "voice": shot.get("voice") or speaking_voice})
        else:
            entry.update({"motion": motion + " His lips stay closed, he is not talking; a narrator speaks over this shot.",
                          "audio": False, "silent": True, "takes": 1})
        cost += 8 * entry["takes"] * PRICE[entry["audio"]]
        shots.append(entry)
    (d / "shots.json").write_text(json.dumps({**{k: v for k, v in base.items() if k != "shots"}, "shots": shots},
                                             ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    vo = json.loads((HERE / "voiceover_reel.json").read_text(encoding="utf-8"))
    posts = []
    for vid, voice, n in (("v2", "Charon", 2),):
        lines = [s for s in spec["scenes"] if s["voice"] == n]
        if lines:
            posts.append({"id": vid, "voice": voice, "gender": "male", "length": 9.0 * len(lines) + 1,
                          "persona": vo["posts"][0]["persona"],
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
    if stage in ("footage", "check", "all") and v1:
        run([sys.executable, "scripts/marketing/avatar_video.py", "check", ",".join(s["role"] for s in v1)],
            {"AVATAR_SHOTS": str(d / "shots.json"), "AVATAR_OUT": rel})
    if stage in ("voices", "all") and any(s["voice"] == 2 for s in spec["scenes"]):
        run([sys.executable, "scripts/marketing/voiceover.py", "/tmp/voiceover"], {"VO_CONFIG": str(d / "voice.json")})
    if stage in ("voice1", "all") and v1:
        jobs = ",".join(f"{s['role']}=clip:{rel}/clips/{s['role']}.mp4" for s in v1)
        run([sys.executable, "scripts/marketing/voice1.py", os.environ.get("SEEDVC_DIR", "/tmp/seed-vc"), "50"],
            {"V1_OUT": f"{rel}/voice1", "V1_JOBS": jobs, "V1_MUX": f"{rel}/lipsync"})
    if stage in ("compose", "all"):
        run([sys.executable, "marketing/source/reel_v3_compose.py"], {"REEL_SPEC": str(d / "spec.json")})


if __name__ == "__main__":
    main()
