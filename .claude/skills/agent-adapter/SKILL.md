---
name: agent-adapter
description: Move the AI lead and marketing agent (Instagram/WhatsApp DM and comment agent, multi-brand) out of the ProHost repo behind an adapter contract so it can serve many apps. Use for functions/src/instagram, tools/control-room, marketing, docs/migration/AGENT_ADAPTER.md.
---

# Agent project and adapter

Target: new repo + new Google project; the agent owns channels (Meta/WhatsApp), brands, conversation state, the LLM (Anthropic API only — never Vertex/Gemini), the sheet console,
the knowledge builder. Each served app implements a versioned HTTPS adapter (OIDC ID tokens, per-app audience + service-account allow-list): `searchListings`, `accountInsight`,
`offers`, `leadSink`, `adminNotify`, `knowledge`, `health` (`docs/migration/AGENT_ADAPTER.md`). New code never reads ProHost collections directly.

## Order (MASTER_PLAN P9)
1 replace `website/officeLeads.ts → instagram/config + graph` (`dmOwner`) with a lead-sink callback · 2 in-process `AppAdapter` interface + ProHost implementation; retarget `aiRuntime.ts` /
`aiSweeper.ts`; drop the imports of billing/listings/website/bookings from `instagram/` (today: `aiListings.ts`, `aiAccountInsight.ts`, `aiOffers.ts`) · 3 move hard-coded ProHost strings
(`ALLOWED_URL`, `FALLBACK_REPLY`, canned lines in en/ar/fr/es, `DEFAULT_SETTINGS`, `IG_SUBJECT_TAG`) into `BrandProfile` · 4 make lead recording work for every brand (`onOfficeLeadSaved`
listens to legacy `ig_leads` only) · 5 contract tests + a contract-level read-only guard (`readOnlyAccess.test.ts` cannot see writes outside `instagram/`) · 6 new repo/project, HTTPS adapter,
one reversible webhook cutover (Meta callbacks, `public/_redirects`, `firebase.json` rewrites `/ig-webhook`, `/wa-webhook`).

## Stays in ProHost
Adapter implementation (reuse `listings/listingPrices.ts`, `legacyPricing.ts`, `attendeePricing.ts`, `listingLocation.ts`, `sitemapXml.ts#isPublicListing`, `billing/playCatalogLive.ts`,
`lib/push.ts`, `lib/email.ts`, `website/addressBook.ts`), contact book `mailing_list`, `public/admin.html`, website forms, `listing/shareLanding.ts` + deep links, `docs/release` producer.

## Moves out
`functions/src/instagram/**` (61 files), `tools/control-room`, `docs/INSTAGRAM.md`, `docs/GEMINI_USE.md` (agent half), `marketing/**` (286 MB), `public/social`, `scripts/marketing`, Maintenance
tasks igsetup/igsmoke/igtest/igmedia/igpublish/aitest/officeleadsmoke, `ig_*` rules and tests, `run-ig`, secrets (today in Firestore `app_config/instagram*`, `ig_brands/*/private/secrets`).

## Hard rules kept (CLAUDE.md)
AI disclosure in the first message and truthful answers; canned lines never pose as a person; no likes promised; offers only from live Play data; silence nudge once after 3 h; 24 h Meta
window; 40 turns + memory kept; no AI-presenter media. Before/after every move: `igsmoke`, `officeleadsmoke`, `aitest` green and DM/comment behaviour unchanged.
