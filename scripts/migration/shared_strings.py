#!/usr/bin/env python3
"""Rewrite a Kotlin file's Android string lookups to the shared, multiplatform ones (plan §68 S1).

  stringResource(R.string.key, ...)          -> appString(Res.string.key, ...)
  pluralStringResource(R.plurals.key, n, ...) -> appPlural(Res.plurals.key, n, ...)
  uiText(R.string.key, ...)                   -> sharedText("key", ...)
  uiPlural(R.plurals.key, n, ...)             -> sharedPlural("key", n, ...)

On Android appString/sharedText look the same key up in Android resources, so the text, locale and formatting do not change;
iOS and the web read the Compose resources generated from the same strings.xml (scripts/i18n/to_compose_resources.py).
Imports are fixed (one `com.example.shared.resources.<key>` per key). Any other `R.string` / `R.plurals` / `R.drawable`
use (an id passed as an Int, a drawable) is left alone and listed, so a file is only moved to :shared once none remain.

Usage:  shared_strings.py FILE...          rewrite in place and print what is left
        shared_strings.py --check FILE...  print the remaining Android resource references only (exit 1 if any)
        shared_strings.py --self-test
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPOSE_STRINGS = ROOT / "shared/src/commonMain/composeResources/values/strings.xml"
RES_PKG = "com.example.shared.resources"
KEYWORDS = {
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
    "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
}

PATTERNS = [
    # (regex, replacement builder, kind, helper import)
    (re.compile(r"\bstringResource\(\s*R\.string\.([A-Za-z0-9_]+)"), lambda k: f"appString(Res.string.{acc(k)}", "string", "com.example.ui.util.appString"),
    (re.compile(r"\bpluralStringResource\(\s*R\.plurals\.([A-Za-z0-9_]+)"), lambda k: f"appPlural(Res.plurals.{acc(k)}", "plurals", "com.example.ui.util.appPlural"),
    (re.compile(r"\buiText\(\s*R\.string\.([A-Za-z0-9_]+)"), lambda k: f'sharedText("{k}"', None, "com.example.ui.util.sharedText"),
    (re.compile(r"\buiPlural\(\s*R\.plurals\.([A-Za-z0-9_]+)"), lambda k: f'sharedPlural("{k}"', None, "com.example.ui.util.sharedPlural"),
]
LEFT = re.compile(r"\bR\.(string|plurals|drawable|raw|font)\.([A-Za-z0-9_]+)")


def acc(key: str) -> str:
    return f"`{key}`" if key in KEYWORDS else key


def compose_keys():
    tree = ET.parse(COMPOSE_STRINGS)
    return {e.get("name") for e in tree.getroot() if e.tag in ("string", "plurals")}


def package_of(src: str) -> str:
    m = re.search(r"^package\s+([\w.]+)", src, re.M)
    return m.group(1) if m else ""


def add_imports(src: str, imports):
    existing = set(re.findall(r"^import\s+([\w.`]+)", src, re.M))
    new = sorted(i for i in imports if i not in existing)
    if not new:
        return src
    block = "".join(f"import {i}\n" for i in new)
    m = list(re.finditer(r"^import\s+.*\n", src, re.M))
    if m:
        pos = m[-1].end()
        return src[:pos] + block + src[pos:]
    m = re.search(r"^package\s+.*\n", src, re.M)
    pos = m.end() if m else 0
    return src[:pos] + "\n" + block + src[pos:]


def drop_unused_import(src: str, imp: str, still_used: str) -> str:
    if re.search(still_used, re.sub(r"^import .*$", "", src, flags=re.M)):
        return src
    return re.sub(rf"^import {re.escape(imp)}\n", "", src, flags=re.M)


def rewrite(src: str, keys):
    used = {"string": set(), "plurals": set()}
    helpers = set()
    missing = []

    for rx, build, kind, helper in PATTERNS:
        def sub(m, build=build, kind=kind, helper=helper):
            k = m.group(1)
            if k not in keys:
                missing.append(k)
                return m.group(0)
            helpers.add(helper)
            if kind:
                used[kind].add(k)
            return build(k)
        src = rx.sub(sub, src)

    imports = set()
    if used["string"] or used["plurals"]:
        imports.add(f"{RES_PKG}.Res")
        imports |= {f"{RES_PKG}.{acc(k)}" for k in used["string"] | used["plurals"]}
    pkg = package_of(src)
    imports |= {h for h in helpers if h.rsplit(".", 1)[0] != pkg}
    src = add_imports(src, imports)

    src = drop_unused_import(src, "androidx.compose.ui.res.stringResource", r"\bstringResource\(")
    src = drop_unused_import(src, "androidx.compose.ui.res.pluralStringResource", r"\bpluralStringResource\(")
    src = drop_unused_import(src, "com.example.ui.util.uiText", r"\buiText\(")
    src = drop_unused_import(src, "com.example.ui.util.uiPlural", r"\buiPlural\(")
    src = drop_unused_import(src, "com.example.R", r"\bR\.")
    return src, missing


def leftovers(src: str):
    code = re.sub(r"^import .*$", "", src, flags=re.M)
    return [f"R.{m.group(1)}.{m.group(2)}" for m in LEFT.finditer(code)]


def main(argv):
    if argv[:1] == ["--self-test"]:
        return self_test()
    check = argv[:1] == ["--check"]
    files = argv[1:] if check else argv
    keys = compose_keys()
    bad = 0
    for f in files:
        p = Path(f)
        src = p.read_text()
        if not check:
            new, missing = rewrite(src, keys)
            if missing:
                print(f"{f}: keys missing from Compose resources (left as is): {sorted(set(missing))}")
            if new != src:
                p.write_text(new)
            src = new
        left = leftovers(src)
        if left:
            bad += 1
            print(f"{f}: {len(left)} Android resource reference(s) left: {', '.join(sorted(set(left)))}")
        else:
            print(f"{f}: no Android resource references left")
    return 1 if (check and bad) else 0


def self_test():
    keys = {"save", "item_count", "continue", "hello"}
    src = (
        "package com.example.ui.screens\n\n"
        "import androidx.compose.ui.res.stringResource\n"
        "import androidx.compose.ui.res.pluralStringResource\n"
        "import com.example.R\n"
        "import com.example.ui.util.uiText\n\n"
        "@Composable fun A(n: Int) {\n"
        "    Text(stringResource(R.string.save))\n"
        "    Text(stringResource(R.string.continue))\n"
        "    Text(pluralStringResource(R.plurals.item_count, n, n))\n"
        "    val s = uiText(R.string.hello, \"x\")\n"
        "    Icon(painterResource(R.drawable.logo))\n"
        "}\n"
    )
    out, missing = rewrite(src, keys)
    assert not missing, missing
    assert "appString(Res.string.save)" in out
    assert "appString(Res.string.`continue`)" in out
    assert "appPlural(Res.plurals.item_count, n, n)" in out
    assert 'sharedText("hello", "x")' in out
    assert "import com.example.shared.resources.Res\n" in out
    assert "import com.example.shared.resources.save\n" in out
    assert "import com.example.shared.resources.`continue`\n" in out
    assert "import com.example.ui.util.appString\n" in out
    assert "import androidx.compose.ui.res.stringResource" not in out
    assert "import com.example.ui.util.uiText" not in out
    assert "import com.example.R\n" in out  # R.drawable still used
    assert leftovers(out) == ["R.drawable.logo"]
    out2, missing2 = rewrite("package p\nfun f() = uiText(R.string.nope)\n", keys)
    assert missing2 == ["nope"] and "uiText(R.string.nope)" in out2
    print("self-test ok")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
