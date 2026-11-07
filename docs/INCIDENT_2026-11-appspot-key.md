# Incident: unauthorized use of the App Engine default service account key (Oct to Nov 2026)

Evidence only; no secrets. Source: Cloud Audit Logs of project prohost-f766f (queries in docs/LOG_QUERIES.md), registry lookups through the Maintenance task `ipwhois`.
Account: `prohost-f766f@appspot.gserviceaccount.com` (role Editor), user-managed key `e5b6952d1fe7c81e72085d3bf174b27035ca51b1`, created 5 Oct 2026. **The key was still enabled on 7 Nov** (owner decision of 5 Nov: keep; being reconsidered, OWNER_TODO #27).

## Who the addresses are (all data-center servers, not homes, not Meta/GitHub/Google/Claude)
| Address | Registered to / location | Client seen |
|---|---|---|
| 23.95.170.108 | RackNerd LLC (ColoCrossing network, AS36352), Buffalo NY, reverse DNS 23-95-170-108-host.colocrossing.com | python-requests 2.31.0 (29 Oct), python-httpx 0.28.1 (5 Nov) |
| 185.121.179.67 | WEYRO LTD / Zappie Host (AS214716), Frankfurt DE | Windows-browser user agent (2 Nov) |
| 198.23.132.121 | RackNerd LLC (ColoCrossing network, AS36352), Dallas TX, reverse DNS 198-23-132-121-host.colocrossing.com | python-httpx 0.26.0 and Python-urllib 3.12 (6 Nov) |

## Timeline (UTC)
- 5 Oct 09:57: key first used from a Google-internal address by a Node.js client (`google-api-nodejs-client`); this is the only use that fits the owner's own tooling.
- 29 Oct 08:15: 23.95.170.108, Service Usage calls (4).
- 2 Nov 05:21: 185.121.179.67, `EnableService` aiplatform (2).
- 5 Nov 08:51 to 08:55: 23.95.170.108 tried `GenerateAccessToken` on other service accounts and `AccessSecretVersion` on all three secrets (BOOTSTRAP_SECRET, EMAIL_VERIFICATION_SECRET, SMTP password): **all refused**.
- 6 Nov 13:07 to 13:12: 198.23.132.121 ran a script: GetProject, ListServiceAccounts, `EnableService` aiplatform (Vertex AI re-enabled), `GenerateAccessToken` against compute, appspot, run-admin, firebase-adminsdk, run-ig and meta-ads-play (**all refused**), `GenerateContent` on Gemini (**refused: "Request rejected in accordance with our terms of use and policies"**). The `VertexAI-or-Gemini-use` alert emailed the owner at 13:10.
- Never touched: public endpoints (no hits in Cloud Run request logs), Firestore data, secrets, other accounts. Vertex AI, however, was left enabled by the actor.

## Assessment
Automated credential-abuse tooling aimed at obtaining free Gemini/Vertex AI access with a leaked service-account key ("LLM jacking"), run from rented VPS servers. Not the owner, not Instagram/Meta (separate ASN, cannot call Google APIs with this key), not GitHub Actions, not Claude (Claude's calls come from Google-internal addresses and use `claude-remediation` / `claude-readonly`, never this key). The person behind it is unknown; only the hosting providers can identify the renter.
How the key leaked is unknown. It is not in the repo (searched working tree and available history). Check: any place the JSON file was saved or pasted (Drive, chat, laptop sync folders, other repos, CI logs).

## Actions recommended
1. Disable (then delete after a week) key `e5b6952d...`; remove Editor from the account; re-disable `aiplatform`, `cloudaicompanion`, `geminicloudassist`, `firebasevertexai` if not needed.
2. Add this evidence to the Google appeal (ticket 35DQMHFCOIBXT6BBR6KMUFHCXI): the activity is unauthorized, attributable to third-party servers, and was blocked by Google's own policy check.
3. Report abuse with the log excerpts to RackNerd (abuse contact in the RDAP record of handle RL-872) and Zappie Host / WEYRO.
4. Keep the alert `VertexAI-or-Gemini-use` and the 3-hourly watch running.
