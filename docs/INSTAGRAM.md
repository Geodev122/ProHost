# Instagram automation (@prohost.app)

Our own ManyChat-style engine: replies to comments, story replies / mentions and DMs on @prohost.app.
Code: `functions/src/instagram/`. Control room: the "IG Control Room" artifact (edits a Google Sheet; see below).

## Flow
Meta webhook → `pro-host.tech/ig-webhook` (`igWebhook`, hosting rewrite) → signature check (`X-Hub-Signature-256`, app secret)
→ `parseWebhook` → `decide` (pure engine) → Instagram API with Instagram login (`graph.instagram.com/v23.0`) → `ig_events` log.

* Comment → public reply under the comment and/or a **private reply** (DM tied to the comment id; one per comment, within 7 days).
* Story reply / story mention / DM → DM to the sender (24 h messaging window; only people who wrote first).
* Rules: trigger (`comment`, `story_reply`, `story_mention`, `dm_keyword`, `dm_welcome`, `dm_default`), keywords (word / contains,
  case + accent insensitive), optional post id, reply variants (random pick), DM text, link button, quick replies, cooldown, tags, priority.
* Guards (`engine.ts`): STOP words opt the person out for good, "agent/human/help me" pauses automation for them 24 h, per-rule cooldown,
  hourly cap, active hours, circuit breaker (5 API errors in an hour pauses sending and emails admin).

## Data (all server-only, rules + rules test)
`app_config/instagram` (secrets, mode, kill switch, `sheetId`, engine `settings`), `ig_rules`, `ig_events`, `ig_contacts`, `ig_stats`.
Secrets come only from GitHub secrets `IG_APP_SECRET`, `IG_ACCESS_TOKEN`, `IG_VERIFY_TOKEN` via Maintenance task `igsetup`. Never paste them in chat or code.

## Control-room sheet ("ProHost IG Automations")
Tabs `Rules` (artifact → backend), `Status` (`mode`, `kill_switch`, engine settings in rows 2-39 written by the artifact; `health_*` rows from row 40 by the backend),
`Log`, `Contacts` (backend → artifact). `igSheetSync` mirrors it every minute; bad rule rows are skipped and listed in `health_rule_errors`.
Share the sheet (Editor) with the functions' runtime service account. Mode stays `dry-run` (logs what it *would* send) until Status `mode` = `live`.

## Go-live checklist
1. Meta app "ProHost-IG" → Instagram → webhooks: callback `https://pro-host.tech/ig-webhook`, verify token = `IG_VERIFY_TOKEN`, fields `comments`, `messages`, `messaging_postbacks`.
2. Maintenance `igsetup` (code = Sheet id), then `igsmoke`.
3. Test from a tester account in dry-run; check the Live log. Then set mode `live` for one rule. Turn off ManyChat's automations on @prohost.app first (double replies).
4. Public (non-tester) accounts need Meta App Review (Advanced Access for `instagram_business_manage_messages` / `_manage_comments`).
5. Tokens last 60 days; `igTokenRefresh` renews weekly and emails admin when it fails or < 14 days remain.
