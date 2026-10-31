#!/usr/bin/env python3
"""Pass 2: remaining user-facing literals in UI code -> uiText(R.string.key, args) (works outside composables)."""
import re, sys, os, glob, json, html, bisect
sys.argv = [sys.argv[0], sys.argv[1]] + sys.argv[2:]
REPO = sys.argv[1]; DRY = '--dry' in sys.argv
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'extract_compose_strings.py')).read().split("grand = 0")[0])

def xml_unescape(v):
    v = v.replace('\\n', '\n').replace("\\'", "'").replace('\\"', '"').replace('\\@', '@').replace('\\?', '?').replace('\\\\', '\\')
    return html.unescape(v)
for k, v in existing.items():
    text_to_key.setdefault(xml_unescape(v), k)

LINE_SKIP = re.compile(r'^\s*(import|package)\b|Log\.[dwiev]\(|AnalyticsTracker\.|testTag\(|TrackScreen\(|@Preview|const val|'
                       r'Crashlytics|FirebaseFirestore|\.collection\(|\.document\(|\.whereEqualTo|\.orderBy|putExtra|getStringExtra|'
                       r'animate\w*AsState|AnimatedContent|Crossfade|updateTransition|InfiniteTransition|require\(|check\(|error\(')
PRE_SKIP = re.compile(r'(SimpleDateFormat|ofPattern|\.format|Regex|toRegex|Pattern\.compile|split|replace|startsWith|endsWith|contains|'
                      r'equals|removePrefix|removeSuffix|substringBefore|substringAfter|indexOf|Intent|Uri\.parse|parse|'
                      r'setType|setAction|setPackage|getSharedPreferences|getString|getBoolean|putString|putBoolean|edit|'
                      r'createChooser\([^,]*,|ClipData\.newPlainText|newPlainText|withName|named|key)\(\s*$|[=!]=\s*$|\bin\s+(listOf|setOf)\([^)]*$')

DATA_WORDS = set('Mon Tue Wed Thu Fri Sat Sun Monday Tuesday Wednesday Thursday Friday Saturday Sunday '
                 'Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec Morning Afternoon Evening Night Lebanon Syria'.split())
EXCLUDE2 = EXCLUDE + ['ui/viewmodel/AdminViewModel.kt', 'ui/navigation/AppNavTab.kt']
# Literals compared anywhere in UI code (when branches, ==, equals/contains, in listOf) stay literal everywhere,
# so a translated assignment never meets an untranslated comparison.
COMPARED = set()
for _f in glob.glob(os.path.join(SRC, 'ui/**/*.kt'), recursive=True):
    _s = open(_f).read()
    for _m in re.finditer(r'"((?:[^"\\\n]|\\.)*)"(?=\s*(,\s*"[^"\n]*"\s*)*->)', _s): COMPARED.add(_m.group(1))
    for _m in re.finditer(r'(?:[=!]=|\.equals\(|\.contains\(|\.startsWith\(|\.endsWith\()\s*"((?:[^"\\\n]|\\.)*)"', _s): COMPARED.add(_m.group(1))
    for _m in re.finditer(r'"((?:[^"\\\n]|\\.)*)"\s*(?:[=!]=|\.equals\()', _s): COMPARED.add(_m.group(1))

def inside_call(src, j, names):
    """True when position j sits inside an open call to one of names( ... )."""
    start = max(0, j - 600)
    for m in re.finditer(r'(' + '|'.join(names) + r')\(', src[start:j]):
        k = start + m.end(); depth = 1; q = k
        while q < j and depth:
            c = src[q]
            if c == '"':
                e = scan_string(src, q)
                if e < 0 or e > j: break
                q = e; continue
            if c == '(': depth += 1
            elif c == ')': depth -= 1
            q += 1
        if depth > 0: return True
    return False

def in_data_list(src, j):
    """Inside a listOf/setOf/arrayOf with 4+ string literals: a preset of data values (amenities…), not UI copy."""
    start = max(0, j - 1500)
    for m in re.finditer(r'\b(listOf|setOf|arrayOf|mutableListOf)\(', src[start:j]):
        k = start + m.end(); depth = 1; q = k
        while q < len(src) and depth:
            c = src[q]
            if c == '"':
                e = scan_string(src, q)
                if e < 0: break
                q = e; continue
            if c == '(': depth += 1
            elif c == ')': depth -= 1
            q += 1
        if k <= j < q and src[k:q].count('"') >= 8: return True
    return False

def process2(path):
    rel = os.path.relpath(path, SRC)
    if not any(rel.startswith(d) for d in UI_DIRS + ['ui/viewmodel']): return 0
    if any(rel.startswith(x) or rel == x for x in EXCLUDE2): return 0
    src = open(path).read()
    out, i, n = '', 0, 0
    while True:
        j = src.find('"', i)
        if j < 0: out += src[i:]; break
        # skip comments
        ls = src.rfind('\n', 0, j) + 1
        line_prefix = src[ls:j]
        if '//' in line_prefix or line_prefix.lstrip().startswith('*'):
            le = src.find('\n', j); le = len(src) if le < 0 else le
            out += src[i:le]; i = le; continue
        if src.startswith('"""', j):
            e = src.find('"""', j + 3) + 3
            out += src[i:e]; i = e; continue
        e = scan_string(src, j)
        if e < 0:
            out += src[i:j+1]; i = j + 1; continue
        lit = src[j+1:e-1]
        le = src.find('\n', e); le = len(src) if le < 0 else le
        line = src[ls:le]
        before = src[max(0, j-60):j]
        after = src[e:le]
        text_preview = kotlin_unescape(re.sub(r'\$\{[^}]*\}|\$[A-Za-z_]\w*', '', lit))
        skip = (inside_call(src, j, ['AuditLog', 'recordAuditLog', 'addAuditLog', 'logAudit']) or in_data_list(src, j)
                or lit in COMPARED or text_preview.strip() in DATA_WORDS or '@Suppress' in line
                or re.fullmatch(r'[A-Z][a-z]+([A-Z][a-z0-9]+)+', text_preview.strip())
                or LINE_SKIP.search(line) or PRE_SKIP.search(before) or re.match(r'\s*(,\s*"[^"\n]*"\s*)*->', after)
                or re.match(r'\s*to\b', after) or re.search(r':\s*String\??\s*=\s*$', before)
                or not line.startswith((' ', '\t')) or '%' in lit or 'stringResource(' in before[-20:]
                or not is_ui_text(text_preview) or re.fullmatch(r'[a-z]+[A-Za-z0-9]*', text_preview)
                or not (' ' in text_preview.strip() or re.match(r'[A-Z][a-z]', text_preview.strip())))
        res = None if skip else resource_for(lit)
        if res:
            key, args, _ = res
            out += src[i:j] + f'uiText(R.string.{key}' + ''.join(', ' + a.strip() for a in args) + ')'
            n += 1
        else:
            out += src[i:e]
        i = e
    if n and not DRY:
        if 'import com.example.ui.util.uiText' not in out and 'package com.example.ui.util' not in out:
            out = re.sub(r'(\nimport )', r'\nimport com.example.ui.util.uiText\1', out, count=1)
        if not re.search(r'^import com\.example\.R$', out, re.M):
            out = re.sub(r'(\nimport )', r'\nimport com.example.R\1', out, count=1)
        open(path, 'w').write(out)
    return n

before_keys = set(text_to_key.values())
grand = 0
for f in sorted(glob.glob(os.path.join(SRC, 'ui/**/*.kt'), recursive=True)):
    c = process2(f)
    if c: print(f'{c:4d}  {os.path.relpath(f, SRC)}')
    grand += c
new = {t: k for t, k in text_to_key.items() if k not in before_keys}
print('replaced', grand, 'new keys', len(new))
if DRY:
    for t in list(new)[:400]: print('  +', repr(t))
else:
    xml = open(RES).read(); add = ''
    for text, key in sorted(new.items(), key=lambda kv: kv[1]):
        fmt = '' if re.search(r'%\d\$s', text) else (' formatted="false"' if '%' in text else '')
        add += f'    <string name="{key}"{fmt}>{xml_escape(text, bool(fmt))}</string>\n'
    open(RES, 'w').write(xml.replace('</resources>', add + '</resources>'))
    json.dump(sorted(new.items(), key=lambda kv: kv[1]), open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'new2.json'), 'w'), ensure_ascii=False, indent=0)
