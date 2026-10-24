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
  (`advance()` in aiAgent.ts, unit-tested). A "no" is final. Threads (`ig_threads`) keep the person's whole history (see "Memory" below).
* Settings (sheet Status): `ai_enabled`, `ai_provider`, `ai_model`, `ai_daily_cap`, `ai_per_person_per_hour`. Unanswered questions go to the sheet `Learn` tab; answers written in `Knowledge` override the built-in facts.
* Maintenance: `geminiprobe`, `aitest` (scripted conversations, nothing sent), `aiprobe`/`aiquota` (Vertex).


## Conversation flow

1. **Replies always reach the agent.** An unpublished Meta app gets webhooks only for accounts with an app role, so `igDmPoll`
   (`dmPoller.ts`, every minute) reads our latest conversations through the Conversations API and runs each person's new
   messages through the same `handleEvent` as webhook DMs (`ig_events/m_<id>` dedupes both; several quick messages are
   answered once; nothing is sent when the team already answered by hand). Health: `ig_stats/dm_poll`, sheet row
   `health_dm_poll`, Maintenance `igsmoke` (also probes the Conversations API: a 403 means the token lacks
   `instagram_business_manage_messages`).
2. **Live conversations win over keyword rules.** Within 24 h of an agent message (`aiMemory.inConversation`) the person's DM
   goes to the agent; only a message that IS an opt-out word ("stop") or clearly asks for a person ("human", "agent", "real
   person") interrupts. If the agent can't answer (off, capped, provider down) the keyword rules answer as before. A comment
   answered by a keyword rule's private reply also opens the thread, so the agent knows what was said.
3. **Memory.** `ig_threads/{igsid}` keeps the last 40 turns for good (the model sees the last 16, with "[2 days later]"
   markers), a rolling `memory` summary written by the model every turn (who they are, city, need, budget, what we sent,
   promises), the space `search` so far and the listing ids already `sent`. Only the flow state (what the last message waited
   for) goes stale after 48 h. A person we have no turns for gets their earlier Instagram messages imported (team, ManyChat).
   Comment replies are appended, never overwrite; a commenter Instagram did not identify (`u:<username>`) is relinked on
   their first DM. Returning after a day the agent welcomes them back and continues from the memory.
4. **Listing finder.** `aiListings.ts` reads every public listing (`isPublicListing`): rooms, place, lowest price per unit
   (`listings/listingPrices.ts`, mirror of the app's `lowestPriceFor`), views / inquiries / saves / accepted bookings — never
   owner contacts. A short catalogue overview is in every turn. In FIND A SPACE mode the agent asks one question at a time
   (kind, city, hour/shift/day/month, people, budget/equipment) and returns `search` + `searchReady`; the server runs the pure
   `searchListings` and calls the model again with `LISTING RESULTS`; it presents 1-3 and the server adds up to 3 "View …"
   buttons to `https://pro-host.tech/listing/{id}?room={sub}&utm_source=instagram…` (only ids from the results). No match:
   said honestly, logged to `ig_learn`. The listing page hands Android visitors (Instagram's in-app browser included) to the
   app with an intent link (`appHandoffScript` in `shareLanding.ts`); app 1.0.45 opens the listing on that room, fetching it
   when it is outside the loaded Explore page and keeping the link through sign-in.
5. **How-to questions.** Answered directly; the account lookup ("the email you registered with, or no if you're new") is asked
   only when the answer depends on their own account. An email reply is the permission for one read-only lookup.
6. **Silence.** One nudge 3 h after our last question, about what they were looking for ("still looking for a clinic room in
   Jounieh?"), never twice, only inside Meta's 24 h window; no goodbye message. "thanks / bye" ends the chat politely;
   a bare "ok / sure / cool" is an answer, not a goodbye. A broken model answer sends the polite fallback and alerts the owner
   once a day; it never pauses the person.
7. Check any change with Maintenance `aitest` (comment-continues, office finder against the live catalogue, returning person
   with memory; nothing is sent).


## Comments (intent agent + poller)

- Keyword rules still win. A comment no rule matches goes to the comment agent: positive -> public thanks (+ one opening DM per day), question -> "see your DMs" + the answer as the private reply, negative -> not answered, not liked, flagged (event status `flagged`, email to admin), neutral/spam -> logged only.
- Instagram has no API to like a comment; the public thank-you is the visible acknowledgement.
- `igCommentPoll` fetches new comments of the latest 5 posts every minute and runs them through the same pipeline; it catches comments the webhook did not deliver (an unpublished Meta app only delivers events for accounts with an app role). Check it with Maintenance `igsmoke` ("comment poller") or the sheet row `health_comment_poll`.
- Status keys: `ai_comments` (default on), `ai_comment_dm` (default on). `igtest` sends synthetic positive / question / negative / neutral comments in dry-run.
