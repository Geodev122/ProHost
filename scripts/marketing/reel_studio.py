"""Reel Studio: a brief-driven Instagram reel with the ProHost avatar, its two voices and the reel montage.

One spec (marketing/reels/<id>/spec.json, usually drafted from a brief in the Reel Studio artifact) → one reel, through the
same tools the office reel used (.claude/skills/prohost-reel/SKILL.md documents the whole chain):

  prepare   validates the spec; writes shots.json (new avatar shots → avatar_video.py) and voice.json (both voices →
            voiceover.py) into the reel's folder; prints the plan and the Veo cost estimate
  footage   avatar_video.py keyframes + clips for the new shots (same man, same office, Veo fast, silent)
  voices    voiceover.py: voice 1 lines in the source voice (Orus), voice 2 lines in the narrator (Charon), Lebanese
  voice1    voice1.py: voice 1 converted to the avatar's hook voice (Seed-VC); Gemini checks words + Lebanese accent
  lipsync   gpu_lipsync.py: LatentSync 1.6 on a GPU drives his mouth for every voice-1 scene
  compose   reel_v3_compose.py with REEL_SPEC: montage.py + reel_studio.html overlay + music/sfx → reel.mp4 + cover.jpg

usage: reel_studio.py <stage> marketing/reels/<id>/spec.json
"""
import json, os, pathlib, re, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).parent
MAX_NEW_SHOTS, PRICE_PER_S = 6, 0.10  # Veo fast, silent
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
            if s.get("voice") == 1 and shot["reuse"] not in ("s1", "s5_a"):
                errs.append(f"{r}: a voice-1 scene needs a speaking take (s1, s5_a or a new shot)")
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
    shots = []
    for s in spec["scenes"]:
        shot = s.get("shot") or {}
        if shot.get("reuse"):
            continue
        talking = s["voice"] == 1
        motion = shot["motion"] + (" He talks to the camera naturally, as if saying one sentence to a friend, natural "
                                   "blinking and head movement." if talking else
                                   " His lips stay closed, he is not talking; a narrator speaks over this shot.")
        shots.append({"id": s["role"], "refs": shot.get("refs") or (["close_neutral", "close_smile", "full_front"] if talking
                                                                   else ["three_quarter", "desk_mid", "close_smile"]),
                      "frame": shot["frame"], "motion": motion, "audio": False, "silent": not talking, "takes": 1})
    (d / "shots.json").write_text(json.dumps({**{k: v for k, v in base.items() if k != "shots"}, "shots": shots},
                                             ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    vo = json.loads((HERE / "voiceover_reel.json").read_text(encoding="utf-8"))
    posts = []
    for vid, voice, n in (("v1", "Orus", 1), ("v2", "Charon", 2)):
        lines = [s for s in spec["scenes"] if s["voice"] == n]
        if lines:
            posts.append({"id": vid, "voice": voice, "gender": "male", "length": 9.0 * len(lines) + 1,
                          "persona": vo["posts"][0]["persona"] if n == 2 else vo["posts"][1]["persona"],
                          "lines": [{"at": 9.0 * i + 0.2, "end": 9.0 * i + 8.8, "style": s.get("style", ""), "text": s["line"]}
                                    for i, s in enumerate(lines)]})
    (d / "voice.json").write_text(json.dumps({"_about": f"Reel Studio voices for {spec.get('title', d.name)}",
                                              "dialect": vo["dialect"], "length": 9.0 * len(spec["scenes"]) + 1,
                                              "output": str(rel / "audio"), "posts": posts}, ensure_ascii=False, indent=2) + "\n",
                                  encoding="utf-8")
    print(f"studio prepare {d.name}: {len(spec['scenes'])} scenes, {new} new shot(s) (~US${new * 8 * PRICE_PER_S:.2f} of Veo), "
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
        run([sys.executable, "scripts/marketing/avatar_video.py", "keyframes", ""], env)
        run([sys.executable, "scripts/marketing/avatar_video.py", "clips", ""], env)
    if stage in ("voices", "all"):
        run([sys.executable, "scripts/marketing/voiceover.py", "/tmp/voiceover"], {"VO_CONFIG": str(d / "voice.json")})
    if stage in ("voice1", "all") and v1:
        jobs = ",".join(f"{s['role']}=v1_{i + 1:02d}" for i, s in enumerate(v1))
        run([sys.executable, "scripts/marketing/voice1.py", os.environ.get("SEEDVC_DIR", "/tmp/seed-vc"), "50"],
            {"V1_SRC": f"{rel}/audio/lines", "V1_OUT": f"{rel}/voice1", "V1_JOBS": jobs})
    if stage in ("lipsync", "all") and v1:
        jobs = ",".join(f"{s['role']}:{s['role']}:{(s.get('shot') or {}).get('reuse') or s['role']}:"
                        f"{(s.get('shot') or {}).get('from', 0.0)}" for s in v1)
        run([sys.executable, "scripts/marketing/gpu_lipsync.py", jobs, "40", "1.5", os.environ.get("LIPSYNC_BACKEND", "vertex")],
            {"LIPSYNC_VOICE": f"{rel}/voice1", "LIPSYNC_CLIPS": f"{rel}/clips", "LIPSYNC_OUT": f"{rel}/lipsync"})
    if stage in ("compose", "all"):
        run([sys.executable, "marketing/source/reel_v3_compose.py"], {"REEL_SPEC": str(d / "spec.json")})


if __name__ == "__main__":
    main()
