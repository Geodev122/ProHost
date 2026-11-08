"""Minimal App Store Connect API client for CI (JWT ES256). Reads ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8 from the environment.
Never prints the key. Usage: from asc_api import ASC; api = ASC(); api.get('/v1/apps')."""
import json
import os
import re
import textwrap
import time
import urllib.error
import urllib.request

import jwt

BASE = "https://api.appstoreconnect.apple.com"


def _pem(raw: str) -> str:
    raw = raw.strip().replace("\\n", "\n").replace("\r", "")
    if "BEGIN" in raw:
        return raw + "\n"
    body = re.sub(r"\s+", "", raw)
    return "-----BEGIN PRIVATE KEY-----\n" + "\n".join(textwrap.wrap(body, 64)) + "\n-----END PRIVATE KEY-----\n"


class ASC:
    def __init__(self):
        self.key_id = os.environ["ASC_KEY_ID"]
        self.issuer = os.environ["ASC_ISSUER_ID"]
        self.key = _pem(os.environ["ASC_KEY_P8"])

    def token(self) -> str:
        now = int(time.time())
        return jwt.encode({"iss": self.issuer, "iat": now, "exp": now + 900, "aud": "appstoreconnect-v1"},
                          self.key, algorithm="ES256", headers={"kid": self.key_id, "typ": "JWT"})

    def request(self, method: str, path: str, body=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(BASE + path if path.startswith("/") else path, data=data, method=method,
                                     headers={"Authorization": "Bearer " + self.token(), "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                raw = r.read()
                return r.status, (json.loads(raw) if raw else {})
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", "ignore")
            try:
                return e.code, json.loads(raw)
            except Exception:
                return e.code, {"raw": raw[:400]}

    def get(self, path):
        return self.request("GET", path)
