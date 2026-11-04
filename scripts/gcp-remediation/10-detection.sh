#!/usr/bin/env bash
# Alerting + audit logs. Additive only.
set -uo pipefail; . "$(dirname "$0")/lib.sh"
: "${ALERT_EMAIL:?set ALERT_EMAIL}"
CH=$(g beta monitoring channels list --filter="displayName=\"ProHost owner email\"" --format='value(name)' 2>/dev/null | head -1)
if [ -z "$CH" ]; then
  if [ "${DRY_RUN:-0}" = 1 ]; then echo "[dry-run] create email channel -> $ALERT_EMAIL"; CH=DRYCHANNEL
  else CH=$(g beta monitoring channels create --display-name="ProHost owner email" --type=email --channel-labels=email_address="$ALERT_EMAIL" --format='value(name)'); fi
fi
echo "channel: $CH"
# Log-based alerts (audit-log events that should never be silent)
mk() { # name, filter, doc
  if [ "${DRY_RUN:-0}" = 1 ]; then echo "[dry-run] alert '$1': $2"; return; fi
  cat > "$BACKUP_DIR/pol-$1.json" <<J
{"displayName":"$1","combiner":"OR","conditions":[{"displayName":"$1","conditionMatchedLog":{"filter":$(python3 -c 'import json,sys;print(json.dumps(sys.argv[1]))' "$2")}}],
 "alertStrategy":{"notificationRateLimit":{"period":"3600s"}},"notificationChannels":["$CH"],
 "documentation":{"content":"$3","mimeType":"text/markdown"},"enabled":true}
J
  g alpha monitoring policies create --policy-from-file="$BACKUP_DIR/pol-$1.json" --format='value(name)'
}
mk "SA-key-created" 'protoPayload.methodName="google.iam.admin.v1.CreateServiceAccountKey"' "A service account key was created."
mk "Project-IAM-changed" 'protoPayload.methodName="SetIamPolicy" AND protoPayload.serviceName="cloudresourcemanager.googleapis.com"' "Project IAM policy changed."
mk "VertexAI-or-Gemini-use" 'protoPayload.serviceName=("aiplatform.googleapis.com" OR "generativelanguage.googleapis.com") AND protoPayload.methodName:("Create" OR "Predict" OR "Generate")' "Gen-AI API activity; not expected for ProHost."
mk "Compute-GPU-or-VM-created" 'protoPayload.serviceName="compute.googleapis.com" AND protoPayload.methodName:"insert"' "Compute resource created; none expected."
mk "ApiKey-created-or-changed" 'protoPayload.serviceName="apikeys.googleapis.com" AND protoPayload.methodName:("CreateKey" OR "UpdateKey")' "API key created or changed."
# Data Access audit logs for IAM-sensitive services
if [ "${DRY_RUN:-0}" = 1 ]; then echo "[dry-run] enable DATA_READ/WRITE audit logs for iam, secretmanager, cloudkms"; else
 g projects get-iam-policy $PROJECT --format=json > "$BACKUP_DIR/iam-before-auditcfg.json"
 python3 -I - "$BACKUP_DIR/iam-before-auditcfg.json" > "$BACKUP_DIR/iam-with-auditcfg.json" <<'PY'
import sys,json
p=json.load(open(sys.argv[1]))
have={a["service"]:a for a in p.get("auditConfigs",[])}
for s in ["iam.googleapis.com","secretmanager.googleapis.com","cloudresourcemanager.googleapis.com","aiplatform.googleapis.com"]:
    a=have.setdefault(s,{"service":s,"auditLogConfigs":[]})
    t={x["logType"] for x in a["auditLogConfigs"]}
    for lt in ["ADMIN_READ","DATA_READ","DATA_WRITE"]:
        if lt not in t: a["auditLogConfigs"].append({"logType":lt})
p["auditConfigs"]=list(have.values()); print(json.dumps(p))
PY
 g projects set-iam-policy $PROJECT "$BACKUP_DIR/iam-with-auditcfg.json" --format='value(etag)'
fi
# Log retention
run logging buckets update _Default --location=global --retention-days=90
