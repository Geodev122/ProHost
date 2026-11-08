# AI agent adapter contract (draft)

The agent project owns channels (Meta/WhatsApp), brands, conversation state, the LLM, the sheet console and the knowledge builder. Each served app implements a small versioned adapter over HTTPS with OIDC ID tokens (audience per app, service-account allow-list). The agent never reads an app's database directly.

| Capability | Request → response | Replaces today (ProHost) |
|---|---|---|
| searchListings | criteria, exclude, limit → matches (id, title, place, room, price, reasons, ready URL) + overview | `aiListings.ts`, `listingPrices.ts`, `legacyPricing.ts`, `attendeePricing.ts`, `listingLocation.ts` |
| accountInsight | email → coarse facts (consent stays in the agent) | `aiAccountInsight.ts` |
| offers | → formatted text + asOf | `aiOffers.ts`, `readPlayCatalog` |
| leadSink | lead (kind, contact, consent, tags, source) → id, idempotent on email or WhatsApp | `officeLeadSync.ts`, `recordOfficeLead`, contact book |
| adminNotify | severity, title, body → channels | `sendPushToAdmins`, `sendEmail`, `sendAdminWhatsApp` |
| knowledge | → text, version | `aiKnowledge.ts`, `aiRelease.generated.ts`, `gen-agent-release.js` |
| health | → versions/status | `igsmoke` additions |

Migration order: (1) replace `officeLeads.ts → instagram/config+graph` import; (2) in-process `AppAdapter` interface with a ProHost implementation; (3) move hard-coded ProHost strings into `BrandProfile`; (4) leads for every brand; (5) shared contract tests and a contract-level read-only guard; (6) new repo + Google project, HTTPS adapter, one reversible webhook cutover.
