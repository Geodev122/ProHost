# ProHost Lead Engine

A claude.ai artifact that finds and qualifies Pro Host prospects (space owners) and writes their outreach, then
exports an Excel workbook. Source: `lead-engine.html`. Published (private, owner only):
https://claude.ai/artifact/Q63Xzo5jQwG6cLhcKBh9HN. To update it, republish this same file from a Claude session
(passing that URL from a new conversation) so the link and its saved data stay.

## Pipeline

| # | Agent | How it runs |
|---|---|---|
| 1 | Company intelligence | Claude reads pasted website text and writes the seller profile (product, buyers, pain points, proof points, markets). |
| 2 | ICP builder | Claude turns the profile, target segments and countries into rules: segment keywords, buyer titles, size band, signal words, Apollo filters and a PDL SQL query. |
| 3 | Lead discovery | You run the Apollo / People Data Labs search and import the export. Imports merge, never replace (see below). The engine never invents contacts. |
| 4 | Email verification | In page: syntax, Apollo locked placeholders, disposable domains, role mailboxes, duplicates, personal / mismatched domains, plus the provider's status. Mailbox checks go through NeverBounce / ZeroBounce / MillionVerifier: download the CSV, verify, import the result file. |
| 5 | Lead research | Claude (fast model, 10 leads per call): signal, pain point, reason selected, summary, using only facts in the record. |
| 6 | Lead scoring | In page, instantly for every lead: ICP fit 40, industry 20, role 20, size 10, activity 10 (weights and threshold editable, default ≥ 75). |
| 7 | Personalized emails | Claude (balanced model, 5 leads per call) for the top N qualified leads only: subject + curiosity / problem / growth bodies; the page adds greeting, closing line and signature. |
| 8 | Quality control | In page: name, usable unique email, all variants present, ≤ 80 words, names the company, no buzzwords. |
| 9 | Excel export | SheetJS in page: Lead Master (with Import Status, Imported On, Updated On, Changes, Source File), AI Insights, Outreach, Rejected, Import Log, Run Summary. |

AI calls run on the viewer's own Claude account, two batches at a time, started by a click and stoppable; each finished
lead is saved immediately, so a stopped or rate-limited run continues where it left off.

## Goal: official business emails

The product is a list of **official business email addresses** (hotels, real-estate agencies and brokers, property
managers, polyclinics and medical centers, coworking spaces, business centers, training centers) in UAE, Saudi Arabia and
Lebanon. A person's name is optional. Shared company inboxes (`info@`, `reservations@`, `contact@`, `hello@`, `sales@`...)
are leads in their own right ("Company mailbox"); only `noreply@`, `privacy@`, `careers@`, `hr@`, `abuse@` and similar are
dropped ("Not for outreach"). A business with several addresses gives several leads. A business with no email yet is a
target for the email finder. The Excel export starts with a **Business Emails** sheet: every usable address, one row each,
whatever its score, with type, status, website, country, city, industry, phone, the evidence URL where it was seen, who
found it and when. Agents only keep emails the business itself published or an official register lists for it, and never
construct addresses from patterns.

Channels: official registers (ministries, regulators, chambers, syndicates, insurer networks), business websites,
directories and listings, Facebook/Instagram pages, hiring ads and openings (company discovery), hotels & venues (meeting-room guides), property listings (portals: the agency or owner behind each listing), and the email finder.

Strategy: companies first, emails second. Scouts record every named business (name, city, country, evidence page; website and phone when shown), with or without an email; the email finder then works through the ones still missing one. **Countries:** every agent works only in the countries chosen in the ICP Builder (`config/icp` `countries`), round-robin, or one country when the run message names it. The ICP Builder also produces `search_terms`, `search_terms_local` and per-country `cities`, which the planner uses (Rebuild ICP in step 2 to refresh them). All routines are manual: tick agents in the Run agents panel, then ask Claude to run the queued agents.
LinkedIn people search was retired. Page fetching is blocked in the default cloud environment, so agents work from search
results (registers often show emails there) until network access is widened.

## Discovery agents

Source: `agents/discovery.js` (copied into the page by `python3 agents/build.py`; edit it there, never in the HTML).

```
 planner (in the page)      scouts (scheduled routines)           inbox (live)        you
 ICP + cities + languages → linkedin · web · social · directories → inbox/<run> rows → review → merge → pipeline
 discovery/<channel> plans    hiring · news · decision-maker finder   learned + suggest
          ▲                                  │                                │
          └────────── strategist (weekly) ◄──┴── discovery/stats, known, targets (written by the page)
```

- **Planner** (`planQueries`, deterministic, "Plan queries" in step 3): per channel, tiered searches (1 plain city
  searches, 2 other languages and angles, 3 neighborhoods) in English, Arabic and French. Re-planning keeps progress and
  everything the strategist added (`mergePlan`).
- **Scouts** (six channels, Mon/Wed/Fri, staggered UTC slots): each picks its next queries (todo first, lowest tier first),
  triages every result (segment, country, name + title + company agree), reformulates weak searches (Arabic/French, no
  operators, neighborhood, synonym), skips companies already in the engine (`discovery/known`), rates confidence, and
  writes `inbox/<channel>-<time>` with rows (each with an evidence URL), `learned` reformulations and `suggest` ideas.
  They search only: no logins, never open LinkedIn or Facebook, no forms, no contacting anyone.
- **Decision-maker finder** (weekdays): reads `discovery/targets` (companies with no named contact, written by the page)
  and finds the owner or operator of each.
- **Strategist** (Mondays): reads plans, `discovery/stats` (rows, leads, qualified, with email per channel) and recent
  runs; retires searches with 2+ tries and no yield, adds variants of productive ones, promotes learned reformulations and
  suggestions, writes `discovery/strategy` (summary, health, guidance) that scouts read next run.
- **Page**: live inbox (`onSnapshot`), "Review and import" goes through the normal import preview (rows become New /
  Updated, source `Agent · <channel>`, evidence URL kept), company-only rows are kept as leads and replaced once a named
  person at that company is imported, runs are marked imported and come back if you undo the import.

Running them: nothing runs on a timer (all routines are disabled). Tick agents in the page's **Run agents** panel and press
Run: that writes a request to `discovery/runQueue` (page sandboxes cannot start routines), then tell Claude "run the queued
agents" and it starts them (scouts first, then the email finder, then the strategist) and marks the request. You can also
press **Run now** on a routine in claude.ai › Routines. Routines are named "ProHost discovery · …" and read their time from
the shell, never from memory. First live test (official registers): 8 searches, 6 attributed emails.

Documents: `discovery/{linkedin,web,social,directories,hiring,news}` (plans), `discovery/{known,targets,stats}` (page →
agents), `discovery/{enrich,strategy}` (agents), `discovery/runQueue` (page → Claude run requests), `inbox/*` (runs), `config/agents` (schedules shown in the page).
Each run is a Claude Code session on your account (usage counts against your plan). Agents run in the cloud environment's
network policy: with default settings they can search but not open arbitrary sites.

## Importing

- Formats: CSV / TSV / TXT (delimiter detected; UTF-8, UTF-16 and Windows-1252/1256 decoded), Excel (best sheet and
  header row detected, both changeable), JSON, JSON lines and nested API JSON (Apollo `people`, PDL `data`), pasted
  rows from Excel or Google Sheets, several files at once (queued). English, French and Arabic headers are recognised;
  unmatched columns are found by their values (emails, LinkedIn URLs, domains). Column choices are remembered per
  header layout (`config/mappings`).
- Same lead = same work email (role mailboxes and Apollo's locked placeholders excluded), LinkedIn profile, name +
  company, or name + domain. A matching lead is updated field by field; empty cells never erase data.
- Every import shows a preview first (new / updated / unchanged / duplicate rows / skipped, with old → new values),
  is logged in `meta/state.imports`, and can be undone until the next import or AI run.
- Each lead keeps `firstImportedAt`, `lastSeenAt` + file, `importStatus` (new / updated / unchanged), `updatedAt` and
  `changes` (old → new). A change to name, title, company, industry, size, location, keywords, description or signals
  marks its research and emails out of date (`aiStaleAt`): they leave the export and are redone on the next run.
- Clean-up on the way in: name casing, honorifics (Dr., Eng.) out of greetings, legal suffixes (S.A.L., LLC, FZE) out of
  email copy, canonical LinkedIn and website URLs, company domain from the work email, "Dubai, UAE" style locations,
  ranges like 51-200 or 1K-5K, ISO / French / Arabic country names.

## Saved data (artifact database)

`config/company`, `config/icp`, `config/outreach`, `config/mappings`, `meta/state` (import log), `leads/c0000…` (50
leads per document, only changed documents are rewritten, up to 20,000 leads), `ai/{leadKey}` (research + email bodies,
each with its own timestamp). Readable and writable only at Contributor level or above
(view-only shares see nothing). The first version was seeded with ProHost's real profile and ICP (from `public/`) and
12 fictional example leads (`.example` domains) that the first import replaces.

## Tests

`tests/` drives the real page in headless Chromium against an in-memory database and a scripted Claude: 102 flow checks and 162 agent checks over
every import path, merge and change tracking, undo, the research and email agents (including a renamed key, a buzzword
and a rate limit), QC, the verifier round-trip, the Excel workbook, a round-trip of the engine's own export, reload and
clean-up. `scale.mjs` imports and re-imports 10,000 leads.

```
cd marketing/lead-engine/tests
npm i --no-save xlsx@0.18.5 playwright
node make-files.js && node flow.mjs && node scale.mjs   # set CHROMIUM=/path/to/chrome if Playwright has no browser
```

## Limits

- An artifact page cannot call outside APIs or open websites, so Apollo / PDL / verifier steps are export → import.
  An Apollo API or PDL key must never be pasted into the page or its database.
- The ProHost website says the product is live in Lebanon only; the email agent is told never to imply it operates
  in a country outside the profile's `markets`. Update the profile before UAE / Saudi outreach makes local claims.
