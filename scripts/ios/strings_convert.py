#!/usr/bin/env python3
"""Android string resources -> iOS String Catalog (Localizable.xcstrings), keys kept identical (snake_case).

Usage: strings_convert.py [--res app/src/main/res] [--out ios/ProHost/Resources/Localizable.xcstrings]
Rules (mirror what aapt/Android does when it reads strings.xml):
  * a value wrapped in double quotes keeps its spaces, otherwise runs of whitespace collapse and the ends are trimmed;
  * escapes \\' \\" \\n \\t \\@ \\? \\\\ and \\uXXXX are resolved (so a trailing \\u0020 survives, as on Android);
  * %1$s -> %1$@, %d -> %lld (Swift Int), %% and %02d / %.1f stay; positional order is preserved;
  * <plurals> become String Catalog plural variations (zero/one/two/few/many/other);
  * translatable="false" strings are skipped. Inline markup (rare) is flattened to its text.
"""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

LANGS = {"en": "values", "ar": "values-ar"}
# Android falls back to the default (en) value when ar has no entry; the brand name is not translated.
BRAND_KEYS = {"app_name"}
PLURAL_ORDER = ["zero", "one", "two", "few", "many", "other"]
_ESC = {"n": "\n", "t": "\t", "'": "'", '"': '"', "@": "@", "?": "?", "\\": "\\"}
_FMT = re.compile(r"%%|%(\d+\$)?([-#+ 0,(]*\d*(?:\.\d+)?)(lld|[a-zA-Z@])")


def unescape(raw: str) -> str:
    out, i = [], 0
    while i < len(raw):
        c = raw[i]
        if c == "\\" and i + 1 < len(raw):
            n = raw[i + 1]
            if n == "u" and re.fullmatch(r"[0-9a-fA-F]{4}", raw[i + 2:i + 6] or ""):
                out.append(chr(int(raw[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append(_ESC.get(n, n))
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def android_text(elem) -> str:
    text = "".join(elem.itertext())
    stripped = text.strip()
    if len(stripped) >= 2 and stripped.startswith('"') and stripped.endswith('"'):
        return unescape(stripped[1:-1])
    # aapt collapses whitespace outside quotes, but escapes (\\u0020, \\n) are resolved afterwards and must survive
    collapsed = re.sub(r"\s+", " ", text).strip()
    return unescape(collapsed)


def to_ios_format(value: str) -> str:
    def repl(m):
        if m.group(0) == "%%":
            return "%%"
        pos, flags, conv = m.group(1) or "", m.group(2), m.group(3)
        if conv == "s":
            return f"%{pos}{flags}@"
        if conv == "d":
            return f"%{pos}{flags}lld"
        return m.group(0)
    return _FMT.sub(repl, value)


def placeholders(value: str):
    return sorted(m.group(0) for m in _FMT.finditer(value) if m.group(0) != "%%")


def parse(res_dir: Path, lang: str):
    folder = res_dir / LANGS[lang]
    strings, plurals = {}, {}
    sx = folder / "strings.xml"
    if sx.exists():
        for e in ET.parse(sx).getroot().findall("string"):
            if e.get("translatable") == "false" and lang != "en":
                continue
            strings[e.get("name")] = android_text(e)
    px = folder / "plurals.xml"
    if px.exists():
        for e in ET.parse(px).getroot().findall("plurals"):
            plurals[e.get("name")] = {i.get("quantity"): android_text(i) for i in e.findall("item")}
    return strings, plurals


def unit(value: str):
    return {"stringUnit": {"state": "translated", "value": to_ios_format(value)}}


def build(res_dir: Path):
    data = {lang: parse(res_dir, lang) for lang in LANGS}
    keys = sorted(set(data["en"][0]) | set(data["en"][1]) | set(data["ar"][0]) | set(data["ar"][1]))
    catalog = {"sourceLanguage": "en", "strings": {}, "version": "1.0"}
    for key in keys:
        locs = {}
        for lang in LANGS:
            strs, plur = data[lang]
            if key in strs:
                locs[lang] = unit(strs[key])
            elif key in plur:
                variants = {q: unit(plur[key][q]) for q in PLURAL_ORDER if q in plur[key]}
                locs[lang] = {"variations": {"plural": variants}}
            elif lang == "ar" and key in BRAND_KEYS and key in data["en"][0]:
                locs[lang] = unit(data["en"][0][key])
        catalog["strings"][key] = {"extractionState": "manual", "localizations": locs}
    return catalog, data


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--res", default="app/src/main/res")
    ap.add_argument("--out", default="ios/ProHost/Resources/Localizable.xcstrings")
    a = ap.parse_args(argv)
    catalog, data = build(Path(a.res))
    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(catalog, ensure_ascii=False, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    print(f"wrote {out}: {len(catalog['strings'])} keys "
          f"(en strings {len(data['en'][0])} plurals {len(data['en'][1])}; ar strings {len(data['ar'][0])} plurals {len(data['ar'][1])})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
