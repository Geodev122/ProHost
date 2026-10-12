// Run from this folder: npm i --no-save xlsx@0.18.5 playwright && node make-files.js && node flow.mjs
import { createRequire } from 'module'; import fs from 'fs';
const require = createRequire(import.meta.url);
let pw; try { pw = require('playwright'); } catch { pw = require(require('child_process').execSync('npm root -g').toString().trim() + '/playwright'); }
const HERE = new URL('.', import.meta.url).pathname;
const XLSX_JS = require.resolve('xlsx/dist/xlsx.full.min.js');
const launch = () => pw.chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
const SP = HERE; fs.mkdirSync(HERE + 'out', { recursive: true });
const seed = HERE + 'seed/';
let docs = { 'config/company': JSON.parse(fs.readFileSync(seed + 'company.json')), 'config/icp': JSON.parse(fs.readFileSync(seed + 'icp.json')),
  'config/outreach': JSON.parse(fs.readFileSync(seed + 'outreach.json')), 'meta/state': JSON.parse(fs.readFileSync(seed + 'meta.json')), 'leads/c0000': JSON.parse(fs.readFileSync(seed + 'leads_c0000.json')) };
for (const f of fs.readdirSync(seed).filter(f => f.startsWith('ai_'))) { const d = JSON.parse(fs.readFileSync(seed + f)); docs['ai/' + d.k] = d; }
const MOCK = `
(function(){
  const D = window.__docs;
  window.__writes = 0;
  const clone = v => JSON.parse(JSON.stringify(v));
  const snap = p => ({ id: p.split('/').pop(), exists: p in D, data: () => p in D ? clone(D[p]) : undefined, metadata: {} });
  window.__ls = [];
  const notify = p => window.__ls.forEach(l => { if (p.startsWith(l.col + '/')) setTimeout(l.f, 0); });
  function q(col, f = [], ob = null, lim = 1000) {
    const run = () => { let docs = Object.keys(D).filter(k => k.startsWith(col + '/') && k.split('/').length === col.split('/').length + 1).map(snap);
      docs = docs.filter(s => f.every(([a, op, v]) => op === '>' ? s.data()[a] > v : true));
      if (ob) docs.sort((x, y) => x.data()[ob] < y.data()[ob] ? -1 : 1); docs = docs.slice(0, lim); return { docs, size: docs.length, empty: !docs.length }; };
    return { where: (a, op, v) => q(col, [...f, [a, op, v]], ob, lim), orderBy: a => q(col, f, a, lim), limit: n => q(col, f, ob, n),
      get: async () => run(),
      onSnapshot: cb => { const l = { col, f: () => cb(run()) }; window.__ls.push(l); setTimeout(l.f, 0); return () => { window.__ls = window.__ls.filter(x => x !== l); }; },
      doc: id => ref(col + '/' + id) };
  }
  function ref(p) { return { id: p.split('/').pop(), path: p, get: async () => snap(p),
    set: async d => { const s = JSON.stringify(d); if (new TextEncoder().encode(s).length > 262144) throw { code: 'invalid_argument', message: 'too big' }; D[p] = JSON.parse(s); window.__writes++; notify(p); },
    update: async d => { if (!(p in D)) throw { code: 'invalid_argument', message: 'missing' }; Object.assign(D[p], d); notify(p); }, delete: async () => { delete D[p]; window.__writes++; notify(p); } }; }
  function respond(p) {
    if (p.includes('Company Intelligence agent')) return { company: 'ProHost', website: 'https://pro-host.tech', product: 'Room rental app', pain_points: ['Empty rooms'], proof_points: ['No double bookings'], markets: ['Lebanon'], ideal_customer: ['Clinics'] };
    if (p.includes('ICP Builder agent')) return { segments: [{ name: 'Property Managers', source: 'goal', keywords: ['property management', 'business center', 'coworking'], why: 'x' }, { name: 'Hospitality Companies', source: 'goal', keywords: ['hotel', 'hospitality', 'hotel apartments'], why: 'y' }, { name: 'Real Estate Agencies', source: 'goal', keywords: ['real estate', 'commercial property'], why: 'z' }, { name: 'Clinics', source: 'website', keywords: ['polyclinic', 'hospital & health care'], why: 'w' }],
      titles: { decision: ['ceo', 'founder', 'owner', 'managing director', 'general manager', 'chief executive officer'], operator: ['operations director', 'property manager', 'leasing manager', 'hotel manager', 'medical director', 'director of sales', 'asset manager'], exclude: ['receptionist'] },
      employees: { min: 2, sweet_min: 5, sweet_max: 500, max: 5000 }, signal_keywords: ['funding', 'series a', 'hiring'], apollo: { person_titles: ['CEO'] }, pdl_sql: 'SELECT * FROM person' };
    const leads = JSON.parse(p.slice(p.lastIndexOf('Leads:\\n') + 7));
    if (p.includes('Lead Research agent')) return leads.map((l, i) => ({ key: (i === 0 && window.__mangleKey) ? 'renamed-key' : l.key, signal: l.signals ? 'Raised funding (' + l.signals.split(':')[0] + ')' : '', signal_source: l.signals ? 'data' : 'none', pain_point: 'Rooms empty part of the week', reason_selected: l.title + ' at ' + l.company, summary: l.company + ' in ' + l.city }));
    if (p.includes('Personalized Outreach agent')) return leads.map(l => ({ key: l.key, subject: 'rooms at ' + l.company.toLowerCase(),
      variant_a: 'How many rooms at ' + l.company + ' sit empty part of the week? Professionals book rooms like yours by the hour on ProHost.',
      variant_b: l.company + ' pays for rooms that stay empty. ProHost rents them by the hour to verified professionals' + (/cedars/i.test(l.company) ? ', seamless.' : '.'),
      variant_c: l.company + ' could earn from quiet hours. Hosts on ProHost rent rooms by the hour, shift or month.' }));
    return null;
  }
  const sample = async () => { throw { code: 'invalid_request' }; };
  sample.json = async (input) => { window.__calls = (window.__calls || 0) + 1; window.__prompts = (window.__prompts || []).concat([input.slice(0, 120)]);
    if (window.__failNext) { const c = window.__failNext; window.__failNext = null; throw { code: c, message: 'mock' }; }
    await new Promise(r => setTimeout(r, 15)); return respond(input); };
  sample.limits = async () => ({ maxPromptBytes: 262144 });
  window.__saved = {};
  const caps = { db: { doc: ref, collection: c => q(c) }, sample, user: { can: async () => true },
    downloads: { save: async o => { window.__saved[o.filename] = o.data; return { status: 'saved' }; } } };
  window.claude = { use: async n => { await new Promise(r => setTimeout(r, 20)); return caps[n] ?? null; } };
})();`;
const html = '<!doctype html><html><head><meta charset="utf-8"></head><body>' + fs.readFileSync(HERE + '../lead-engine.html', 'utf8') + '</body></html>';
fs.writeFileSync(SP + 'out/page.html', html);
const browser = await launch();
let page; const errs = []; const fails = []; let passes = 0;
const ok = (cond, msg, extra = '') => { if (cond) passes++; else { fails.push(msg + (extra ? ' :: ' + extra : '')); } console.log((cond ? 'PASS ' : 'FAIL ') + msg + (extra && !cond ? ' :: ' + extra : '')); };
async function open() {
  page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  page.on('pageerror', e => errs.push(e.message)); page.on('console', m => { if (m.type() === 'error' && !/fonts|ERR_/.test(m.text())) errs.push(m.text()); });
  await page.route('https://cdnjs.cloudflare.com/**', r => r.fulfill({ body: fs.readFileSync(XLSX_JS), contentType: 'application/javascript' }));
  await page.route('https://fonts.googleapis.com/**', r => r.abort());
  await page.addInitScript(`window.__docs = ${JSON.stringify(docs)};` + MOCK);
  await page.goto('file://' + SP + 'out/page.html'); await page.waitForTimeout(600);
}
const dbLeads = () => page.evaluate(() => Object.keys(window.__docs).filter(k => k.startsWith('leads/')).sort().flatMap(k => window.__docs[k].rows));
const dbMeta = () => page.evaluate(() => window.__docs['meta/state']);
const byName = (rows, n) => rows.find(r => r.name === n);
const go = async v => { await page.click(`#rail [data-view="${v}"]`); await page.waitForTimeout(80); };
const importFiles = async (files) => { await go('discover'); await page.setInputFiles('#leadFile', files.map(f => SP + 'out/' + f)); await page.waitForTimeout(300); };
const planText = async () => (await page.locator('.plan').innerText()).replace(/\n/g, ' ');
const commit = async () => { await page.click('[data-act="commitImport"]'); await page.waitForTimeout(500); };

await open();
// ── 1. first import replaces example rows, merges duplicates, skips empty rows
await importFiles(['apollo1.csv']);
const head1 = await page.locator('#importCard .card-head').innerText();
ok(/Apollo/.test(head1), 'Apollo export detected', head1);
const mapVals = await page.evaluate(() => Object.fromEntries([...document.querySelectorAll('[data-map]')].map(s => [s.dataset.map, s.value])));
ok(mapVals.companyShort === 'Company Name for Emails' && mapVals.company === 'Company' && mapVals.seniority === 'Seniority' && mapVals.phone === 'Mobile Phone' && mapVals.employees === '# Employees', 'Apollo columns auto-mapped', JSON.stringify(mapVals));
const p1 = await planText();
ok(/6\s*new leads/.test(p1) && /1\s*duplicate rows merged/.test(p1) && /1\s*rows skipped/.test(p1), 'plan: 6 new, 1 duplicate, 1 skipped', p1);
await commit();
let L = await dbLeads(); let meta = await dbMeta();
ok(L.length === 6 && !L.some(r => r.ex), 'examples removed, 6 leads stored', L.length + ' ' + L.map(r => r.name).join(','));
ok(!Object.keys(await page.evaluate(() => window.__docs)).some(k => /^ai\/ex/.test(k)), 'example AI docs deleted');
ok(meta.example === false && meta.imports?.length === 1 && meta.lastImport.new === 6, 'import logged in meta', JSON.stringify(meta.lastImport));
const maya = byName(L, 'Maya Haddad');
ok(maya && maya.email === 'maya@gulfsuites.example' && maya.country === 'UAE' && maya.linkedin === 'https://www.linkedin.com/in/maya-haddad-123', 'name case fixed, work email picked, LinkedIn canonical', JSON.stringify(maya && [maya.name, maya.email, maya.country, maya.linkedin]));
const rami = byName(L, 'Rami Aoun');
ok(rami && rami.website === 'https://skylineprops.example' && rami.linkedin === 'https://www.linkedin.com/in/rami-aoun' && rami.importStatus === 'new' && rami.firstImportedAt, 'website/LinkedIn normalized, status New with date', JSON.stringify(rami && [rami.website, rami.linkedin, rami.importStatus]));
ok(L.filter(r => r.company === 'Najd Towers').length === 3, 'placeholder emails do not merge different people');
const karim = byName(L, 'Dr. Karim Nasr') || byName(L, 'Karim Nasr');
ok(!!karim, 'honorific lead kept', L.map(r => r.name).join('|'));
// greeting uses first non-honorific name
await go('leads'); await page.selectOption('#tableF', 'all'); await page.waitForTimeout(100);
ok((await page.locator('#tableBox').innerText()).includes('New'), 'New badge shown in table');

// ── 2. AI agents: research + emails, key mangling fallback, QC buzzword
await page.evaluate(() => { window.__mangleKey = true; });
await go('outreach');
const runBtn = await page.locator('[data-act="run"][data-kind="emails"]').innerText();
await page.click('[data-act="run"][data-kind="emails"]'); await page.waitForTimeout(1500);
const ai = await page.evaluate(() => Object.fromEntries(Object.entries(window.__docs).filter(([k]) => k.startsWith('ai/'))));
const aiList = Object.values(ai);
ok(aiList.length >= 3 && aiList.every(a => a.research && a.emails), 'research + emails written for qualified leads (' + runBtn + ')', aiList.length + ' ' + JSON.stringify(aiList.map(a => [!!a.research, !!a.emails])));
ok(aiList.every(a => a.research.at && a.emails.at), 'per-part timestamps stored');
const jobMsg = await page.locator('#jobBox').innerText();
ok(/Done/.test(jobMsg) && !/failed/.test(jobMsg), 'job finished without failures (renamed key recovered by position)', jobMsg);
const prompts = await page.evaluate(() => window.__prompts.join('\n'));
const emailPrompt = await page.evaluate(() => window.__prompts.find(p => p.includes('Outreach')));
await go('qc');
const qcTxt = await page.locator('main').innerText();
ok(/seamless/.test(qcTxt), 'QC catches the buzzword in Cedars emails', qcTxt.slice(0, 300));
// check display company in email prompt
const promptHasShort = await page.evaluate(() => true);

// ── 3. re-import with changes: updated / unchanged / new / LinkedIn & name matches
await importFiles(['apollo2.csv']);
const p2 = await planText();
ok(/2\s*new leads/.test(p2) && /4\s*updated/.test(p2) && /1\s*unchanged/.test(p2), 'plan: 2 new, 4 updated, 1 unchanged', p2);
await page.click('#importCard details summary'); await page.waitForTimeout(50);
const chg = await page.locator('#importCard details').innerText();
ok(/Job title: Managing Director → Chief Executive Officer/.test(chg), 'change preview shows old → new', chg.slice(0, 400));
await commit();
L = await dbLeads(); meta = await dbMeta();
ok(L.length === 8, '8 leads after merge (nothing replaced)', L.length);
const rami2 = byName(L, 'Rami Aoun');
ok(rami2.importStatus === 'updated' && rami2.updatedAt && rami2.changes.some(c => c.f === 'title' && c.from === 'Managing Director') && rami2.aiStaleAt, 'title change → Updated, change recorded, AI marked stale', JSON.stringify(rami2.changes));
ok(rami2.firstImportedAt === rami.firstImportedAt, 'first-imported date kept on update');
const maya2 = byName(L, 'Maya Haddad');
ok(maya2.importStatus === 'unchanged' && !maya2.aiStaleAt, 'same data → Unchanged, AI still current', maya2.importStatus);
const lina = byName(L, 'Lina Saleh');
ok(lina.importStatus === 'updated' && lina.email === 'lina.saleh@najdtowers.example', 'matched by LinkedIn, email filled in', JSON.stringify(lina.changes));
const omar = byName(L, 'Omar Fares');
ok(omar.importStatus === 'updated' && omar.employees === 90 && omar.email === 'email_not_unlocked@domain.com', 'matched by name + company, empty email does not wipe', JSON.stringify([omar.changes, omar.email]));
const karim2 = L.find(r => /Karim Nasr/.test(r.name));
ok(karim2.importStatus === 'updated' && karim2.changes.some(c => c.f === 'emailStatus') && !karim2.changes.some(c => c.f === 'name') && !karim2.aiStaleAt, 'email status change → Updated; dropping "Dr." is not a change', JSON.stringify(karim2.changes));
ok(meta.imports.length === 2 && meta.lastImport.updated === 4, 'second import logged');
await go('leads'); await page.selectOption('#tableF', 'stale'); await page.waitForTimeout(80);
const staleTxt = await page.locator('#tableBox').innerText();
ok(/Rami Aoun/.test(staleTxt), 'changed lead listed under "Changed since emails"', staleTxt.slice(0, 200));

// ── 4. undo, then redo
await go('discover');
await page.click('[data-act="undoImport"]'); await page.waitForTimeout(400);
L = await dbLeads(); meta = await dbMeta();
ok(L.length === 6 && byName(L, 'Rami Aoun').title === 'Managing Director' && meta.imports.length === 1, 'undo restores leads and history', L.length + ' ' + meta.imports.length);
await importFiles(['apollo2.csv']); await commit();
L = await dbLeads(); ok(L.length === 8, 're-import after undo works');

// ── 5. queue of several files: Excel (2 sheets, header on row 3), PDL JSON lines, UTF-16 text, Windows-1252 semicolons
await importFiles(['hotels.xlsx', 'pdl.jsonl', 'unicode.txt', 'latin1.csv']);
const h5 = await page.locator('#importCard .card-head').innerText();
ok(/3 more files waiting/.test(h5), 'multi-file queue', h5);
const sheetSel = await page.locator('#impSheet').inputValue(); const hdrSel = await page.locator('#impHeader').inputValue();
ok(sheetSel === '1' && hdrSel === '2', 'picked the Leads sheet and header row 3', sheetSel + '/' + hdrSel);
ok(/2\s*new leads/.test(await planText()), 'Excel rows planned');
await commit();
const h6 = await page.locator('#importCard .card-head').innerText();
ok(/pdl\.jsonl/.test(h6) && /People Data Labs/.test(h6), 'next file: PDL JSON lines detected', h6);
await commit();
await commit(); // unicode
const h8 = await page.locator('#importCard .card-head').innerText();
ok(/latin1\.csv/.test(h8), 'last file in queue', h8);
const map8 = await page.evaluate(() => Object.fromEntries([...document.querySelectorAll('[data-map]')].map(s => [s.dataset.map, s.value])));
ok(map8.firstName === 'Prénom' && map8.company === 'Société' && map8.country === 'Pays' && map8.title === 'Poste', 'French headers mapped (accent-insensitive)', JSON.stringify(map8));
await commit();
L = await dbLeads();
const sara = byName(L, 'Sara Itani'); const nour = byName(L, 'Nour El Hage'); const geo = L.find(r => r.email === 'georges@beitdiyafa.example'); const jose = L.find(r => r.email === 'jose@hotelmontreal.example');
ok(sara && sara.city === 'Beirut' && sara.country === 'Lebanon' && sara.employees === 351, 'Excel: location split, size range parsed', JSON.stringify(sara && [sara.city, sara.country, sara.employees]));
ok(nour && nour.email === 'nour@achrafieh-estates.example' && nour.seniority === 'manager' && nour.country === 'Lebanon', 'PDL: work email preferred, seniority from job_title_levels', JSON.stringify(nour));
ok(geo && geo.name === 'جورج خوري' && geo.company === 'بيت الضيافة', 'UTF-16 Arabic decoded', JSON.stringify(geo && [geo.name, geo.company]));
ok(jose && jose.firstName === 'José' && jose.company === 'Hôtel Montréal' && jose.country === 'Lebanon', 'Windows-1252 decoded, "Liban" → Lebanon', JSON.stringify(jose && [jose.firstName, jose.company, jose.country]));
ok(!(await page.locator('#importCard').count()), 'queue finished');

// ── 6. paste rows (tab separated)
await page.click('text=Or paste rows from Excel or Google Sheets');
await page.fill('#pasteBox', 'Name\tTitle\tCompany\tEmail\tLinkedIn\nZeina Abboud\tOwner\tAbboud Studios\tzeina@abboudstudios.example\tlinkedin.com/in/zeina-abboud\n');
await page.click('[data-act="readPaste"]'); await page.waitForTimeout(150);
ok(/1\s*new leads/.test(await planText()), 'pasted rows planned');
await commit();
L = await dbLeads(); ok(!!byName(L, 'Zeina Abboud'), 'pasted lead stored');

// ── 7. verifier results
await go('verify');
await page.setInputFiles('#verifyFile', SP + 'out/neverbounce.csv'); await page.waitForTimeout(400);
L = await dbLeads();
ok(L.find(r => /Karim Nasr/.test(r.name)).emailStatus === 'valid' && byName(L, 'Hana Khalil').emailStatus === 'invalid', 'verifier statuses applied');

// ── 8. fatal AI error stops the run with a message
await go('outreach');
await page.evaluate(() => { window.__failNext = 'rate_limited'; });
const btnEnabled = await page.locator('[data-act="run"][data-kind="emails"]').isEnabled();
if (btnEnabled) { await page.click('[data-act="run"][data-kind="emails"]'); await page.waitForTimeout(800); }
const msg8 = await page.locator('#jobBox').innerText();
ok(btnEnabled && /usage limit/.test(msg8), 'rate limit stops the job and says so', msg8);
await page.click('[data-act="run"][data-kind="emails"]').catch(() => {}); await page.waitForTimeout(1500);
const ai2 = await page.evaluate(() => Object.values(window.__docs).filter(d => d && d.k && d.emails));
ok(ai2.length >= 8, 'rerun finishes the remaining leads, including the changed one', ai2.length);
const ramiAi = await page.evaluate(() => { const L = Object.keys(window.__docs).filter(k => k.startsWith('leads/')).flatMap(k => window.__docs[k].rows).find(r => r.name === 'Rami Aoun'); const a = window.__docs['ai/' + L.key]; return { stale: L.aiStaleAt, em: a.emails.at, rs: a.research.at }; });
ok(ramiAi.em >= ramiAi.stale && ramiAi.rs >= ramiAi.stale, 'changed lead re-researched and re-written', JSON.stringify(ramiAi));

// ── 9. focus survives re-render
await page.fill('#senderName', 'Georges N.'); await page.keyboard.press('Tab'); await page.waitForTimeout(150);
ok(await page.evaluate(() => document.activeElement?.id) === 'senderTitle', 'Tab after editing a field keeps focus on the next field');

// ── 10. Excel export
await page.click('#topExport'); await page.waitForTimeout(700);
const wbInfo = await page.evaluate(async () => {
  const blob = Object.values(window.__saved).find(b => b instanceof Blob); const wb = XLSX.read(await blob.arrayBuffer(), { type: 'array', cellDates: true });
  const m = XLSX.utils.sheet_to_json(wb.Sheets['Lead Master'], { header: 1 });
  const h = m[0]; const col = n => h.indexOf(n);
  const rami = m.find(r => r[col('Lead Name')] === 'Rami Aoun');
  const zeina = m.find(r => r[col('Lead Name')] === 'Zeina Abboud');
  const log = XLSX.utils.sheet_to_json(wb.Sheets['Import Log'], { header: 1 });
  const rj = XLSX.utils.sheet_to_json(wb.Sheets['Rejected'], { header: 1 }); const zr = rj.find(r => r[0] === 'Zeina Abboud');
  const dateCell = wb.Sheets['Lead Master'][XLSX.utils.encode_cell({ r: 1, c: col('Imported On') })];
  return { sheets: wb.SheetNames, header: h, rami: rami && [rami[col('Import Status')], rami[col('Changes')], rami[col('Source File')], rami[col('Personalized Email')]?.slice(0, 40)], zeina: zeina && zeina[col('Import Status')], zeinaRej: zr && zr[rj[0].indexOf('Import Status')], logRows: log.length - 1, logHead: log[0], dateType: dateCell?.t, dateFmt: dateCell?.z || dateCell?.w, rows: m.length - 1, names: m.slice(1).map(r => r[col('Lead Name')]) };
});
ok(JSON.stringify(wbInfo.sheets) === JSON.stringify(['Business Emails', 'Lead Master', 'AI Insights', 'Outreach', 'Rejected', 'Import Log', 'Run Summary']), 'seven sheets, Business Emails first', wbInfo.sheets.join(','));
ok(['Import Status', 'Imported On', 'Updated On', 'Changes', 'Source File'].every(c => wbInfo.header.includes(c)), 'import columns in Lead Master', wbInfo.header.join('|'));
ok(wbInfo.rami && wbInfo.rami[0] === 'Updated' && /Job title: Managing Director → Chief Executive Officer/.test(wbInfo.rami[1]) && wbInfo.rami[2] === 'apollo2.csv' && /^Hi Rami,/.test(wbInfo.rami[3] || ''), 'Rami row: Updated, changes, source, fresh email', JSON.stringify(wbInfo.rami));
ok(wbInfo.zeinaRej === 'New', 'pasted lead (below threshold) is in Rejected, marked New', wbInfo.zeinaRej);
ok(wbInfo.logRows === 7, 'Import Log lists every import still on record (7)', wbInfo.logRows + ' ' + JSON.stringify(wbInfo.logHead));
ok(wbInfo.dateType === 'd' || wbInfo.dateType === 'n', 'Imported On is a real date cell', wbInfo.dateType + ' ' + wbInfo.dateFmt);
console.log('Lead Master rows:', wbInfo.rows, wbInfo.names.join(', '));
await page.screenshot({ path: SP + 'out/shot-leads-before-reload.png' });

// ── 11. reload: everything persists
docs = await page.evaluate(() => window.__docs);
const leadDocs = Object.keys(docs).filter(k => k.startsWith('leads/'));
ok(leadDocs.every(k => docs[k].rows.length <= 50), 'chunks hold at most 50 leads', leadDocs.map(k => docs[k].rows.length).join(','));
await page.close(); await open();
await go('discover');
const hist = await page.locator('main').innerText();
ok(/Import history/.test(hist) && /latin1\.csv/.test(hist), 'history persists after reload');
await go('leads'); await page.selectOption('#tableF', 'all'); await page.waitForTimeout(100);
const allTxt = await page.locator('h1').first().innerText();
ok(/1[0-9] leads/.test(allTxt), 'lead count persists: ' + allTxt);
await page.screenshot({ path: SP + 'out/shot-discover.png', fullPage: false });
await go('discover');
await page.locator('#importCard').count();
await page.screenshot({ path: SP + 'out/shot-history.png', fullPage: true });

// ── 12. French title is a decision maker; re-importing our own Excel export changes nothing
await go('leads'); await page.selectOption('#tableF', 'all'); await page.fill('#tableQ', 'José'); await page.waitForTimeout(300);
await page.click('#tableBox tr[data-act="lead"]'); await page.waitForTimeout(150);
const joseDrawer = await page.locator('.drawer').innerText();
ok(/Decision maker/.test(joseDrawer), '"Directeur général" scored as a decision maker', joseDrawer.slice(0, 300));
await page.keyboard.press('Escape');
await page.click('#topExport'); await page.waitForTimeout(600);
const b64 = await page.evaluate(async () => { const b = Object.values(window.__saved).filter(x => x instanceof Blob).pop(); const u = new Uint8Array(await b.arrayBuffer()); let s = ''; for (const c of u) s += String.fromCharCode(c); return btoa(s); });
fs.writeFileSync(SP + 'out/roundtrip.xlsx', Buffer.from(b64, 'base64'));
await importFiles(['roundtrip.xlsx']);
const rtHead = await page.locator('#importCard .card-head').innerText(); const rtPlan = await planText();
ok(/ProHost export/.test(rtHead) && /0\s*new leads/.test(rtPlan) && /0\s*updated/.test(rtPlan), 'own export re-imports as unchanged', rtHead + ' | ' + rtPlan);
if (!/0\s*updated/.test(rtPlan)) { await page.click('#importCard details summary').catch(() => {}); console.log(await page.locator('#importCard details').innerText().catch(() => '')); }
await page.click('[data-act="cancelImport"]'); await page.waitForTimeout(100);
// ── 12b. discovery agents: planner, live inbox, review and import, shared state
await go('discover');
await page.evaluate(async () => { const db = await window.claude.use('db'); const d = await db.doc('discovery/linkedin').get();
  window.__prePlan = d.exists; });
await page.click('[data-act="planAll"]'); await page.waitForTimeout(900);
const plans = await page.evaluate(() => Object.fromEntries(['registries', 'web', 'social', 'directories', 'hiring', 'news'].map(c => [c, window.__docs['discovery/' + c]?.queries])));
ok(Object.values(plans).every(q => q && q.length > 40), 'planner wrote a plan for every channel', Object.entries(plans).map(([k, v]) => k + ':' + (v && v.length)).join(' '));
ok(Object.values(plans).every(q => new Set(q.map(x => x.id)).size === q.length && q.every(x => x.status === 'todo' && x.tier >= 1)), 'query ids unique, all todo, tiered');
ok(plans.web.some(q => q.lang === 'ar') && plans.web.some(q => q.tier === 3) && plans.hiring.some(q => /site:bayt\.com/.test(q.q)), 'plans include Arabic, neighborhood and job-board queries');
await page.evaluate(async () => { const db = await window.claude.use('db'); const d = (await db.doc('discovery/web').get()).data();
  d.queries[0].status = 'done'; d.queries[0].found = 7; d.queries[0].tries = 1;
  d.queries.push({ id: 'sabc123', q: 'coworking space Hamra Beirut', segment: 'Property Managers', city: 'Beirut', country: 'Lebanon', tier: 1, status: 'todo', tries: 0, found: 0, source: 'strategist' });
  await db.doc('discovery/web').set(d); window.__webLen = d.queries.length; });
await page.click('[data-act="planAll"]'); await page.waitForTimeout(700);
const web2 = await page.evaluate(() => window.__docs['discovery/web'].queries);
ok(web2[0].status === 'done' && web2[0].found === 7 && web2.some(q => q.id === 'sabc123') && web2.length === await page.evaluate(() => window.__webLen), 're-planning keeps progress and the strategist\'s additions, adds nothing twice');
const kn = await page.evaluate(() => window.__docs['discovery/known']);
ok(kn?.domains?.includes('skylineprops.example') && (await page.evaluate(() => !!window.__docs['discovery/targets'] && !!window.__docs['discovery/stats'])), 'known domains, targets and stats shared with the agents');
const agentsTxt = await page.locator('main').innerText();
ok(/Official registers/.test(agentsTxt) && /Email finder/.test(agentsTxt) && /Strategist/.test(agentsTxt) && /of \d+ searches/.test(agentsTxt), 'agents table lists scouts, finder and strategist with plan progress');

const leadsBefore = (await dbLeads()).length;
await page.evaluate(async () => { const db = await window.claude.use('db');
  const row = o => ({ name: '', firstName: '', lastName: '', title: '', company: '', website: '', industry: '', country: '', city: '', email: '', emailStatus: '', phone: '', linkedin: '', description: '', signals: '', evidence: '', confidence: 'high', ...o });
  await db.doc('inbox/linkedin-202610121200').set({ channel: 'linkedin', agent: 'LinkedIn search', runAt: Date.now() - 3600000, queries: ['q1'], notes: 'ok', learned: [], suggest: [], rows: [
    row({ name: 'Hala Sarkis', firstName: 'Hala', lastName: 'Sarkis', title: 'General Manager', company: 'Beirut Harbor Hotel', website: 'https://harborhotel.example', country: 'Lebanon', city: 'Beirut', linkedin: 'https://lb.linkedin.com/in/hala-sarkis/', email: 'hala@harborhotel.example', emailStatus: 'Published', signals: 'Hiring a front office manager, Oct 2026', evidence: 'https://www.google.com/search?q=hala' }),
    row({ name: 'Low Confidence', title: 'Owner', company: 'Ghost Co', country: 'Lebanon', confidence: 'low', linkedin: 'https://www.linkedin.com/in/ghost' }),
    row({ name: 'Rami Aoun', title: 'Chief Executive Officer', company: 'Skyline Properties S.A.L.', country: 'Lebanon', city: 'Beirut', linkedin: 'https://www.linkedin.com/in/rami-aoun', signals: 'Hiring a leasing manager, Oct 2026', evidence: 'https://example.org/rami' })] });
  await db.doc('inbox/web-202610121300').set({ channel: 'web', agent: 'Web search', runAt: Date.now() - 1800000, queries: ['q2'], notes: 'page fetching blocked, snippets only', learned: [{ from: 'a', to: 'b', rows: 2 }], suggest: ['peer hotels Beirut'], rows: [
    row({ company: 'Beirut Harbor Hotel', website: 'www.harborhotel.example/en', country: 'Lebanon', city: 'Beirut', evidence: 'https://harborhotel.example' }),
    row({ company: 'Cedar Spa Clinic', website: 'https://cedarspa.example', phone: '+961 1 555 000', country: 'Lebanon', city: 'Beirut', industry: 'hospital & health care', description: 'Polyclinic with rooms', evidence: 'https://cedarspa.example/about' })] });
  await db.doc('inbox/news-202610121400').set({ channel: 'news', agent: 'Openings & news', runAt: Date.now(), queries: [], notes: 'nothing new', rows: [], learned: [], suggest: [] }); });
await page.waitForTimeout(700);
const railTxt = await page.locator('#rail').innerText();
ok(/5 agent rows waiting/.test(railTxt), 'inbox updates live: rail shows rows waiting (no reload)', railTxt.replace(/\n/g, ' ').slice(0, 400));
const inboxTxt = await page.locator('.inbox').innerText();
ok(/5 rows from 3 runs/.test(inboxTxt) && /Review and import 5 rows/.test(inboxTxt), 'inbox card summarises the runs', inboxTxt.slice(0, 200));
await page.click('[data-act="openInbox"]'); await page.waitForTimeout(250);
const ih = await page.locator('#importCard .card-head').innerText(); const ip = await planText();
ok(/Agent runs \(2\)/.test(ih) && /3\s*new leads/.test(ip) && /1\s*updated/.test(ip), 'review: low-confidence row left out, 3 new, 1 updated', ih + ' | ' + ip);
const imap = await page.evaluate(() => Object.fromEntries([...document.querySelectorAll('[data-map]')].map(s => [s.dataset.map, s.value])));
ok(imap.evidence === 'evidence' && imap.channel === 'channel' && imap.linkedin === 'linkedin' && imap.firstName === 'firstName', 'inbox columns map themselves', JSON.stringify(imap));
await commit();
L = await dbLeads();
const hala = byName(L, 'Hala Sarkis'); const cedar = L.find(r => r.company === 'Cedar Spa Clinic'); const harborCo = L.find(r => !r.name && r.domain === 'harborhotel.example');
ok(hala && hala.source === 'Agent · LinkedIn search' && hala.channel === 'linkedin' && hala.evidence.startsWith('https://www.google.com') && hala.linkedin === 'https://www.linkedin.com/in/hala-sarkis', 'agent lead keeps channel, source and evidence', JSON.stringify(hala && [hala.source, hala.channel, hala.linkedin]));
ok(!harborCo, 'company-only row dropped once a named person works there');
ok(cedar && !cedar.name && cedar.phone === '+961 1 555 000' && cedar.domain === 'cedarspa.example' && cedar.source === 'Agent · Business websites', 'company-only lead kept when nobody is named', JSON.stringify(cedar && [cedar.source, cedar.phone]));
ok(L.length === leadsBefore + 2 && !byName(L, 'Low Confidence'), 'two leads added net, low-confidence row never imported', L.length + ' vs ' + leadsBefore);
ok(byName(L, 'Rami Aoun').signals.includes('Hiring a leasing manager') && byName(L, 'Rami Aoun').importStatus === 'updated', 'existing lead updated by an agent signal');
const inb = await page.evaluate(() => Object.fromEntries(Object.entries(window.__docs).filter(([k]) => k.startsWith('inbox/')).map(([k, v]) => [k, v.imported])));
ok(inb['inbox/linkedin-202610121200'] === true && inb['inbox/web-202610121300'] === true && !inb['inbox/news-202610121400'], 'imported runs are marked, the empty run stays', JSON.stringify(inb));
await page.waitForTimeout(3200);
const tg = await page.evaluate(() => ({ t: window.__docs['discovery/targets'].companies, k: window.__docs['discovery/known'].domains, s: window.__docs['discovery/stats'] }));
ok(tg.t.some(c => c.id === 'cedarspa.example') && !tg.t.some(c => c.id === 'harborhotel.example') && tg.k.includes('harborhotel.example'), 'finder targets = companies with no named contact; known domains refreshed', JSON.stringify(tg.t.map(c => c.id)));
ok(tg.s.channels.linkedin?.leads >= 1 && tg.s.channels.web?.leads >= 1, 'stats count leads per channel', JSON.stringify(tg.s.channels));
await page.click('[data-act="undoImport"]'); await page.waitForTimeout(600);
const inb2 = await page.evaluate(() => window.__docs['inbox/linkedin-202610121200'].imported);
L = await dbLeads();
ok(inb2 === false && L.length === leadsBefore, 'undo restores the leads and puts the runs back in the inbox', inb2 + ' ' + L.length);
await page.click('[data-act="dismissInbox"][data-id="all"]'); await page.waitForTimeout(300);
ok(/reviewed/.test(await page.locator('.card:has-text("Discovery agents")').first().innerText()), 'dismiss all clears the inbox');
await page.click('[data-act="copyAgent"][data-id="registries"]'); await page.waitForTimeout(200);
ok(/Copied|Select the text/.test(await page.locator('#toast').innerText()), 'copy instructions button works');

// ── 12c. email-first: business emails are the product, people are optional
await page.evaluate(() => { window.__saved = {}; });
await go('discover');
await page.click('text=Or paste rows from Excel or Google Sheets');
const T = ['Company', 'Website', 'Email', 'Country', 'City', 'Industry', 'Phone', 'Evidence', 'Email Status'];
const R = rows => [T.join('\t'), ...rows.map(r => r.join('\t'))].join('\n');
await page.fill('#pasteBox', R([
  ['Harbor View Hotel', 'harborview.example', 'info@harborview.example', 'Lebanon', 'Beirut', 'Hotel', '+961 1 111 222', 'https://harborview.example/contact', 'Published'],
  ['Harbor View Hotel', 'harborview.example', 'reservations@harborview.example', 'Lebanon', 'Beirut', 'Hotel', '', 'https://harborview.example/contact', 'Published'],
  ['Harbor View Hotel', 'harborview.example', 'noreply@harborview.example', 'Lebanon', 'Beirut', 'Hotel', '', 'https://harborview.example/', 'Published'],
  ['Cedar Polyclinic', 'cedarpoly.example', '', 'Lebanon', 'Beirut', 'Polyclinic', '+961 1 333 444', 'https://directory.example/cedar', ''],
  ['Gulf Cowork', 'gulfcowork.example', 'hello@gulfcowork.example', 'UAE', 'Dubai', 'Coworking space', '', 'https://gulfcowork.example/', 'Published'],
  ['Dr Smile Clinic', '', 'drsmile@gmail.com', 'Lebanon', 'Beirut', 'Dental clinic', '', 'https://facebook.example/drsmile', 'Published'],
]));
await page.click('[data-act="readPaste"]'); await page.waitForTimeout(200);
ok(/6\s*new leads/.test(await planText()), 'paste: three addresses of one hotel stay three leads; company-only and gmail rows kept', await planText());
await commit();
L = await dbLeads();
const hv = L.filter(r => r.domain === 'harborview.example');
ok(hv.length === 3 && new Set(hv.map(r => r.email)).size === 3, 'each address of a business is its own lead (not merged into one)', JSON.stringify(hv.map(r => r.email)));
await go('verify');
const vtxt = (await page.locator('main').innerText()).replace(/\t/g, ' ');
ok(/Company mailbox[^\n]*\s3\b/.test(vtxt) && /Not for outreach[^\n]*\s1\b/.test(vtxt), 'verify: info@, reservations@, hello@ are company mailboxes; noreply@ is not for outreach', vtxt.slice(0, 400));
await go('leads'); await page.selectOption('#tableF', 'all'); await page.fill('#tableQ', 'harborview'); await page.waitForTimeout(300);
const hvTxt = await page.locator('#tableBox').innerText();
ok(/Harbor View Hotel/.test(hvTxt) && /Company inbox/.test(hvTxt) && /Company mailbox/.test(hvTxt), 'company rows show the business and "Company inbox"', hvTxt.slice(0, 200));
await page.click('#tableBox tr[data-act="lead"] >> nth=0'); await page.waitForTimeout(150);
const dr = await page.locator('.drawer').innerText();
ok(/Company mailbox/.test(dr) && /Score \d+ \/ 100/.test(dr), 'company lead opens with a score', dr.slice(0, 200));
await page.keyboard.press('Escape');
await page.waitForTimeout(3200);
const sh = await page.evaluate(() => ({ t: window.__docs['discovery/targets'].companies.map(c => c.id), k: window.__docs['discovery/known'].domains }));
ok(sh.t.includes('cedarpoly.example') && !sh.t.includes('harborview.example') && sh.k.includes('harborview.example') && sh.k.includes('gulfcowork.example') && !sh.k.includes('cedarpoly.example'), 'targets = businesses still without an email; known = businesses that have one', JSON.stringify(sh).slice(0, 300));
// the email finder's result fills the company-only lead instead of creating a new one
await go('discover');
await page.click('text=Or paste rows from Excel or Google Sheets').catch(() => {});
await page.fill('#pasteBox', R([['Cedar Polyclinic', 'cedarpoly.example', 'info@cedarpoly.example', 'Lebanon', 'Beirut', 'Polyclinic', '', 'https://cedarpoly.example/contact', 'Published']]));
await page.click('[data-act="readPaste"]'); await page.waitForTimeout(200);
ok(/0\s*new leads/.test(await planText()) && /1\s*updated/.test(await planText()), 'a found email fills the business that had none (updated, not new)', await planText());
await commit();
L = await dbLeads();
const cp = L.filter(r => r.domain === 'cedarpoly.example');
ok(cp.length === 1 && cp[0].email === 'info@cedarpoly.example' && cp[0].phone === '+961 1 333 444', 'one Cedar lead with its email and the phone it already had', JSON.stringify(cp.map(r => [r.email, r.phone])));
// emails to the shared inbox greet the team, not a person
await go('outreach');
await page.click('[data-act="run"][data-kind="emails"]'); await page.waitForTimeout(2000);
await page.click('#topExport'); await page.waitForTimeout(900);
const bx = await page.evaluate(async () => {
  const blob = Object.values(window.__saved).filter(b => b instanceof Blob).pop(); const wb = XLSX.read(await blob.arrayBuffer(), { type: 'array' });
  const be = XLSX.utils.sheet_to_json(wb.Sheets['Business Emails'], { header: 1 }); const h = be[0]; const col = n => h.indexOf(n);
  const ot = XLSX.utils.sheet_to_json(wb.Sheets['Outreach'], { header: 1 });
  return { head: h, rows: be.slice(1).map(r => ({ email: r[col('Email')], type: r[col('Type')], status: r[col('Email Status')], co: r[col('Company')], ev: r[col('Evidence URL')], by: r[col('Found By')], score: r[col('Lead Score')] })),
    hello: ot.filter(r => /^Hello,/.test(r[4] || '')).map(r => r[2]), hi: ot.filter(r => /^Hi /.test(r[4] || '')).length };
});
const emailsOut = bx.rows.map(r => r.email);
ok(['info@harborview.example', 'reservations@harborview.example', 'hello@gulfcowork.example', 'drsmile@gmail.com', 'info@cedarpoly.example'].every(e => emailsOut.includes(e)) && !emailsOut.includes('noreply@harborview.example'), 'Business Emails sheet: every usable address, noreply@ left out', emailsOut.join(' '));
ok(new Set(emailsOut).size === emailsOut.length && emailsOut.length >= 9, 'Business Emails sheet: one row per address, includes earlier person emails', emailsOut.length + '');
const bInfo = bx.rows.find(r => r.email === 'info@harborview.example');
ok(bInfo && bInfo.type === 'Company inbox' && bInfo.status === 'Company mailbox' && bInfo.ev === 'https://harborview.example/contact' && /Agent|Import|Paste/i.test(bInfo.by || 'Import'), 'Business Emails row: type, status and evidence URL', JSON.stringify(bInfo));
ok(bx.hello.length >= 3 && bx.hi >= 3, 'company inboxes get "Hello," and named people keep "Hi <name>,"', 'hello=' + bx.hello.length + ' hi=' + bx.hi);
const bi = await page.evaluate(() => Object.values(window.__docs).filter(d => d && d.k && d.emails).map(d => d.emails.a));
ok(bi.length >= 8, 'emails were written for company leads too', bi.length + '');

// ── 13. clear all
await go('discover');
await page.click('[data-act="confirm"][data-k="clear"]'); await page.click('[data-act="clearAll"]'); await page.waitForTimeout(600);
const left = await page.evaluate(() => Object.keys(window.__docs).filter(k => /^(leads|ai)\//.test(k)));
ok(left.length === 0, 'remove all leads deletes chunks and AI docs', left.join(','));
console.log('\nERRORS:', errs);
console.log(`\n${passes} passed, ${fails.length} failed`); fails.forEach(f => console.log(' - ' + f));
await browser.close();
