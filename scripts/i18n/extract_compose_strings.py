#!/usr/bin/env python3
"""Move user-facing string literals in Compose UI code into res/values/strings.xml.

Targets (composable context only):
  * the text argument of Text(...)
  * contentDescription = <expr>
  * named UI params (title, subtitle, label, description, message, text, placeholder, ...) in UI files
  * UiMessages.show(ctx, <expr>)  -> ctx.getString(...)
String templates become format args (%1$s ...). Identical texts share one key.
Usage: extract.py <repo> [--dry]
"""
import re, sys, os, glob, json, html

REPO = sys.argv[1]
DRY = '--dry' in sys.argv
SRC = os.path.join(REPO, 'app/src/main/java/com/example')
RES = os.path.join(REPO, 'app/src/main/res/values/strings.xml')
SKIP_FILE = os.path.join(os.path.dirname(__file__), 'skip.json')  # {"file": [line, ...]} literals to leave alone
skips = json.load(open(SKIP_FILE)) if os.path.exists(SKIP_FILE) else {}

UI_DIRS = ['ui/screens', 'ui/components', 'ui/navigation']
EXCLUDE = ['ui/screens/admin/', 'ui/screens/AdminConsoleScreen.kt', 'ui/screens/AdminAnalyticsScreen.kt',
           'ui/components/dialogs/SystemDebuggerDialog.kt', 'ui/components/AdminGrantAccessCard.kt',
           'ui/preview/']
NAMED = ['title', 'subtitle', 'label', 'description', 'message', 'text', 'placeholder', 'supportingText',
         'actionButtonText', 'confirmText', 'dismissText', 'helperText', 'pageTitle', 'body', 'cta', 'hint',
         'headline', 'caption', 'emptyText', 'buttonText', 'contentDescription', 'errorText', 'tooltip']

JAVA_KEYWORDS = set('abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null'.split())
existing = {}
if os.path.exists(RES):
    for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', open(RES).read(), re.S):
        existing[m.group(1)] = m.group(2)
text_to_key = {}
keys = set(existing)

def kotlin_unescape(s):
    return s.replace('\\"', '"').replace('\\$', '$').replace('\\n', '\n').replace('\\t', '\t').replace("\\'", "'").replace('\\\\', '\\')

def is_ui_text(t):
    if not re.search(r'[A-Za-z]', t): return False
    if re.fullmatch(r'[a-z0-9_.:/\-]+', t): return False          # ids, routes, keys, urls
    if re.fullmatch(r'[A-Z0-9_]+', t) and '_' in t: return False  # ENUM_NAMES
    if t.startswith(('http', 'mailto:', 'tel:', 'prohost:', 'gs://', 'image/', 'application/', 'text/')): return False
    if re.fullmatch(r'[A-Z]{1,4}', t): return False                 # "USD", "ID", "L-"
    return True

def make_key(t):
    base = re.sub(r'%\d\$[sd]', 'x', t)
    words = re.findall(r'[A-Za-z0-9]+', base.lower())
    k = '_'.join(words)[:42].strip('_') or 'text'
    if k[0].isdigit(): k = 't_' + k
    if k in JAVA_KEYWORDS: k = 'action_' + k
    cand, n = k, 2
    while cand in keys and existing.get(cand) != None and cand not in text_to_key.values():
        cand = f'{k}_{n}'; n += 1
    while cand in keys:
        cand = f'{k}_{n}'; n += 1
    keys.add(cand)
    return cand

def xml_escape(t, has_args):
    t = html.escape(t, quote=False)
    t = t.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')
    if t.startswith(('@', '?')): t = '\\' + t
    return t

def parse_template(lit):
    """Kotlin literal body -> (format text, [arg exprs]) or None if unsupported."""
    out, args, i = '', [], 0
    while i < len(lit):
        c = lit[i]
        if c == '\\' and i + 1 < len(lit):
            out += kotlin_unescape(lit[i:i+2]); i += 2; continue
        if c == '$' and i + 1 < len(lit):
            if lit[i+1] == '{':
                depth, j = 1, i + 2
                while j < len(lit) and depth:
                    if lit[j] == '{': depth += 1
                    elif lit[j] == '}': depth -= 1
                    j += 1
                expr = lit[i+2:j-1]
                if '"' in expr: return None
                args.append(expr); out += '\x00'; i = j; continue
            m = re.match(r'[A-Za-z_][A-Za-z0-9_]*', lit[i+1:])
            if m:
                args.append(m.group(0)); out += '\x00'; i += 1 + len(m.group(0)); continue
        out += c; i += 1
    return out, args

def resource_for(lit):
    p = parse_template(lit)
    if p is None: return None
    text, args = p
    if not is_ui_text(text.replace('\x00', '')): return None
    if args:
        text = text.replace('%', '%%')
        n = 0
        def rep(_):
            nonlocal n; n += 1; return f'%{n}$s'
        text = re.sub('\x00', rep, text)
    key = text_to_key.get(text)
    if key is None:
        key = make_key(text); text_to_key[text] = key
    return key, args, text

def scan_string(src, i):
    """src[i] == '"' (not a raw/triple string). Returns end index after closing quote."""
    j = i + 1
    depth = 0
    while j < len(src):
        c = src[j]
        if c == '\\': j += 2; continue
        if c == '$' and j + 1 < len(src) and src[j+1] == '{':
            # skip template expression (may contain nested strings)
            k, d = j + 2, 1
            while k < len(src) and d:
                if src[k] == '"':
                    k = scan_string(src, k); continue
                if src[k] == '{': d += 1
                elif src[k] == '}': d -= 1
                k += 1
            j = k; continue
        if c == '"': return j + 1
        if c == '\n': return -1
        j += 1
    return -1

def expr_end(src, i):
    """End of an argument expression starting at i (top-level ',' or ')')."""
    depth, j = 0, i
    while j < len(src):
        c = src[j]
        if c == '"':
            if src.startswith('"""', j): return -1
            e = scan_string(src, j)
            if e < 0: return -1
            j = e; continue
        if c in '([{': depth += 1
        elif c in ')]}':
            if depth == 0: return j
            depth -= 1
        elif c == ',' and depth == 0: return j
        j += 1
    return -1

def rewrite_expr(src, a, b, mode, ctx_var, path, line_of):
    """Replace string literals in src[a:b]. mode: 'compose' or 'context'."""
    out, j, changed = '', a, 0
    while j < b:
        c = src[j]
        if c == '"' and not src.startswith('"""', j):
            e = scan_string(src, j)
            if e < 0 or e > b: return None, 0
            lit = src[j+1:e-1]
            ln = line_of(j)
            before = src[max(0, j-40):j]
            after_line = src[e:src.find('\n', e) if src.find('\n', e) > 0 else len(src)]
            is_condition = (re.search(r'(\.(contains|startsWith|endsWith|equals|replace|split|substringBefore|substringAfter|removePrefix|removeSuffix|indexOf)\(\s*|[=!]=\s*|\bin\s+(listOf|setOf)\([^)]*)$', before)
                            or re.match(r'\s*(,\s*"[^"\n]*"\s*)*->', after_line))
            res = None if (ln in skips.get(path, []) or is_condition) else resource_for(lit)
            if res:
                key, args, _ = res
                argtxt = ''.join(', ' + x.strip() for x in args)
                if mode == 'compose':
                    out += f'stringResource(R.string.{key}{argtxt})'
                else:
                    out += f'{ctx_var}.getString(R.string.{key}{argtxt})'
                changed += 1
            else:
                out += src[j:e]
            j = e; continue
        if c == '{' and mode == 'compose':
            # a lambda inside the argument: not composable context, leave it alone
            depth, k = 1, j + 1
            while k < b and depth:
                if src[k] == '"':
                    e = scan_string(src, k)
                    if e < 0: return None, 0
                    k = e; continue
                if src[k] == '{': depth += 1
                elif src[k] == '}': depth -= 1
                k += 1
            out += src[j:k]; j = k; continue
        out += c; j += 1
    return out, changed

def process(path):
    rel = os.path.relpath(path, SRC)
    if not any(rel.startswith(d) for d in UI_DIRS + ['ui/viewmodel']): return 0
    if any(rel.startswith(x) or rel == x for x in EXCLUDE): return 0
    src = open(path).read()
    starts = [0]
    for m in re.finditer('\n', src): starts.append(m.end())
    import bisect
    line_of = lambda pos: bisect.bisect_right(starts, pos)
    edits = []  # (a, b, replacement)
    is_vm = rel.startswith('ui/viewmodel')
    if not is_vm:
        for m in re.finditer(r'(?<![A-Za-z0-9_.])Text\(\s*(text\s*=\s*)?', src):
            a = m.end()
            if src[a:a+1] != '"' and not m.group(1) and not re.match(r'(if|when)\b', src[a:]): continue
            b = expr_end(src, a)
            if b < 0: continue
            edits.append((a, b, 'compose', None))
        for m in re.finditer(r'\b(' + '|'.join(NAMED) + r')\s*=\s*', src):
            a = m.end()
            if src[a:a+1] == '{': continue
            if not (src[a:a+1] == '"' or re.match(r'(if|when)\b', src[a:])): continue
            # skip declarations: "val title = " / "var label = " / parameter defaults "title: String = "
            pre = src[max(0, m.start()-12):m.start()]
            if re.search(r'\b(val|var)\s+$', pre): continue
            b = expr_end(src, a)
            if b < 0: continue
            edits.append((a, b, 'compose', None))
    for m in re.finditer(r'UiMessages\.show\(\s*([A-Za-z_][A-Za-z0-9_.]*)\s*,\s*', src):
        a = m.end(); b = expr_end(src, a)
        if b < 0: continue
        edits.append((a, b, 'context', m.group(1)))
    # drop overlapping edits (keep the outermost first one)
    edits.sort()
    kept, last = [], -1
    for e in edits:
        if e[0] >= last: kept.append(e); last = e[1]
    total, out, pos = 0, '', 0
    for a, b, mode, ctxv in kept:
        rep, n = rewrite_expr(src, a, b, mode, ctxv, rel, line_of)
        if rep is None or n == 0: continue
        out += src[pos:a] + rep; pos = b; total += n
    out += src[pos:]
    if total and not DRY:
        if 'stringResource(' in out and 'import androidx.compose.ui.res.stringResource' not in out:
            out = re.sub(r'(\nimport )', r'\nimport androidx.compose.ui.res.stringResource\1', out, count=1)
        if 'R.string.' in out and not re.search(r'^import com\.example\.R$', out, re.M):
            out = re.sub(r'(\nimport )', r'\nimport com.example.R\1', out, count=1)
        open(path, 'w').write(out)
    return total

grand = 0
for f in sorted(glob.glob(os.path.join(SRC, 'ui/**/*.kt'), recursive=True)):
    n = process(f)
    if n: print(f'{n:4d}  {os.path.relpath(f, SRC)}')
    grand += n
print('replaced', grand, 'unique', len(text_to_key))
if not DRY:
    xml = open(RES).read() if os.path.exists(RES) else '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n'
    add = ''
    for text, key in sorted(text_to_key.items(), key=lambda kv: kv[1]):
        if key in existing: continue
        fmt = '' if re.search(r'%\d\$s', text) else (' formatted="false"' if '%' in text else '')
        add += f'    <string name="{key}"{fmt}>{xml_escape(text, bool(fmt))}</string>\n'
    xml = xml.replace('</resources>', add + '</resources>')
    open(RES, 'w').write(xml)
    json.dump(text_to_key, open(os.path.join(os.path.dirname(__file__), 'extracted.json'), 'w'), ensure_ascii=False, indent=1)
