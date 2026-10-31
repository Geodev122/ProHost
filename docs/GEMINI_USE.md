# Google generative AI use in prohost-f766f

Kept current for Google Cloud Trust & Safety (Generative AI Prohibited Use Policy warning, Oct 2026). Update it when a use changes.

| Use | Model | Trigger | Volume |
|---|---|---|---|
| Instagram support assistant for @prohost.app DMs and comments (`functions/src/instagram/`) | Claude on Vertex by default; Gemini Flash-Lite only if the sheet sets `ai_provider=gemini` (`GEMINI_API_KEY`) | inbound DMs and comments, polled every minute | capped 300 replies/day, 12/person/hour, kill switch, dry-run until `live` |
| Narration for our own Story/posts | Gemini TTS (`scripts/marketing/voiceover*.json`) | manual workflow | a few runs a month |
| Background music | Lyria (`music.json`) | manual workflow | a few runs a month |
| Empty-room photos (no people) | Imagen (`backgrounds.json`) | manual workflow | a few runs a month |

Access: Cloud Functions (europe-west1), GitHub Actions (US). Audience: Lebanon and the wider Middle East; English, Arabic, French, Spanish.

Removed (Oct 2026): the AI-presenter video pipeline (Veo, Gemini image keyframes, lip-sync). It was only used for internal tests; nothing was published.

Rules: the assistant always says it is an AI and never claims to be human; no fabricated testimonials or synthetic people; no bulk/cold outreach, scraping, fake reviews, or sexual/violent/deceptive content; opt-out and hand-off are honoured; contact details are saved only after consent.
