"""Fill App Store Connect for ProHost from ios/store/metadata.json. Idempotent; prints a plan and changes nothing unless --apply.

    python3 scripts/ios/asc_prepare.py [--apply] [--only app,categories,info,version,capability,price,availability,age,testflight,subscriptions,review,betareview,tester,offers]

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
        override = os.environ.get("ASC_AGE_OVERRIDE", "").strip()
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
    if not all(env[k] for k in ("REVIEW_FIRST_NAME", "REVIEW_LAST_NAME", "REVIEW_PHONE", "REVIEW_EMAIL")):
        print("SKIP    review contact (needs REVIEW_FIRST_NAME, REVIEW_LAST_NAME, REVIEW_PHONE, REVIEW_EMAIL)")
    else:
        attrs = {"contactFirstName": env["REVIEW_FIRST_NAME"], "contactLastName": env["REVIEW_LAST_NAME"], "contactPhone": env["REVIEW_PHONE"],
                 "contactEmail": env["REVIEW_EMAIL"], "demoAccountRequired": bool(env["REVIEW_DEMO_USER"])}
        if env["REVIEW_DEMO_USER"]:
            attrs.update({"demoAccountName": env["REVIEW_DEMO_USER"], "demoAccountPassword": env["REVIEW_DEMO_PASSWORD"]})
        notes = env["REVIEW_NOTES"] or META.get("reviewNotes", "")
        if notes:
            attrs["notes"] = notes
        detail = next((i for i in versions.get("included", []) if i["type"] == "appStoreReviewDetails"), None)
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
    if get(f"/v1/apps/{APP}/appPriceSchedule"):
        same("app price schedule")
    else:
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
            if get_all(f'/v1/subscriptions/{s["id"]}/prices?limit=5'):
                same(f'{p["productId"]} prices')
                continue
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
            ok = 0
            for terr, point in targets:
                status, data = api.request("POST", "/v1/subscriptionPrices", {"data": {"type": "subscriptionPrices", "attributes": {"preserveCurrentPrice": False},
                    "relationships": {"subscription": rel("subscriptions", s["id"]), "subscriptionPricePoint": rel("subscriptionPricePoints", point), "territory": rel("territories", terr)}}})
                ok += status in (200, 201)
                if status not in (200, 201) and ok == 0:
                    print("FAILED ", p["productId"], "price", terr, status, "; ".join(str(e.get("detail")) for e in (data or {}).get("errors", []))[:400]); failures.append(p["productId"] + " price"); break
            print(f'DONE    {p["productId"]} price ${usd}: {ok} of {len(targets)} countries')


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
        if get_all(f'/v1/subscriptions/{s["id"]}/introductoryOffers?limit=5'):
            same(f"{product} introductory offer")
            continue
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

print("\nRESULT ", "plan only (nothing changed)" if not args.apply else ("applied with %d failure(s): %s" % (len(failures), ", ".join(failures)) if failures else "applied"))
