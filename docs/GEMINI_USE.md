# Google generative AI use in prohost-f766f

Kept current for Google Cloud Trust & Safety (Generative AI Prohibited Use Policy warning, Oct 2026). Update it when a use changes.

| Use | Model | Trigger | Volume |
|---|---|---|---|
| Instagram/WhatsApp support assistant for our own accounts (`functions/src/instagram/`) | Claude (Anthropic API, `claude-haiku-5-5`) since 31 Oct 2026; before that Gemini Flash-Lite (`gemini-3.1-flash-lite`) via an AI Studio key | inbound DMs and comments, polled every minute | capped 300 replies/day, 12/person/hour, kill switch, dry-run until `live` |
| Narration for our own Story/posts | Gemini TTS (`scripts/marketing/voiceover*.json`) | manual workflow | a few runs a month |
| Background music | Lyria (`music.json`) | manual workflow | a few runs a month |
| Empty-room photos (no people) | Imagen (`backgrounds.json`) | manual workflow | a few runs a month |

Access: Cloud Functions (europe-west1), GitHub Actions (US). Audience: Lebanon and the wider Middle East; English, Arabic, French, Spanish.

Removed (Oct 2026): the AI-presenter video pipeline (Veo, Gemini image keyframes, lip-sync). It was only used for internal tests; nothing was published.

Rules: the assistant always says it is an AI and never claims to be human; no fabricated testimonials or synthetic people; no bulk/cold outreach, scraping, fake reviews, or sexual/violent/deceptive content; opt-out and hand-off are honoured; contact details are saved only after consent.

History: until 31 Oct 2026 the live support assistant ran on Gemini (Status `ai_provider = gemini`). It now runs on Claude through Anthropic's API (key in the `ANTHROPIC_API_KEY` repo secret, workspace id in Status `ai_workspace_id`). The Gemini provider code remains as an unused option. Text generation no longer touches Google Cloud.
