/* ---- Rules engine: JS copy of functions/src/instagram/engine.ts + rulesSheet.ts (parity-tested against the compiled backend). ---- */
const TRIGGERS = ['comment', 'story_reply', 'story_mention', 'dm_keyword', 'dm_welcome', 'dm_default'];
const TRIGGER_LABEL = { comment: 'Comment', story_reply: 'Story reply', story_mention: 'Story mention', dm_keyword: 'DM keyword', dm_welcome: 'First DM (welcome)', dm_default: 'Any other DM' };
const RULE_COLUMNS = ['id', 'enabled', 'name', 'trigger', 'keywords', 'match', 'post_id', 'comment_reply', 'private_reply', 'dm_text', 'button_label', 'button_url', 'quick_replies', 'cooldown_hours', 'tags', 'priority', 'platform'];
const DEFAULTS = { optOutWords: ['stop', 'unsubscribe', 'cancel'], handoffWords: ['agent', 'human', 'support', 'help me'], handoffHours: 24, hourlyCap: 60, sentThisHour: 0, utcOffsetMinutes: 0, vars: {}, optOutReply: "Done, you won't get any more automatic messages from us. Write to us any time if you change your mind.", handoffReply: "Thanks! I'm an automated assistant, so I've passed this to the team and a person will reply here soon." };

const normalize = s => String(s || '').normalize('NFD').replace(/[̀-ًͯ-ٰٟ]/g, '').toLowerCase().replace(/\s+/g, ' ').trim();
const words = s => normalize(s).replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
function matchesKeyword(text, keyword, style) {
  const kw = normalize(keyword); if (!kw) return false;
  if (style === 'word') { const k = words(keyword); if (!k) return normalize(text).includes(kw); return (' ' + words(text) + ' ').includes(' ' + k + ' '); }
  return normalize(text).includes(kw);
}
const matchesRule = (rule, text) => rule.keywords.length === 0 || rule.keywords.some(k => matchesKeyword(text, k, rule.match));
const renderTpl = (t, vars) => String(t).replace(/\{([a-z_]+)\}/g, (_m, k) => (vars[k] != null ? vars[k] : '')).replace(/[ \t]+\n/g, '\n').trim();
function inActiveHours(now, s) {
  if (s.activeFromHour == null || s.activeToHour == null || s.activeFromHour === s.activeToHour) return true;
  const h = new Date(now + s.utcOffsetMinutes * 60000).getUTCHours();
  return s.activeFromHour < s.activeToHour ? h >= s.activeFromHour && h < s.activeToHour : h >= s.activeFromHour || h < s.activeToHour;
}
const ignored = reason => ({ kind: 'ignored', reason, steps: [], tags: [] });
function triggersFor(ev, contact) {
  if (ev.kind === 'comment') return ['comment'];
  if (ev.kind === 'story_reply') return ['story_reply'];
  if (ev.kind === 'story_mention') return ['story_mention'];
  return contact.seenBefore ? ['dm_keyword', 'dm_default'] : ['dm_keyword', 'dm_welcome'];
}
function buildSteps(rule, ev, vars, rand) {
  const steps = [], button = rule.buttonLabel && rule.buttonUrl ? { buttonLabel: rule.buttonLabel, buttonUrl: rule.buttonUrl } : {};
  if (ev.kind === 'comment' && ev.commentId) {
    if (rule.commentReplies.length) {
      const pick = rule.commentReplies[Math.min(rule.commentReplies.length - 1, Math.floor(rand() * rule.commentReplies.length))], t = renderTpl(pick, vars);
      if (t) steps.push({ type: 'comment_reply', commentId: ev.commentId, text: t });
    }
    const priv = renderTpl(rule.privateReply || rule.dm || '', vars);
    if (priv) steps.push(Object.assign({ type: 'private_reply', commentId: ev.commentId, text: priv }, button));
    return steps;
  }
  const dm = renderTpl(rule.dm || rule.privateReply || '', vars);
  if (dm) steps.push(Object.assign({ type: 'dm', recipientId: ev.senderId, text: dm }, button, rule.quickReplies.length ? { quickReplies: rule.quickReplies.slice(0, 13) } : {}));
  return steps;
}
function decide(ev, rules, contact, settings, now, rand) {
  rand = rand || Math.random;
  const text = ev.kind === 'postback' ? (ev.payload || ev.text) : ev.text;
  const isMessage = ev.kind === 'dm' || ev.kind === 'postback' || ev.kind === 'story_reply';
  if (contact.optedOut) return ignored('person opted out');
  if (contact.handoffUntil && contact.handoffUntil > now) return ignored('paused for a human reply');
  if (settings.sentThisHour >= settings.hourlyCap) return ignored('hourly cap of ' + settings.hourlyCap + ' replies reached');
  if (!inActiveHours(now, settings)) return ignored('outside active hours');
  if (!text.trim() && ev.kind !== 'story_mention' && ev.kind !== 'story_reply') return ignored('empty message');
  if (isMessage) {
    if (settings.optOutWords.some(w => matchesKeyword(text, w, 'word'))) return { kind: 'opt_out', reason: 'opt-out word', steps: [{ type: 'dm', recipientId: ev.senderId, text: settings.optOutReply }], tags: ['opted-out'] };
    if (settings.handoffWords.some(w => matchesKeyword(text, w, 'word'))) return { kind: 'handoff', reason: 'asked for a human', steps: [{ type: 'dm', recipientId: ev.senderId, text: settings.handoffReply }], tags: ['handoff'] };
  }
  const triggers = triggersFor(ev, contact);
  const cands = rules.filter(r => r.enabled && triggers.includes(r.trigger))
    .filter(r => !r.platform || r.platform === 'all' || r.platform === (ev.channel || 'instagram'))
    .filter(r => ev.kind !== 'comment' || !r.mediaId || r.mediaId === ev.mediaId)
    .filter(r => r.trigger === 'dm_welcome' || r.trigger === 'dm_default' || matchesRule(r, text))
    .sort((a, b) => b.priority - a.priority || (b.keywords.length > 0) - (a.keywords.length > 0) || triggers.indexOf(a.trigger) - triggers.indexOf(b.trigger));
  if (!cands.length) return ignored('no rule matched');
  const rule = cands.find(r => { const last = contact.lastRuleAt && contact.lastRuleAt[r.id]; return !(r.cooldownHours > 0 && last && now - last < r.cooldownHours * 3600000); });
  if (!rule) return ignored('"' + cands[0].name + '" already replied to this person within its cooldown');
  const vars = Object.assign({}, settings.vars, { username: ev.username || '', first_name: ev.username || 'there' });
  const steps = buildSteps(rule, ev, vars, rand);
  if (!steps.length) return ignored('"' + rule.name + '" matched but has no reply text');
  return { kind: 'replied', reason: 'rule "' + rule.name + '"', ruleId: rule.id, ruleName: rule.name, steps, tags: rule.tags };
}

/* ---- Rules sheet <-> rules ---- */
const splitList = (v, re) => String(v || '').split(re).map(s => s.trim()).filter(Boolean);
const truthy = v => /^(true|yes|y|1|on|✓|✔)$/i.test(String(v || '').trim());
function validateRule(r) {
  const errs = [];
  if (!r.id) errs.push('id is missing');
  if (!TRIGGERS.includes(r.trigger)) errs.push('pick a trigger');
  if (!r.commentReplies.length && !r.privateReply && !r.dm) errs.push('write at least one reply (public comment reply, private reply or DM)');
  if (r.buttonUrl && !/^https:\/\//i.test(r.buttonUrl)) errs.push('button link must start with https://');
  if (r.buttonUrl && !r.buttonLabel) errs.push('a button link needs a button label');
  return errs;
}
function parseRules(values) {
  const rules = [], errors = [];
  if (!values.length) return { rules, errors };
  const header = values[0].map(h => String(h).trim().toLowerCase()), seen = new Set();
  const col = (row, n) => String(row[header.indexOf(n)] == null ? '' : row[header.indexOf(n)]).trim();
  values.slice(1).forEach((row, i) => {
    if (row.every(c => !String(c || '').trim())) return;
    const id = col(row, 'id').replace(/[^A-Za-z0-9_-]/g, ''); if (!id || seen.has(id)) { errors.push('row ' + (i + 2) + ': id missing or duplicate'); return; }
    const m = col(row, 'match').toLowerCase(), cd = col(row, 'cooldown_hours');
    const r = {
      id, name: col(row, 'name') || id, enabled: truthy(col(row, 'enabled')), trigger: col(row, 'trigger').toLowerCase(),
      keywords: splitList(col(row, 'keywords'), /\s*,\s*/), match: m === 'contains' || m === 'any' ? m : 'word', mediaId: col(row, 'post_id') || undefined,
      commentReplies: splitList(col(row, 'comment_reply'), /\s*\|\s*/), privateReply: col(row, 'private_reply') || undefined, dm: col(row, 'dm_text') || undefined,
      buttonLabel: col(row, 'button_label') || undefined, buttonUrl: col(row, 'button_url') || undefined, quickReplies: splitList(col(row, 'quick_replies'), /\s*,\s*/),
      cooldownHours: cd !== '' && Number.isFinite(Number(cd)) ? Math.max(0, Number(cd)) : 24, tags: splitList(col(row, 'tags'), /\s*,\s*/), priority: Number(col(row, 'priority')) || 0,
      platform: ['instagram', 'whatsapp'].includes(col(row, 'platform').toLowerCase()) ? col(row, 'platform').toLowerCase() : 'all',
    };
    const e = validateRule(r); if (e.length) { errors.push('row ' + (i + 2) + ' (' + id + '): ' + e[0]); return; }
    seen.add(id); rules.push(r);
  });
  return { rules, errors };
}
const safeCell = v => { v = String(v == null ? '' : v); return /^[=+\-@]/.test(v) ? "'" + v : v; };
const ruleToRow = r => [r.id, r.enabled ? 'TRUE' : 'FALSE', r.name, r.trigger, r.keywords.join(', '), r.match, r.mediaId || '', r.commentReplies.join(' | '), r.privateReply || '', r.dm || '', r.buttonLabel || '', r.buttonUrl || '', r.quickReplies.join(', '), String(r.cooldownHours), r.tags.join(', '), String(r.priority), r.platform && r.platform !== 'all' ? r.platform : ''].map(safeCell);
const blankRule = () => ({ id: '', name: '', enabled: false, trigger: 'comment', keywords: [], match: 'word', mediaId: undefined, commentReplies: [], privateReply: undefined, dm: undefined, buttonLabel: undefined, buttonUrl: undefined, quickReplies: [], cooldownHours: 24, tags: [], priority: 0, platform: 'all' });
function settingsFromStatus(map) {
  const out = {}, num = (k, lo, hi) => { const raw = (map[k] || '').trim(), n = Number(raw); return raw !== '' && Number.isFinite(n) && n >= lo && n <= hi ? Math.round(n) : undefined; };
  const list = k => { const l = splitList(map[k], /\s*,\s*/); return l.length ? l : undefined; }, text = k => (map[k] || '').trim() || undefined;
  const set = (k, v) => { if (v !== undefined) out[k] = v; };
  set('hourlyCap', num('hourly_cap', 1, 1000)); set('handoffHours', num('handoff_hours', 1, 168)); set('activeFromHour', num('active_from', 0, 23)); set('activeToHour', num('active_to', 0, 23));
  set('utcOffsetMinutes', num('utc_offset_minutes', -720, 840)); set('optOutWords', list('opt_out_words')); set('handoffWords', list('handoff_words')); set('optOutReply', text('opt_out_reply')); set('handoffReply', text('handoff_reply'));
  const vars = {}; Object.keys(map).forEach(k => { if (k.startsWith('var_') && /^[a-z_]+$/.test(k.slice(4)) && String(map[k]).trim()) vars[k.slice(4)] = String(map[k]).trim(); });
  if (Object.keys(vars).length) out.vars = vars;
  return out;
}

