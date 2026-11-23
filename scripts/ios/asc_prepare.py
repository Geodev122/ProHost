"""Fill App Store Connect for ProHost from ios/store/metadata.json. Idempotent; prints a plan and changes nothing unless --apply.

    python3 scripts/ios/asc_prepare.py [--apply] [--only app,categories,info,version,capability,price,availability,nochina,build,age,testflight,subscriptions,review,betareview,tester,offers,screenshots,subreview]

Environment (optional, never printed): ASC_COPYRIGHT ("2026 Legal Name"), REVIEW_FIRST_NAME, REVIEW_LAST_NAME, REVIEW_PHONE, REVIEW_EMAIL,
REVIEW_DEMO_USER, REVIEW_DEMO_PASSWORD, REVIEW_NOTES, SUBSCRIPTION_YEARLY_USD. Sections whose inputs are missing are skipped with a note.
Nothing here submits the app for review or makes anything public.
"""
import argparse
import json
import os
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from asc_api import ASC  # noqa: E402

ROOT = __file__.rsplit("/", 3)[0]
META = json.load(open(os.path.join(ROOT, "ios/store/metadata.json"), encoding="utf-8"))
api = ASC()
ap = argparse.ArgumentParser()
ap.add_argument("--apply", action="store_true")
ap.add_argument("--only", default="")
args = ap.parse_args()
ONLY = {s.strip() for s in args.only.split(",") if s.strip()}
failures = []


def want(section):
    return not ONLY or section in ONLY


def get(path):
    status, data = api.get(path)
    return data if status == 200 else None


def get_all(path):
    out, url = [], path
    while url:
        status, data = api.request("GET", url)
        if status != 200:
            return out
        out += data.get("data", [])
        url = (data.get("links") or {}).get("next")
    return out


def write(method, path, body, label):
    if not args.apply:
        print(f"PLAN    {label}")
        return None
    status, data = api.request(method, path, body)
    if status in (200, 201, 204):
        print(f"DONE    {label}")
        return data
    errors = "; ".join(f'{e.get("code")}: {e.get("detail")}' for e in (data or {}).get("errors", [])) or str(data)[:300]
    print(f"FAILED  {label} (HTTP {status}) {errors}")
    failures.append(label)
    return None


def same(label):
    print(f"OK      {label}")


def rel(kind, ident):
    return {"data": {"type": kind, "id": ident}}


apps = (get(f'/v1/apps?filter[bundleId]={META["bundleId"]}&limit=5') or {}).get("data", [])
if not apps:
    sys.exit("No app record for " + META["bundleId"])
app = apps[0]
APP = app["id"]

# ---- app-level attributes
if want("app"):
    if app["attributes"].get("contentRightsDeclaration") != META["contentRightsDeclaration"]:
        write("PATCH", f"/v1/apps/{APP}", {"data": {"type": "apps", "id": APP, "attributes": {"contentRightsDeclaration": META["contentRightsDeclaration"]}}},
              "content rights: does not use third-party content")
    else:
        same("content rights")

# ---- App Store Server Notifications V2 (production + sandbox) -> the appStoreNotifications function
if want("notifications"):
    url = META.get("serverNotificationsUrl")
    want_attrs = {"subscriptionStatusUrl": url, "subscriptionStatusUrlVersion": "V2",
                  "subscriptionStatusUrlForSandbox": url, "subscriptionStatusUrlVersionForSandbox": "V2"}
    if not url:
        print("SKIP    server notifications: no serverNotificationsUrl in metadata.json")
    elif all(app["attributes"].get(k) == v for k, v in want_attrs.items()):
        same("server notifications V2 (production + sandbox)")
    else:
        write("PATCH", f"/v1/apps/{APP}", {"data": {"type": "apps", "id": APP, "attributes": want_attrs}},
              f"server notifications V2 (production + sandbox) -> {url}")

# ---- categories + name / subtitle / privacy URL per language
info_doc = get(f"/v1/apps/{APP}/appInfos?include=primaryCategory,secondaryCategory,ageRatingDeclaration,appInfoLocalizations&limit=5") or {}
info = next((i for i in info_doc.get("data", []) if (i["attributes"].get("appStoreState") or i["attributes"].get("state")) != "READY_FOR_SALE"), None) or (info_doc.get("data") or [None])[0]
if info and want("categories"):
    r = info.get("relationships", {})
    cur = ((r.get("primaryCategory", {}).get("data") or {}).get("id"), (r.get("secondaryCategory", {}).get("data") or {}).get("id"))
    if cur != (META["primaryCategory"], META["secondaryCategory"]):
        write("PATCH", f'/v1/appInfos/{info["id"]}', {"data": {"type": "appInfos", "id": info["id"], "relationships": {
            "primaryCategory": rel("appCategories", META["primaryCategory"]), "secondaryCategory": rel("appCategories", META["secondaryCategory"])}}},
            f'categories: {META["primaryCategory"]} / {META["secondaryCategory"]}')
    else:
        same("categories")
if info and want("info"):
    existing = {i["attributes"]["locale"]: i for i in info_doc.get("included", []) if i["type"] == "appInfoLocalizations"}
    for locale, text in META["locales"].items():
        attrs = {"name": text["name"], "subtitle": text["subtitle"], "privacyPolicyUrl": META["urls"]["privacy"]}
        cur = existing.get(locale)
        if cur is None:
            write("POST", "/v1/appInfoLocalizations", {"data": {"type": "appInfoLocalizations", "attributes": {"locale": locale, **attrs},
                  "relationships": {"appInfo": rel("appInfos", info["id"])}}}, f"[{locale}] add name, subtitle, privacy URL")
        elif any(cur["attributes"].get(k) != v for k, v in attrs.items()):
            write("PATCH", f'/v1/appInfoLocalizations/{cur["id"]}', {"data": {"type": "appInfoLocalizations", "id": cur["id"], "attributes": attrs}},
                  f"[{locale}] name, subtitle, privacy URL")
        else:
            same(f"[{locale}] name, subtitle, privacy URL")

# ---- age rating (answers from docs/APPLE_DECLARATIONS.md section 4). Only keys Apple currently returns are sent.
if info and want("age"):
    decl = next((i for i in info_doc.get("included", []) if i["type"] == "ageRatingDeclarations"), None)
    if decl:
        keys = decl["attributes"]
        print("INFO    age rating fields:", ", ".join(f"{k}={v}" for k, v in sorted(keys.items())))
        explicit = {"userGeneratedContent": True, "unrestrictedWebAccess": False, "gambling": False, "lootBox": False, "advertising": False,
                    "messagingAndChat": False, "parentalControls": False, "ageAssurance": False, "healthOrWellnessTopics": False,
                    "kidsAgeBand": None, "koreaAgeRatingOverride": "NONE", "ageRatingOverride": "NONE", "ageRatingOverrideV2": "NONE",
                    "developerAgeRatingInfoUrl": None, "seventeenPlus": None, "gracRatingClassificationNumber": None,
                    "socialMedia": False, "socialMediaAgeRestricted": False}
        answers = {}
        for k, v in keys.items():
            if k in explicit:
                if explicit[k] is not None:
                    answers[k] = explicit[k]
            elif isinstance(v, bool) or (v is None and k.startswith(("is", "has", "contains"))):
                answers[k] = False
            else:
                answers[k] = "NONE"
        # Owner decision 14 Nov 2026: the store shows 18+ (the Terms require adults), whatever the questionnaire computes.
        override = os.environ.get("ASC_AGE_OVERRIDE", "").strip() or META.get("ageRatingOverride", "")  # kept in metadata so a later run never resets it
        if override and "ageRatingOverrideV2" in keys:
            answers["ageRatingOverrideV2"] = override
        changed = {k: v for k, v in answers.items() if keys.get(k) != v}
        if changed:
            write("PATCH", f'/v1/ageRatingDeclarations/{decl["id"]}', {"data": {"type": "ageRatingDeclarations", "id": decl["id"], "attributes": changed}},
                  f"age rating answers ({len(changed)} fields; user-generated content = yes, everything else none)")
        else:
            same("age rating answers")

# ---- version: copyright + description, keywords, URLs, promotional text per language
versions = (get(f"/v1/apps/{APP}/appStoreVersions?limit=5&include=appStoreVersionLocalizations,appStoreReviewDetail") or {})
version = next((v for v in versions.get("data", []) if (v["attributes"].get("appStoreState") or v["attributes"].get("appVersionState")) in ("PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED")), None)
if version and want("version"):
    copyright_text = os.environ.get("ASC_COPYRIGHT", "").strip()
    if not copyright_text:
        print("SKIP    copyright (needs the operator's legal name: ASC_COPYRIGHT)")
    elif version["attributes"].get("copyright") != copyright_text:
        write("PATCH", f'/v1/appStoreVersions/{version["id"]}', {"data": {"type": "appStoreVersions", "id": version["id"], "attributes": {"copyright": copyright_text}}}, "copyright")
    else:
        same("copyright")
    locs = get_all(f'/v1/appStoreVersions/{version["id"]}/appStoreVersionLocalizations?limit=50')
    existing = {i["attributes"]["locale"]: i for i in locs}
    for locale, text in META["locales"].items():
        attrs = {"description": text["description"], "keywords": text["keywords"], "promotionalText": text["promotionalText"],
                 "supportUrl": META["urls"]["support"], "marketingUrl": META["urls"]["marketing"]}
        cur = existing.get(locale)
        if cur is None:
            write("POST", "/v1/appStoreVersionLocalizations", {"data": {"type": "appStoreVersionLocalizations", "attributes": {"locale": locale, **attrs},
                  "relationships": {"appStoreVersion": rel("appStoreVersions", version["id"])}}}, f"[{locale}] add description, keywords, URLs, promotional text")
        elif any((cur["attributes"].get(k) or "") != v for k, v in attrs.items()):
            write("PATCH", f'/v1/appStoreVersionLocalizations/{cur["id"]}', {"data": {"type": "appStoreVersionLocalizations", "id": cur["id"], "attributes": attrs}},
                  f"[{locale}] description, keywords, URLs, promotional text")
        else:
            same(f"[{locale}] description, keywords, URLs, promotional text")
elif want("version"):
    print("SKIP    version texts (no editable App Store version)")

# ---- review details (contact + demo account; values come from the environment, never from the repository)
if version and want("review"):
    env = {k: os.environ.get(k, "").strip() for k in ("REVIEW_FIRST_NAME", "REVIEW_LAST_NAME", "REVIEW_PHONE", "REVIEW_EMAIL", "REVIEW_DEMO_USER", "REVIEW_DEMO_PASSWORD", "REVIEW_NOTES")}
    # Maintenance reviewaccounts adds the second (Pro Host) login line here; never printed.
    notes_extra = os.environ.get("REVIEW_NOTES_EXTRA", "").rstrip()
    detail = next((i for i in versions.get("included", []) if i["type"] == "appStoreReviewDetails"), None)
    has_contact = all(env[k] for k in ("REVIEW_FIRST_NAME", "REVIEW_LAST_NAME", "REVIEW_PHONE", "REVIEW_EMAIL"))
    if not has_contact and env["REVIEW_DEMO_USER"] and detail:
        # Contact already on file: change only the demo login and the notes.
        attrs = {"demoAccountRequired": True, "demoAccountName": env["REVIEW_DEMO_USER"], "demoAccountPassword": env["REVIEW_DEMO_PASSWORD"],
                 "notes": (env["REVIEW_NOTES"] or META.get("reviewNotes", "")) + notes_extra}
        write("PATCH", f'/v1/appStoreReviewDetails/{detail["id"]}', {"data": {"type": "appStoreReviewDetails", "id": detail["id"], "attributes": attrs}}, "review demo accounts and notes")
    elif not has_contact:
        print("SKIP    review contact (needs REVIEW_FIRST_NAME, REVIEW_LAST_NAME, REVIEW_PHONE, REVIEW_EMAIL)")
    else:
        attrs = {"contactFirstName": env["REVIEW_FIRST_NAME"], "contactLastName": env["REVIEW_LAST_NAME"], "contactPhone": env["REVIEW_PHONE"],
                 "contactEmail": env["REVIEW_EMAIL"]}
        # No demo login in the environment: leave the one Maintenance reviewaccounts stored untouched.
        if env["REVIEW_DEMO_USER"]:
            attrs.update({"demoAccountRequired": True, "demoAccountName": env["REVIEW_DEMO_USER"], "demoAccountPassword": env["REVIEW_DEMO_PASSWORD"]})
        notes = (env["REVIEW_NOTES"] or META.get("reviewNotes", "")) + notes_extra
        if notes:
            attrs["notes"] = notes
        if detail:
            write("PATCH", f'/v1/appStoreReviewDetails/{detail["id"]}', {"data": {"type": "appStoreReviewDetails", "id": detail["id"], "attributes": attrs}}, "review contact and demo account")
        else:
            write("POST", "/v1/appStoreReviewDetails", {"data": {"type": "appStoreReviewDetails", "attributes": attrs,
                  "relationships": {"appStoreVersion": rel("appStoreVersions", version["id"])}}}, "review contact and demo account")

# ---- Associated Domains capability (Universal Links to pro-host.tech)
if want("capability"):
    bid = get(f'/v1/bundleIds?filter[identifier]={META["bundleId"]}&include=bundleIdCapabilities&limit=5') or {}
    have = {i["attributes"]["capabilityType"] for i in bid.get("included", []) if i["type"] == "bundleIdCapabilities"}
    if "ASSOCIATED_DOMAINS" in have:
        same("capability ASSOCIATED_DOMAINS")
    elif bid.get("data"):
        write("POST", "/v1/bundleIdCapabilities", {"data": {"type": "bundleIdCapabilities", "attributes": {"capabilityType": "ASSOCIATED_DOMAINS"},
              "relationships": {"bundleId": rel("bundleIds", bid["data"][0]["id"])}}}, "capability ASSOCIATED_DOMAINS (links from pro-host.tech open the app)")

# ---- price: free app
if want("price"):
    sched = get(f"/v1/apps/{APP}/appPriceSchedule")
    manual = get(f"/v1/apps/{APP}/appPriceSchedule/manualPrices?limit=5") if sched else None
    if sched and (manual or {}).get("data"):
        same("app price schedule")
    else:
        # A schedule without a manual price shows as "choose a price tier" in App Store Connect: (re)create it as free.
        points = get_all(f"/v1/apps/{APP}/appPricePoints?filter[territory]=USA&limit=200")
        free = next((p for p in points if float(p["attributes"].get("customerPrice") or 1) == 0.0), None)
        if not free:
            print("FAILED  free price point not found"); failures.append("price")
        else:
            write("POST", "/v1/appPriceSchedules", {
                "data": {"type": "appPriceSchedules", "relationships": {"app": rel("apps", APP), "baseTerritory": rel("territories", "USA"),
                         "manualPrices": {"data": [{"type": "appPrices", "id": "${free}"}]}}},
                "included": [{"type": "appPrices", "id": "${free}", "attributes": {"startDate": None},
                              "relationships": {"appPricePoint": rel("appPricePoints", free["id"])}}]}, "app price: free (base territory USA)")

# ---- availability: every territory, including new ones
if want("availability"):
    if get(f"/v1/apps/{APP}/appAvailabilityV2"):
        same("availability")
    else:
        territories = [t["id"] for t in get_all("/v1/territories?limit=200")]
        write("POST", "/v2/appAvailabilities", {
            "data": {"type": "appAvailabilities", "attributes": {"availableInNewTerritories": True},
                     "relationships": {"app": rel("apps", APP), "territoryAvailabilities": {"data": [{"type": "territoryAvailabilities", "id": "${%s}" % t} for t in territories]}}},
            "included": [{"type": "territoryAvailabilities", "id": "${%s}" % t, "attributes": {"available": True},
                          "relationships": {"territory": rel("territories", t)}} for t in territories]}, f"availability: all {len(territories)} countries and regions, plus new ones")

# ---- availability without China mainland (it needs an ICP filing, a security assessment and an AI-content filing)
if want("nochina"):
    avail = get(f"/v1/apps/{APP}/appAvailabilityV2")
    if not avail:
        print("SKIP    no availability yet (run section availability first)")
    else:
        aid = avail["data"]["id"]
        rows = get_all(f"/v2/appAvailabilities/{aid}/territoryAvailabilities?include=territory&limit=200")
        china = next((r for r in rows if (r.get("relationships", {}).get("territory", {}).get("data") or {}).get("id") == "CHN"), None)
        if not china:
            print("FAILED  China mainland row not found"); failures.append("nochina")
        elif not china["attributes"].get("available"):
            same("China mainland not available")
        else:
            write("PATCH", f'/v1/territoryAvailabilities/{china["id"]}', {"data": {"type": "territoryAvailabilities", "id": china["id"],
                  "attributes": {"available": False}}}, "availability: remove China mainland")

# ---- attach a build to the editable version (BUILD_NUMBER, e.g. 22)
if version and want("build"):
    number = os.environ.get("BUILD_NUMBER", "").strip()
    if not number:
        print("SKIP    build (needs BUILD_NUMBER)")
    else:
        builds = (get(f"/v1/builds?filter[app]={APP}&filter[version]={number}&limit=5") or {}).get("data", [])
        valid = [b for b in builds if b["attributes"].get("processingState") == "VALID"]
        if not valid:
            print(f"FAILED  build {number} not found or not VALID"); failures.append("build")
        else:
            write("PATCH", f'/v1/appStoreVersions/{version["id"]}/relationships/build', {"data": {"type": "builds", "id": valid[0]["id"]}},
                  f"attach build {number} to version {version['attributes'].get('versionString')}")

# ---- TestFlight test information
if want("testflight"):
    locs = get_all(f"/v1/apps/{APP}/betaAppLocalizations?limit=20")
    tf = META["testFlight"]
    cur = next((x for x in locs if x["attributes"].get("locale") == "en-US"), None)
    attrs = {"feedbackEmail": tf["feedbackEmail"], "description": tf["description"], "privacyPolicyUrl": META["urls"]["privacy"], "marketingUrl": META["urls"]["marketing"]}
    if cur is None:
        write("POST", "/v1/betaAppLocalizations", {"data": {"type": "betaAppLocalizations", "attributes": {"locale": "en-US", **attrs},
              "relationships": {"app": rel("apps", APP)}}}, "TestFlight test information (what to test, feedback email)")
    elif any((cur["attributes"].get(k) or "") != v for k, v in attrs.items()):
        write("PATCH", f'/v1/betaAppLocalizations/{cur["id"]}', {"data": {"type": "betaAppLocalizations", "id": cur["id"], "attributes": attrs}}, "TestFlight test information")
    else:
        same("TestFlight test information")

# ---- subscriptions: one group, monthly + yearly, names in both languages, USD price with Apple's equivalents elsewhere
if want("subscriptions"):
    sub = META["subscriptions"]
    groups = get_all(f"/v1/apps/{APP}/subscriptionGroups?limit=20")
    group = next((g for g in groups if g["attributes"].get("referenceName") == sub["groupReferenceName"]), None)
    if group is None:
        created = write("POST", "/v1/subscriptionGroups", {"data": {"type": "subscriptionGroups", "attributes": {"referenceName": sub["groupReferenceName"]},
                        "relationships": {"app": rel("apps", APP)}}}, f'subscription group "{sub["groupReferenceName"]}"')
        group = (created or {}).get("data")
    else:
        same("subscription group")
    if group:
        gl = {x["attributes"]["locale"] for x in get_all(f'/v1/subscriptionGroups/{group["id"]}/subscriptionGroupLocalizations?limit=50')}
        for locale, name in sub["groupDisplayName"].items():
            if locale not in gl:
                write("POST", "/v1/subscriptionGroupLocalizations", {"data": {"type": "subscriptionGroupLocalizations", "attributes": {"locale": locale, "name": name},
                      "relationships": {"subscriptionGroup": rel("subscriptionGroups", group["id"])}}}, f"[{locale}] subscription group name")
        have = {s["attributes"]["productId"]: s for s in get_all(f'/v1/subscriptionGroups/{group["id"]}/subscriptions?limit=50')}
        for p in sub["products"]:
            usd = p["usd"] or (os.environ.get("SUBSCRIPTION_YEARLY_USD", "").strip() if p["period"] == "ONE_YEAR" else "")
            s = have.get(p["productId"])
            if s is None:
                created = write("POST", "/v1/subscriptions", {"data": {"type": "subscriptions", "attributes": {
                    "name": p["referenceName"], "productId": p["productId"], "subscriptionPeriod": p["period"], "groupLevel": p["groupLevel"], "familySharable": False},
                    "relationships": {"group": rel("subscriptionGroups", group["id"])}}}, f'subscription {p["productId"]} ({p["period"]})')
                s = (created or {}).get("data")
            else:
                same(f'subscription {p["productId"]} (state {s["attributes"].get("state")})')
            if not s:
                continue
            sl = {x["attributes"]["locale"] for x in get_all(f'/v1/subscriptions/{s["id"]}/subscriptionLocalizations?limit=50')}
            for locale in p["name"]:
                if locale not in sl:
                    write("POST", "/v1/subscriptionLocalizations", {"data": {"type": "subscriptionLocalizations", "attributes": {
                        "locale": locale, "name": p["name"][locale], "description": p["description"][locale]},
                        "relationships": {"subscription": rel("subscriptions", s["id"])}}}, f'[{locale}] {p["productId"]} name and description')
            # A subscription must be available in a country before it can carry a price there.
            if not get(f'/v1/subscriptions/{s["id"]}/subscriptionAvailability'):
                all_territories = [t["id"] for t in get_all("/v1/territories?limit=200")]
                write("POST", "/v1/subscriptionAvailabilities", {"data": {"type": "subscriptionAvailabilities", "attributes": {"availableInNewTerritories": True},
                      "relationships": {"subscription": rel("subscriptions", s["id"]),
                                        "availableTerritories": {"data": [{"type": "territories", "id": t} for t in all_territories]}}}},
                      f'{p["productId"]} available in all {len(all_territories)} countries')
            else:
                same(f'{p["productId"]} availability')
            if not usd:
                print(f'SKIP    {p["productId"]} price (needs SUBSCRIPTION_YEARLY_USD)')
                continue
            # A price that already exists is changed only when the USA price differs from metadata (owner's price change,
            # e.g. Unlimited doubled on 23 Nov 2026): the new price is for new subscribers, current ones keep theirs.
            preserve = False
            if get_all(f'/v1/subscriptions/{s["id"]}/prices?limit=5'):
                cur = get_all(f'/v1/subscriptions/{s["id"]}/prices?filter[territory]=USA&include=subscriptionPricePoint&limit=5')
                status_, raw = api.request("GET", f'/v1/subscriptions/{s["id"]}/prices?filter[territory]=USA&include=subscriptionPricePoint&limit=5')
                points_ = {x["id"]: x["attributes"].get("customerPrice") for x in (raw or {}).get("included", []) if x.get("type") == "subscriptionPricePoints"}
                now_usd = next((points_.get(((c.get("relationships", {}).get("subscriptionPricePoint", {}).get("data")) or {}).get("id")) for c in cur), None)
                # ASC_REPRICE_ALL=1 re-sends every country (a run cut off half way left some on the old price).
                if now_usd is not None and abs(float(now_usd) - float(usd)) < 0.001 and os.environ.get("ASC_REPRICE_ALL") != "1":
                    same(f'{p["productId"]} prices (USA {now_usd})')
                    continue
                print(f'INFO    {p["productId"]}: USA price {now_usd} -> {usd} (existing subscribers keep their price)')
                preserve = True
            points = get_all(f'/v1/subscriptions/{s["id"]}/pricePoints?filter[territory]=USA&limit=200')
            base = next((x for x in points if abs(float(x["attributes"].get("customerPrice") or 0) - float(usd)) < 0.001), None)
            if not base:
                print(f'FAILED  {p["productId"]}: Apple has no ${usd} price point'); failures.append(p["productId"] + " price")
                continue
            targets = [("USA", base["id"])]
            for eq in get_all(f'/v1/subscriptionPricePoints/{base["id"]}/equalizations?include=territory&limit=200'):
                terr = ((eq.get("relationships", {}).get("territory", {}).get("data")) or {}).get("id")
                if terr and terr != "USA":
                    targets.append((terr, eq["id"]))
            if not args.apply:
                print(f'PLAN    {p["productId"]} price ${usd} in the USA and Apple\'s equivalent in {len(targets) - 1} other countries')
                continue
            ok, bad, first_err = 0, 0, ""
            for terr, point in targets:
                status, data = api.request("POST", "/v1/subscriptionPrices", {"data": {"type": "subscriptionPrices", "attributes": {"preserveCurrentPrice": preserve},
                    "relationships": {"subscription": rel("subscriptions", s["id"]), "subscriptionPricePoint": rel("subscriptionPricePoints", point), "territory": rel("territories", terr)}}})
                if status in (200, 201):
                    ok += 1
                else:
                    bad += 1
                    first_err = first_err or f"{terr} HTTP {status}: " + "; ".join(str(e.get("detail")) for e in (data or {}).get("errors", []))[:300]
                    if ok == 0 and bad >= 3:
                        break  # nothing is being accepted: stop instead of 175 identical errors
            print(f'DONE    {p["productId"]} price ${usd}: {ok} of {len(targets)} countries' + (f", {bad} refused (first: {first_err})" if bad else ""))
            if ok == 0:
                failures.append(p["productId"] + " price")


# ---- TestFlight beta-review contact (same person as the App Review contact)
if want("betareview"):
    env = {k: os.environ.get(k, "").strip() for k in ("REVIEW_FIRST_NAME", "REVIEW_LAST_NAME", "REVIEW_PHONE", "REVIEW_EMAIL")}
    if not all(env.values()):
        print("SKIP    TestFlight beta-review contact (needs the review contact)")
    else:
        write("PATCH", f"/v1/betaAppReviewDetails/{APP}", {"data": {"type": "betaAppReviewDetails", "id": APP, "attributes": {
            "contactFirstName": env["REVIEW_FIRST_NAME"], "contactLastName": env["REVIEW_LAST_NAME"], "contactPhone": env["REVIEW_PHONE"],
            "contactEmail": env["REVIEW_EMAIL"], "demoAccountRequired": False}}}, "TestFlight beta-review contact")

# ---- TestFlight tester: (re)send the invitation to one person (TESTER_EMAIL, names from the review contact). Emails are never printed.
if want("tester"):
    email = os.environ.get("TESTER_EMAIL", "").strip().lower()
    first, last = os.environ.get("REVIEW_FIRST_NAME", "").strip(), os.environ.get("REVIEW_LAST_NAME", "").strip()
    # A tester other than the review contact: the workflow's review_name input ("First Last") names them.
    named = os.environ.get("REVIEW_NAME_INPUT", "").strip().split(None, 1)
    if named:
        first, last = named[0], (named[1] if len(named) > 1 else "")
    if not email:
        print("SKIP    tester invitation (needs TESTER_EMAIL)")
    else:
        groups = get_all(f"/v1/betaGroups?filter[app]={APP}&limit=20")
        internal = next((g for g in groups if g["attributes"].get("isInternalGroup")), None)
        testers = get_all(f"/v1/betaTesters?filter[email]={email}&limit=5")
        team_user = any((u["attributes"].get("username") or "").lower() == email for u in get_all("/v1/users?limit=200"))
        pending = any((u["attributes"].get("email") or "").lower() == email for u in get_all("/v1/userInvitations?limit=200"))
        print(f"INFO    tester: already a tester={bool(testers)} team member={team_user} team invitation pending={pending}")
        if testers:
            tid = testers[0]["id"]
            if internal:
                write("POST", f'/v1/betaGroups/{internal["id"]}/relationships/betaTesters', {"data": [{"type": "betaTesters", "id": tid}]}, "tester in the internal TestFlight group")
            write("POST", "/v1/betaTesterInvitations", {"data": {"type": "betaTesterInvitations", "relationships": {
                "betaTester": rel("betaTesters", tid), "app": rel("apps", APP)}}}, "TestFlight invitation e-mail sent again")
        elif team_user and internal:
            write("POST", "/v1/betaTesters", {"data": {"type": "betaTesters", "attributes": {"email": email, "firstName": first, "lastName": last},
                  "relationships": {"betaGroups": {"data": [{"type": "betaGroups", "id": internal["id"]}]}}}}, "tester added to the internal TestFlight group (invitation e-mail follows)")
        elif pending:
            print("OK      a team invitation is already waiting in her inbox (from Apple, subject 'You have been invited to join a development team'); she accepts it first, then run this again")
        else:
            write("POST", "/v1/userInvitations", {"data": {"type": "userInvitations", "attributes": {
                "email": email, "firstName": first or "Tester", "lastName": last or "ProHost", "roles": ["DEVELOPER"], "allAppsVisible": False, "provisioningAllowed": False},
                "relationships": {"visibleApps": {"data": [{"type": "apps", "id": APP}]}}}},
                "team invitation sent (role Developer, this app only); after she accepts, run this again to add her to TestFlight")

# ---- launch offers for new subscribers, mirroring Google Play: monthly = 1 month free; yearly = first year at half price
if want("offers"):
    group = next((g for g in get_all(f"/v1/apps/{APP}/subscriptionGroups?limit=20") if g["attributes"].get("referenceName") == META["subscriptions"]["groupReferenceName"]), None)
    subs = {x["attributes"]["productId"]: x for x in (get_all(f'/v1/subscriptionGroups/{group["id"]}/subscriptions?limit=50') if group else [])}
    plans = [("prohost.premium.monthly", {"offerMode": "FREE_TRIAL", "duration": "ONE_MONTH", "numberOfPeriods": 1}, None),
             ("prohost.premium.yearly", {"offerMode": "PAY_UP_FRONT", "duration": "ONE_YEAR", "numberOfPeriods": 1}, os.environ.get("YEARLY_INTRO_USD", "").strip())]
    for product, attrs, intro_usd in plans:
        s = subs.get(product)
        if not s:
            print(f"SKIP    {product} offer (subscription missing)")
            continue
        existing_offers = get_all(f'/v1/subscriptions/{s["id"]}/introductoryOffers?include=subscriptionPricePoint,territory&limit=200')
        if existing_offers:
            usa_price = None
            if intro_usd:
                _, raw = api.request("GET", f'/v1/subscriptions/{s["id"]}/introductoryOffers?filter[territory]=USA&include=subscriptionPricePoint&limit=5')
                pts = {x["id"]: x["attributes"].get("customerPrice") for x in (raw or {}).get("included", []) if x.get("type") == "subscriptionPricePoints"}
                usa = next(iter((raw or {}).get("data", [])), None)
                usa_price = pts.get((((usa or {}).get("relationships", {}).get("subscriptionPricePoint", {}).get("data")) or {}).get("id"))
            if not intro_usd or usa_price is None or abs(float(usa_price) - float(intro_usd)) < 0.001:
                same(f"{product} introductory offer")
                continue
            if not args.apply:
                print(f"PLAN    {product} introductory offer: USA {usa_price} -> {intro_usd} ({len(existing_offers)} countries replaced)")
                continue
            for o in existing_offers:
                api.request("DELETE", f'/v1/subscriptionIntroductoryOffers/{o["id"]}')
            print(f"DONE    {product} introductory offer: removed {len(existing_offers)} at {usa_price}, creating at {intro_usd}")
        targets = []
        if attrs["offerMode"] == "FREE_TRIAL":
            targets = [(t["id"], None) for t in get_all("/v1/territories?limit=200")]
        elif not intro_usd:
            print(f"SKIP    {product} offer (needs YEARLY_INTRO_USD)")
            continue
        else:
            points = get_all(f'/v1/subscriptions/{s["id"]}/pricePoints?filter[territory]=USA&limit=200')
            base = next((x for x in points if abs(float(x["attributes"].get("customerPrice") or 0) - float(intro_usd)) < 0.001), None)
            if not base:
                print(f"FAILED  {product}: Apple has no ${intro_usd} price point"); failures.append(product + " offer")
                continue
            targets = [("USA", base["id"])]
            for eq in get_all(f'/v1/subscriptionPricePoints/{base["id"]}/equalizations?include=territory&limit=200'):
                terr = ((eq.get("relationships", {}).get("territory", {}).get("data")) or {}).get("id")
                if terr and terr != "USA":
                    targets.append((terr, eq["id"]))
        label = "1 month free" if attrs["offerMode"] == "FREE_TRIAL" else f"first year ${intro_usd}"
        if not args.apply:
            print(f"PLAN    {product} offer for new subscribers: {label} in {len(targets)} countries")
            continue
        ok, first_error = 0, ""
        for terr, point in targets:
            relationships = {"subscription": rel("subscriptions", s["id"]), "territory": rel("territories", terr)}
            if point:
                relationships["subscriptionPricePoint"] = rel("subscriptionPricePoints", point)
            status, data = api.request("POST", "/v1/subscriptionIntroductoryOffers", {"data": {"type": "subscriptionIntroductoryOffers", "attributes": attrs, "relationships": relationships}})
            if status in (200, 201):
                ok += 1
            elif not first_error:
                first_error = f"{terr} HTTP {status} " + "; ".join(str(e.get("detail")) for e in (data or {}).get("errors", []))[:300]
                if ok == 0:
                    break
        print(f'{"DONE   " if ok else "FAILED "} {product} offer ({label}): {ok} of {len(targets)} countries' + (f" · first error: {first_error}" if first_error else ""))
        if not ok:
            failures.append(product + " offer")


# --- media: App Store screenshots and the subscriptions' review screenshot -------------------------------------------------------------
# Files: ios/store/screenshots/<locale>/*.jpg (scripts/ios/render_store_screenshots.js) and review/premium-plans.png.
import hashlib  # noqa: E402
import urllib.request  # noqa: E402

SHOTS_DIR = os.path.join(ROOT, "ios/store/screenshots")
# Real captures of the iOS app (ios-screenshots.yml, folders en/ ar/) replace the mock-ups when present;
# SCREENSHOT_SRC=mockups forces the old set. Empty-state screens (rentals, saved) are left out on purpose.
REAL_DIR = os.path.join(ROOT, "ios/store/screenshots-real")
REAL_PICK = ["01_explore", "03_listing", "04_availability", "05_premium", "07_profile", "00_welcome"]


def _shot_files(locale):
    """(folder, ordered file names) for one App Store locale: real captures first, else the mock-ups."""
    real = os.path.join(REAL_DIR, locale.split("-")[0])
    if os.environ.get("SCREENSHOT_SRC", "real") != "mockups" and os.path.isdir(real):
        files = [f"{n}.png" for n in REAL_PICK if os.path.isfile(os.path.join(real, f"{n}.png"))]
        if len(files) >= 3:
            return real, files
    folder = os.path.join(SHOTS_DIR, locale)
    return folder, (sorted(f for f in os.listdir(folder) if f.lower().endswith((".jpg", ".png"))) if os.path.isdir(folder) else [])


def _upload(kind, relationship, file_path, label):
    """Reserve → PUT the parts → commit with the MD5. Returns True when Apple accepted the file."""
    raw = open(file_path, "rb").read()
    body = {"data": {"type": kind, "attributes": {"fileName": os.path.basename(file_path), "fileSize": len(raw)}, "relationships": relationship}}
    status, data = api.request("POST", f"/v1/{kind}", body)
    if status not in (200, 201):
        print(f"FAILED  {label} reserve (HTTP {status}) " + "; ".join(str(e.get("detail")) for e in (data or {}).get("errors", []))[:300])
        failures.append(label)
        return False
    item = data["data"]
    for op in item["attributes"].get("uploadOperations") or []:
        req = urllib.request.Request(op["url"], data=raw[op["offset"]:op["offset"] + op["length"]], method=op["method"])
        for h in op.get("requestHeaders") or []:
            req.add_header(h["name"], h["value"])
        with urllib.request.urlopen(req, timeout=120) as r:
            if r.status not in (200, 201, 204):
                print(f"FAILED  {label} upload part (HTTP {r.status})")
                failures.append(label)
                return False
    status, data = api.request("PATCH", f'/v1/{kind}/{item["id"]}', {"data": {"type": kind, "id": item["id"], "attributes": {"uploaded": True, "sourceFileChecksum": hashlib.md5(raw).hexdigest()}}})
    if status != 200:
        print(f"FAILED  {label} commit (HTTP {status})")
        failures.append(label)
        return False
    print(f"DONE    {label}")
    return True


def _md5(path):
    return hashlib.md5(open(path, "rb").read()).hexdigest()


if want("screenshots") and version:
    for loc in get_all(f'/v1/appStoreVersions/{version["id"]}/appStoreVersionLocalizations?limit=50'):
        locale = loc["attributes"]["locale"]
        folder, files = _shot_files(locale)
        if not files:
            print(f"SKIP    screenshots {locale}: no files in ios/store/screenshots/{locale}")
            continue
        sets = get_all(f'/v1/appStoreVersionLocalizations/{loc["id"]}/appScreenshotSets?limit=50')
        cur = next((x for x in sets if x["attributes"]["screenshotDisplayType"] == "APP_IPHONE_67"), None)
        existing = get_all(f'/v1/appScreenshotSets/{cur["id"]}/appScreenshots?limit=50') if cur else []
        have = [(x["attributes"].get("fileName"), x["attributes"].get("sourceFileChecksum")) for x in existing]
        wanted = [(f, _md5(os.path.join(folder, f))) for f in files]
        if have == wanted:
            same(f"screenshots {locale}: {len(files)} files")
            continue
        if not args.apply:
            print(f"PLAN    screenshots {locale}: upload {len(files)} files from {os.path.relpath(folder, ROOT)} (6.9-inch iPhone), replacing {len(existing)}")
            continue
        if not cur:
            created = write("POST", "/v1/appScreenshotSets", {"data": {"type": "appScreenshotSets", "attributes": {"screenshotDisplayType": "APP_IPHONE_67"},
                            "relationships": {"appStoreVersionLocalization": rel("appStoreVersionLocalizations", loc["id"])}}}, f"screenshot set {locale}")
            cur = (created or {}).get("data")
            if not cur:
                continue
        for x in existing:
            api.request("DELETE", f'/v1/appScreenshots/{x["id"]}')
        for f in files:
            _upload("appScreenshots", {"appScreenshotSet": rel("appScreenshotSets", cur["id"])}, os.path.join(folder, f), f"screenshot {locale} {f}")

if want("subreview"):
    shot = os.path.join(SHOTS_DIR, "review/premium-plans.png")
    note = META["subscriptions"].get("reviewNote", "")
    group = next((g for g in get_all(f"/v1/apps/{APP}/subscriptionGroups?limit=20") if g["attributes"].get("referenceName") == META["subscriptions"]["groupReferenceName"]), None)
    for s_ in (get_all(f'/v1/subscriptionGroups/{group["id"]}/subscriptions?limit=50') if group else []):
        product = s_["attributes"]["productId"]
        if note and s_["attributes"].get("reviewNote") != note:
            write("PATCH", f'/v1/subscriptions/{s_["id"]}', {"data": {"type": "subscriptions", "id": s_["id"], "attributes": {"reviewNote": note}}}, f"{product} review note")
        current = (get(f'/v1/subscriptions/{s_["id"]}/appStoreReviewScreenshot') or {}).get("data")
        if current and current["attributes"].get("sourceFileChecksum") == _md5(shot):
            same(f"{product} review screenshot")
        elif not args.apply:
            print(f"PLAN    {product} review screenshot: upload the Premium screen")
        else:
            if current:
                api.request("DELETE", f'/v1/subscriptionAppStoreReviewScreenshots/{current["id"]}')
            _upload("subscriptionAppStoreReviewScreenshots", {"subscription": rel("subscriptions", s_["id"])}, shot, f"{product} review screenshot")

# ---- phased release (7 days), like Play's staged production rollout
if version and want("phased"):
    ph = get(f'/v1/appStoreVersions/{version["id"]}/appStoreVersionPhasedRelease')
    if ph and ph.get("data"):
        same("phased release")
    else:
        write("POST", "/v1/appStoreVersionPhasedReleases", {"data": {"type": "appStoreVersionPhasedReleases",
              "attributes": {"phasedReleaseState": "INACTIVE"}, "relationships": {"appStoreVersion": rel("appStoreVersions", version["id"])}}},
              "phased release over 7 days (starts when the version is released)")

# ---- offer code PROHOST2026 (= Play's promo offer: Unlimited monthly, 1 month free, new customers, until 31 Dec 2026)
if want("offercode"):
    group = next((g for g in get_all(f"/v1/apps/{APP}/subscriptionGroups?limit=20") if g["attributes"].get("referenceName") == META["subscriptions"]["groupReferenceName"]), None)
    subs = {x["attributes"]["productId"]: x for x in (get_all(f'/v1/subscriptionGroups/{group["id"]}/subscriptions?limit=50') if group else [])}
    monthly = subs.get("prohost.premium.monthly")
    if not monthly:
        print("SKIP    offer code (prohost.premium.monthly missing)")
    else:
        codes = get_all(f'/v1/subscriptions/{monthly["id"]}/offerCodes?limit=50')
        existing = next((c for c in codes if c["attributes"].get("name") == "PROHOST2026"), None)
        if existing:
            if get_all(f'/v1/subscriptionOfferCodes/{existing["id"]}/customCodes?limit=5'):
                same("offer code PROHOST2026 and its custom code")
            else:
                # Apple accepts custom codes only once the app and the subscription are approved (409 OFFER_CODE_APP_STATE_INVALID).
                write("POST", "/v1/subscriptionOfferCodeCustomCodes", {"data": {"type": "subscriptionOfferCodeCustomCodes",
                      "attributes": {"customCode": "PROHOST2026", "numberOfCodes": 10000, "expirationDate": "2026-12-31"},
                      "relationships": {"offerCode": rel("subscriptionOfferCodes", existing["id"])}}},
                      "custom code PROHOST2026 (needs the app approved; re-run after approval)")
        elif not args.apply:
            print("PLAN    offer code PROHOST2026 (1 month free, new customers, all countries) + custom code valid until 2026-12-31")
        else:
            terrs = [t["id"] for t in get_all("/v1/territories?limit=200")]
            body = {"data": {"type": "subscriptionOfferCodes",
                             "attributes": {"name": "PROHOST2026", "customerEligibilities": ["NEW"], "offerEligibility": "REPLACE_INTRO_OFFERS",
                                            "offerMode": "FREE_TRIAL", "duration": "ONE_MONTH", "numberOfPeriods": 1},
                             "relationships": {"subscription": rel("subscriptions", monthly["id"]),
                                               "prices": {"data": [{"type": "subscriptionOfferCodePrices", "id": "${p%s}" % t} for t in terrs]}}},
                    "included": [{"type": "subscriptionOfferCodePrices", "id": "${p%s}" % t,
                                  "relationships": {"territory": rel("territories", t)}} for t in terrs]}
            created = write("POST", "/v1/subscriptionOfferCodes", body, f"offer code PROHOST2026 ({len(terrs)} countries)")
            if created:
                write("POST", "/v1/subscriptionOfferCodeCustomCodes", {"data": {"type": "subscriptionOfferCodeCustomCodes",
                      "attributes": {"customCode": "PROHOST2026", "numberOfCodes": 10000, "expirationDate": "2026-12-31"},
                      "relationships": {"offerCode": rel("subscriptionOfferCodes", created["data"]["id"])}}},
                      "custom code PROHOST2026 (10,000 redemptions, valid until 2026-12-31)")

# ---- promotional images (1024x1024) for the subscriptions, ios/store/promo/<product>.png
if want("promoimages"):
    group = next((g for g in get_all(f"/v1/apps/{APP}/subscriptionGroups?limit=20") if g["attributes"].get("referenceName") == META["subscriptions"]["groupReferenceName"]), None)
    subs = {x["attributes"]["productId"]: x for x in (get_all(f'/v1/subscriptionGroups/{group["id"]}/subscriptions?limit=50') if group else [])}
    for product, s in sorted(subs.items()):
        f = os.path.join(ROOT, "ios/store/promo", product.replace("prohost.", "").replace(".", "-") + ".png")
        if not os.path.exists(f):
            print(f"SKIP    {product} promotional image (no file)")
            continue
        if get_all(f'/v1/subscriptions/{s["id"]}/images?limit=5'):
            same(f"{product} promotional image")
        elif not args.apply:
            print(f"PLAN    {product} promotional image")
        else:
            _upload("subscriptionImages", {"subscription": rel("subscriptions", s["id"])}, f, f"{product} promotional image")

print("\nRESULT ", "plan only (nothing changed)" if not args.apply else ("applied with %d failure(s): %s" % (len(failures), ", ".join(failures)) if failures else "applied"))
