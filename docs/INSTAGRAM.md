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

## AI DM agent (Gemini / Claude)
`functions/src/instagram/aiAgent.ts` (prompt, output schema, conversation state), `aiRuntime.ts` (thread memory, caps, leads), `aiLlm.ts`
(providers: `gemini` AI Studio key, `anthropic` key, `vertex`), `aiKnowledge.ts` (site facts + feature value) and the generated
`aiRelease.generated.ts` (current version + latest "What's new", produced from `docs/release` by `scripts/website/gen-agent-release.js` on every functions build).
* Two modes per turn: a direct question gets one full answer with the right button; a vague opener starts a 2-3 exchange discovery (what brings them, their
  profession/space, the gap) before connecting ProHost to that need.
* Name/email are saved to `ig_leads` (sheet tab `Leads`, admin email) ONLY after the person said yes to a consent question asked in an earlier turn
  (`advance()` in aiAgent.ts, unit-tested). A "no" is final. Threads (`ig_threads`) hold the last 12 turns for 48 h.
* Settings (sheet Status): `ai_enabled`, `ai_provider`, `ai_model`, `ai_daily_cap`, `ai_per_person_per_hour`. Unanswered questions go to the sheet `Learn` tab; answers written in `Knowledge` override the built-in facts.
* Maintenance: `geminiprobe`, `aitest` (scripted conversations, nothing sent), `aiprobe`/`aiquota` (Vertex).


## Conversation flow (how-to questions)

1. Greeting by first name and the "may I look up your account with the email you registered with? type no if you're new" question go out as two separate DMs.
2. An email reply is the person's permission for one read-only account lookup (role, Premium, listings, rentals, profile gaps). The agent only uses it to choose the next step and never repeats it back. "no" = new person: download, live Premium offers (from Google Play, cached in `app_config/instagram_offers`), then the publishing steps, each as its own message.
3. Silence: one nudge after 2 minutes, goodbye after 2 more (`igFollowUps`); thanks/ok/bye ends the chat politely. All inside Meta's 24 h window.
4. Check any change with Maintenance `aitest` (prints every message part and the live offers given to the agent).


## Comments (intent agent + poller)

- Keyword rules still win. A comment no rule matches goes to the comment agent: positive -> public thanks (+ one opening DM per day), question -> "see your DMs" + the answer as the private reply, negative -> not answered, not liked, flagged (event status `flagged`, email to admin), neutral/spam -> logged only.
- Instagram has no API to like a comment; the public thank-you is the visible acknowledgement.
- `igCommentPoll` fetches new comments of the latest 5 posts every minute and runs them through the same pipeline; it catches comments the webhook did not deliver (an unpublished Meta app only delivers events for accounts with an app role). Check it with Maintenance `igsmoke` ("comment poller") or the sheet row `health_comment_poll`.
- Status keys: `ai_comments` (default on), `ai_comment_dm` (default on). `igtest` sends synthetic positive / question / negative / neutral comments in dry-run.
