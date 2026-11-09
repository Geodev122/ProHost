#!/usr/bin/env python3
"""Android string resources -> Compose Multiplatform resources (migration P4.3).

    python3 scripts/i18n/to_compose_resources.py            # write shared/src/commonMain/composeResources
    python3 scripts/i18n/to_compose_resources.py --check    # fail when the generated files differ from the Android ones (CI)

The Android files (app/src/main/res/values{,-ar}/{strings,plurals}.xml) stay the source of truth until every screen reads from
the Compose resources; the generator keeps keys, placeholders and plural categories identical and only changes the escaping
(Android: \\' and \\" ; Compose resources: plain characters). `formatted="false"` is dropped (the value is a literal, not a format).
"""
from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/res"
OUT = ROOT / "shared/src/commonMain/composeResources"
LOCALES = {"values": "values", "values-ar": "values-ar"}


def unescape_android(text: str) -> str:
    """Android resource escapes that Compose resources do not need: \\' \\" \\@ \\?. (\\n, \\t, \\\\ and \\uXXXX are understood by both.)"""
    return re.sub(r"\\(['\"@?])", r"\1", text)


def xml_escape(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def load(path: Path):
    if not path.exists():
        return []
    items = []
    for el in ET.parse(path).getroot():
        if el.tag == "string":
            items.append(("string", el.get("name"), unescape_android(el.text or "")))
        elif el.tag == "plurals":
            items.append(("plurals", el.get("name"), [(i.get("quantity"), unescape_android(i.text or "")) for i in el.findall("item")]))
    return items


def render(items) -> str:
    out = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for kind, name, value in items:
        if kind == "string":
            out.append(f'    <string name="{name}">{xml_escape(value)}</string>')
        else:
            out.append(f'    <plurals name="{name}">')
            for quantity, text in value:
                out.append(f'        <item quantity="{quantity}">{xml_escape(text)}</item>')
            out.append("    </plurals>")
    out.append("</resources>")
    return "\n".join(out) + "\n"


def generated() -> dict[Path, str]:
    files = {}
    for folder in LOCALES:
        items = load(SRC / folder / "strings.xml") + load(SRC / folder / "plurals.xml")
        files[OUT / folder / "strings.xml"] = render(items)
    return files


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    bad = []
    for path, content in generated().items():
        if args.check:
            if not path.exists() or path.read_text(encoding="utf-8") != content:
                bad.append(path)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            print(f"wrote {path.relative_to(ROOT)} ({content.count('<string ')} strings, {content.count('<plurals ')} plurals)")
    if bad:
        print("Compose resources are out of date; run scripts/i18n/to_compose_resources.py:", *map(str, bad), sep="\n  ", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
