# Backlog: backend, data, integrations, deep links, operations and cost (proposed 9 Nov 2026)

Requested by the owner while the Compose parity work runs: "add more tasks and improvements, consider backend, database, integrations, deep links, functions and other areas, and the operating cost of Firebase and Google Cloud, with strategies to lower it and to scale cheaply." Everything below is a **proposal**: nothing here changes behaviour until it is picked up as a unit through the normal protocol (learn → plan → execute → compare → confirm), and anything that changes what a person sees is listed in `OWNER_QUEUE.md` first. Prices change: figures are orders of magnitude to rank work by, not quotes; verify on the pricing pages before committing to a number. Findings come from reading this repo (9 Nov 2026).

## 0. What the repo shows today (cost-relevant facts)
- 54 exported functions (Node 22, v2), one global region (`europe-west1`) and a global `maxInstances: 10`, six run-as service accounts already prepared (`runAs("billing"|"ig"|…)`), so cost can be attributed per group.
- Scheduled work: **four jobs every minute** (`igCommentPoll`, `igSheetSync`, `igFollowUps`, `igDmPoll` ≈ 175,000 invocations a month, each with Firestore reads), billing retry every 15 min, hourly `expirePackages` and `pruneExpiredNotifications`, daily billing sync, weekly token refresh.
- Firestore: 5 composite indexes, 1 field override, 671 lines of rules; Explore reads public listings 100 at a time through a **live snapshot listener** that grows with "load more"; 20 snapshot listeners in the client; admin listeners already removed.
- Images: client compresses to JPEG 82 with sub-sampling; Coil asks for 256/800 px but the **file served is the uploaded original** (no server-side resized variants).
- Email through the Trigger Email extension; DM agent on the Anthropic API; Cloud Text-to-Speech only for marketing audio; Hosting + Netlify config both present; CI renders 280 screenshots in 6 shards per push.

## 1. Quick wins (low risk, big effect) — do first
| # | Item | Why / expected effect | Behaviour change |
|---|---|---|---|
| Q1 | **Public pages that are functions**: *verified already in place* — `siteSitemap` (`max-age=600, s-maxage=3600`) and `listingShareLanding` (`max-age=300, s-maxage=900`) sit behind Hosting rewrites, so the CDN answers repeat requests. Remaining refinement: add `stale-while-revalidate` and a longer `s-maxage` for listings that did not change (ETag from `updatedAt`), and `s-maxage` on `legalDocumentPage` (currently `max-age=300` only) | fewer function invocations from crawlers and link previews | none |
| Q2 | **Firestore TTL policies** on `notifications.expireAt` (replaces the hourly `pruneExpiredNotifications` query + deletes), `mail` (30 days), `email_send_limits`, `email_otps`, `play_billing_pending` closed rows, `ig_*` logs | removes a scheduled function and unbounded storage growth | none |
| Q3 | **IG pollers adaptive**: one dispatcher instead of four per-minute jobs; every minute only while a conversation is live, 5–10 min otherwise, exponential back-off when idle; move to webhooks the day Meta delivers them to a published app | ~70–90 % fewer invocations and reads; fewer Sheets API calls (quota 60/min/user) | none (DM latency tiers documented) |
| Q4 | **Instance caps**: *verified already in place* — `setGlobalOptions({ region: "europe-west1", maxInstances: 10 })` in `functions/src/index.ts`. Remaining: per-group overrides where 10 is too low (the Meta webhook and Play RTDN under bursts) or needlessly high (batch jobs → 1), `concurrency` (80) where CPU ≥ 1, `minInstances: 0` except where a cold start is measured to hurt sign-in | worst-case bill already bounded; tune per group | none |
| Q5 | **Budgets and alerts** on the billing account at 25/50/90/100 %, plus per-service alerts (Firestore reads, Functions, Storage egress, Anthropic spend); GCP **billing export to BigQuery** + a small Looker Studio page grouped by run-as account / label | you see cost per function group before it surprises you | none |
| Q6 | **Anthropic prompt caching** for the DM agent's fixed prompt and knowledge, a small model for intent/routing and a larger one only to answer, `max_tokens` caps, a per-contact daily budget written to `ig_stats` | input tokens dominate; caching the static prefix cuts that by roughly an order of magnitude | none (answers unchanged in quality; tested with `aitest`) |
| Q7 | **CI cost guard**: full 280-render parity gate only on PRs, merges and a `parity` label; on ordinary pushes run the build plus the screens whose files changed; nightly full run | each full gate is ~6 shards × 4–25 min of runner time per push; this session alone pushed dozens | none |
| Q8 | Cloud Logging hygiene: drop `info` from pollers, structured severity, 30-day default retention, an exclusion filter for health noise | ingestion beyond the free allowance is billed per GiB | none |

## 2. Database (Firestore) — reads are the bill that grows with users
1. **Explore catalogue as a Firestore data bundle**: a scheduled function builds a bundle of public listings (changes since the last one) into Hosting/Storage behind the CDN; the app loads it first and only listens to deltas (`updatedAt` cursor). A bundle read is a CDN hit, not a billed read per user; at 10 k daily users this is the single largest saving.
2. **Stop re-listening to everything**: replace the growing 100/200/… snapshot listener by "initial page from cache/bundle + incremental `updatedAt > lastSync` query"; stop listeners in `onStop`; keep the persistent cache sized explicitly (`PersistentCacheSettings`, e.g. 100 MB) so a cold start is mostly local.
3. **Viewport queries when the catalogue grows**: store a geohash (or S2 cell) on each listing and query by map bounds instead of paging by document id. Ladder: < 2 k listings bundles + current filters; 2 k–50 k geohash tiles (the pure `searchListings` already exists server-side for the agent); beyond that a managed search (Typesense on Cloud Run or Algolia) — decide on data, not guesses.
4. **Index hygiene**: add single-field exemptions for fields never queried (long descriptions, photo arrays, `savedSpaceIds`, `subdivisions`) — fewer index entries per write, lower storage; review `firestore.indexes.json` against real queries with the Firestore "index usage" insights.
5. **Rules cost audit**: every `get()` / `exists()` in `firestore.rules` is a billed read per evaluation. Verify hot paths use token claims only (admin checks already do); add a rules test per `get()` that proves it is needed.
6. **Write amplification**: `subdivisions` is saved whole; if a listing exceeds ~20 rooms move rooms to a subcollection (the app already treats rooms as units). Not needed yet; add a metric (document size p95) first.
7. **PITR and backups**: keep PITR (7 days) but set backup retention deliberately (daily 7 + weekly 4) and export a monthly snapshot to a Coldline bucket; run a **restore drill** once and write the runbook (ops).
8. **Retention policy** for `audit_security_logs`: keep 13 months hot, export older to BigQuery/GCS, then TTL.

## 3. Storage, images and CDN — egress is the other big line
1. **Resized variants** generated on upload (Cloud Function or the Resize Images extension): 256 / 800 / 1600 px WebP (AVIF later); the app's Coil requests map to a variant name; listing cards stop downloading multi-hundred-KB originals. Largest egress saving for an image-led product.
2. **Immutable names + `Cache-Control: public, max-age=31536000, immutable`** on listing photos (object names already carry the image id); serve public images through a CDN origin (Firebase Hosting rewrite, Cloud CDN backend bucket, or an owner-approved Cloudflare in front) instead of direct `firebasestorage` URLs.
3. **Lifecycle rules**: delete orphaned uploads (not referenced after 7 days), move verification documents and booking agreements to Nearline after the review window, delete after the legal retention period (owner decides the period).
4. Keep verification documents private `gs://` references (already the rule); never turn them into download URLs.

## 4. Functions and scheduled work
1. Group by cost profile: user-facing callables (low latency, `maxInstances` capped), triggers (retry-safe, idempotent), batch (scheduled, `cpu` low, long timeout). Apply the run-as accounts already prepared; label each function (`group`, `owner`) for billing export.
2. `expirePackages` queries by `ownerPackageExpiryMillis <= now` with an index (reads only due users) or schedule a Cloud Task per user at expiry (event-driven, zero polling).
3. `billingSyncJob` (daily) already pages with a cursor; keep, but cap concurrency and skip subscriptions whose state cannot have changed (RTDN already covers changes).
4. `retry: true` functions: ensure idempotency keys (already for favorites and billing) and set `maxRetrySeconds` so a poison message cannot retry for days.
5. Secrets: read once per instance (module scope), never per request; keep the number of secrets small (each version is billed).
6. Cold start: keep dependencies lean (`googleapis` is heavy — import only the sub-APIs used), `minInstances: 1` only for sign-in if measured p95 > 2 s.
7. Remove dead exports found by the P2 audit (`aiprobe`/`aiquota` Maintenance entries with no handler).

## 5. Integrations
- **FCM**: free, but each push also writes `notifications/{id}`; with TTL (Q2) that is bounded. Use topics for broadcasts, collapse keys for chatty events, and remove a token on `registration-token-not-registered` so dead tokens are not retried forever.
- **Anthropic** (see Q6): spend caps, per-contact budgets, kill switch exists; log tokens per brand for the multi-brand agent so each brand's cost is visible.
- **Google Maps Platform**: keep on-device geocoding (`Geocoder` on Android, `CLGeocoder` on iOS) — free — and avoid server-side Geocoding/Places API calls; restrict every key (package + SHA-1, bundle id) and set per-key quotas so an abused key cannot bill.
- **Email**: provider cost is per message; keep the 5/hour per address limit, batch campaigns, and suppress addresses that bounce.
- **Google Sheets bridge** (every minute): back off, batch writes, and keep the Sheet as an editing surface only (not a data path).
- **Cloud Text-to-Speech**: marketing audio is generated once and committed; never call it at runtime.
- **Pub/Sub (Play RTDN)**: tiny; keep the self-test and the heartbeat.

## 6. Deep links and attribution
1. **Android App Links** (`assetlinks.json`, verified for `pro-host.tech`) + the `prohost://` scheme; **iOS Universal Links** (`apple-app-site-association`, P8). One `DeepLinkRouter` in common code with **shared test vectors** (redeem codes, `/listing/{id}?room=`, notification payload routes) run on both platforms.
2. **Deferred deep links** (install → first open): Firebase Dynamic Links is shut down; use the **Play Install Referrer** (free) to carry `utm_*`, promo `code` and listing id through an Android install; on iOS use a Universal Link with an App Store campaign token and, only if the owner wants cross-install attribution, a vendor (cost and privacy decision → owner queue D14).
3. Link-preview pages (`/listing/{id}`) cached at the CDN (Q1) with correct `og:` tags; canonical + hreflang kept (already tested).
4. Notification taps route like deep links (already: "Open routes like a push tap"); add the vectors above to prove it.

## 7. Security, reliability, operations
- **App Check**: stay unenforced until ≥ 99 % verified (documented); add a dashboard of verified vs unverified calls so the day to enforce is data-driven.
- **Rules tests** for admin-only collections (audit finding F1) before the web admin goes live.
- **SLOs** (Cloud Monitoring): callable error rate and p95 latency, RTDN delivery, push delivery; **uptime checks** on `/`, `/get`, `/sitemap.xml` (owner item already listed).
- **Infrastructure as code**: capture budgets, alert policies, IAM and TTL policies in Terraform (or the existing `scripts/gcp-remediation/` made declarative) so a project can be rebuilt; any IAM change still updates `docs/GCP_IAM.md`.
- **Disaster recovery**: written runbook + one tested restore (Firestore PITR/backup, Storage, secrets, DNS) with target RTO/RPO.
- **Privacy**: account deletion already server-side; add a data-export callable for the user's own data (GDPR/Apple expectation) and document retention per collection.

## 8. Scaling ladder (when to spend)
| Stage | Signal | Action |
|---|---|---|
| now (hundreds of users) | free tier covers most | Q1–Q8, budgets, TTL |
| 1–10 k monthly users | Firestore reads and image egress lead the bill | bundles, resized images, CDN, listener hygiene |
| 10–100 k | map/search queries and push volume | geohash viewport queries, search service decision, topic pushes |
| 100 k+ | multi-region latency, support load | regional read replicas decision, dedicated search, review Firestore edition/pricing, SRE on-call |

## 9. Mobile-side efficiency that lowers server cost
Stop listeners when the app is backgrounded; reuse the persistent cache; debounce search; request only the image variant a card needs; batch analytics (consent-gated already); baseline profile and R8 keep startup and memory low (also helps older Android and iOS memory limits).

## 10. How these become plan units
- **P11 Backend efficiency** (new): Q1–Q8, then sections 2–4, each as a unit with an emulator test or a measurement before/after (reads per Explore session, invocations per day, egress per card).
- **P5 data layer** gains: incremental sync + bundle loader behind the `FirestoreGateway` (shared by Android and iOS).
- **P8 iOS backend** gains: Universal Links, deferred-link decision, APNs topic strategy.
- **P9 agent** gains: adaptive polling, prompt caching, per-brand cost accounting.
- **P10 hardening** gains: SLOs, restore drill, cost dashboard review.
- Owner decisions and manual jobs raised here are in `OWNER_QUEUE.md` (D14–D18, M6–M9).
