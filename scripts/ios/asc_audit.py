"""Read-only, detailed App Store Connect audit for ProHost (product page, media, subscriptions, offers, legal links).
Prints one line per item: OK / MISSING / CHECK / INFO, plus a per-subscription block (prices in USA, territory counts,
introductory offers) so the App Store side can be compared with Google Play (Maintenance `playcatalog`).

    python3 scripts/ios/asc_audit.py
"""
import json
import os
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from asc_api import ASC  # noqa: E402

ROOT = __file__.rsplit("/", 3)[0]
META = json.load(open(os.path.join(ROOT, "ios/store/metadata.json"), encoding="utf-8"))
BUNDLE = META["bundleId"]
api = ASC()


def get(path):
    s, d = api.get(path)
    return d if s == 200 else None


def get_all(path):
    out, url, inc = [], path, []
    while url:
        s, d = api.request("GET", url)
        if s != 200:
            break
        out += d.get("data", [])
        inc += d.get("included", [])
        url = (d.get("links") or {}).get("next")
    return out, inc


def row(state, label, value=""):
    print(f"{state:<8}{label}{': ' + str(value) if value != '' else ''}")


A = lambda o, k: (o or {}).get("attributes", {}).get(k)

app = (get(f"/v1/apps?filter[bundleId]={BUNDLE}&limit=1") or {}).get("data", [None])[0]
if not app:
    row("MISSING", "app record"); sys.exit(0)
APP = app["id"]

# ---- App information (per locale)
print("== App information")
infos = get(f"/v1/apps/{APP}/appInfos?include=appInfoLocalizations&limit=5") or {}
for loc in [i for i in infos.get("included", []) if i["type"] == "appInfoLocalizations"]:
    l = A(loc, "locale")
    for k in ("name", "subtitle", "privacyPolicyUrl"):
        row("OK" if A(loc, k) else "MISSING", f"[{l}] {k}", (A(loc, k) or "")[:70])
    row("OK" if A(loc, "privacyChoicesUrl") else "INFO", f"[{l}] privacyChoicesUrl (optional)", A(loc, "privacyChoicesUrl") or "not set")
eula = get(f"/v1/apps/{APP}/endUserLicenseAgreement")
row("INFO", "custom EULA", "set" if eula and eula.get("data") else "none (Apple's standard EULA applies)")

# ---- Version, localizations, media
print("== Version 1.0")
vers = get(f"/v1/apps/{APP}/appStoreVersions?limit=5&filter[platform]=IOS") or {}
version = next((v for v in vers.get("data", []) if A(v, "appStoreState") in ("PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED")), None)
if version:
    vid = version["id"]
    row("INFO", "version", f'{A(version, "versionString")} state={A(version, "appStoreState")} release={A(version, "releaseType")}')
    build = get(f"/v1/appStoreVersions/{vid}/build")
    row("OK" if build and build.get("data") else "MISSING", "build", A((build or {}).get("data"), "version") or "")
    phased = get(f"/v1/appStoreVersions/{vid}/appStoreVersionPhasedRelease")
    row("OK" if phased and phased.get("data") else "INFO", "phased release (7 days)", "on" if phased and phased.get("data") else "off")
    locs, _ = get_all(f"/v1/appStoreVersions/{vid}/appStoreVersionLocalizations?limit=50")
    for loc in locs:
        l = A(loc, "locale")
        desc = A(loc, "description") or ""
        for k in ("description", "keywords", "promotionalText", "supportUrl", "marketingUrl"):
            row("OK" if A(loc, k) else "MISSING", f"[{l}] {k}", len(A(loc, k) or "") if k in ("description", "keywords", "promotionalText") else (A(loc, k) or ""))
        has_terms = "pro-host.tech/terms" in desc or "apple.com/legal/internet-services/itunes/dev/stdeula" in desc
        row("OK" if has_terms else "MISSING", f"[{l}] Terms of Use (EULA) link in the description (Guideline 3.1.2)")
        row("OK" if "pro-host.tech/privacy" in desc else "CHECK", f"[{l}] privacy policy link in the description")
        sets, inc = get_all(f"/v1/appStoreVersionLocalizations/{loc['id']}/appScreenshotSets?include=appScreenshots&limit=50")
        for s in sets:
            n = len(((s.get("relationships") or {}).get("appScreenshots") or {}).get("data") or [])
            row("OK" if n else "MISSING", f"[{l}] screenshots {A(s, 'screenshotDisplayType')}", n)
        if not sets:
            row("MISSING", f"[{l}] screenshots")
        prev, _ = get_all(f"/v1/appStoreVersionLocalizations/{loc['id']}/appPreviewSets?limit=50")
        row("INFO", f"[{l}] app preview videos (optional)", len(prev))
else:
    row("MISSING", "editable App Store version")

# ---- Subscriptions
print("== Subscriptions")
groups, ginc = get_all(f"/v1/apps/{APP}/subscriptionGroups?include=subscriptionGroupLocalizations&limit=10")
for g in groups:
    row("INFO", "group", A(g, "referenceName"))
    glocs = [i for i in ginc if i["type"] == "subscriptionGroupLocalizations"]
    for gl in glocs:
        row("OK", f"group localization [{A(gl, 'locale')}]", f"{A(gl, 'name')} / app name: {A(gl, 'customAppName') or '-'}")
    have = {A(gl, "locale") for gl in glocs}
    for l in ("en-US", "ar-SA"):
        if l not in have:
            row("MISSING", f"group localization [{l}]")
    subs, _ = get_all(f"/v1/subscriptionGroups/{g['id']}/subscriptions?limit=50")
    for s in sorted(subs, key=lambda x: A(x, "groupLevel") or 0):
        sid, pid = s["id"], A(s, "productId")
        print(f"-- {pid}  period={A(s, 'subscriptionPeriod')} level={A(s, 'groupLevel')} state={A(s, 'state')} "
              f"familySharable={A(s, 'familySharable')}")
        sl, _ = get_all(f"/v1/subscriptions/{sid}/subscriptionLocalizations?limit=50")
        locs = {A(x, "locale"): x for x in sl}
        for l in ("en-US", "ar-SA"):
            x = locs.get(l)
            row("OK" if x and A(x, "name") and A(x, "description") else "MISSING", f"   localization [{l}]",
                f"{A(x, 'name')} — {A(x, 'description')}" if x else "")
        us, uinc = get_all(f"/v1/subscriptions/{sid}/prices?filter[territory]=USA&include=subscriptionPricePoint&limit=10")
        pp = [i for i in uinc if i["type"] == "subscriptionPricePoints"]
        row("OK" if pp else "MISSING", "   price USA", ", ".join(A(p, "customerPrice") for p in pp))
        allp, _ = get_all(f"/v1/subscriptions/{sid}/prices?limit=200")
        terr = len({(((p.get("relationships") or {}).get("territory") or {}).get("data") or {}).get("id") for p in allp} - {None})
        row("OK" if allp else "MISSING", "   priced territories", terr or len(allp))
        offers, oinc = get_all(f"/v1/subscriptions/{sid}/introductoryOffers?include=subscriptionPricePoint,territory&limit=200")
        if offers:
            kinds = {}
            for o in offers:
                key = (A(o, "offerMode"), A(o, "duration"), A(o, "numberOfPeriods"))
                kinds[key] = kinds.get(key, 0) + 1
            usa = next((o for o in offers if (((o.get("relationships") or {}).get("territory") or {}).get("data") or {}).get("id") == "USA"), None)
            usa_pp = None
            if usa:
                ref = (((usa.get("relationships") or {}).get("subscriptionPricePoint") or {}).get("data") or {}).get("id")
                usa_pp = next((A(i, "customerPrice") for i in oinc if i["type"] == "subscriptionPricePoints" and i["id"] == ref), None)
            row("INFO", "   introductory offers", "; ".join(f"{m} {d} x{n}: {c} territories" for (m, d, n), c in kinds.items())
                + (f"; USA price {usa_pp}" if usa_pp else ""))
        else:
            row("INFO", "   introductory offers", "none")
        promos, _ = get_all(f"/v1/subscriptions/{sid}/promotionalOffers?limit=50")
        row("INFO", "   promotional offers", len(promos))
        shot = get(f"/v1/subscriptions/{sid}/appStoreReviewScreenshot")
        row("OK" if shot and shot.get("data") else "MISSING", "   review screenshot")
        row("OK" if A(s, "reviewNote") else "MISSING", "   review note")
        imgs, _ = get_all(f"/v1/subscriptions/{sid}/images?limit=10")
        row("INFO", "   promotional image 1024x1024 (optional, for App Store promotion)", len(imgs))
print("== Not readable by the API (check in App Store Connect): App Privacy, agreements/tax/banking, trader status, "
      "subscriptions selected on the version page, accessibility nutrition labels (optional).")
