#!/usr/bin/env python3
"""Regenerate the in-app Privacy Policy and Terms of Use (Android LegalContent.kt) from the website pages.

public/privacy.html and public/terms.html are the single source of truth (they are what pro-host.tech serves and what
the app stores' listings link to). Run `python3 scripts/legal/sync_legal_content.py` after editing them; it rewrites the
sections between the GENERATED markers of LegalContent.kt. `--check` exits 1 if the file is out of date (CI/NIGHTHAWK).
The consent version must move together with the effective date: see EFFECTIVE_DATE in LegalContent.kt and
CURRENT_CONSENT_VERSION in functions/src/roles/assignInitialRole.ts.
"""
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KT = ROOT / "app/src/main/java/com/example/legal/LegalContent.kt"
DOCS = {"privacyPolicy": ROOT / "public/privacy.html", "termsOfUse": ROOT / "public/terms.html"}
FIRST_SECTION = {"privacyPolicy": "Summary and What Changed", "termsOfUse": "Summary and What Changed"}
BEGIN = "// BEGIN GENERATED from {src} (scripts/legal/sync_legal_content.py)"
END = "// END GENERATED"


class Extract(HTMLParser):
    """Walks .wrap and returns [(heading, [paragraphs])]."""

    def __init__(self, first_title):
        super().__init__(convert_charrefs=True)
        self.sections = [[first_title, []]]
        self.in_wrap = False
        self.depth_wrap = 0
        self.buf = None  # (kind, [text pieces])
        self.href = None
        self.in_contact = False
        self.skip = 0
        self.div_depth = 0
        self.wrap_div_depth = None

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "div":
            self.div_depth += 1
            if "wrap" in (a.get("class") or "").split() and not self.in_wrap:
                self.in_wrap = True
                self.wrap_div_depth = self.div_depth
            elif self.in_wrap and "contact-box" in (a.get("class") or "").split():
                self.in_contact = True
                self.sections.append(["Contact", []])
            elif self.in_wrap and "doc-head" in (a.get("class") or "").split():
                self.skip += 1  # title block is rendered by LegalDocument itself
        if not self.in_wrap or self.skip:
            return
        if tag in ("h2", "h3", "p", "li"):
            self.buf = (tag, [])
        elif tag == "a":
            self.href = a.get("href")
        elif tag == "br" and self.buf:
            self.buf[1].append(" ")

    def handle_endtag(self, tag):
        if tag == "div":
            if self.skip and self.div_depth >= 1:
                # leaving doc-head
                if self.skip:
                    self.skip -= 1
            if self.in_wrap and self.div_depth == self.wrap_div_depth:
                self.in_wrap = False
            self.div_depth -= 1
        if not self.in_wrap:
            return
        if tag == "a":
            self.href = None
        if tag in ("h2", "h3", "p", "li") and self.buf and self.buf[0] == tag:
            text = re.sub(r"\s+", " ", "".join(self.buf[1])).strip()
            kind = self.buf[0]
            self.buf = None
            if not text:
                return
            if kind == "h2":
                if self.in_contact:
                    return
                self.sections.append([text, []])
            elif kind == "h3":
                if self.in_contact:
                    return
                self.sections[-1][1].append(text)
            elif kind == "li":
                self.sections[-1][1].append("• " + text)
            else:
                self.sections[-1][1].append(text)

    def handle_data(self, data):
        if self.buf is not None and self.in_wrap and not self.skip:
            self.buf[1].append(data)

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)

    def feed_link(self, text):
        return text


def parse(path, first_title):
    html = path.read_text(encoding="utf-8")
    # keep external link targets visible in plain text
    html = re.sub(
        r'<a\b[^>]*href="(https?://[^"]+)"[^>]*>(.*?)</a>',
        lambda m: f"{m.group(2)} ({m.group(1)})" if m.group(1).replace("https://", "").rstrip("/") not in re.sub(r"<[^>]+>", "", m.group(2)) else m.group(2),
        html,
        flags=re.S,
    )
    html = re.sub(r"<!--.*?-->", "", html, flags=re.S)
    p = Extract(first_title)
    p.feed(html)
    out = [(h, ps) for h, ps in p.sections if ps]
    return out


def kt_string(text):
    text = text.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    pieces, line = [], ""
    for word in text.split(" "):
        if line and len(line) + len(word) + 1 > 88:
            pieces.append(line + " ")
            line = word
        else:
            line = f"{line} {word}" if line else word
    pieces.append(line)
    pad = " " * 20
    return (" +\n" + pad).join(f'"{p}"' for p in pieces)


def emit(sections, src):
    ind = " " * 12
    lines = [f"{ind}{BEGIN.format(src=src)}"]
    for n, (heading, paras) in enumerate(sections):
        lines.append(f"{ind}LegalSection(")
        lines.append(f"{ind}    {kt_string(heading)},")
        lines.append(f"{ind}    listOf(")
        for i, para in enumerate(paras):
            comma = "," if i < len(paras) - 1 else ""
            lines.append(f"{ind}        {kt_string(para)}{comma}")
        lines.append(f"{ind}    )")
        lines.append(f"{ind}){',' if n < len(sections) - 1 else ''}")
    lines.append(f"{ind}{END}")
    return "\n".join(lines)


def main():
    check = "--check" in sys.argv
    kt = KT.read_text(encoding="utf-8")
    for val, path in DOCS.items():
        src = path.relative_to(ROOT).as_posix()
        block = emit(parse(path, FIRST_SECTION[val]), src)
        start = kt.index(f"val {val} = LegalDocument(")
        s0 = kt.index("sections = listOf(\n", start) + len("sections = listOf(\n")
        if BEGIN.split("{")[0] in kt[s0:s0 + 200]:
            e0 = kt.index(END, s0) + len(END)
        else:
            e0 = kt.index("\n        )\n    )\n", s0)
        kt = kt[:s0] + block + kt[e0:]
    if check:
        if kt != KT.read_text(encoding="utf-8"):
            print("LegalContent.kt is out of date: run python3 scripts/legal/sync_legal_content.py")
            sys.exit(1)
        print("LegalContent.kt matches the website pages")
        return
    KT.write_text(kt, encoding="utf-8")
    print("LegalContent.kt updated from", ", ".join(p.name for p in DOCS.values()))


if __name__ == "__main__":
    main()
