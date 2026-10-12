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
| 3 | Lead discovery | You run the Apollo / People Data Labs search and import the export (CSV, Excel or PDL JSON). Columns are auto-mapped. The engine never invents contacts. |
| 4 | Email verification | In page: syntax, Apollo locked placeholders, disposable domains, role mailboxes, duplicates, personal / mismatched domains, plus the provider's status. Mailbox checks go through NeverBounce / ZeroBounce / MillionVerifier: download the CSV, verify, import the result file. |
| 5 | Lead research | Claude (fast model, 10 leads per call): signal, pain point, reason selected, summary, using only facts in the record. |
| 6 | Lead scoring | In page, instantly for every lead: ICP fit 40, industry 20, role 20, size 10, activity 10 (weights and threshold editable, default ≥ 75). |
| 7 | Personalized emails | Claude (balanced model, 5 leads per call) for the top N qualified leads only: subject + curiosity / problem / growth bodies; the page adds greeting, closing line and signature. |
| 8 | Quality control | In page: name, usable unique email, all variants present, ≤ 80 words, names the company, no buzzwords. |
| 9 | Excel export | SheetJS in page: Lead Master, AI Insights, Outreach, Rejected, Run Summary. |

AI calls run on the viewer's own Claude account, two batches at a time, started by a click and stoppable; each finished
lead is saved immediately, so a stopped or rate-limited run continues where it left off.

## Saved data (artifact database)

`config/company`, `config/icp`, `config/outreach`, `meta/state`, `leads/c0000…` (100 leads per document, up to
20,000 leads), `ai/{leadKey}` (research + email bodies). Readable and writable only at Contributor level or above
(view-only shares see nothing). The first version was seeded with ProHost's real profile and ICP (from `public/`) and
12 fictional example leads (`.example` domains) that the first import replaces.

## Limits

- An artifact page cannot call outside APIs or open websites, so Apollo / PDL / verifier steps are export → import.
  An Apollo API or PDL key must never be pasted into the page or its database.
- The ProHost website says the product is live in Lebanon only; the email agent is told never to imply it operates
  in a country outside the profile's `markets`. Update the profile before UAE / Saudi outreach makes local claims.
