# Appeal addendum (DRAFT, NOT SENT) - ticket 35DQMHFCOIBXT6BBR6KMUFHCXI
Owner decides if/when to send. Nothing here has been submitted.

Remediation completed 7 Nov 2026 on prohost-f766f:
- Compromised key e5b6952d... of prohost-f766f@appspot disabled (7 Nov).
- aiplatform.googleapis.com disabled; the app has no Vertex/Gemini text provider (docs/GEMINI_USE.md).
- Default compute SA lost Editor (4 Nov); workloads moved to six least-privilege group service accounts.
- Data Access audit logs, owner-email alerts (key use, AI API enable), Firestore PITR/backups on.
- Suspicious sign-up account disabled and flagged (data kept).
- Abuse traffic seen from data-center IPs (RackNerd/ColoCrossing, Zappie Host); abuse reports drafted.
- The owner's own Oct 25 GPU jobs (marketing renders) ran on a different provider since; AI-presenter pipeline removed.
