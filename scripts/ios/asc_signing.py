"""Temporary App Store distribution signing for CI, through the App Store Connect API (Admin key).

  asc_signing.py setup    create a distribution certificate + App Store profile, write key.pem, cert.cer and the profile
  asc_signing.py cleanup  delete the profile and the certificate again (run in an always() step)

Files go to $RUNNER_TEMP/signing. Nothing secret is printed. Each run uses its own certificate (Apple allows only a few
distribution certificates per team), so cleanup must always run.
"""
import base64
import datetime
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from asc_api import ASC  # noqa: E402
from cryptography import x509  # noqa: E402
from cryptography.hazmat.primitives import hashes, serialization  # noqa: E402
from cryptography.hazmat.primitives.asymmetric import rsa  # noqa: E402
from cryptography.x509.oid import NameOID  # noqa: E402

BUNDLE = "app.geonajjar.prohost"
PROFILE_NAME = "ProHost AppStore CI"
WORK = Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "signing"
STATE = WORK / "state.json"


def fail(msg, body=None):
    print("::error::" + msg + (" " + json.dumps(body)[:500] if body else ""))
    sys.exit(1)


def setup():
    WORK.mkdir(parents=True, exist_ok=True)
    api = ASC()
    s, d = api.get(f"/v1/bundleIds?filter[identifier]={BUNDLE}&limit=10")
    ids = [b["id"] for b in d.get("data", []) if b["attributes"]["identifier"] == BUNDLE] if s == 200 else []
    if not ids:
        fail("bundle ID not found", d)
    bundle_id = ids[0]

    # leftovers of an earlier crashed run
    s, d = api.get("/v1/profiles?limit=200")
    for p in d.get("data", []) if s == 200 else []:
        if p["attributes"]["name"] == PROFILE_NAME:
            api.request("DELETE", f"/v1/profiles/{p['id']}")
    s, d = api.get("/v1/certificates?filter[certificateType]=DISTRIBUTION&limit=50")
    existing = d.get("data", []) if s == 200 else []
    for c in existing:
        if c["attributes"].get("displayName", "").startswith("ProHost CI"):
            api.request("DELETE", f"/v1/certificates/{c['id']}")
    print(f"distribution certificates before: {len(existing)}")

    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    csr = (x509.CertificateSigningRequestBuilder()
           .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "ProHost CI"),
                                    x509.NameAttribute(NameOID.COUNTRY_NAME, "US")]))
           .sign(key, hashes.SHA256()))
    csr_pem = csr.public_bytes(serialization.Encoding.PEM).decode()
    s, d = api.request("POST", "/v1/certificates", {"data": {"type": "certificates", "attributes": {
        "certificateType": "DISTRIBUTION", "csrContent": csr_pem}}})
    if s not in (200, 201):
        fail(f"certificate creation failed (HTTP {s})", d)
    cert = d["data"]
    (WORK / "cert.cer").write_bytes(base64.b64decode(cert["attributes"]["certificateContent"]))
    (WORK / "key.pem").write_bytes(key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.TraditionalOpenSSL, serialization.NoEncryption()))
    os.chmod(WORK / "key.pem", 0o600)

    s, d = api.request("POST", "/v1/profiles", {"data": {
        "type": "profiles",
        "attributes": {"name": PROFILE_NAME, "profileType": "IOS_APP_STORE"},
        "relationships": {
            "bundleId": {"data": {"type": "bundleIds", "id": bundle_id}},
            "certificates": {"data": [{"type": "certificates", "id": cert["id"]}]},
        }}})
    if s not in (200, 201):
        api.request("DELETE", f"/v1/certificates/{cert['id']}")
        fail(f"profile creation failed (HTTP {s})", d)
    prof = d["data"]
    uuid = prof["attributes"]["uuid"]
    dest = Path.home() / "Library/MobileDevice/Provisioning Profiles"
    dest.mkdir(parents=True, exist_ok=True)
    (dest / f"{uuid}.mobileprovision").write_bytes(base64.b64decode(prof["attributes"]["profileContent"]))
    STATE.write_text(json.dumps({"certificate": cert["id"], "profile": prof["id"], "uuid": uuid}))
    print(f"created certificate {cert['id']} ({cert['attributes'].get('name')}) and profile {PROFILE_NAME} ({uuid})")


def cleanup():
    if not STATE.exists():
        print("nothing to clean up")
        return
    st = json.loads(STATE.read_text())
    api = ASC()
    for kind in ("profile", "certificate"):
        path = "profiles" if kind == "profile" else "certificates"
        s, d = api.request("DELETE", f"/v1/{path}/{st[kind]}")
        print(f"deleted {kind} {st[kind]}: HTTP {s}")
    prof = Path.home() / "Library/MobileDevice/Provisioning Profiles" / f"{st['uuid']}.mobileprovision"
    prof.unlink(missing_ok=True)


if __name__ == "__main__":
    {"setup": setup, "cleanup": cleanup}[sys.argv[1]]()
