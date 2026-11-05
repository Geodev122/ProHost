# GCP remediation scripts (project prohost-f766f, 4 Nov 2026)
Run with a credential that has the needed roles; every mutating call goes through `run` in `lib.sh` (`DRY_RUN=1` prints instead of running).
`lib.sh` strips `CLOUDSDK_AUTH_ACCESS_TOKEN` so the activated service account is used. Order: 00-preflight (backup, not committed) → 20 → 10 → 40 (create group SAs) →
50 (Editor off the default compute SA; `STEP=b`) → 41-migrate/99-rollback only work while function images still exist (see docs/GCP_IAM.md).
Executed on 4 Nov: 00, 20, 10 (alerts created via REST because `gcloud alpha` is unavailable), 40a, 50a+50b. Not executed: 30, 60, 70, 80 (not written yet).
`99-rollback-editor.sh` restores Editor on the default compute SA. Never commit backups or keys.

Update 5 Nov 2026: executed also 41-migrate (canary only, see docs/OPS_LOG.md), 50-privilege-trim (steps a and b). Per-service identities are applied through source deploys (`runAs(group)` in functions/src/lib/runtimeIdentity.ts), not 41-migrate.
