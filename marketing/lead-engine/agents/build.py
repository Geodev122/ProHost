#!/usr/bin/env python3
"""Copies agents/discovery.js into lead-engine.html between the DISCOVERY markers."""
import pathlib
root = pathlib.Path(__file__).resolve().parent
page = root.parent / 'lead-engine.html'
src = (root / 'discovery.js').read_text()
s = page.read_text()
a = s.index('/* DISCOVERY:BEGIN')
b = s.index('/* DISCOVERY:END */') + len('/* DISCOVERY:END */')
s = s[:a] + src.strip() + s[b:]
page.write_text(s)
print('ok')
