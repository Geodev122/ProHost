"""Read-only App Store readiness report for ProHost: what App Store Connect already has and what is still empty.
No secrets, no emails, no personal data printed. Lines start with OK / MISSING / INFO so a log tail is enough.

    python3 scripts/ios/asc_store_status.py
"""
import sys
sys.path.insert(0, __file__.rsplit("/", 1)[0])
from asc_api import ASC  # noqa: E402

BUNDLE = "app.geonajjar.prohost"
api = ASC()


def get(path):
    status, data = api.get(path)
    return (data if status == 200 else None), status


def line(ok, label, value=""):
    print(f'{"OK     " if ok else "MISSING"} {label}{": " + str(value) if value != "" else ""}')


def info(label, value=""):
    print(f"INFO    {label}: {value}")


def attr(obj, key):
    return (obj or {}).get("attributes", {}).get(key)


d, s = get(f"/v1/apps?filter[bundleId]={BUNDLE}&limit=5")
apps = (d or {}).get("data", [])
if not apps:
    line(False, "app record for " + BUNDLE, f"HTTP {s} (create it in App Store Connect > Apps > +)")
    sys.exit(0)
app = apps[0]
app_id = app["id"]
info("app", f'{attr(app, "name")} sku={attr(app, "sku")} primaryLocale={attr(app, "primaryLocale")}')
line(bool(attr(app, "contentRightsDeclaration")), "content rights declaration", attr(app, "contentRightsDeclaration") or "")
line(bool(attr(app, "subscriptionStatusUrl")), "App Store Server Notifications URL (production)", "set" if attr(app, "subscriptionStatusUrl") else "")
line(bool(attr(app, "subscriptionStatusUrlForSandbox")), "App Store Server Notifications URL (sandbox)", "set" if attr(app, "subscriptionStatusUrlForSandbox") else "")

# Bundle ID capabilities
d, s = get(f"/v1/bundleIds?filter[identifier]={BUNDLE}&include=bundleIdCapabilities&limit=5")
caps = sorted(i["attributes"]["capabilityType"] for i in (d or {}).get("included", []) if i["type"] == "bundleIdCapabilities")
info("bundle capabilities", caps)
for cap in ("APPLE_ID_AUTH", "PUSH_NOTIFICATIONS", "ASSOCIATED_DOMAINS", "IN_APP_PURCHASE"):
    line(cap in caps, "capability " + cap)

# App info (name, subtitle, categories, privacy URL, age rating)
d, s = get(f"/v1/apps/{app_id}/appInfos?include=primaryCategory,secondaryCategory,ageRatingDeclaration,appInfoLocalizations&limit=5")
for ai in (d or {}).get("data", []):
    info("app info", f'state={attr(ai, "appStoreState") or attr(ai, "state")} ageRating={attr(ai, "appStoreAgeRating")}')
    rel = ai.get("relationships", {})
    line(bool((rel.get("primaryCategory", {}).get("data") or {}).get("id")), "primary category", (rel.get("primaryCategory", {}).get("data") or {}).get("id", ""))
    info("secondary category", (rel.get("secondaryCategory", {}).get("data") or {}).get("id", "(none)"))
    for inc in (d or {}).get("included", []):
        if inc["type"] == "appInfoLocalizations":
            a = inc["attributes"]
            loc = a.get("locale")
            line(bool(a.get("name")), f"[{loc}] name", a.get("name") or "")
            line(bool(a.get("subtitle")), f"[{loc}] subtitle", a.get("subtitle") or "")
            line(bool(a.get("privacyPolicyUrl")), f"[{loc}] privacy policy URL", a.get("privacyPolicyUrl") or "")
        if inc["type"] == "ageRatingDeclarations":
            answered = {k: v for k, v in inc["attributes"].items() if v is not None}
            line(len(answered) > 3, "age rating questionnaire", f"{len(answered)} answers")
    break

# Versions (description, keywords, URLs, review detail, screenshots, build)
d, s = get(f"/v1/apps/{app_id}/appStoreVersions?limit=3&include=appStoreVersionLocalizations,appStoreReviewDetail,build")
versions = (d or {}).get("data", [])
if not versions:
    line(False, "App Store version", f"HTTP {s}")
included = (d or {}).get("included", [])
for v in versions[:1]:
    info("version", f'{attr(v, "versionString")} state={attr(v, "appStoreState") or attr(v, "appVersionState")} platform={attr(v, "platform")} releaseType={attr(v, "releaseType")}')
    line(bool(attr(v, "copyright")), "copyright", attr(v, "copyright") or "")
    line(bool((v.get("relationships", {}).get("build", {}).get("data") or {}).get("id")), "build attached to the version")
    for inc in included:
        if inc["type"] == "appStoreVersionLocalizations":
            a = inc["attributes"]
            loc = a.get("locale")
            for key in ("description", "keywords", "supportUrl", "marketingUrl", "promotionalText"):
                val = a.get(key) or ""
                line(bool(val), f"[{loc}] {key}", f"{len(val)} chars" if key in ("description", "keywords", "promotionalText") else val)
            sd, ss = get(f'/v1/appStoreVersionLocalizations/{inc["id"]}/appScreenshotSets?include=appScreenshots&limit=20')
            sets = (sd or {}).get("data", [])
            counts = {attr(x, "screenshotDisplayType"): len((x.get("relationships", {}).get("appScreenshots", {}).get("data") or [])) for x in sets}
            line(any(c >= 3 for c in counts.values()), f"[{loc}] screenshots", counts or "none")
        if inc["type"] == "appStoreReviewDetails":
            a = inc["attributes"]
            line(bool(a.get("contactEmail")) and bool(a.get("contactPhone")), "review contact (name, phone, email)", "set" if a.get("contactEmail") else "")
            line(a.get("demoAccountRequired") is False or bool(a.get("demoAccountName")), "review demo account", "set" if a.get("demoAccountName") else "")
            line(bool(a.get("notes")), "review notes", f'{len(a.get("notes") or "")} chars')
    if not any(i["type"] == "appStoreReviewDetails" for i in included):
        line(False, "review details (contact, demo account, notes)")

# Subscriptions
d, s = get(f"/v1/apps/{app_id}/subscriptionGroups?include=subscriptions&limit=10")
groups = (d or {}).get("data", [])
line(bool(groups), "subscription group", [attr(g, "referenceName") for g in groups] or f"HTTP {s}")
for inc in (d or {}).get("included", []):
    if inc["type"] == "subscriptions":
        a = inc["attributes"]
        info("subscription", f'{a.get("productId")} period={a.get("subscriptionPeriod")} state={a.get("state")} level={a.get("groupLevel")}')

# Price / availability
d, s = get(f"/v1/apps/{app_id}/appPriceSchedule")
line(d is not None and bool((d or {}).get("data")), "app price schedule (free app)", "" if d else f"HTTP {s}")
d, s = get(f"/v1/apps/{app_id}/appAvailabilityV2")
line(d is not None, "availability (countries)", "" if d else f"HTTP {s}")

# Builds and testers
d, s = get(f"/v1/builds?filter[app]={app_id}&sort=-uploadedDate&limit=3")
builds = (d or {}).get("data", [])
line(bool(builds), "builds uploaded", [f'{attr(b, "version")}:{attr(b, "processingState")}:encryption={attr(b, "usesNonExemptEncryption")}' for b in builds])
d, s = get(f"/v1/betaGroups?filter[app]={app_id}&limit=20")
info("TestFlight groups", [f'{attr(g, "name")}(internal={attr(g, "isInternalGroup")})' for g in (d or {}).get("data", [])])
d, s = get(f"/v1/apps/{app_id}/betaAppReviewDetail")
line(d is not None and bool(attr((d or {}).get("data"), "contactEmail")), "TestFlight beta review contact")
d, s = get(f"/v1/apps/{app_id}/betaAppLocalizations?limit=10")
line(bool((d or {}).get("data")), "TestFlight test information (what to test, feedback email)")
print("NOTE    Not readable by this API (check by hand): Agreements, Tax and Banking; App Privacy answers; trader status.")
