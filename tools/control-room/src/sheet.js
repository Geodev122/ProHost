/* ---- State and Google Sheets I/O (one spreadsheet holds every brand; non-ProHost tabs carry the brand prefix) ---- */
const SHEET_ID = '1cyZ03fpBArHF1_GeYZkK4k0XImccR4xfVnkyO3mSLA8';
const SHEET_URL = 'https://docs.google.com/spreadsheets/d/' + SHEET_ID + '/edit';
const LEGACY = 'prohost';
const WEBHOOKS = { instagram: 'https://pro-host.tech/ig-webhook', whatsapp: 'https://pro-host.tech/wa-webhook' };
const BRAND_TABS = ['Rules', 'Log', 'Contacts', 'Status', 'Knowledge', 'Learn', 'Leads', 'Profile', 'Buttons', 'Playbooks', 'Sources', 'KnowledgeDraft'];
const SECRET_KEYS = { instagram: ['access_token', 'app_secret', 'verify_token'], whatsapp: ['access_token', 'app_secret', 'verify_token'], ai: ['anthropic_key', 'gemini_key'] };

const emptyB = () => ({ ready: false, rules: [], ruleErrors: [], log: [], contacts: [], knowledge: [], learn: [], leads: [], status: {}, profile: {}, buttons: [], playbooks: [], sources: [], draft: [] });
const S = {
  view: 'dash', inbox: 'log', mcp: null, dl: null, brand: LEGACY, brands: [], platforms: [], secrets: {}, B: emptyB(),
  loaded: false, busy: false, err: '', syncedAt: 0, logFilter: 'all', logQ: '', peopleQ: '', confirmLive: false,
  sim: { kind: 'comment', channel: 'instagram', text: 'link please', username: 'rana', mediaId: '', first: true, optedOut: false, handoff: false, disabled: false },
  editor: null, tpl: false, kedit: null, pedit: null, newBrand: false, building: false,
};
const $ = id => document.getElementById(id);
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const prefixOf = id => (id === LEGACY ? '' : id + '_');
const tabA1 = (tab, range, brand) => "'" + ((brand == null ? prefixOf(S.brand) : prefixOf(brand)) + tab).replace(/'/g, "''") + "'!" + range;
const gA1 = (tab, range) => "'" + tab + "'!" + range;
const sheetCall = (tool, input) => S.mcp.callTool('Google Sheets', tool, Object.assign({ spreadsheetId: SHEET_ID }, input), { cache: false }).then(r => (r && r.payload) || r || {});
const getVals = async range => (await sheetCall('get_values', { range })).values || [];
const putVals = (range, values) => sheetCall('update_values', { range, values });
const parseTime = s => { const m = /^(\d{4})-(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)$/.exec(String(s || '')); return m ? Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6]) : 0; };
const ago = ms => { if (!ms) return 'never'; const s = Math.max(0, Math.round((Date.now() - ms) / 1000)); return s < 60 ? s + 's ago' : s < 3600 ? Math.round(s / 60) + ' min ago' : s < 86400 ? Math.round(s / 3600) + ' h ago' : Math.round(s / 86400) + ' d ago'; };
const when = ms => ms ? new Date(ms).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '';
const truthyS = v => /^(true|yes|y|1|on|✓|✔)$/i.test(String(v || '').trim());
const falsyS = v => /^(false|no|n|0|off)$/i.test(String(v || '').trim());
const cur = () => S.brands.find(b => b.id === S.brand) || { id: S.brand, name: S.brand, enabled: true };
const platformRow = (brand, platform) => S.platforms.find(p => p.brandId === brand && p.platform === platform);
const secretInfo = (brand, platform, key) => S.secrets[brand + '|' + platform + '|' + key] || null;

function friendly(e) {
  const m = String((e && e.message) || e || ''), c = e && e.code;
  if (c === 'server_not_connected' || /not.?connected/i.test(m)) return 'Google Sheets is not connected for this page. Add it under claude.ai Settings › Connectors, then press Refresh.';
  if (c === 'needs_reauth') return 'The Google Sheets connection expired. Reconnect it in claude.ai Settings › Connectors.';
  if (c === 'not_in_manifest' || c === 'consent_required' || c === 'approval_required') return 'This page was not allowed to use Google Sheets. Allow it when asked, then press Refresh.';
  if (/not found|404|Unable to parse range/i.test(m)) return 'A sheet tab was not found yet (' + m.slice(0, 90) + '). New brands get their tabs within a minute.';
  if (/permission|403|forbidden/i.test(m)) return 'No access to the control-room sheet from this account.';
  return m || 'Could not reach the sheet.';
}

/* ---- Loading ---- */
async function loadRegistry() {
  const [bv, pv, sv] = await Promise.all([getVals(gA1('Brands', 'A1:E60')), getVals(gA1('Platforms', 'A1:J200')), getVals(gA1('Secrets', 'A1:E300')).catch(() => [])]);
  S.brands = bv.slice(1).filter(r => (r[0] || '').trim()).map((r, i) => ({ row: i + 2, id: String(r[0]).trim().toLowerCase(), name: (r[1] || r[0]).trim(), enabled: !falsyS(r[2] || ''), note: r[4] || '' }));
  if (!S.brands.some(b => b.id === LEGACY)) S.brands.unshift({ row: 0, id: LEGACY, name: 'ProHost', enabled: true, note: '' });
  S.platforms = pv.slice(1).map((r, i) => ({ row: i + 2, brandId: String(r[0] || '').trim().toLowerCase(), platform: String(r[1] || '').trim().toLowerCase(), accountId: r[2] || '', appId: r[3] || '', wabaId: r[4] || '', phoneId: r[5] || '', templateName: r[6] || '', templateLang: r[7] || 'en', enabled: !falsyS(r[8] || 'TRUE'), status: r[9] || '' })).filter(p => p.brandId && p.platform);
  S.secrets = {};
  sv.slice(1).forEach((r, i) => { const k = String(r[0] || '').toLowerCase() + '|' + String(r[1] || '').toLowerCase() + '|' + String(r[2] || '').toLowerCase(); if (k !== '||') S.secrets[k] = { row: i + 2, status: r[4] || '' }; });
}

async function loadBrand() {
  const id = S.brand, B = emptyB();
  try {
    const [rv, lv, cv, sv, kv, nv, ldv, pv, bv, pbv, srv, dv] = await Promise.all([
      getVals(tabA1('Rules', 'A1:Q500')), getVals(tabA1('Log', 'A1:J2001')), getVals(tabA1('Contacts', 'A1:G501')), getVals(tabA1('Status', 'A1:B100')),
      getVals(tabA1('Knowledge', 'A1:B200')).catch(() => []), getVals(tabA1('Learn', 'A1:F300')).catch(() => []), getVals(tabA1('Leads', 'A1:O500')).catch(() => []),
      getVals(tabA1('Profile', 'A1:B60')).catch(() => []), getVals(tabA1('Buttons', 'A1:K40')).catch(() => []), getVals(tabA1('Playbooks', 'A1:C40')).catch(() => []),
      getVals(tabA1('Sources', 'A1:D40')).catch(() => []), getVals(tabA1('KnowledgeDraft', 'A1:D100')).catch(() => []),
    ]);
    const pr = parseRules(rv); B.rules = pr.rules; B.ruleErrors = pr.errors;
    B.log = lv.slice(1).filter(r => r.some(c => c)).map(r => ({ t: parseTime(r[0]), kind: r[1] || '', person: r[2] || '', text: r[3] || '', rule: r[4] || '', status: r[5] || '', reason: r[6] || '', reply: r[7] || '', error: r[8] || '', id: r[9] || '' })).sort((a, b) => b.t - a.t);
    B.contacts = cv.slice(1).filter(r => r[0]).map(r => ({ id: r[0], username: r[1] || '', last: parseTime(r[2]), text: r[3] || '', tags: r[4] || '', opted: /^true$/i.test(r[5] || ''), handoff: r[6] || '' }));
    B.knowledge = kv.slice(1).map((r, i) => ({ row: i + 2, q: r[0] || '', a: r[1] || '' })).filter(r => r.q || r.a);
    B.learn = nv.slice(1).map((r, i) => ({ row: i + 2, time: r[0] || '', lang: r[1] || '', q: r[2] || '', times: r[3] || '1', status: r[4] || 'open', id: r[5] || '' })).filter(r => r.q);
    const seen = {}; ldv.slice(1).filter(r => r[0] && r[9]).forEach(r => { seen[r[9]] = { time: r[0], ig: r[1] || '', name: r[2] || '', email: r[3] || '', role: r[4] || '', need: r[5] || '', lang: r[6] || '', consent: r[7] || '', id: r[9], whatsapp: r[10] || '', source: r[11] || '', profession: r[13] || '', city: r[14] || '' }; });
    B.leads = Object.values(seen).sort((a, b) => (a.time < b.time ? 1 : -1));
    sv.forEach(r => { const k = String(r[0] || '').trim().toLowerCase(); if (k && k !== 'key') B.status[k] = String(r[1] == null ? '' : r[1]); });
    pv.slice(1).forEach(r => { const k = String(r[0] || '').trim().toLowerCase(); if (k) B.profile[k] = String(r[1] == null ? '' : r[1]); });
    const bh = (bv[0] || []).map(h => String(h).trim().toLowerCase());
    B.buttons = bv.slice(1).filter(r => (r[0] || '').trim()).map(r => ({ id: String(r[0]).trim(), url: r[bh.indexOf('url')] || '', labels: { en: r[bh.indexOf('label_en')] || '', ar: r[bh.indexOf('label_ar')] || '', fr: r[bh.indexOf('label_fr')] || '', es: r[bh.indexOf('label_es')] || '' } }));
    B.playbooks = pbv.slice(1).filter(r => (r[0] || '').trim()).map(r => ({ name: r[0], enabled: !falsyS(r[1] || 'TRUE'), text: r[2] || '' }));
    B.sources = srv.slice(1).map((r, i) => ({ row: i + 2, type: (r[0] || '').trim().toLowerCase(), ref: r[1] || '', note: r[2] || '', status: r[3] || '' })).filter(r => r.type || r.ref);
    B.draft = dv.slice(1).map((r, i) => ({ row: i + 2, section: r[0] || '', text: r[1] || '', sources: r[2] || '', status: (r[3] || '').toLowerCase() })).filter(r => r.section);
    B.ready = rv.length > 0 && sv.length > 0;
  } catch (e) { S.B = B; throw e; }
  S.B = B;
}

async function refreshAll(quiet) {
  if (!S.mcp || S.busy) return; S.busy = true; if (!quiet) render();
  try {
    await loadRegistry();
    if (!S.brands.some(b => b.id === S.brand)) S.brand = LEGACY;
    await loadBrand().catch(e => { S.B = emptyB(); S.err = friendly(e); throw e; });
    S.loaded = true; S.err = ''; S.syncedAt = Date.now();
  } catch (e) { S.err = S.err || friendly(e); S.loaded = S.loaded || S.brands.length > 0; }
  S.busy = false;
  // A background refresh never replaces a form someone is typing in (the data is updated, the page is redrawn after the next save).
  const typing = quiet && (Object.keys(dirty).length || Array.from(document.querySelectorAll('input[type=password]')).some(i => i.value));
  if (!typing) render();
}

/* ---- Writes ---- */
async function setKV(tab, obj, limit) {
  const sv = await getVals(tabA1(tab, 'A1:B100')), rows = {}; let used = 1;
  sv.forEach((r, i) => { const k = String(r[0] || '').trim().toLowerCase(); if (k) { rows[k] = i + 1; if (!limit || i + 1 < limit) used = Math.max(used, i + 1); } });
  for (const k of Object.keys(obj)) {
    let n = rows[k]; if (!n) { n = ++used; rows[k] = n; if (limit && n >= limit) throw new Error(tab + ' tab is full'); }
    await putVals(tabA1(tab, 'A' + n + ':B' + n), [[k, safeCell(obj[k])]]);
  }
}
const setStatus = obj => setKV('Status', obj, 40);
const setProfile = obj => setKV('Profile', obj);

async function writeTable(tab, header, rows, oldCount) {
  const table = [header].concat(rows.map(r => r.map(safeCell)));
  for (let i = rows.length; i < oldCount; i++) table.push(header.map(() => ''));
  await putVals(tabA1(tab, 'A1:' + String.fromCharCode(64 + header.length) + table.length), table);
}
const saveButtons = list => writeTable('Buttons', ['id', 'url', 'label_en', 'label_ar', 'label_fr', 'label_es'], list.map(b => [b.id, b.url, b.labels.en, b.labels.ar, b.labels.fr, b.labels.es]), S.B.buttons.length);
const savePlaybooks = list => writeTable('Playbooks', ['name', 'enabled', 'text'], list.map(p => [p.name, p.enabled ? 'TRUE' : 'FALSE', p.text]), S.B.playbooks.length);
const saveSources = list => writeTable('Sources', ['type', 'url_or_text', 'note', 'status'], list.map(s => [s.type, s.ref, s.note, s.status || '']), S.B.sources.length);
const setDraftStatus = (row, status) => putVals(tabA1('KnowledgeDraft', 'D' + row), [[status]]);
const saveDraftRow = (d) => putVals(tabA1('KnowledgeDraft', 'A' + d.row + ':D' + d.row), [[safeCell(d.section), safeCell(d.text), safeCell(d.sources), d.status]]);

async function writeRules(mutator) {
  const values = await getVals(tabA1('Rules', 'A1:Q500'));
  const header = (values[0] || RULE_COLUMNS).map(h => String(h).trim().toLowerCase());
  let rows = values.slice(1).filter(r => r.some(c => String(c || '').trim())).map(r => { const d = {}; RULE_COLUMNS.forEach(c => { const i = header.indexOf(c); d[c] = i >= 0 && r[i] != null ? String(r[i]) : ''; }); return d; });
  const before = rows.length; rows = mutator(rows) || rows;
  const table = [RULE_COLUMNS.slice()].concat(rows.map(d => RULE_COLUMNS.map(c => d[c] == null ? '' : d[c])));
  for (let i = rows.length; i < before; i++) table.push(RULE_COLUMNS.map(() => ''));
  await putVals(tabA1('Rules', 'A1:Q' + table.length), table);
}
const ruleDict = r => { const row = ruleToRow(r), d = {}; RULE_COLUMNS.forEach((c, i) => { d[c] = row[i]; }); return d; };
async function saveRule(rule, isNew) {
  await writeRules(rows => {
    const i = rows.findIndex(d => d.id === rule.id);
    if (isNew && i >= 0) throw new Error('A rule with the id "' + rule.id + '" already exists.');
    if (i >= 0) rows[i] = ruleDict(rule); else rows.push(ruleDict(rule));
    return rows;
  });
}
const deleteRule = id => writeRules(rows => rows.filter(d => d.id !== id));
const toggleRule = (id, on) => writeRules(rows => { const d = rows.find(x => x.id === id); if (d) d.enabled = on ? 'TRUE' : 'FALSE'; return rows; });

async function writeKnowledge(rows) {
  const table = [['question', 'answer']].concat(rows.map(r => [safeCell(r.q), safeCell(r.a)]));
  for (let i = rows.length; i < S.B.knowledge.length; i++) table.push(['', '']);
  await putVals(tabA1('Knowledge', 'A1:B' + table.length), table);
}
const saveKnowledgeRow = (row, q, a) => writeKnowledge(row ? S.B.knowledge.map(r => r.row === row ? { row, q, a } : r) : S.B.knowledge.concat([{ row: 0, q, a }]));
const deleteKnowledgeRow = row => writeKnowledge(S.B.knowledge.filter(r => r.row !== row));
async function answerLearn(item, answer) { await writeKnowledge(S.B.knowledge.concat([{ row: 0, q: item.q, a: answer }])); await putVals(tabA1('Learn', 'E' + item.row), [['answered']]); }
const dismissLearn = item => putVals(tabA1('Learn', 'E' + item.row), [['ignored']]);

/* Registry: brands, platforms, secrets */
async function createBrand(name) {
  const id = brandIdFrom(name);
  if (!id) throw new Error('Use a name that starts with a letter (2-20 letters or digits).');
  if (S.brands.some(b => b.id === id)) throw new Error('A brand with the id "' + id + '" already exists.');
  const n = (await getVals(gA1('Brands', 'A1:A60'))).length + 1;
  await putVals(gA1('Brands', 'A' + n + ':E' + n), [[id, safeCell(name.trim()), 'TRUE', '', 'created in the console']]);
  return id;
}
const brandIdFrom = name => { const id = String(name || '').toLowerCase().replace(/[^a-z0-9]/g, '').replace(/^[0-9]+/, '').slice(0, 20); return /^[a-z][a-z0-9]{1,19}$/.test(id) ? id : ''; };
async function savePlatform(p) {
  const values = [p.brandId, p.platform, p.accountId, p.appId, p.wabaId, p.phoneId, safeCell(p.templateName), p.templateLang || 'en', p.enabled ? 'TRUE' : 'FALSE'];
  const row = p.row || (await getVals(gA1('Platforms', 'A1:A200'))).length + 1;
  await putVals(gA1('Platforms', 'A' + row + ':I' + row), [values]);
}
async function saveSecrets(brand, platform, entries) {
  const sv = await getVals(gA1('Secrets', 'A1:E300')); let next = sv.length + 1;
  for (const [key, value] of entries) {
    let row = 0; sv.forEach((r, i) => { if (String(r[0] || '').toLowerCase() === brand && String(r[1] || '').toLowerCase() === platform && String(r[2] || '').toLowerCase() === key) row = i + 1; });
    if (!row) { row = next++; await putVals(gA1('Secrets', 'A' + row + ':C' + row), [[brand, platform, key]]); }
    await putVals(gA1('Secrets', 'D' + row + ':E' + row), [[value, 'pending · the server takes it in within a minute']]);
  }
}
const randomToken = () => { const a = new Uint8Array(16); (window.crypto || window.msCrypto).getRandomValues(a); return Array.from(a, x => ('0' + x.toString(16)).slice(-2)).join(''); };

/* ---- Derived ---- */
const engineSettings = () => { const o = settingsFromStatus(S.B.status); const dv = Object.assign({}, DEFAULTS); if (S.brand !== LEGACY) { dv.vars = {}; } return Object.assign({}, dv, o, { vars: Object.assign({}, dv.vars, o.vars || {}) }); };
const effectiveMode = () => { const st = S.B.status, kill = /^(true|yes|1|on)$/i.test(st.kill_switch || ''); return kill ? 'kill' : (st.mode || '').toLowerCase() === 'live' ? 'live' : 'dry'; };
function explainError(m) {
  m = String(m || ''); if (!m) return '';
  if (/190|access token|session has expired|invalid oauth/i.test(m)) return 'The access token expired or was revoked. Paste a new token in Platforms.';
  if (/outside of allowed window|allowed window|24.?hour|2018278|131047/i.test(m)) return 'More than 24 hours since this person last wrote, so the platform blocks the message (WhatsApp then needs an approved template).';
  if (/cannot message|2534014|user not found|can.?t be (?:messaged|found)/i.test(m)) return "The platform won't let us message this person (privacy settings, or they restricted messages).";
  if (/already|duplicate|2534022|private repl/i.test(m)) return 'A private reply was already sent for this comment (Instagram allows one).';
  if (/permission|\(#10\)|\(#200\)|not authorized/i.test(m)) return 'Missing permission. The Meta app needs the messaging permissions, and public accounts need Meta App Review.';
  if (/rate|limit|#4\b|#17|#32|too many/i.test(m)) return 'Rate limit hit; we retried and gave up. It resolves by itself.';
  if (/circuit/i.test(m)) return 'Sending is paused because of too many errors in the last hour. Fix the cause, then it resumes by itself.';
  return m;
}
const slug = s => String(s || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40);
