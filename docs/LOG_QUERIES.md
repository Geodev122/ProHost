# Log queries for project prohost-f766f (Cloud Logging > Logs Explorer)

Paste a query into the Logs Explorer query box, set the time range (last 24 hours or 7 days) and press **Run query**.
Tested on 6 Nov 2026 against the real logs: the 7-day run found the igDmPoll/igCommentPoll errors, the 4 Nov "Ready condition False" revisions and every IAM refusal.
Note: in the Logging query language **OR binds tighter than AND**, so every OR group below is wrapped in parentheses. Keep them.
Tip: Summary fields to show (Logs Explorer > Fields > Edit summary fields): `severity`, `resource.labels.service_name`, `httpRequest.status`, `jsonPayload.message`, `protoPayload.methodName`.

## A. ALL ERRORS (main query)
```
(
  severity>=WARNING
  OR (resource.type="cloud_run_revision" AND httpRequest.status>=500)
  OR (resource.type="cloud_run_revision" AND httpRequest.status=429)
  OR (protoPayload.@type="type.googleapis.com/google.cloud.audit.AuditLog" AND protoPayload.status.code>0)
  OR textPayload:("PERMISSION_DENIED" OR "RESOURCE_EXHAUSTED" OR "DEADLINE_EXCEEDED" OR "Quota Exceeded" OR "Memory limit" OR "Uncaught" OR "Unhandled" OR "FATAL")
  OR jsonPayload.message:("PERMISSION_DENIED" OR "RESOURCE_EXHAUSTED" OR "DEADLINE_EXCEEDED" OR "Quota Exceeded" OR "Unhandled" OR "failed")
)
AND NOT jsonPayload.message="app_check_unverified"
AND NOT textPayload:"Default STARTUP TCP probe"
```
Optional extra line to hide the temporary admin account's own refused calls: `AND NOT protoPayload.authenticationInfo.principalEmail="claude-remediation@prohost-f766f.iam.gserviceaccount.com"`

## B. App errors only (functions and Cloud Run, no IAM noise)
```
resource.type="cloud_run_revision"
AND (severity>=ERROR OR httpRequest.status>=500)
AND NOT jsonPayload.message="app_check_unverified"
```

## C. Permission and identity problems (who was refused what)
```
protoPayload.@type="type.googleapis.com/google.cloud.audit.AuditLog"
AND (protoPayload.status.code=7 OR protoPayload.status.code=16 OR protoPayload.status.message:"PERMISSION_DENIED")
```
After an identity change (a `runAs(...)` batch), look here for a `run-*` principal: `protoPayload.authenticationInfo.principalEmail:"run-"`.

## D. Deploy and revision problems
```
(resource.type="cloud_run_revision" AND protoPayload.status.message:"Ready condition status changed to False")
OR (resource.type="build" AND severity>=ERROR)
OR (protoPayload.serviceName="cloudfunctions.googleapis.com" AND protoPayload.status.code>0)
```

## E. Schedules, triggers and purchase notifications failing to deliver
```
(resource.type="cloud_scheduler_job" AND severity>=WARNING)
OR (resource.type="pubsub_subscription" AND severity>=WARNING)
OR (resource.type="cloud_run_revision" AND resource.labels.service_name=("playbillingrtdn" OR "retrypendingplayactivations" OR "billingsyncjob" OR "expirepackages") AND severity>=ERROR)
```

## Reading them from the command line (what Claude does)
`env -u CLOUDSDK_AUTH_ACCESS_TOKEN gcloud logging read '<query>' --project=prohost-f766f --freshness=7d --limit=2000 --format=json` (see docs/GCP_IAM.md "Sandbox notes"). The Maintenance task `logerrors` (hours in `code`) gives a grouped summary through GitHub Actions.
