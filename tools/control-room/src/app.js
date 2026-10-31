/* ---- Shell, actions and boot ---- */
/* Typing is never lost to a refresh: edited fields are remembered and put back after every re-render, until the section is saved. */
const dirty = {};
const fieldKey = el => el.id ? '#' + el.id : (el.dataset && el.dataset.f && el.closest('[data-src],[data-btn],[data-draft]') ? (function (r) { return Object.keys(r.dataset).filter(k => k === 'src' || k === 'btn' || k === 'draft').map(k => k + r.dataset[k]).join('') + '.' + el.dataset.f; })(el.closest('[data-src],[data-btn],[data-draft]')) : '');
function restoreDirty() {
  Object.keys(dirty).forEach(k => {
    let el = null;
    if (k[0] === '#') el = document.getElementById(k.slice(1));
    else { const m = /^(src|btn|draft)(\d+)\.(\w+)$/.exec(k); if (m) { const row = document.querySelector('[data-' + m[1] + '="' + m[2] + '"]'); el = row && row.querySelector('[data-f="' + m[3] + '"]'); } }
    if (el && el.type !== 'checkbox') el.value = dirty[k];
  });
}
function render() {
  const keep = document.activeElement && document.activeElement.id;
  const body = S.err && !S.loaded ? '<div class="card banner bad">' + esc(S.err) + '<div class="row" style="margin-top:8px"><button class="btn" data-act="refresh">Try again</button></div></div>' :
    !S.loaded ? '<div class="card empty">Loading from the sheet…</div>' :
    (S.err ? '<div class="banner bad">' + esc(S.err) + '</div>' : '') + ({ dash: dashView, brand: brandView, platforms: platformsView, agent: agentView, rules: rulesView, know: knowView, inbox: inboxView, sim: simView }[S.view])();
  $('app').innerHTML = renderHeader() + '<main>' + body + '</main>' + (S.editor ? editorView() : '') + (S.tpl ? tplView() : '') + (S.kedit ? kEditor() : '') + (S.pedit ? pEditor() : '') + (S.newBrand ? newBrandView() : '');
  restoreDirty();
  if (S.view === 'sim' && S.loaded) $('simout').innerHTML = simOut();
  if (S.editor) syncEditorVisibility();
  if (keep && $(keep) && ['l_q', 'p_q', 's_text', 's_user', 's_media'].includes(keep)) { try { $(keep).focus(); } catch (e) { /* ignore */ } }
}
function syncEditorVisibility() {
  const c = $('e_trigger') && $('e_trigger').value === 'comment';
  ['e_postwrap', 'e_cwrap', 'e_pwrap'].forEach(i => { if ($(i)) $(i).hidden = !c; });
  if ($('e_qwrap')) $('e_qwrap').hidden = c;
}

let toastTimer;
function toast(msg) { let t = $('toast'); if (!t) { t = document.createElement('div'); t.id = 'toast'; t.className = 'toast'; t.setAttribute('role', 'status'); document.body.appendChild(t); } t.textContent = msg; t.hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => { t.hidden = true; }, 4000); }
const guard = async (fn, okMsg) => { try { await fn(); if (okMsg) toast(okMsg); return true; } catch (e) { toast(friendly(e)); return false; } };
const csvCell = v => '"' + String(v == null ? '' : v).replace(/"/g, '""') + '"';
const saveFile = (filename, csv) => { if (!S.dl) return toast('Saving files is not available here.'); return guard(() => S.dl.save({ filename, data: new Blob([csv], { type: 'text/csv' }) })); };
const rowsOf = (sel, fields) => Array.from(document.querySelectorAll(sel)).map(row => { const o = {}; fields.forEach(f => { const el = row.querySelector('[data-f="' + f + '"]'); o[f] = el ? el.value.trim() : ''; }); return o; });
const readSources = () => rowsOf('[data-src]', ['type', 'ref', 'note']).map((o, i) => Object.assign({ status: (S.B.sources[i] || {}).status || '' }, o));
const readButtons = () => rowsOf('[data-btn]', ['id', 'url', 'en', 'ar', 'fr', 'es']).map(o => ({ id: o.id.toLowerCase().replace(/[^a-z0-9_]/g, ''), url: o.url, labels: { en: o.en, ar: o.ar, fr: o.fr, es: o.es } }));
const platformValues = p => ({ brandId: S.brand, platform: p, accountId: g('p_' + p + '_acct').replace(/\s+/g, ''), appId: g('p_' + p + '_app').replace(/\s+/g, ''), wabaId: g('p_' + p + '_waba').replace(/\s+/g, ''), phoneId: g('p_' + p + '_phone').replace(/\s+/g, ''), templateName: g('p_' + p + '_tpl'), templateLang: g('p_' + p + '_lang') || 'en', enabled: !!(document.querySelector('[data-act="platenabled"][data-p="' + p + '"]') || {}).checked, row: (platformRow(S.brand, p) || {}).row || 0 });

document.addEventListener('click', async ev => {
  const nav = ev.target.closest('[data-view]'); if (nav) { Object.keys(dirty).forEach(k => delete dirty[k]); S.view = nav.dataset.view; try { localStorage.setItem('croom.view', S.view); } catch (e) { /* ignore */ } render(); return; }
  if (ev.target.matches('[data-overlay]')) { S.editor = null; S.tpl = false; S.kedit = null; S.pedit = null; S.newBrand = false; render(); return; }
  const b = ev.target.closest('[data-act]'); if (!b || b.type === 'checkbox') return;
  const act = b.dataset.act, id = b.dataset.id;
  const done = async (ok, msg) => { if (ok) { if (msg) toast(msg); Object.keys(dirty).forEach(k => delete dirty[k]); await refreshAll(true); } };
  if (act === 'refresh') return refreshAll();
  if (act === 'newbrand') { S.newBrand = true; return render(); }
  if (act === 'nbclose') { S.newBrand = false; return render(); }
  if (act === 'nbcreate') {
    const box = $('nb_err'); b.disabled = true; box.hidden = true;
    try { const nid = await createBrand(g('nb_name')); S.newBrand = false; S.brand = nid; try { localStorage.setItem('croom.brand', nid); } catch (e) { /* ignore */ } S.view = 'brand'; toast('Brand created. Its tabs are ready within about a minute.'); S.brands.push({ row: 0, id: nid, name: g('nb_name') || nid, enabled: true }); S.B = emptyB(); render(); setTimeout(() => refreshAll(true), 70000); }
    catch (e) { b.disabled = false; box.hidden = false; box.textContent = friendly(e); }
    return;
  }
  if (act === 'inboxsec') { S.inbox = b.dataset.s; return render(); }
  if (act === 'copy') { try { await navigator.clipboard.writeText(b.dataset.t); toast('Copied'); } catch (e) { toast('Select the address and copy it.'); } return; }
  if (act === 'gentoken') { const el = $(b.dataset.target); if (el) { el.type = 'text'; el.value = randomToken(); toast('Copy this token into Meta, then save.'); } return; }

  /* brand */
  if (act === 'saveidentity') {
    const name = g('b_name'); if (!name) return toast('The brand needs a name.');
    return done(await guard(() => setProfile({ name, assistant_name: g('b_aname'), purpose: g('b_purpose'), contact_email: g('b_email'), languages: g('b_langs'), persona: $('b_persona').value.trim() }), 'Saved. The server applies it within a minute.'));
  }
  if (act === 'saveknowledgebase') { const t = $('b_kb').value.trim().slice(0, 45000); return done(await guard(() => setProfile({ knowledge_base: t }), 'Knowledge saved. The server applies it within a minute.')); }
  if (act === 'integ') return;
  if (act === 'addsource') { S.B.sources = readSources().concat([{ type: 'url', ref: '', note: '', status: '' }]); return render(); }
  if (act === 'delsource') { const l = readSources(); l.splice(+b.dataset.i, 1); S.B.sources = l; return render(); }
  if (act === 'savesources') { const l = readSources().filter(s => s.ref); return done(await guard(() => saveSources(l), 'Sources saved'), ''); }
  if (act === 'buildknowledge') {
    const l = readSources().filter(s => s.ref); if (!l.length) return toast('Add at least one source first.');
    S.building = true; render();
    const ok = await guard(async () => { await saveSources(l); await setStatus({ build_knowledge: 'request' }); }, 'Building. This takes a minute or two; press Refresh to see the draft.');
    S.building = false; return done(ok);
  }
  if (act === 'approve' || act === 'reject' || act === 'draftsave') {
    const row = +b.dataset.row, d = S.B.draft.find(x => x.row === row), area = document.querySelector('[data-draft="' + row + '"] textarea'); if (!d) return;
    const next = Object.assign({}, d, { text: area ? area.value.trim() : d.text, status: act === 'approve' ? 'approved' : act === 'reject' ? 'rejected' : d.status });
    return done(await guard(() => saveDraftRow(next), act === 'approve' ? 'Approved. It goes live within a minute.' : act === 'reject' ? 'Rejected' : 'Saved'));
  }
  if (act === 'addbutton') { S.B.buttons = readButtons().concat([{ id: '', url: 'https://', labels: { en: '', ar: '', fr: '', es: '' } }]); return render(); }
  if (act === 'delbutton') { const l = readButtons(); l.splice(+b.dataset.i, 1); S.B.buttons = l; return render(); }
  if (act === 'savebuttons') {
    const l = readButtons().filter(x => x.id || x.url !== 'https://'), bad = l.find(x => !/^[a-z][a-z0-9_]{0,23}$/.test(x.id) || !/^https:\/\//i.test(x.url) || !x.labels.en);
    if (bad) return toast('Each button needs an id (letters, digits, _), an https link and an English label.');
    if (new Set(l.map(x => x.id)).size !== l.length) return toast('Button ids must be different.');
    return done(await guard(() => saveButtons(l), 'Buttons saved'));
  }

  /* platforms */
  if (act === 'saveplatform') { const v = platformValues(b.dataset.p); return done(await guard(() => savePlatform(v), 'Saved. The server connects it within a minute.')); }
  if (act === 'savesecrets') {
    const p = b.dataset.p, entries = SECRET_KEYS[p].map(k => [k, ($('sec_' + p + '_' + k) || {}).value || '']).filter(e => e[1].trim()).map(e => [e[0], e[1].trim()]);
    if (!entries.length) return toast('Paste at least one value first.');
    if (p !== 'ai' && !platformRow(S.brand, p)) await guard(() => savePlatform(platformValues(p)));
    const ok = await guard(() => saveSecrets(S.brand, p, entries), 'Sent to the server. It is checked with Meta and erased from the sheet within a minute.');
    entries.forEach(e => { const el = $('sec_' + p + '_' + e[0]); if (el) el.value = ''; });
    return done(ok);
  }

  /* agent */
  if (act === 'mode') { if (b.dataset.m === 'live') { S.confirmLive = true; return render(); } S.confirmLive = false; return done(await guard(() => setStatus({ mode: 'dry-run' }), 'Dry-run: nothing will be sent')); }
  if (act === 'cancellive') { S.confirmLive = false; return render(); }
  if (act === 'golive') { S.confirmLive = false; return done(await guard(() => setStatus({ mode: 'live' }), 'LIVE. Replies are really sent from now on')); }
  if (act === 'aisave') { return done(await guard(() => setStatus({ ai_provider: g('ai_provider'), ai_model: g('ai_model'), ai_region: g('ai_region'), ai_daily_cap: g('ai_cap'), ai_per_person_per_hour: g('ai_pp'), ai_workspace_id: g('ai_ws') }), 'AI settings saved')); }
  if (act === 'savesettings') {
    const vars = {}; (($('k_vars') || {}).value || '').split('\n').forEach(line => { const m = /^\s*([a-z_]+)\s*=\s*(.+)$/.exec(line); if (m) vars[m[1]] = m[2].trim(); });
    const old = Object.keys(S.B.status).filter(k => k.startsWith('var_') && !vars[k.slice(4)]); const obj = { hourly_cap: g('k_cap'), handoff_hours: g('k_hh'), active_from: g('k_af'), active_to: g('k_at'), utc_offset_minutes: g('k_tz'), opt_out_words: g('k_oo'), opt_out_reply: g('k_oor'), handoff_words: g('k_hw'), handoff_reply: g('k_hr') };
    Object.keys(vars).forEach(k => { obj['var_' + k] = vars[k]; }); old.forEach(k => { obj[k] = ''; });
    return done(await guard(() => setStatus(obj), 'Settings saved'));
  }
  if (act === 'addplaybook') { S.pedit = { i: -1, name: '', text: '' }; return render(); }
  if (act === 'editplaybook') { const p = S.B.playbooks[+b.dataset.i]; if (p) { S.pedit = { i: +b.dataset.i, name: p.name, text: p.text }; render(); } return; }
  if (act === 'pclose') { S.pedit = null; return render(); }
  if (act === 'psave') {
    const name = g('pb_name'), text = $('pb_text').value.trim().slice(0, 6000); if (!name || !text) return toast('Write a name and the instruction.');
    const l = S.B.playbooks.map(p => Object.assign({}, p)); if (S.pedit.i >= 0) { l[S.pedit.i].name = name; l[S.pedit.i].text = text; } else l.push({ name, enabled: true, text });
    S.pedit = null; return done(await guard(() => savePlaybooks(l), 'Playbook saved'));
  }
  if (act === 'pdel') { const l = S.B.playbooks.filter((_p, i) => i !== S.pedit.i); S.pedit = null; return done(await guard(() => savePlaybooks(l), 'Deleted')); }

  /* rules */
  if (act === 'newrule') { S.editor = { isNew: true, rule: blankRule() }; return render(); }
  if (act === 'tpl') { S.tpl = true; return render(); }
  if (act === 'closetpl') { S.tpl = false; return render(); }
  if (act === 'usetpl') { const t = templates()[+b.dataset.i]; S.tpl = false; S.editor = { isNew: true, rule: Object.assign(blankRule(), JSON.parse(JSON.stringify(t.r)), { enabled: false }) }; return render(); }
  if (act === 'edit') { const r = S.B.rules.find(x => x.id === id); if (r) { S.editor = { isNew: false, rule: JSON.parse(JSON.stringify(r)) }; render(); } return; }
  if (act === 'dup') { const r = S.B.rules.find(x => x.id === id); if (r) { const c = JSON.parse(JSON.stringify(r)); c.id = ''; c.name = r.name + ' copy'; c.enabled = false; S.editor = { isNew: true, rule: c }; render(); } return; }
  if (act === 'del') { if (b.dataset.sure !== '1') { b.dataset.sure = '1'; b.textContent = 'Really delete?'; return; } return done(await guard(() => deleteRule(id), 'Rule deleted')); }
  if (act === 'closeedit') { S.editor = null; return render(); }
  if (act === 'saverule') {
    const rule = collectRule(), errs = validateRule(rule), box = $('e_err');
    if (errs.length) { box.hidden = false; box.textContent = errs[0]; return; }
    b.disabled = true; box.hidden = true;
    try { await saveRule(rule, S.editor.isNew); S.editor = null; toast('Saved. The engine picks it up within a minute.'); await refreshAll(true); } catch (e) { b.disabled = false; box.hidden = false; box.textContent = friendly(e); }
    return;
  }

  /* knowledge, inbox */
  if (act === 'kadd') { S.kedit = { row: 0, q: '', a: '' }; return render(); }
  if (act === 'kedit') { const k = S.B.knowledge.find(x => String(x.row) === b.dataset.row); if (k) { S.kedit = { row: k.row, q: k.q, a: k.a }; render(); } return; }
  if (act === 'kclose') { S.kedit = null; return render(); }
  if (act === 'ksave') { const q = g('k_q'), a = g('k_a'); if (!q || !a) return toast('Write both the question and the answer.'); const row = S.kedit.row; S.kedit = null; return done(await guard(() => saveKnowledgeRow(row, q, a), 'Saved. The assistant uses it within a minute.')); }
  if (act === 'kdel') { const row = +b.dataset.row; S.kedit = null; return done(await guard(() => deleteKnowledgeRow(row), 'Deleted')); }
  if (act === 'learnsave') { const item = S.B.learn.find(x => String(x.row) === b.dataset.row), box = document.querySelector('[data-learn="' + b.dataset.row + '"]'), a = box ? box.value.trim() : ''; if (!item || !a) return toast('Write the answer first.'); return done(await guard(() => answerLearn(item, a), 'Saved. The assistant now knows it.')); }
  if (act === 'learnskip') { const item = S.B.learn.find(x => String(x.row) === b.dataset.row); if (item) return done(await guard(() => dismissLearn(item))); return; }
  if (act === 'logf') { S.logFilter = b.dataset.f; return render(); }
  if (act === 'exportleads') return saveFile(S.brand + '-leads.csv', ['time,instagram,name,email,whatsapp,role,profession,city,need,language,consent'].concat(S.B.leads.map(l => [l.time, l.ig, l.name, l.email, l.whatsapp, l.role, l.profession, l.city, l.need, l.lang, l.consent].map(csvCell).join(','))).join('\n'));
  if (act === 'exportpeople') return saveFile(S.brand + '-people.csv', ['id,username,last_seen,last_message,tags,opted_out,human_until'].concat(S.B.contacts.map(p => [p.id, p.username, p.last ? new Date(p.last).toISOString() : '', p.text, p.tags, p.opted ? 'yes' : '', p.handoff].map(csvCell).join(','))).join('\n'));
});

document.addEventListener('change', async ev => {
  const t = ev.target, a = t.dataset && t.dataset.act;
  const done = async ok => { if (ok) { Object.keys(dirty).forEach(k => delete dirty[k]); await refreshAll(true); } };
  if (t.id === 'brandsel') { Object.keys(dirty).forEach(k => delete dirty[k]); S.brand = t.value; try { localStorage.setItem('croom.brand', S.brand); } catch (e) { /* ignore */ } S.B = emptyB(); S.loaded = true; S.busy = false; render(); return refreshAll(); }
  if (a === 'toggle') return done(await guard(() => toggleRule(t.dataset.id, t.checked), t.checked ? 'Rule on' : 'Rule off'));
  if (a === 'aitoggle') return done(await guard(() => setStatus({ ai_enabled: t.checked ? 'TRUE' : 'FALSE' }), t.checked ? 'AI agent ON' : 'AI agent off'));
  if (a === 'kill') return done(await guard(() => setStatus({ kill_switch: t.checked ? 'TRUE' : 'FALSE' }), t.checked ? 'Kill switch ON: nothing is sent' : 'Kill switch off'));
  if (a === 'aicomments') return done(await guard(() => setStatus({ ai_comments: t.checked ? 'TRUE' : 'FALSE' }), 'Saved'));
  if (a === 'aicommentdm') return done(await guard(() => setStatus({ ai_comment_dm: t.checked ? 'TRUE' : 'FALSE' }), 'Saved'));
  if (a === 'integ') return done(await guard(() => setProfile({ [t.dataset.k]: t.checked ? 'TRUE' : 'FALSE' }), 'Saved'));
  if (a === 'pbtoggle') { const l = S.B.playbooks.map(p => Object.assign({}, p)); l[+t.dataset.i].enabled = t.checked; return done(await guard(() => savePlaybooks(l), t.checked ? 'Playbook on' : 'Playbook off')); }
  if (a === 'platenabled') return;
  if (t.id === 'e_trigger') syncEditorVisibility();
  if (t.id && t.id.startsWith('s_')) simRead();
});
document.addEventListener('input', ev => {
  const t = ev.target;
  if (t.matches && t.matches('input:not([type=checkbox]):not([type=password]),textarea,select') && !/^(l_q|p_q|s_)/.test(t.id || '') && !t.closest('.overlay')) { const k = fieldKey(t); if (k) dirty[k] = t.value; }
  if (t.id === 'l_q') { S.logQ = t.value; $('loglist').innerHTML = logList(); }
  if (t.id === 'p_q') { S.peopleQ = t.value; $('peoplelist').innerHTML = peopleList(); }
  if (t.id === 'b_kb' && $('kbcount')) $('kbcount').textContent = t.value.length + ' characters';
  if (t.id && t.id.startsWith('s_')) simRead();
});
function simRead() {
  const s = S.sim, v = i => ($(i) ? $(i).value : '');
  s.channel = v('s_channel') || 'instagram'; s.kind = v('s_kind'); s.username = v('s_user').trim(); s.text = v('s_text'); s.mediaId = v('s_media').trim();
  s.first = $('s_first').checked; s.optedOut = $('s_opt').checked; s.handoff = $('s_hand').checked; s.disabled = $('s_dis').checked;
  $('simout').innerHTML = simOut();
}
document.addEventListener('keydown', ev => { if (ev.key === 'Escape' && (S.editor || S.tpl || S.kedit || S.pedit || S.newBrand)) { S.editor = null; S.tpl = false; S.kedit = null; S.pedit = null; S.newBrand = false; render(); } });

async function boot() {
  try { const v = localStorage.getItem('croom.view'); if (v && VIEWS.some(x => x[0] === v)) S.view = v; const b = localStorage.getItem('croom.brand'); if (b) S.brand = b; } catch (e) { /* ignore */ }
  render();
  try { S.mcp = window.claude ? await window.claude.use('mcp') : null; S.dl = window.claude ? await window.claude.use('downloads') : null; } catch (e) { S.mcp = null; }
  if (!S.mcp) { S.err = 'Google Sheets access is not available in this view. Open the page in claude.ai and allow the Google Sheets connector.'; return render(); }
  await refreshAll();
  setInterval(() => { if (!document.hidden && !S.editor && !S.kedit && !S.pedit && !S.busy && !S.newBrand) refreshAll(true); }, 60000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden && Date.now() - S.syncedAt > 30000 && !S.editor) refreshAll(true); });
}
boot();
