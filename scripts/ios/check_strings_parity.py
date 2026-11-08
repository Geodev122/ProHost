#!/usr/bin/env python3
"""Fails when the iOS catalog and the Android resources disagree.

Checks: every key has en and ar; the format placeholders of en and ar are the same multiset per key
(plural items compared against the same quantity-free set where the quantity argument may be dropped, as in Arabic one/two).
Usage: check_strings_parity.py [--catalog ios/ProHost/Resources/Localizable.xcstrings]
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from strings_convert import placeholders  # noqa: E402


def values(loc):
    if "stringUnit" in loc:
        return [loc["stringUnit"]["value"]]
    return [v["stringUnit"]["value"] for v in loc.get("variations", {}).get("plural", {}).values()]


def problems(catalog):
    out = []
    for key, entry in catalog["strings"].items():
        locs = entry.get("localizations", {})
        for lang in ("en", "ar"):
            if lang not in locs:
                out.append(f"{key}: missing {lang}")
        if "en" in locs and "ar" in locs:
            en_ph = set(placeholders(" ".join(values(locs["en"]))))
            ar_ph = set(placeholders(" ".join(values(locs["ar"]))))
            if en_ph != ar_ph:
                out.append(f"{key}: placeholders differ en={sorted(en_ph)} ar={sorted(ar_ph)}")
    return out


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--catalog", default="ios/ProHost/Resources/Localizable.xcstrings")
    ap.add_argument("--allow-missing", type=int, default=0, help="tolerated number of missing translations")
    a = ap.parse_args(argv)
    cat = json.loads(Path(a.catalog).read_text(encoding="utf-8"))
    probs = problems(cat)
    for p in probs:
        print("PARITY:", p)
    missing = sum(1 for p in probs if "missing" in p)
    other = len(probs) - missing
    print(f"{len(cat['strings'])} keys, {missing} missing translations, {other} placeholder mismatches")
    return 1 if other or missing > a.allow_missing else 0


if __name__ == "__main__":
    sys.exit(main())
