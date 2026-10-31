# Video sources

Deterministic HTML animations rendered frame by frame (`window.render(t)`), then scored and exported.

- `reel.js`: second-person template for videos 03–10 (`mode: "renter" | "host"`); each `post*.html` sets `window.P`.
- `post1_hosts.html`, `post2_app.html`: standalone timelines. Every template publishes `window.EVENTS` (the sound cue sheet).
- `base.css`: brand tokens + the canvas. It's checked in for the **4:5 feed** canvas (1080×1350). For **9:16 Reels** set
  `html, body` to 1080×1920 and `#stage { top: 250px; transform: scale(.92); transform-origin: 50% 0 }` (Instagram safe
  zone), and `render.py`'s viewport to 1080×1920. The **16:9** layout of video 01 used a 1920×1080 canvas with the
  two-column positions described in the commit "Marketing: … 16:9".
- `bg` → `../backgrounds`.

Pipeline (Python with `playwright imageio-ffmpeg pillow numpy`, Chromium):

    python render.py post7_hotel.html 16 30                  # → out/post7_hotel.mp4, _cover.png, _grid.png, _events.json
    cp out/*_events.json events/
    VIDEOS=out OUT_DIR=out/en VOICE=audio/voiceovers-english python mix.py   # voice + dimmed/ducked music + UI sounds
    VIDEOS=out OUT_DIR=out/feed python mix.py                                 # music + UI sounds only
    python export.py MIX_EN=out/en RENDERS_916=out                            # → ../01-reels-english-9x16/ etc.
