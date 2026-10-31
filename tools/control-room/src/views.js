/* ---- Views ---- */
const VIEWS = [['dash', 'Overview'], ['brand', 'Brand'], ['platforms', 'Platforms'], ['agent', 'Agent'], ['rules', 'Rules'], ['know', 'Knowledge'], ['inbox', 'Inbox'], ['sim', 'Simulator']];
const STATUS_CHIP = { sent: 'good', 'dry-run': 'warn', failed: 'bad', paused: 'bad', flagged: 'bad', ignored: 'mute' };
const chip = (t, c) => '<span class="chip ' + (c || '') + '">' + esc(t) + '</span>';
const statusChip = s => chip(s || '—', STATUS_CHIP[s] || 'mute');
const kindLabel = k => ({ comment: 'Comment', story_reply: 'Story reply', story_mention: 'Story mention', dm: 'DM', postback: 'Button' }[k] || k);
const val = (k, d) => { const v = S.B.status[k]; return esc(v != null && v !== '' ? v : (d == null ? '' : d)); };
const isOn = k => /^(true|yes|1|on)$/i.test(S.B.status[k] || '');
const aiOn = () => isOn('ai_enabled');
const fld = (label, id, value, o) => { o = o || {}; return '<label class="f' + (o.wide ? ' wide' : '') + '">' + label + (o.hint ? ' <small>' + o.hint + '</small>' : '') + (o.area ? '<textarea id="' + id + '" rows="' + (o.rows || 4) + '"' + (o.ph ? ' placeholder="' + esc(o.ph) + '"' : '') + '>' + esc(value) + '</textarea>' : '<input type="' + (o.type || 'text') + '" id="' + id + '" value="' + esc(value) + '"' + (o.ph ? ' placeholder="' + esc(o.ph) + '"' : '') + (o.max ? ' maxlength="' + o.max + '"' : '') + (o.type === 'password' ? ' autocomplete="new-password" spellcheck="false"' : '') + '>') + '</label>'; };
const sw = (act, on, label, data) => '<label class="row"><span class="sw"><input type="checkbox" data-act="' + act + '"' + (data ? ' ' + data : '') + (on ? ' checked' : '') + ' aria-label="' + esc(label) + '"><i></i></span><span>' + esc(label) + '</span></label>';
const g = id => ($(id) ? $(id).value.trim() : '');

function modePill() {
  const m = effectiveMode();
  return m === 'live' ? '<span class="pill live">LIVE</span>' : m === 'kill' ? '<span class="pill kill">KILL SWITCH ON</span>' : '<span class="pill dry">DRY-RUN</span>';
}
function renderHeader() {
  return '<header class="brandbar"><div class="bb"><span class="tile">' + (S.brand === LEGACY ? '<img alt="" src="data:image/png;base64,__LOGO__">' : '<b class="mark">' + esc((cur().name || '?').charAt(0).toUpperCase()) + '</b>') + '</span><span class="word">Control<i>Room</i></span>' +
    '<label class="bsel"><span class="mini" style="color:#C9D3E0">Brand</span><select id="brandsel" aria-label="Brand">' + S.brands.map(b => '<option value="' + esc(b.id) + '"' + (b.id === S.brand ? ' selected' : '') + '>' + esc(b.name) + (b.enabled ? '' : ' (off)') + '</option>').join('') + '</select></label>' +
    '<button class="btn sm" data-act="newbrand" style="background:transparent;color:#fff;border-color:var(--navy-line)">+ New brand</button>' +
    '<div class="pills">' + (S.loaded ? modePill() : '') + '<span class="pill">' + (S.busy ? 'syncing…' : S.syncedAt ? 'updated ' + ago(S.syncedAt) : 'not loaded') + '</span><button class="btn sm" data-act="refresh" style="background:transparent;color:#fff;border-color:var(--navy-line)">Refresh</button></div></div></header>' +
    '<nav class="nav" aria-label="Sections"><div class="nav-in">' + VIEWS.map(v => '<button data-view="' + v[0] + '"' + (S.view === v[0] ? ' aria-current="page"' : '') + '>' + v[1] + '</button>').join('') + '</div></nav>';
}

/* ---- Overview ---- */
function stats(windowMs) {
  const from = Date.now() - windowMs, rows = S.B.log.filter(r => r.t >= from), c = { total: rows.length, sent: 0, 'dry-run': 0, failed: 0, ignored: 0, paused: 0 };
  rows.forEach(r => { if (c[r.status] != null) c[r.status]++; });
  c.people = new Set(rows.map(r => r.person)).size; return c;
}
function platformState(p) {
  const row = platformRow(S.brand, p), st = row ? row.status : '';
  return { row, ok: /^connected/i.test(st), text: st || 'not set up' };
}
function dashView() {
  if (!S.loaded) return '';
  if (!S.B.ready) return notReady();
  const a = stats(864e5), w = stats(7 * 864e5), st = S.B.status, m = effectiveMode();
  const daysLeft = st.health_token_days_left === '' || st.health_token_days_left == null ? null : Number(st.health_token_days_left);
  const lastEv = S.B.log[0] ? S.B.log[0].t : 0, enabled = S.B.rules.filter(r => r.enabled).length;
  const days = []; for (let i = 6; i >= 0; i--) { const d0 = new Date(); d0.setUTCHours(0, 0, 0, 0); const s = d0.getTime() - i * 864e5, rs = S.B.log.filter(r => r.t >= s && r.t < s + 864e5); days.push({ label: new Date(s).toLocaleDateString([], { weekday: 'short', timeZone: 'UTC' }), sent: rs.filter(r => r.status === 'sent').length, dry: rs.filter(r => r.status === 'dry-run').length, bad: rs.filter(r => r.status === 'failed' || r.status === 'paused').length, ign: rs.filter(r => r.status === 'ignored').length, n: rs.length }); }
  const max = Math.max(1, ...days.map(d => d.n));
  const byRule = {}; S.B.log.filter(r => r.t >= Date.now() - 7 * 864e5 && r.rule).forEach(r => { byRule[r.rule] = (byRule[r.rule] || 0) + 1; });
  const top = Object.entries(byRule).sort((x, y) => y[1] - x[1]).slice(0, 6), topMax = top.length ? top[0][1] : 1;
  const ig = platformState('instagram'), wa = platformState('whatsapp');
  const kb = (S.B.profile.knowledge_base || '').length;
  const steps = [
    [true, 'Control-room sheet connected', 'Tabs for ' + cur().name + ' are readable.'],
    [!!(S.B.profile.name || '').trim() && kb > 200, 'Brand profile and knowledge filled in', kb > 200 ? (kb + ' characters of knowledge') : 'Add what the business does under Brand, or build it from your website under Brand › Sources.'],
    [ig.ok || wa.ok, 'A platform is connected', 'Instagram: ' + ig.text + ' · WhatsApp: ' + wa.text],
    [S.B.log.length > 0, 'Messages are arriving', S.B.log.length ? 'Last event ' + ago(lastEv) : 'Send a test message to the connected account, then press Refresh.'],
    [enabled > 0 || aiOn(), 'A rule or the AI agent is switched on', enabled + ' of ' + S.B.rules.length + ' rules on · AI agent ' + (aiOn() ? 'on' : 'off')],
    [daysLeft == null || daysLeft >= 14, 'Access token healthy', daysLeft == null ? 'No expiry known.' : daysLeft + ' days left'],
    [m === 'live', 'Mode is LIVE', m === 'live' ? 'Replies are really sent.' : m === 'kill' ? 'Kill switch is on.' : 'Dry-run: replies are logged, not sent.'],
  ];
  return (S.B.ruleErrors.length || st.health_rule_errors ? '<div class="banner warn"><b>Some rule rows are ignored:</b> ' + esc(S.B.ruleErrors.concat(st.health_rule_errors ? [st.health_rule_errors] : []).join(' • ')) + '</div>' : '') +
    (st.health_profile_errors ? '<div class="banner warn"><b>Profile tab:</b> ' + esc(st.health_profile_errors) + '</div>' : '') +
    (st.health_last_api_error ? '<div class="banner bad"><b>Last platform error:</b> ' + esc(explainError(st.health_last_api_error)) + '</div>' : '') +
    (m === 'dry' ? '<div class="banner info"><b>Dry-run.</b> Nothing is sent yet. The log shows what would be sent. Switch to LIVE under Agent when you are happy.</div>' : '') +
    '<div class="grid"><div class="stat"><b>' + a.total + '</b><span>events, last 24 h</span></div><div class="stat good"><b>' + a.sent + '</b><span>replies sent</span></div><div class="stat warn"><b>' + a['dry-run'] + '</b><span>would send (dry-run)</span></div><div class="stat ' + (a.failed + a.paused ? 'bad' : '') + '"><b>' + (a.failed + a.paused) + '</b><span>failed or paused</span></div><div class="stat"><b>' + a.people + '</b><span>people, 24 h</span></div><div class="stat"><b>' + w.total + '</b><span>events, 7 days</span></div></div>' +
    '<div class="card"><div class="row"><h3>Last 7 days</h3></div><div class="bars" role="img" aria-label="Events per day">' +
    days.map(d => '<div><div class="col" style="height:' + (d.n ? Math.max(6, Math.round(d.n / max * 82)) : 3) + 'px">' + [[d.sent, 'var(--good)'], [d.dry, 'var(--warn)'], [d.bad, 'var(--bad)'], [d.ign, 'var(--line2)']].map(([n, c]) => n ? '<span style="flex:' + n + ';background:' + c + '"></span>' : '').join('') + '</div><span>' + d.n + '</span><span>' + d.label + '</span></div>').join('') + '</div></div>' +
    '<div class="form"><div class="card"><h3>Top rules, 7 days</h3>' + (top.length ? top.map(([n, c]) => '<div style="margin-top:10px"><div class="row"><span>' + esc(n) + '</span><b class="sp num">' + c + '</b></div><div class="bar" style="width:' + Math.round(c / topMax * 100) + '%"></div></div>').join('') : '<p class="empty">No rule has replied yet.</p>') +
    '<div class="mini" style="margin-top:12px">AI replies today: <b>' + esc(st.health_ai_replies_today || '0') + '</b> · tokens ' + esc(st.health_ai_tokens_today || '-') + '</div></div>' +
    '<div class="card"><h3>Go-live checklist</h3><ul class="steps" style="margin-top:10px">' + steps.map(s => '<li><span class="dot ' + (s[0] ? 'ok' : 'no') + '">' + (s[0] ? '✓' : '!') + '</span><div><b>' + esc(s[1]) + '</b><div class="mini">' + esc(s[2]) + '</div></div></li>').join('') + '</ul></div></div>' +
    '<div class="card mini">Backend sync ' + (st.health_synced_at ? esc(st.health_synced_at) + ' UTC' : 'has not run yet') + ' · sent this hour ' + esc(st.health_sent_this_hour || '0') + ' · hourly cap ' + esc(String(engineSettings().hourlyCap)) + ' · <a href="' + SHEET_URL + '" target="_blank" rel="noopener">open the sheet</a></div>';
}
function notReady() {
  return '<div class="card"><h3>Setting up ' + esc(cur().name) + '…</h3><p class="mini" style="margin-top:6px">The server creates this brand\'s tabs in the sheet within about a minute (it runs every minute). Press Refresh to check. Nothing else is needed.</p><div class="row" style="margin-top:10px"><button class="btn pri" data-act="refresh">Refresh</button></div></div>';
}

/* ---- Brand ---- */
const INTEG = [['integration_offers', 'Live Premium offers from Google Play'], ['integration_listings', 'Live listings catalogue and space finder'], ['integration_account_lookup', 'App account lookup by email'], ['integration_release', 'Release notes ("what\'s new")']];
function brandView() {
  if (!S.loaded) return '';
  if (!S.B.ready) return notReady();
  const P = S.B.profile, isPro = S.brand === LEGACY, kb = P.knowledge_base || '';
  const needBuild = S.B.status.build_knowledge || '';
  return '<h2>' + esc(cur().name) + '</h2><p class="mini">Everything the assistant knows about this business. Saved to the sheet; the server applies it within a minute.</p>' +
    '<div class="card"><h3>Identity</h3><div class="form" style="margin-top:10px">' +
    fld('Brand name', 'b_name', P.name || cur().name) + fld('Assistant name <small>how it introduces itself, e.g. "Acme\'s automated assistant"</small>', 'b_aname', P.assistant_name || '') +
    fld('Contact email <small>alerts and the fallback reply</small>', 'b_email', P.contact_email || '', { type: 'text' }) + fld('Languages <small>comma separated, e.g. en, ar, fr</small>', 'b_langs', P.languages || 'en') +
    fld('Purpose <small>one sentence: what the assistant helps people with</small>', 'b_purpose', P.purpose || '', { wide: true, area: true, rows: 2 }) +
    fld('Voice and selling approach <small>how it sounds and sells; cannot override the locked rules (Agent › Locked core)</small>', 'b_persona', P.persona || '', { wide: true, area: true, rows: 7 }) +
    '</div><div class="row" style="margin-top:12px"><span class="mini sp">Name, assistant name, contact, languages, purpose and voice</span><button class="btn pri" data-act="saveidentity">Save identity</button></div></div>' +
    '<div class="card"><div class="row"><h3>Business knowledge</h3><span class="sp mini num" id="kbcount">' + kb.length + ' characters</span></div><p class="mini" style="margin:4px 0 8px">What the assistant answers from: products, how it works, prices only as stated, contact, policies. Build it from your sources below, or write it yourself. Without knowledge the assistant only says it does not know.</p>' +
    '<textarea id="b_kb" rows="14" style="width:100%">' + esc(kb) + '</textarea><div class="row" style="margin-top:10px"><span class="mini sp">Max about 45,000 characters</span><button class="btn pri" data-act="saveknowledgebase">Save knowledge</button></div></div>' +
    sourcesCard(needBuild) + draftCard() + buttonsCard() +
    '<div class="card"><h3>Data providers</h3><p class="mini" style="margin:4px 0 8px">' + (isPro ? 'ProHost-only connections to its own app data. They stay on for ProHost.' : 'These read ProHost\'s own app data (Google Play offers, listings, accounts). They are off for other brands and cannot be turned on from here.') + '</p>' +
    INTEG.map(([k, label]) => '<div class="row" style="margin-top:6px">' + (isPro ? sw('integ', !/^false$/i.test(P[k] || 'TRUE'), label, 'data-k="' + k + '"') : '<span class="dot no">–</span><span class="muted">' + esc(label) + ': off</span>') + '</div>').join('') + '</div>';
}
function sourcesCard(needBuild) {
  const rows = S.B.sources;
  return '<div class="card"><div class="row"><h3>Knowledge sources</h3><span class="sp"></span><button class="btn sm" data-act="addsource">+ Add source</button></div>' +
    '<p class="mini" style="margin:4px 0 8px"><b>url</b> a public https page · <b>repo</b> a public GitHub repo (or <span class="mono">…repo#docs/FAQ.md</span>) · <b>drive</b> a Google Doc, PDF or Word file shared with the service account · <b>text</b> pasted text.</p>' +
    (rows.length ? rows.map((s, i) => '<div class="srcrow" data-src="' + i + '"><select data-f="type" aria-label="Type">' + ['url', 'repo', 'drive', 'text'].map(t => '<option' + (s.type === t ? ' selected' : '') + '>' + t + '</option>').join('') + '</select><input type="text" data-f="ref" value="' + esc(s.ref) + '" placeholder="https://… or text" aria-label="Link or text"><input type="text" data-f="note" value="' + esc(s.note) + '" placeholder="Label" aria-label="Label"><button class="btn sm danger" data-act="delsource" data-i="' + i + '" aria-label="Remove source">✕</button>' + (s.status ? '<div class="mini srcstat' + (/^FAILED/.test(s.status) ? ' bad' : '') + '">' + esc(s.status) + '</div>' : '') + '</div>').join('') : '<p class="empty">No sources yet.</p>') +
    '<div class="row" style="margin-top:12px"><span class="mini sp">' + (/^(request|working)/i.test(needBuild) ? 'Building…' : needBuild ? esc(needBuild) : '') + '</span><button class="btn" data-act="savesources">Save sources</button><button class="btn cta" data-act="buildknowledge"' + (S.building ? ' disabled' : '') + '>Build knowledge</button></div></div>';
}
function draftCard() {
  const d = S.B.draft;
  if (!d.length) return '';
  const pending = d.filter(x => x.status === 'draft').length;
  return '<div class="card"><div class="row"><h3>Review the draft</h3><span class="sp mini">' + pending + ' to review</span></div><p class="mini" style="margin:4px 0 8px">The AI drafted this from your sources. Edit anything, then approve the sections that are right. Approved sections become the live knowledge within a minute. Nothing reaches the assistant until you approve it.</p>' +
    d.map(x => '<div class="draft" data-draft="' + x.row + '"><div class="row"><b>' + esc(x.section) + '</b>' + chip(x.status || 'draft', x.status === 'applied' ? 'good' : x.status === 'approved' ? 'warn' : 'mute') + '<span class="mini sp">' + esc(x.sources) + '</span></div><textarea data-f="text" rows="4">' + esc(x.text) + '</textarea><div class="row"><button class="btn sm pri" data-act="approve" data-row="' + x.row + '">Approve</button><button class="btn sm" data-act="draftsave" data-row="' + x.row + '">Save edit</button><button class="btn sm danger" data-act="reject" data-row="' + x.row + '">Reject</button></div></div>').join('') + '</div>';
}
function buttonsCard() {
  const b = S.B.buttons;
  return '<div class="card"><div class="row"><h3>Buttons</h3><span class="sp"></span><button class="btn sm" data-act="addbutton">+ Add button</button></div><p class="mini" style="margin:4px 0 8px">Links the assistant can attach to a message (a Book, Website or WhatsApp button). Labels max 20 characters, https links only. Leave a language empty to use English.</p>' +
    (b.length ? b.map((x, i) => '<div class="btnrow" data-btn="' + i + '"><input type="text" data-f="id" value="' + esc(x.id) + '" placeholder="id e.g. book" aria-label="Button id"><input type="text" data-f="url" value="' + esc(x.url) + '" placeholder="https://…" aria-label="Link"><input type="text" data-f="en" value="' + esc(x.labels.en) + '" placeholder="English label" maxlength="20" aria-label="English label"><input type="text" data-f="ar" value="' + esc(x.labels.ar) + '" placeholder="Arabic" maxlength="20" aria-label="Arabic label" dir="rtl"><input type="text" data-f="fr" value="' + esc(x.labels.fr) + '" placeholder="French" maxlength="20" aria-label="French label"><input type="text" data-f="es" value="' + esc(x.labels.es) + '" placeholder="Spanish" maxlength="20" aria-label="Spanish label"><button class="btn sm danger" data-act="delbutton" data-i="' + i + '" aria-label="Remove button">✕</button></div>').join('') : '<p class="empty">No buttons yet.</p>') +
    '<div class="row" style="margin-top:12px"><span class="mini sp"></span><button class="btn pri" data-act="savebuttons">Save buttons</button></div></div>';
}

/* ---- Platforms ---- */
const PLAT = {
  instagram: { name: 'Instagram', blurb: 'Comments, story replies and DMs of one Instagram professional account (Instagram login API).', idLabel: 'Instagram account id', idHint: 'filled in automatically when you save a valid access token' },
  whatsapp: { name: 'WhatsApp Business', blurb: 'Chats on one WhatsApp Business Cloud API number. Free-form replies work for 24 hours after the person last wrote.', idLabel: 'Phone number id', idHint: 'from Meta › WhatsApp › API setup (not the phone number itself)' },
};
function secretLine(brand, platform, key, label) {
  const info = secretInfo(brand, platform, key), st = info ? info.status : '';
  const good = /^set /.test(st), pend = /^pending/.test(st), bad = /^REJECTED/.test(st);
  return '<div class="secret"><label class="f">' + label + '<span class="row"><input type="password" id="sec_' + platform + '_' + key + '" autocomplete="new-password" spellcheck="false" placeholder="' + (good ? 'saved — paste a new value to replace' : 'paste it here') + '">' + (key === 'verify_token' ? '<button class="btn sm" type="button" data-act="gentoken" data-target="sec_' + platform + '_' + key + '">Generate</button>' : '') + '</span></label>' + (st ? '<div class="mini ' + (bad ? 'bad' : good ? 'okc' : '') + '">' + chip(bad ? 'rejected' : good ? 'saved' : pend ? 'pending' : '—', bad ? 'bad' : good ? 'good' : 'warn') + ' ' + esc(st) + '</div>' : '<div class="mini">' + chip('not set', 'mute') + '</div>') + '</div>';
}
function platformCard(key) {
  const meta = PLAT[key], row = platformRow(S.brand, key) || { brandId: S.brand, platform: key, accountId: '', appId: '', wabaId: '', phoneId: '', templateName: '', templateLang: 'en', enabled: true, status: '', row: 0 };
  const st = platformState(key), wa = key === 'whatsapp';
  return '<div class="card" data-plat="' + key + '"><div class="row"><h3>' + meta.name + '</h3>' + chip(st.ok ? 'connected' : 'not connected', st.ok ? 'good' : 'warn') + '<span class="sp mini">' + esc(st.row ? st.row.status : '') + '</span></div><p class="mini" style="margin:4px 0 10px">' + meta.blurb + '</p>' +
    '<div class="form">' + (wa ? fld('Phone number id <small>' + meta.idHint + '</small>', 'p_' + key + '_phone', row.phoneId) + fld('WhatsApp Business account id', 'p_' + key + '_waba', row.wabaId) : fld(meta.idLabel + ' <small>' + meta.idHint + '</small>', 'p_' + key + '_acct', row.accountId)) +
    fld('App ID <small>empty = the shared Meta app</small>', 'p_' + key + '_app', row.appId) + (wa ? fld('Template name <small>approved template for messages outside 24 h</small>', 'p_' + key + '_tpl', row.templateName) + fld('Template language', 'p_' + key + '_lang', row.templateLang) : '') + '</div>' +
    '<div class="row" style="margin-top:10px">' + sw('platenabled', row.enabled, 'Enabled', 'data-p="' + key + '"') + '<span class="sp"></span><button class="btn pri" data-act="saveplatform" data-p="' + key + '">Save platform</button></div>' +
    '<h3 style="margin-top:18px">Credentials</h3><p class="mini" style="margin:4px 0 8px">Typed here, taken in by the server within a minute, checked with Meta, stored server-side only, and then <b>erased from the sheet</b>. The page never shows them again. Leave a field empty to keep the saved value.</p>' +
    '<div class="form">' + SECRET_KEYS[key].map(k => secretLine(S.brand, key, k, { access_token: 'Access token', app_secret: 'App secret (app\'s Basic settings)', verify_token: 'Webhook verify token (you choose it)' }[k])).join('') + '</div>' +
    '<div class="row" style="margin-top:10px"><span class="mini sp">Secrets sit in your Google Sheet only until the server collects them; sheet version history may keep a copy in your Drive.</span><button class="btn pri" data-act="savesecrets" data-p="' + key + '">Save credentials</button></div>' +
    '<h3 style="margin-top:18px">Connect it in Meta</h3><ul class="steps" style="margin-top:8px"><li><span class="dot no">1</span><div>Callback URL <span class="copy">' + WEBHOOKS[key] + '</span> <button class="btn sm" data-act="copy" data-t="' + WEBHOOKS[key] + '">Copy</button></div></li><li><span class="dot no">2</span><div>Verify token: the same value you saved above.</div></li><li><span class="dot no">3</span><div>' + (wa ? 'Subscribe the app to the <b>messages</b> field of the WhatsApp Business Account.' : 'Subscribe to <b>comments, messages, messaging_postbacks</b>; the server also subscribes the account when a valid token is saved.') + '</div></li></ul></div>';
}
function platformsView() {
  if (!S.loaded) return '';
  return '<h2>Platforms of ' + esc(cur().name) + '</h2><p class="mini">Each platform has its own account, app and credentials. A brand can use one or both; the same assistant and rules answer on both (a rule can be limited to one platform).</p>' + platformCard('instagram') + platformCard('whatsapp');
}

/* ---- Agent ---- */
function agentView() {
  if (!S.loaded) return '';
  if (!S.B.ready) return notReady();
  const st = S.B.status, es = engineSettings(), tz = -new Date().getTimezoneOffset();
  const vars = Object.keys(st).filter(k => k.startsWith('var_')).map(k => k.slice(4) + ' = ' + st[k]).join('\n');
  const prov = st.ai_provider || 'vertex';
  const keyNeeded = prov === 'gemini' ? 'gemini_key' : prov === 'anthropic' ? 'anthropic_key' : '';
  return '<div class="row"><h2>Agent</h2><span class="sp"></span>' + sw('aitoggle', aiOn(), aiOn() ? 'AI agent on' : 'AI agent off') + '</div>' +
    '<p class="mini">Answers DMs no keyword rule handled, in the language the person writes, from this brand\'s knowledge. It always says it is an AI. Quiet while the kill switch is on; logs without sending in dry-run.</p>' +
    '<div class="card"><h3>Mode</h3><p class="mini" style="margin:4px 0 10px">Dry-run records what would be sent. LIVE sends real replies to real people.</p><div class="row"><div class="seg" role="group" aria-label="Mode"><button data-act="mode" data-m="dry-run" aria-pressed="' + ((st.mode || '').toLowerCase() !== 'live') + '">Dry-run</button><button data-act="mode" data-m="live" aria-pressed="' + ((st.mode || '').toLowerCase() === 'live') + '">Live</button></div>' +
    '<label class="row" style="margin-left:auto"><span><b>Kill switch</b><br><span class="mini">Stops all sending within a minute</span></span><span class="sw"><input type="checkbox" data-act="kill"' + (isOn('kill_switch') ? ' checked' : '') + ' aria-label="Kill switch"><i></i></span></label></div>' +
    (S.confirmLive ? '<div class="banner warn" style="margin-top:12px"><b>Go LIVE for ' + esc(cur().name) + '?</b> Matching messages will get real replies. Turn off any other automation tool on the same account first or people get two answers.<div class="row" style="margin-top:8px"><button class="btn cta" data-act="golive">Yes, go live</button><button class="btn" data-act="cancellive">Not yet</button></div></div>' : '') + '</div>' +
    '<div class="card"><h3>AI settings</h3><div class="form" style="margin-top:10px"><label class="f">Provider<select id="ai_provider"><option value="vertex"' + (prov === 'vertex' ? ' selected' : '') + '>Claude on Google Vertex AI (no key)</option><option value="anthropic"' + (prov === 'anthropic' ? ' selected' : '') + '>Anthropic API key</option><option value="gemini"' + (prov === 'gemini' ? ' selected' : '') + '>Gemini API key</option></select></label>' +
    fld('Model', 'ai_model', st.ai_model || '', { ph: 'claude-haiku-4-5@20251001' }) + fld('Vertex region', 'ai_region', st.ai_region || '', { ph: 'global' }) + fld('Max AI replies per day', 'ai_cap', st.ai_daily_cap || '', { type: 'number', ph: '300' }) + fld('Max AI replies per person per hour', 'ai_pp', st.ai_per_person_per_hour || '', { type: 'number', ph: '12' }) +
    '<div class="f">Public comments<div class="row">' + sw('aicomments', !/^(false|no|0|off)$/i.test(st.ai_comments || 'TRUE'), 'Answer by intent') + sw('aicommentdm', !/^(false|no|0|off)$/i.test(st.ai_comment_dm || 'TRUE'), 'Thank + one opening DM') + '</div></div></div>' +
    (keyNeeded ? '<div class="form" style="margin-top:12px">' + secretLine(S.brand, 'ai', keyNeeded, prov === 'gemini' ? 'Gemini API key' : 'Anthropic API key') + '</div><div class="row" style="margin-top:6px"><button class="btn" data-act="savesecrets" data-p="ai">Save key</button></div>' : '') +
    '<div class="row" style="margin-top:12px"><span class="mini sp">Saved to the Status tab; live within a minute.</span><button class="btn pri" data-act="aisave">Save AI settings</button></div></div>' +
    playbooksCard() + coreCard() +
    '<div class="card"><h3>Replies and safety</h3><div class="form" style="margin-top:10px">' +
    fld('Max replies per hour (all platforms)', 'k_cap', st.hourly_cap || '', { type: 'number', ph: String(es.hourlyCap) }) + fld('Pause for a human (hours)', 'k_hh', st.handoff_hours || '', { type: 'number', ph: String(es.handoffHours) }) +
    fld('Active from hour <small>0-23, empty = always</small>', 'k_af', st.active_from || '', { type: 'number' }) + fld('Active until hour', 'k_at', st.active_to || '', { type: 'number' }) +
    fld('Time zone, minutes from UTC <small>this browser: ' + tz + '</small>', 'k_tz', st.utc_offset_minutes || '', { type: 'number', ph: '0' }) + '<span></span>' +
    fld('Opt-out words <small>comma separated; the person gets no more automatic messages</small>', 'k_oo', st.opt_out_words || '', { wide: true, ph: es.optOutWords.join(', ') }) +
    fld('Opt-out confirmation', 'k_oor', st.opt_out_reply || '', { wide: true, area: true, rows: 2, ph: es.optOutReply }) +
    fld('Ask-for-a-human words', 'k_hw', st.handoff_words || '', { wide: true, ph: es.handoffWords.join(', ') }) +
    fld('Hand-over reply', 'k_hr', st.handoff_reply || '', { wide: true, area: true, rows: 2, ph: es.handoffReply }) +
    fld('Variables <small>one per line, name = value; use as {name} in rules</small>', 'k_vars', vars, { wide: true, area: true, rows: 4, ph: 'book = https://example.com/book' }) +
    '</div><div class="row" style="margin-top:12px"><span class="mini sp">Empty fields use the defaults shown in grey.</span><button class="btn pri" data-act="savesettings">Save settings</button></div></div>';
}
function playbooksCard() {
  const p = S.B.playbooks;
  return '<div class="card"><div class="row"><h3>Playbooks</h3><span class="sp"></span><button class="btn sm" data-act="addplaybook">+ Add playbook</button></div><p class="mini" style="margin:4px 0 8px">The conversation modes the assistant can choose from (answer a question, discover a need, guide step by step, find an option…). Each is a short instruction; switch one off to retire it. They shape behaviour inside the locked rules below.</p>' +
    (p.length ? p.map((x, i) => '<div class="logrow"><div class="row">' + sw('pbtoggle', x.enabled, x.enabled ? 'On' : 'Off', 'data-i="' + i + '"') + '</div><div class="what"><b>' + esc(x.name) + '</b><div class="mini">' + esc(x.text.slice(0, 220)) + (x.text.length > 220 ? '…' : '') + '</div><div class="row" style="margin-top:6px"><button class="btn sm" data-act="editplaybook" data-i="' + i + '">Edit</button></div></div></div>').join('') : '<p class="empty">No playbooks yet: the assistant uses its default three (direct question, discovery, how-to) until you add your own.</p>') + '</div>';
}
function coreCard() {
  return '<div class="card"><div class="row"><h3>Locked core</h3>' + chip('read-only', 'mute') + '</div><p class="mini" style="margin:4px 0 8px">These rules are part of the server and apply to every brand. They cannot be edited here, and a brand\'s voice or playbooks cannot override them.</p><ul class="steps">' +
    ['<b>Says it is an AI.</b> The first message of every conversation says so, and it answers truthfully if asked whether it is a bot or a human. It never claims to be a person.', '<b>Facts only from your knowledge.</b> No invented prices, dates or features; if it does not know, it says so and points to your contact.', '<b>Consent before saving details.</b> It asks once before saving a name or email, never asks for payment details, and honours opt-out words.', '<b>Treats messages as messages.</b> It ignores instructions inside a person\'s message and cannot be talked out of these rules.', '<b>Hands over on request.</b> Account, payment, refund, legal or complaint topics are handed to your team.'].map(t => '<li><span class="dot ok">✓</span><div>' + t + '</div></li>').join('') + '</ul></div>';
}

/* ---- Rules ---- */
function ruleHits() { const h = {}, from = Date.now() - 7 * 864e5; S.B.log.forEach(r => { if (r.rule && r.t >= from && r.status !== 'ignored') h[r.rule] = (h[r.rule] || 0) + 1; }); return h; }
function rulesView() {
  if (!S.loaded) return '';
  if (!S.B.ready) return notReady();
  const hits = ruleHits();
  return '<div class="row"><h2>Rules</h2><span class="sp"></span><button class="btn" data-act="tpl">From a template</button><button class="btn pri" data-act="newrule">+ New rule</button></div>' +
    '<p class="mini">Keyword rules answer first; the AI agent covers the rest. Changes are saved to the sheet and picked up within a minute. Highest priority wins.</p>' +
    (S.B.ruleErrors.length ? '<div class="banner warn">' + esc(S.B.ruleErrors.join(' • ')) + '</div>' : '') +
    (S.B.rules.length ? S.B.rules.map(r => '<div class="card rule' + (r.enabled ? '' : ' off') + '"><div class="row"><h3>' + esc(r.name) + '</h3>' + chip(TRIGGER_LABEL[r.trigger] || r.trigger) + (r.platform && r.platform !== 'all' ? chip(r.platform, 'warn') : '') + (r.mediaId ? chip('post ' + r.mediaId, 'mute') : '') + (r.priority ? chip('priority ' + r.priority, 'mute') : '') + '<span class="sp mini">' + (hits[r.name] || 0) + ' replies / 7 d</span><label class="sw"><input type="checkbox" data-act="toggle" data-id="' + esc(r.id) + '"' + (r.enabled ? ' checked' : '') + ' aria-label="Enabled"><i></i></label></div>' +
      '<div class="row">' + (r.keywords.length ? r.keywords.map(k => '<span class="chip mute">' + esc(k) + '</span>').join('') + '<span class="mini">' + (r.match === 'word' ? 'whole word' : 'contains') + '</span>' : '<span class="mini">any ' + esc((TRIGGER_LABEL[r.trigger] || '').toLowerCase()) + '</span>') + '</div>' +
      r.commentReplies.slice(0, 2).map(t => '<div class="bubble pub">' + esc(t) + '</div>').join('') + ((r.privateReply || r.dm) ? '<div class="bubble">' + esc(r.privateReply || r.dm) + '</div>' : '') +
      '<div class="row mini">cooldown ' + r.cooldownHours + ' h' + (r.tags.length ? ' · tags ' + esc(r.tags.join(', ')) : '') + '<span class="sp"></span><button class="btn sm" data-act="edit" data-id="' + esc(r.id) + '">Edit</button><button class="btn sm" data-act="dup" data-id="' + esc(r.id) + '">Duplicate</button><button class="btn sm danger" data-act="del" data-id="' + esc(r.id) + '">Delete</button></div></div>').join('') : '<div class="card empty">No rules yet. Start from a template, or rely on the AI agent.</div>');
}
function editorView() {
  const e = S.editor; if (!e) return '';
  const r = e.rule, v = x => esc(x == null ? '' : x), vars = Object.keys(engineSettings().vars).map(k => '{' + k + '}').join(' ');
  return '<div class="overlay" data-overlay><div class="sheet" role="dialog" aria-modal="true" aria-label="Rule editor"><div class="row"><h2>' + (e.isNew ? 'New rule' : 'Edit rule') + '</h2><button class="btn sm sp" data-act="closeedit">Close</button></div>' +
    '<div class="form" id="ed">' +
    '<label class="f">Name<input type="text" id="e_name" value="' + v(r.name) + '"></label>' +
    '<label class="f">When<select id="e_trigger">' + TRIGGERS.map(t => '<option value="' + t + '"' + (r.trigger === t ? ' selected' : '') + '>' + TRIGGER_LABEL[t] + '</option>').join('') + '</select></label>' +
    '<label class="f">Applies on<select id="e_platform">' + [['all', 'All platforms'], ['instagram', 'Instagram only'], ['whatsapp', 'WhatsApp only']].map(o => '<option value="' + o[0] + '"' + ((r.platform || 'all') === o[0] ? ' selected' : '') + '>' + o[1] + '</option>').join('') + '</select></label>' +
    '<label class="f wide">Keywords <small>comma separated; leave empty to match everything of this kind. Case and accents are ignored; emoji work.</small><input type="text" id="e_keywords" value="' + v(r.keywords.join(', ')) + '"></label>' +
    '<label class="f">Match style<select id="e_match"><option value="word"' + (r.match === 'word' ? ' selected' : '') + '>Whole word or phrase</option><option value="contains"' + (r.match !== 'word' ? ' selected' : '') + '>Contains</option></select></label>' +
    '<label class="f" id="e_postwrap">Only on this post <small>post id; empty = any post</small><input type="text" id="e_post" value="' + v(r.mediaId) + '"></label>' +
    '<label class="f wide" id="e_cwrap">Public comment replies <small>one per line; one is picked at random</small><textarea id="e_creply">' + v(r.commentReplies.join('\n')) + '</textarea></label>' +
    '<label class="f wide" id="e_pwrap">Private reply to the comment <small>sent as a DM, once per comment</small><textarea id="e_priv">' + v(r.privateReply) + '</textarea></label>' +
    '<label class="f wide" id="e_dwrap">Direct message <small>variables: {first_name} {username} ' + esc(vars) + '</small><textarea id="e_dm">' + v(r.dm) + '</textarea></label>' +
    '<label class="f">Button label <small>max 20 characters</small><input type="text" id="e_bl" maxlength="20" value="' + v(r.buttonLabel) + '"></label>' +
    '<label class="f">Button link <small>https://</small><input type="url" id="e_bu" value="' + v(r.buttonUrl) + '"></label>' +
    '<label class="f wide" id="e_qwrap">Quick replies <small>comma separated, shown under the DM</small><input type="text" id="e_qr" value="' + v(r.quickReplies.join(', ')) + '"></label>' +
    '<label class="f">Reply to the same person at most once every (hours)<input type="number" id="e_cd" min="0" value="' + v(r.cooldownHours) + '"></label>' +
    '<label class="f">Priority <small>higher wins</small><input type="number" id="e_prio" value="' + v(r.priority) + '"></label>' +
    '<label class="f wide">Tags <small>added to the person, comma separated</small><input type="text" id="e_tags" value="' + v(r.tags.join(', ')) + '"></label>' +
    '<label class="f"><span>Switched on</span><label class="sw"><input type="checkbox" id="e_on"' + (r.enabled ? ' checked' : '') + '><i></i></label></label></div>' +
    '<div id="e_err" class="banner bad" hidden></div><div class="row"><span class="mini sp">id: <span class="mono">' + esc(r.id || '(from the name)') + '</span></span><button class="btn" data-act="closeedit">Cancel</button><button class="btn pri" data-act="saverule">Save to sheet</button></div></div></div>';
}
function collectRule() {
  const e = S.editor, r = Object.assign({}, e.rule);
  r.name = g('e_name'); r.trigger = g('e_trigger'); r.platform = g('e_platform') || 'all'; r.keywords = splitList(g('e_keywords'), /\s*,\s*/); r.match = g('e_match');
  r.mediaId = g('e_post') || undefined; r.commentReplies = ($('e_creply') ? $('e_creply').value : '').split('\n').map(s => s.replace(/\|/g, '/').trim()).filter(Boolean);
  r.privateReply = g('e_priv') || undefined; r.dm = g('e_dm') || undefined; r.buttonLabel = g('e_bl') || undefined; r.buttonUrl = g('e_bu') || undefined;
  r.quickReplies = splitList(g('e_qr'), /\s*,\s*/).slice(0, 13); r.cooldownHours = Math.max(0, Number(g('e_cd')) || 0); r.priority = Number(g('e_prio')) || 0; r.tags = splitList(g('e_tags'), /\s*,\s*/); r.enabled = !!($('e_on') && $('e_on').checked);
  if (r.trigger !== 'comment') { r.commentReplies = []; r.mediaId = undefined; }
  if (!r.id) { const base = slug(r.name) || 'rule'; let id = base, n = 2; const taken = new Set(S.B.rules.map(x => x.id)); while (taken.has(id)) id = base + '-' + n++; r.id = id; }
  return r;
}
function tplView() {
  const T = templates();
  return '<div class="overlay" data-overlay><div class="sheet" role="dialog" aria-modal="true" aria-label="Templates"><div class="row"><h2>Start from a template</h2><button class="btn sm sp" data-act="closetpl">Close</button></div><p class="mini">A template opens in the editor, switched off, so you can adjust it before saving.</p>' +
    T.map((t, i) => '<div class="card"><div class="row"><div><b>' + esc(t.title) + '</b><div class="mini">' + esc(t.hint) + '</div></div><button class="btn sm sp" data-act="usetpl" data-i="' + i + '">Use</button></div></div>').join('') + '</div></div>';
}

/* ---- Knowledge ---- */
function knowView() {
  if (!S.loaded) return '';
  if (!S.B.ready) return notReady();
  const open = S.B.learn.filter(l => l.status === 'open');
  return '<h2>Knowledge</h2><p class="mini">Answers you approve here win over the business knowledge when they differ. Questions the assistant could not answer are collected below.</p>' +
    '<div class="card"><h3>Questions it could not answer' + (open.length ? ' (' + open.length + ')' : '') + '</h3><p class="mini" style="margin:4px 0 8px">Write the right answer once; from then on the assistant uses it.</p>' +
    (open.length ? open.map(l => '<div class="logrow"><div><div class="mini">' + esc(l.time) + '</div>' + (l.lang ? chip(l.lang, 'mute') : '') + '</div><div class="what"><div>“' + esc(l.q) + '”</div><textarea data-learn="' + l.row + '" placeholder="Your answer, in English (the assistant translates when it replies)"></textarea><div class="row"><button class="btn sm pri" data-act="learnsave" data-row="' + l.row + '">Save answer</button><button class="btn sm" data-act="learnskip" data-row="' + l.row + '">Ignore</button></div></div></div>').join('') : '<p class="empty">Nothing waiting.</p>') + '</div>' +
    '<div class="card"><div class="row"><h3>Approved answers (' + S.B.knowledge.length + ')</h3><span class="sp"></span><button class="btn sm pri" data-act="kadd">+ Add</button></div>' +
    (S.B.knowledge.length ? S.B.knowledge.map(k => '<div class="logrow"><div class="row"><button class="btn sm" data-act="kedit" data-row="' + k.row + '">Edit</button></div><div class="what"><b>' + esc(k.q) + '</b><div class="mini">' + esc(k.a) + '</div></div></div>').join('') : '<p class="empty">No approved answers yet.</p>') + '</div>';
}
function kEditor() {
  const k = S.kedit;
  return '<div class="overlay" data-overlay><div class="sheet" role="dialog" aria-modal="true"><div class="row"><h2>' + (k.row ? 'Edit answer' : 'New answer') + '</h2><button class="btn sm sp" data-act="kclose">Close</button></div><label class="f">Question<input type="text" id="k_q" value="' + esc(k.q) + '"></label><label class="f">Answer<textarea id="k_a" style="min-height:120px">' + esc(k.a) + '</textarea></label><div class="row"><span class="sp"></span>' + (k.row ? '<button class="btn danger" data-act="kdel" data-row="' + k.row + '">Delete</button>' : '') + '<button class="btn pri" data-act="ksave">Save</button></div></div></div>';
}
function pEditor() {
  const p = S.pedit;
  return '<div class="overlay" data-overlay><div class="sheet" role="dialog" aria-modal="true"><div class="row"><h2>' + (p.i >= 0 ? 'Edit playbook' : 'New playbook') + '</h2><button class="btn sm sp" data-act="pclose">Close</button></div><label class="f">Name<input type="text" id="pb_name" value="' + esc(p.name) + '"></label><label class="f">Instruction <small>start with a letter and a bracket, e.g. "G) BOOKING (…): what to do". Max 6,000 characters.</small><textarea id="pb_text" style="min-height:200px">' + esc(p.text) + '</textarea></label><div class="row"><span class="sp"></span>' + (p.i >= 0 ? '<button class="btn danger" data-act="pdel">Delete</button>' : '') + '<button class="btn pri" data-act="psave">Save</button></div></div></div>';
}
function newBrandView() {
  return '<div class="overlay" data-overlay><div class="sheet" role="dialog" aria-modal="true" aria-label="New brand"><div class="row"><h2>New brand</h2><button class="btn sm sp" data-act="nbclose">Close</button></div><p class="mini">A brand is one business with its own accounts, knowledge, rules and settings. It starts in dry-run with the AI agent off and nothing connected.</p><label class="f">Brand name<input type="text" id="nb_name" placeholder="Acme Dental" autocomplete="off"></label><div id="nb_err" class="banner bad" hidden></div><div class="row"><span class="sp"></span><button class="btn" data-act="nbclose">Cancel</button><button class="btn pri" data-act="nbcreate">Create brand</button></div></div></div>';
}

/* ---- Simulator ---- */
function simView() {
  if (!S.loaded) return '';
  const s = S.sim;
  return '<h2>Simulator</h2><p class="mini">Type what a person would write and see which keyword rule answers and what is sent. Uses the same engine as the server with this brand\'s saved rules and settings. (The AI agent answers whatever no rule handles; it is not simulated.)</p>' +
    '<div class="card"><div class="form"><label class="f">Platform<select id="s_channel"><option value="instagram"' + (s.channel === 'instagram' ? ' selected' : '') + '>Instagram</option><option value="whatsapp"' + (s.channel === 'whatsapp' ? ' selected' : '') + '>WhatsApp</option></select></label>' +
    '<label class="f">What happens<select id="s_kind">' + [['comment', 'Someone comments on a post'], ['story_reply', 'Someone replies to a story'], ['story_mention', 'Someone mentions us in a story'], ['dm', 'Someone sends a message']].map(o => '<option value="' + o[0] + '"' + (s.kind === o[0] ? ' selected' : '') + '>' + o[1] + '</option>').join('') + '</select></label>' +
    '<label class="f">Their name<input type="text" id="s_user" value="' + esc(s.username) + '"></label>' +
    '<label class="f wide">Their text<input type="text" id="s_text" value="' + esc(s.text) + '" autocomplete="off"></label>' +
    '<label class="f">Post id <small>comments only</small><input type="text" id="s_media" value="' + esc(s.mediaId) + '"></label>' +
    '<div class="f"><span>Situation</span><label class="row"><input type="checkbox" id="s_first"' + (s.first ? ' checked' : '') + '> First time they write to us</label><label class="row"><input type="checkbox" id="s_opt"' + (s.optedOut ? ' checked' : '') + '> They opted out earlier</label><label class="row"><input type="checkbox" id="s_hand"' + (s.handoff ? ' checked' : '') + '> A human is handling them</label><label class="row"><input type="checkbox" id="s_dis"' + (s.disabled ? ' checked' : '') + '> Include switched-off rules</label></div></div></div>' +
    '<div id="simout"></div>';
}
function simOut() {
  const s = S.sim, settings = engineSettings(), now = Date.now();
  const ev = { id: 'sim', kind: s.kind, channel: s.channel, senderId: 'sim-user', username: s.username, text: s.kind === 'story_mention' ? '' : s.text, mediaId: s.mediaId || undefined, commentId: s.kind === 'comment' ? 'sim-comment' : undefined, timestamp: now };
  const rules = S.B.rules.map(r => s.disabled ? Object.assign({}, r, { enabled: true }) : r);
  const d = decide(ev, rules, { seenBefore: !s.first, optedOut: s.optedOut, handoffUntil: s.handoff ? now + 3600000 : 0 }, settings, now, () => 0);
  const label = { replied: ['good', 'Replies'], ignored: ['mute', 'Does nothing'], opt_out: ['warn', 'Opts the person out'], handoff: ['warn', 'Hands over to a human'] }[d.kind];
  return '<div class="card"><div class="row"><h3>Result</h3>' + chip(label[1], label[0]) + '<span class="mini">' + esc(d.reason) + '</span></div>' +
    (d.steps.length ? d.steps.map(st => '<div style="margin-top:10px"><div class="mini">' + ({ comment_reply: 'Public reply under the comment', private_reply: 'Private reply (DM tied to the comment)', dm: 'Direct message' }[st.type]) + '</div><div class="bubble ' + (st.type === 'comment_reply' ? 'pub' : '') + '">' + esc(st.text) + '</div>' + (st.buttonLabel ? '<div class="mini">Button: <b>' + esc(st.buttonLabel) + '</b> → ' + esc(st.buttonUrl) + '</div>' : '') + (st.quickReplies ? '<div class="row" style="margin-top:4px">' + st.quickReplies.map(q => chip(q)).join('') + '</div>' : '') + '</div>').join('') : '<p class="empty">Nothing would be sent' + (d.kind === 'ignored' && d.reason === 'no rule matched' ? ' by a keyword rule; the AI agent would answer a message if it is on.' : '.') + '</p>') +
    (d.tags.length ? '<div class="mini" style="margin-top:10px">Tags added: ' + esc(d.tags.join(', ')) + '</div>' : '') + '</div>';
}

/* ---- Inbox ---- */
function filteredLog() {
  const q = normalize(S.logQ);
  return S.B.log.filter(r => (S.logFilter === 'all' || (S.logFilter === 'failed' ? r.status === 'failed' || r.status === 'paused' : r.status === S.logFilter)) && (!q || normalize([r.person, r.text, r.rule, r.reply, r.error, r.reason].join(' ')).includes(q)));
}
function logList() {
  const rows = filteredLog().slice(0, 200);
  return rows.length ? '<div class="card">' + rows.map(r => '<div class="logrow"><div><div class="num">' + esc(when(r.t)) + '</div><div class="mini">' + esc(ago(r.t)) + '</div></div><div class="what"><div class="row">' + statusChip(r.status) + chip(kindLabel(r.kind), 'mute') + (/^wa:/.test(r.person) ? chip('WhatsApp', 'mute') : '') + '<b>' + esc(r.person) + '</b>' + (r.rule ? '<span class="mini">→ ' + esc(r.rule) + '</span>' : '') + '</div>' +
    (r.text ? '<div>“' + esc(r.text) + '”</div>' : '') + (r.reply ? '<div class="mini">' + (r.status === 'dry-run' ? 'Would send: ' : r.status === 'sent' ? 'Sent: ' : '') + esc(r.reply) + '</div>' : '') +
    (r.error ? '<div class="banner bad" style="margin-top:6px"><b>' + esc(explainError(r.error)) + '</b>' + (explainError(r.error) !== r.error ? '<div class="mini mono">' + esc(r.error) + '</div>' : '') + '</div>' : (r.status === 'ignored' && r.reason ? '<div class="mini">Skipped: ' + esc(r.reason) + '</div>' : '')) + '</div></div>').join('') + '</div>' : '<div class="card empty">No events match.</div>';
}
function peopleList() {
  const q = normalize(S.peopleQ), rows = S.B.contacts.filter(p => !q || normalize([p.username, p.id, p.text, p.tags].join(' ')).includes(q));
  return rows.length ? '<div class="card">' + rows.map(p => '<div class="logrow"><div><b>' + esc(p.username || p.id) + '</b><div class="mini">' + esc(ago(p.last)) + '</div></div><div class="what"><div class="row">' + (p.opted ? chip('Opted out', 'bad') : '') + (p.handoff ? chip('Human until ' + p.handoff, 'warn') : '') + p.tags.split(',').map(t => t.trim()).filter(Boolean).map(t => chip(t)).join('') + '</div><div class="mini">Last message: ' + esc(p.text) + '</div></div></div>').join('') + '</div>' : '<div class="card empty">No people yet. They appear after their first message or comment.</div>';
}
function leadsList() {
  return '<div class="card"><div class="row"><h3>Leads (' + S.B.leads.length + ')</h3><span class="sp mini">saved only after the person said yes</span><button class="btn sm" data-act="exportleads">Export CSV</button></div>' +
    (S.B.leads.length ? S.B.leads.slice(0, 80).map(l => '<div class="logrow"><div><b>' + esc(l.name || 'No name') + '</b><div class="mini">' + esc(l.time) + '</div></div><div class="what"><div>' + esc(l.email || '(no email yet)') + (l.whatsapp ? ' · ' + esc(l.whatsapp) : '') + (l.ig ? ' · @' + esc(l.ig) : '') + '</div><div class="mini">' + esc([l.role, l.profession, l.city, l.need].filter(Boolean).join(' · ')) + '</div></div></div>').join('') : '<p class="empty">No leads yet.</p>') + '</div>';
}
function inboxView() {
  if (!S.loaded) return '';
  if (!S.B.ready) return notReady();
  const c = f => f === 'all' ? S.B.log.length : S.B.log.filter(r => f === 'failed' ? r.status === 'failed' || r.status === 'paused' : r.status === f).length;
  const seg = '<div class="seg" role="group" aria-label="Inbox section">' + [['log', 'Live log'], ['people', 'People'], ['leads', 'Leads']].map(o => '<button data-act="inboxsec" data-s="' + o[0] + '" aria-pressed="' + (S.inbox === o[0]) + '">' + o[1] + '</button>').join('') + '</div>';
  if (S.inbox === 'people') return '<div class="row"><h2>Inbox</h2>' + seg + '<span class="sp"></span><input type="text" id="p_q" placeholder="Search…" value="' + esc(S.peopleQ) + '" style="max-width:240px"><button class="btn" data-act="exportpeople">Export CSV</button></div><p class="mini">The most recent ' + S.B.contacts.length + ' people. Opt-outs and human handovers are respected automatically.</p><div id="peoplelist">' + peopleList() + '</div>';
  if (S.inbox === 'leads') return '<div class="row"><h2>Inbox</h2>' + seg + '</div>' + leadsList();
  return '<div class="row"><h2>Inbox</h2>' + seg + '<span class="sp mini">refreshes every minute</span></div><div class="row">' +
    [['all', 'All'], ['sent', 'Sent'], ['dry-run', 'Dry-run'], ['failed', 'Failed'], ['ignored', 'Ignored']].map(f => '<button class="btn sm' + (S.logFilter === f[0] ? ' pri' : '') + '" data-act="logf" data-f="' + f[0] + '">' + f[1] + ' ' + c(f[0]) + '</button>').join('') +
    '<input type="text" id="l_q" placeholder="Search person, text, rule…" value="' + esc(S.logQ) + '" style="max-width:260px" class="sp"></div><div id="loglist">' + logList() + '</div>';
}
