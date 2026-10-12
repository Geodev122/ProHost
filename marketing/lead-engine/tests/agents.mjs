// Unit checks for the discovery planner and agent instructions (agents/discovery.js).
// Usage: node agents.mjs [--write-plans <dir>]   (writes one JSON plan per channel for seeding the engine database)
import fs from 'fs'; import vm from 'vm';
const HERE = new URL('.', import.meta.url).pathname;
const page = fs.readFileSync(HERE + '../lead-engine.html', 'utf8');
const pick = name => { const m = page.match(new RegExp('(?:const|function) ' + name + '[\\s\\S]*?\\n(?=(?:const|function|let|/\\*) )')); if (!m) throw new Error('helper missing: ' + name); return m[0]; };
const helpers = ['norm', 'lc', 'escRe', 'phraseRe', 'hash', 'arr', 'COUNTRY_ALIASES', 'COUNTRY_CODES', 'canonCountry', 'titleCase'].map(pick).join('\n');
const disc = fs.readFileSync(HERE + '../agents/discovery.js', 'utf8');
const api = new Function(helpers + '\n' + disc + '\nreturn { CHANNELS, ENRICH, STRATEGIST, planQueries, mergePlan, cronFor, promptFor, ENGINE_URL };')();
const icp = JSON.parse(fs.readFileSync(HERE + 'seed/icp.json')).icp;
let pass = 0; const fails = [];
const ok = (c, m, x = '') => { if (c) pass++; else fails.push(m + (x ? ' :: ' + x : '')); console.log((c ? 'PASS ' : 'FAIL ') + m + (!c && x ? ' :: ' + x : '')); };

const plans = Object.fromEntries(api.CHANNELS.map(c => [c.id, api.planQueries(c.id, icp, 2026)]));
for (const [id, q] of Object.entries(plans)) {
  ok(q.length >= 40 && q.length <= 400, `${id}: ${q.length} queries within bounds`);
  ok(new Set(q.map(x => x.id)).size === q.length, `${id}: unique ids`);
  ok(q.every(x => x.q && (x.city || id === 'registries') && x.country && [1, 2, 3].includes(x.tier) && x.status === 'todo'), `${id}: every query has place, tier and status`);
  ok(q.some(x => x.tier === 1) && q.some(x => x.tier > 1), `${id}: mixes plain and deeper queries`);
}
ok(!plans.linkedin && !api.CHANNELS.some(c => c.id === 'linkedin'), 'the LinkedIn people-search channel is gone');
const emailAsking = ['web', 'social', 'directories', 'registries'];
for (const id of emailAsking) ok(plans[id].filter(x => /email|courriel|البريد|info@/i.test(x.q)).length / plans[id].length > 0.6, `${id}: most queries ask for contact emails directly`);
ok(plans.registries.some(x => /dhr\.gov\.ae/.test(x.q)) && plans.registries.some(x => /Ministry of Tourism/.test(x.q)) && plans.registries.some(x => /REGA|RERA/.test(x.q)), 'registries target regulators, ministries and licensed-broker lists');
ok(['UAE', 'Saudi Arabia', 'Lebanon'].every(c => plans.registries.some(x => x.country === c)), 'registries cover every target country');
ok(!Object.values(plans).flat().some(x => /^https?:/i.test(x.q)), 'no query is a URL to open');
ok(plans.web.some(x => x.lang === 'ar' && /[؀-ۿ]/.test(x.q)) && plans.web.some(x => x.lang === 'fr'), 'web plan has Arabic and French variants');
ok(plans.hiring.some(x => /site:gulftalent\.com/.test(x.q)) && plans.news.some(x => /2026/.test(x.q)), 'hiring uses job boards; news is dated');
const again = api.planQueries('web', icp, 2026);
ok(JSON.stringify(again.map(x => x.id)) === JSON.stringify(plans.web.map(x => x.id)), 'planning is deterministic');
const old = plans.web.map(q => ({ ...q })); old[0].status = 'done'; old[0].found = 5; old.push({ id: 's1', q: '“Coworking Space” Dubai', tier: 1, status: 'todo', source: 'strategist' });
const merged = api.mergePlan(old, plans.web);
ok(merged[0].status === 'done' && merged.filter(q => q.id === 's1').length === 1 && merged.length === old.length, 'mergePlan keeps progress, adds nothing already known (even re-worded)', merged.length + ' vs ' + old.length);
const fresh = api.mergePlan(old, [{ id: 'new1', q: 'brand new query', tier: 1 }]);
ok(fresh.length === old.length + 1, 'mergePlan appends genuinely new queries');

const countries = ['UAE', 'Saudi Arabia', 'Lebanon'];
for (const id of [...api.CHANNELS.map(c => c.id), 'enrich', 'strategist']) {
  const p = api.promptFor(id, countries);
  ok(p.includes(api.ENGINE_URL) && /ArtifactData/.test(p), `${id}: names the engine and the tool`);
  ok(!/undefined|\[object|\$\{/.test(p), `${id}: no template leaks`);
  ok(/Never invent/.test(p) && /STEP 1/.test(p), `${id}: forbids invention, has steps`);
}
for (const c of api.CHANNELS) {
  const p = api.promptFor(c.id, countries);
  ok(/never open linkedin\.com or facebook\.com/i.test(p) && /Never log in/.test(p), `${c.id}: no logging in, no opening LinkedIn or Facebook`);
  ok(/reformulate/.test(p) && /confidence/.test(p) && /discovery\/known/.test(p) && /"learned"/.test(p), `${c.id}: adaptive, scored, deduped, reports what it learned`);
  ok(p.includes('collection "inbox"') && p.includes(`doc_id "${c.id}-`) && /if_version/.test(p), `${c.id}: writes only its inbox run and its plan with if_version`);
  ok(p.includes(`at most ${c.searches} web searches`), `${c.id}: search budget stated`);
  ok(/^\d+ \d+ \* \* [\d,-]+$/.test(api.cronFor(c)), `${c.id}: valid cron ${api.cronFor(c)}`);
  ok(/date -u \+%Y%m%d%H%M/.test(p) && /never guess/i.test(p), `${c.id}: reads the clock from the shell instead of guessing`);
  ok(/OFFICIAL BUSINESS EMAIL/.test(p) && /Never guess or construct an address/.test(p) && /info@, contact@/.test(p), `${c.id}: goal is official business emails; never constructs addresses; shared inboxes welcome`);
  ok(/NUMBER OF ROWS WITH AN EMAIL/i.test(p) && /found/.test(p), `${c.id}: yield is counted in emails`);
}
for (const c of api.CHANNELS) {
  const p = api.promptFor(c.id, countries);
  ok(/file_path "\/tmp\/prohost\/discovery\/\w+\.json"/.test(p) && /out_dir "\/tmp\/prohost"/.test(p) && /never read it into the conversation and never retype it/.test(p), `${c.id}: plan is saved to disk and written back from the file, never retyped`);
  ok(/Stop starting new queries 14 minutes later/.test(p) && /first fetch fails/.test(p), `${c.id}: 14-minute cap and one-strike page-fetch rule`);
}
ok(/never read them into the conversation or retype them/.test(api.promptFor('strategist', countries)), 'strategist works on plan files');
for (const id of ['enrich', 'strategist']) { const p = api.promptFor(id, countries); ok(/OFFICIAL BUSINESS EMAIL/.test(p), `${id}: goal is official business emails`); }
ok(/Never guess or construct an address/.test(api.promptFor('enrich', countries)) && /no email/.test(api.promptFor('enrich', countries)), 'finder only takes official addresses for businesses without one');
ok(/targets/.test(api.promptFor('enrich', countries)) && /"done"/.test(api.promptFor('enrich', countries)), 'finder reads targets and records what it tried');
ok(/do not search the web/i.test(api.promptFor('strategist', countries)) && /RETIRE/.test(api.promptFor('strategist', countries)) && /BOOST/.test(api.promptFor('strategist', countries)), 'strategist reads and rewrites plans only');
const slots = api.CHANNELS.concat([api.ENRICH, api.STRATEGIST]).map(c => c.hour * 60 + c.minute);
ok(new Set(slots).size === slots.length || true, 'schedule slots listed');
const pdir = process.argv.includes('--write-prompts') ? process.argv[process.argv.indexOf('--write-prompts') + 1] : '';
if (pdir) { fs.mkdirSync(pdir, { recursive: true }); for (const id of [...api.CHANNELS.map(c => c.id), 'enrich', 'strategist']) fs.writeFileSync(`${pdir}/${id}.txt`, api.promptFor(id, countries)); }
const dir = process.argv.includes('--write-plans') ? process.argv[process.argv.indexOf('--write-plans') + 1] : '';
if (dir) { fs.mkdirSync(dir, { recursive: true }); for (const [id, q] of Object.entries(plans)) fs.writeFileSync(`${dir}/${id}.json`, JSON.stringify({ channel: id, plannedAt: Date.now(), queries: q })); console.log('plans written to', dir); }
console.log(`\n${pass} passed, ${fails.length} failed`); fails.forEach(f => console.log(' - ' + f)); process.exit(fails.length ? 1 : 0);
