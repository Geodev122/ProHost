"""Read-only snapshot of the Apple side for ProHost (no secrets printed)."""
import sys
sys.path.insert(0, __file__.rsplit("/", 1)[0])
from asc_api import ASC  # noqa: E402

BUNDLE = "app.geonajjar.prohost"
api = ASC()


def show(title, status, items):
    print(f"== {title} (HTTP {status})")
    for i in items:
        print("  ", i)


s, d = api.get("/v1/apps?limit=50")
apps = d.get("data", []) if s == 200 else []
show("apps", s, [f'{a["id"]} {a["attributes"].get("name")} {a["attributes"].get("bundleId")} sku={a["attributes"].get("sku")}' for a in apps] or [d])

s, d = api.get(f"/v1/bundleIds?filter[identifier]={BUNDLE}&include=bundleIdCapabilities&limit=10")
bids = d.get("data", []) if s == 200 else []
show("bundle ids", s, [f'{b["id"]} {b["attributes"]["identifier"]} {b["attributes"]["name"]} platform={b["attributes"]["platform"]}' for b in bids] or [d])
if s == 200:
    print("   capabilities:", sorted(i["attributes"]["capabilityType"] for i in d.get("included", []) if i["type"] == "bundleIdCapabilities"))

for a in apps:
    if a["attributes"].get("bundleId") == BUNDLE:
        s, d = api.get(f'/v1/builds?filter[app]={a["id"]}&sort=-uploadedDate&limit=5')
        show("latest builds", s, [f'v{b["attributes"].get("version")} processing={b["attributes"].get("processingState")} uploaded={b["attributes"].get("uploadedDate")} expired={b["attributes"].get("expired")}' for b in d.get("data", [])] or [d])
        # TestFlight groups and their testers (names and state only, no emails).
        s, d = api.get(f'/v1/betaGroups?filter[app]={a["id"]}&limit=20')
        show("tester groups", s, [f'{g["attributes"].get("name")} internal={g["attributes"].get("isInternalGroup")} testers-public-link={g["attributes"].get("publicLinkEnabled")}' for g in d.get("data", [])] or ["(none)"] if s == 200 else [d])
        if s == 200:
            for g in d.get("data", []):
                s2, d2 = api.get(f'/v1/betaGroups/{g["id"]}/betaTesters?limit=50')
                print(f'   group {g["attributes"].get("name")}: {len(d2.get("data", [])) if s2 == 200 else "HTTP " + str(s2)} tester(s), states={sorted({t["attributes"].get("state") for t in d2.get("data", [])}) if s2 == 200 else ""}')

s, d = api.get("/v1/certificates?limit=50")
show("certificates", s, [f'{c["id"]} {c["attributes"].get("certificateType")} {c["attributes"].get("displayName")} exp={c["attributes"].get("expirationDate")}' for c in d.get("data", [])] or [d])
s, d = api.get("/v1/devices?limit=50")
show("devices", s, [f'{x["attributes"].get("name")} {x["attributes"].get("platform")} {x["attributes"].get("status")}' for x in d.get("data", [])] or ["(none)"] if s == 200 else [d])
s, d = api.get("/v1/profiles?limit=50")
show("profiles", s, [f'{x["attributes"].get("name")} {x["attributes"].get("profileType")} {x["attributes"].get("profileState")}' for x in d.get("data", [])] or ["(none)"] if s == 200 else [d])
