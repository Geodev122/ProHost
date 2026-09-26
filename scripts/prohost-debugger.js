#!/usr/bin/env node
/**
 * ProHost Static-Analysis Debugger — NIGHTHAWK
 *
 * Codeword: NIGHTHAWK
 * Usage   : node scripts/prohost-debugger.js      (from repo root)
 * Output  : scripts/nighthawk-report.html
 *           scripts/nighthawk-report.json
 *
 * Covers: Cloud Function consistency, Firestore rules coverage, empty catch
 * blocks, forced unwraps, coroutine error handling, screen loading/error/empty
 * states, hardcoded secrets, deep-link registration, AuthStep completeness,
 * TypeScript safety, orphaned modules, navigation graph, and more.
 */

'use strict';

const fs   = require('fs');
const path = require('path');

// ─── Paths ───────────────────────────────────────────────────────────────────

const ROOT          = path.resolve(__dirname, '..');
const KT_SCREENS    = path.join(ROOT, 'app/src/main/java/com/example/ui/screens');
const KT_VM         = path.join(ROOT, 'app/src/main/java/com/example/ui/viewmodel');
const KT_DATA       = path.join(ROOT, 'app/src/main/java/com/example/data');
const KT_UI         = path.join(ROOT, 'app/src/main/java/com/example/ui');
const KT_ROOT       = path.join(ROOT, 'app/src/main/java/com/example');
const FN_SRC        = path.join(ROOT, 'functions/src');
const FIRESTORE_RULES = path.join(ROOT, 'firestore.rules');
const MANIFEST      = path.join(ROOT, 'app/src/main/AndroidManifest.xml');
const OUT_JSON      = path.join(__dirname, 'nighthawk-report.json');
const OUT_HTML      = path.join(__dirname, 'nighthawk-report.html');

// ─── Utilities ────────────────────────────────────────────────────────────────

function readSafe(filePath) {
  try { return fs.readFileSync(filePath, 'utf8'); }
  catch { return null; }
}

function walkFiles(dir, ext) {
  const results = [];
  if (!fs.existsSync(dir)) return results;
  function walk(d) {
    for (const entry of fs.readdirSync(d, { withFileTypes: true })) {
      const full = path.join(d, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (!ext || entry.name.endsWith(ext)) results.push(full);
    }
  }
  walk(dir);
  return results;
}

function relPath(absPath) {
  return path.relative(ROOT, absPath);
}

function grepFile(filePath, regex) {
  const content = readSafe(filePath);
  if (!content) return [];
  const results = [];
  const lines = content.split('\n');
  lines.forEach((line, i) => {
    const re = new RegExp(regex.source, regex.flags.includes('g') ? regex.flags : regex.flags + 'g');
    let m;
    while ((m = re.exec(line)) !== null) {
      results.push({ file: relPath(filePath), line: i + 1, match: m[0], groups: m, lineText: line.trim() });
    }
  });
  return results;
}

function grepDir(dir, ext, regex) {
  const files = walkFiles(dir, ext);
  return files.flatMap(f => grepFile(f, regex));
}

// ─── Finding Store ────────────────────────────────────────────────────────────

const SEV_ORDER = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3, INFO: 4 };
const findings  = [];
const passes    = [];

function bug(severity, category, check, file, line, message, suggestion) {
  findings.push({ severity, category, check, file: file || null, line: line || null, message, suggestion: suggestion || null });
}

function pass(check, message) {
  passes.push({ check, message });
}

function esc(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ─── CHECK 1: Cloud Function name consistency ─────────────────────────────────

function checkCloudFunctionNames() {
  const ktFiles = walkFiles(KT_DATA, '.kt');
  const calledFns  = new Map(); // name → first call site
  const callSites  = {};

  for (const f of ktFiles) {
    for (const m of grepFile(f, /getHttpsCallable\("([^"]+)"\)/g)) {
      const name = m.groups[1];
      if (!calledFns.has(name)) calledFns.set(name, m);
      if (!callSites[name]) callSites[name] = [];
      callSites[name].push({ file: m.file, line: m.line });
    }
  }

  const indexContent = readSafe(path.join(FN_SRC, 'index.ts')) || '';
  const exportedFns  = new Set();

  for (const m of indexContent.matchAll(/export\s*\{([^}]+)\}/g)) {
    m[1].split(',').forEach(n => exportedFns.add(n.trim().split(/\s+as\s+/)[0].trim()));
  }
  for (const m of indexContent.matchAll(/export\s+const\s+(\w+)/g)) {
    exportedFns.add(m[1]);
  }

  // Firestore/HTTP triggers and HTTP endpoints (never called via getHttpsCallable)
  const triggerExports = new Set([
    'ping','onBookingRequestCreated','onBookingRequestStatusChanged','onBookingPaymentAcknowledged',
    'onWorkspaceListingCreated','onWorkspaceListingDeleted','onWorkspaceListingStatusChanged',
    'onWorkspaceListingPublishValidation','onWorkspaceListingDeletedCleanup',
    'onBookingAcceptConflictGuard','onUserFavoritesChanged','expirePackages',
    'bootstrapSuperAdmin','listingShareLanding','legalDocumentPage','clickEmailOtpLink',
    'playBillingRtdn','verifyEmailLink','assignInitialRole',
  ]);

  let mismatches = 0;
  for (const [name, site] of calledFns) {
    if (!exportedFns.has(name)) {
      mismatches++;
      bug('CRITICAL','consistency','CF Name Consistency', site.file, site.line,
        `getHttpsCallable("${name}") called from Kotlin but "${name}" is NOT exported in functions/src/index.ts. This feature WILL throw at runtime.`,
        `Implement and export the "${name}" Cloud Function, or remove the Kotlin call if the feature is not ready.`);
    }
  }

  let orphans = 0;
  for (const name of exportedFns) {
    if (!calledFns.has(name) && !triggerExports.has(name)) {
      orphans++;
      bug('INFO','consistency','CF Orphaned Export','functions/src/index.ts', null,
        `Cloud Function "${name}" is exported but never called via getHttpsCallable() in Kotlin. May be a trigger/HTTP endpoint or dead code.`,
        `Verify this is intentional. If dead code, remove it to keep the deploy surface small.`);
    }
  }

  if (mismatches === 0) pass('CF Name Consistency', `All ${calledFns.size} getHttpsCallable() calls match exported functions in index.ts.`);
}

// ─── CHECK 2: Firestore collection coverage ───────────────────────────────────

function checkFirestoreRules() {
  const tsFiles  = walkFiles(FN_SRC, '.ts');
  const used     = new Map(); // collection → first occurrence

  for (const f of tsFiles) {
    for (const m of grepFile(f, /\.collection\("([^"]+)"\)/g)) {
      if (!used.has(m.groups[1])) used.set(m.groups[1], m);
    }
  }

  const rulesContent = readSafe(FIRESTORE_RULES) || '';
  const covered = new Set();
  for (const m of rulesContent.matchAll(/match\s+\/(\w+)\//g)) covered.add(m[1]);

  // Admin-SDK-only: client rules intentionally absent
  const serverOnly = new Set(['email_otps']);

  let gaps = 0;
  for (const [name, site] of used) {
    if (!covered.has(name) && !serverOnly.has(name)) {
      gaps++;
      bug('HIGH','security','Firestore Rules Coverage', site.file, site.line,
        `Collection "${name}" is accessed in Cloud Functions but has no rule block in firestore.rules. Falls through to default-deny — client-side reads will silently fail.`,
        `Add an explicit rule for "${name}" in firestore.rules, even if it should be "allow read, write: if false" (to document the server-only intent).`);
    }
  }

  if (serverOnly.has('email_otps') && used.has('email_otps')) {
    pass('Firestore Rules Coverage (email_otps)', '"email_otps" is intentionally Admin-SDK-only — no client rule needed.');
  }
  if (gaps === 0) pass('Firestore Rules Coverage', `All ${used.size} Firestore collections used in Cloud Functions are covered by firestore.rules or are documented as server-only.`);
}

// ─── CHECK 3: Empty catch blocks ─────────────────────────────────────────────

function checkEmptyCatch() {
  const files = [...walkFiles(KT_ROOT, '.kt')];
  let count = 0;
  for (const f of files) {
    for (const m of grepFile(f, /\}\s*catch\s*\([^)]*\)\s*\{\s*\}/g)) {
      count++;
      bug('MEDIUM','reliability','Empty Catch Block', m.file, m.line,
        `Empty catch block silently swallows exceptions. If this path fails, users see nothing and logs capture nothing.`,
        `At minimum: Log.e(TAG, "...", e). Better: surface _errorMessage.value = e.message to the UI.`);
    }
  }
  if (count === 0) pass('Empty Catch Block', 'No empty catch blocks found.');
}

// ─── CHECK 4: Forced non-null assertions !!  ─────────────────────────────────

function checkForcedUnwrap() {
  const files = [...walkFiles(KT_SCREENS, '.kt'), ...walkFiles(KT_VM, '.kt')];
  let count = 0;
  for (const f of files) {
    const content = readSafe(f);
    if (!content) continue;
    content.split('\n').forEach((line, i) => {
      if (line.trim().startsWith('//') || line.trim().startsWith('*')) return;
      const stripped = line.replace(/"(?:[^"\\]|\\.)*"/g, '""');
      const n = (stripped.match(/!!/g) || []).length;
      if (n > 0) {
        count++;
        bug('MEDIUM','reliability','Forced Non-Null Assertion', relPath(f), i + 1,
          `Forced non-null assertion (!!) can throw NullPointerException during recomposition or async state transitions.`,
          `Use safe call ?. with a fallback, requireNotNull() with a message, or guard with a null check before use.`);
      }
    });
  }
  if (count === 0) pass('Forced Non-Null Assertion', 'No !! unwraps found in screen/viewmodel files.');
}

// ─── CHECK 5: Screens missing loading state ───────────────────────────────────

function checkScreenLoading() {
  const files = walkFiles(KT_SCREENS, '.kt');
  let missing = 0;
  for (const f of files) {
    const c = readSafe(f) || '';
    if (!/isLoading|Loading|CircularProgress/.test(c)) {
      missing++;
      bug('MEDIUM','ux','Screen Loading State', relPath(f), null,
        `Screen has no loading indicator. Users see a blank or stale screen while data is fetching.`,
        `Add an isLoading state in the ViewModel and render a CircularProgressIndicator overlay while loading.`);
    }
  }
  pass('Screen Loading State', `${files.length - missing}/${files.length} screens have a loading indicator.`);
}

// ─── CHECK 6: Screens missing error state ─────────────────────────────────────

function checkScreenError() {
  const files = walkFiles(KT_SCREENS, '.kt');
  let missing = 0;
  for (const f of files) {
    const c = readSafe(f) || '';
    if (!/errorMessage|isError|ErrorText|\.error|onError|snackbar/i.test(c)) {
      missing++;
      bug('LOW','ux','Screen Error State', relPath(f), null,
        `Screen has no visible error feedback. Failed network or permission operations go unnoticed by the user.`,
        `Add errorMessage: String? to the screen's state and display it in a Snackbar or a red Text composable.`);
    }
  }
  if (missing === 0) pass('Screen Error State', 'All screens surface error feedback.');
}

// ─── CHECK 7: List screens missing empty state ────────────────────────────────

function checkScreenEmptyState() {
  const files = walkFiles(KT_SCREENS, '.kt');
  let missing = 0;
  for (const f of files) {
    const c = readSafe(f) || '';
    if (!/LazyColumn|LazyRow/.test(c)) continue; // not a list screen
    if (!/isEmpty\(\)|isNullOrEmpty|\.empty\b|no.*item|nothing.*here|no.*found|empty.*state/i.test(c)) {
      missing++;
      bug('MEDIUM','ux','Screen Empty State', relPath(f), null,
        `Screen has a list (LazyColumn/LazyRow) but no empty-state branch. Users see a blank screen when there is no data.`,
        `When the list is empty, show an icon + descriptive message (e.g. "No listings yet — tap + to add your first space.").`);
    }
  }
  if (missing === 0) pass('Screen Empty State', 'All list screens handle the empty case.');
}

// ─── CHECK 8: Coroutines without error handling ───────────────────────────────

function checkCoroutineErrors() {
  const files = walkFiles(KT_VM, '.kt');
  let count = 0;
  for (const f of files) {
    const content = readSafe(f);
    if (!content) continue;
    const lines = content.split('\n');
    for (let i = 0; i < lines.length; i++) {
      if (/viewModelScope\.launch\s*\{/.test(lines[i])) {
        const window = lines.slice(i + 1, Math.min(i + 7, lines.length)).join('\n');
        if (!/\btry\b|\brunCatching\b|\bcatch\b/.test(window)) {
          count++;
          bug('HIGH','reliability','Coroutine Error Handling', relPath(f), i + 1,
            `viewModelScope.launch block has no try-catch/runCatching within the first 6 lines. Uncaught exceptions are swallowed silently — the UI never shows an error.`,
            `Wrap the body: try { ... } catch (e: Exception) { _errorMessage.value = e.localizedMessage } or use runCatching { ... }.onFailure { ... }.`);
        }
      }
    }
  }
  if (count === 0) pass('Coroutine Error Handling', 'All viewModelScope.launch blocks have nearby error handling.');
}

// ─── CHECK 9: Hardcoded secrets ───────────────────────────────────────────────

function checkHardcodedSecrets() {
  const files = [
    ...walkFiles(KT_ROOT, '.kt'),
    ...walkFiles(FN_SRC, '.ts'),
  ];
  const patterns = [
    { re: /AIza[0-9A-Za-z\-_]{35}/,        name: 'Google API key' },
    { re: /password\s*=\s*"[^"]{6,}"/i,     name: 'Hardcoded password' },
    { re: /api_?key\s*=\s*"[^"]{8,}"/i,     name: 'Hardcoded API key' },
    { re: /sk_live_[0-9a-zA-Z]{24}/,         name: 'Stripe live key' },
    { re: /Bearer\s+[A-Za-z0-9_\-.]{30,}/,   name: 'Bearer token' },
    { re: /SMTP.*password\s*=\s*"[^"]+"/i,   name: 'SMTP password' },
  ];
  let count = 0;
  for (const f of files) {
    const content = readSafe(f);
    if (!content) continue;
    content.split('\n').forEach((line, i) => {
      if (/^\s*(\/\/|\*)/.test(line)) return;
      for (const { re, name } of patterns) {
        if (re.test(line)) {
          count++;
          bug('CRITICAL','security','Hardcoded Secret', relPath(f), i + 1,
            `Possible ${name} hardcoded in source. Will be visible in version control and the compiled APK.`,
            `Move to local.properties + BuildConfig (Android) or Google Cloud Secret Manager / environment variables (Cloud Functions).`);
        }
      }
    });
  }
  if (count === 0) pass('Hardcoded Secret', 'No obvious hardcoded secrets found.');
}

// ─── CHECK 10: Deep link registration ─────────────────────────────────────────

function checkDeepLinks() {
  const tsFiles = walkFiles(FN_SRC, '.ts');
  const usedHosts = new Map(); // host → first occurrence

  for (const f of tsFiles) {
    for (const m of grepFile(f, /prohost:\/\/([\w-]+)/g)) {
      const host = m.groups[1];
      if (!usedHosts.has(host)) usedHosts.set(host, m);
    }
  }
  // Also check Kotlin for deep links sent to external systems
  for (const f of walkFiles(KT_ROOT, '.kt')) {
    for (const m of grepFile(f, /prohost:\/\/([\w-]+)/g)) {
      const host = m.groups[1];
      if (!usedHosts.has(host)) usedHosts.set(host, m);
    }
  }

  const manifestContent = readSafe(MANIFEST) || '';
  const registeredHosts = new Set();
  for (const m of manifestContent.matchAll(/android:scheme="prohost"[\s\S]*?android:host="([^"]+)"/g)) {
    registeredHosts.add(m[1]);
  }
  // Also catch host declared before scheme (order varies in XML)
  for (const m of manifestContent.matchAll(/android:host="([^"]+)"[^/]*\/>/g)) {
    // Rough: if this intent-filter block also contains scheme="prohost"
    // We already extracted all prohost hosts from the grep above, just use the full manifest scan
  }
  // Re-extract all prohost hosts from manifest robustly
  const manifestBlocks = manifestContent.split('<intent-filter');
  for (const block of manifestBlocks) {
    if (block.includes('scheme="prohost"') || block.includes("scheme='prohost'")) {
      const hm = block.match(/android:host="([^"]+)"/);
      if (hm) registeredHosts.add(hm[1]);
    }
  }

  let missing = 0;
  for (const [host, site] of usedHosts) {
    if (!registeredHosts.has(host)) {
      missing++;
      bug('HIGH','consistency','Deep Link Registration', site.file, site.line,
        `Deep link "prohost://${host}" is used in Cloud Functions/emails but host "${host}" is NOT registered in AndroidManifest.xml. Clicking this link will NOT open the app.`,
        `Add an intent-filter block with android:scheme="prohost" and android:host="${host}" to AndroidManifest.xml.`);
    }
  }
  if (missing === 0) pass('Deep Link Registration', `All ${usedHosts.size} prohost:// deep link hosts are registered in AndroidManifest.xml.`);
}

// ─── CHECK 11: AuthStep enum completeness ─────────────────────────────────────

function checkAuthStep() {
  const loginFile = path.join(KT_SCREENS, 'LoginAuthScreen.kt');
  const content   = readSafe(loginFile);
  if (!content) {
    bug('HIGH','consistency','AuthStep Completeness','LoginAuthScreen.kt', null,
      'LoginAuthScreen.kt not found.', 'Verify file location.');
    return;
  }

  const enumMatch = content.match(/enum class AuthStep\s*\{([^}]+)\}/);
  if (!enumMatch) {
    bug('MEDIUM','consistency','AuthStep Completeness','LoginAuthScreen.kt', null,
      'Could not find AuthStep enum definition.', 'Ensure the enum is declared in the file.');
    return;
  }

  const values = enumMatch[1].split(',').map(v => v.trim()).filter(Boolean);
  const missing = values.filter(v => !content.includes(`AuthStep.${v}`));

  if (missing.length > 0) {
    bug('HIGH','consistency','AuthStep Completeness','LoginAuthScreen.kt', null,
      `AuthStep values [${missing.join(', ')}] are defined but never referenced in the UI — these steps will never render.`,
      `Add a when(step == AuthStep.VALUE) branch for each missing value in LoginAuthScreen.kt.`);
  } else {
    pass('AuthStep Completeness', `All ${values.length} AuthStep values (${values.join(', ')}) are referenced in the UI.`);
  }
}

// ─── CHECK 12: TypeScript safety ─────────────────────────────────────────────

function checkTypeScriptSafety() {
  const tsFiles = walkFiles(FN_SRC, '.ts');
  let count = 0;

  for (const f of tsFiles) {
    // as any
    for (const m of grepFile(f, /\bas\s+any\b/g)) {
      count++;
      bug('LOW','reliability','TypeScript Safety', m.file, m.line,
        `"as any" bypasses TypeScript type-checking — runtime type mismatches become silent bugs.`,
        `Use a proper type assertion or unknown + type guard instead of "as any".`);
    }

    // snap.data()! without preceding exists check in the same function
    for (const m of grepFile(f, /\.data\(\)!/g)) {
      count++;
      bug('MEDIUM','reliability','Firestore Snapshot Safety', m.file, m.line,
        `snap.data()! assumes the document exists. If snap.exists is false, data() returns undefined and ! throws.`,
        `Always guard: if (!snap.exists) throw new Error("Document not found"); before calling snap.data()!.`);
    }

    // Missing await on async functions (basic heuristic: calling without await or .then)
    for (const m of grepFile(f, /(?<!await\s)(?<!return\s)(?<!\.then\()(?<!\bPromise\.all\b)\b(sendEmail|sendPushToUser|sendPushToAdmins)\s*\(/g)) {
      // Skip function declarations, comment lines, and calls wrapped in Promise.all/map
      if (/^\s*(export\s+)?async\s+function/.test(m.lineText)) continue;
      if (/^\s*\*/.test(m.lineText) || /^\s*\/\//.test(m.lineText)) continue;
      if (/Promise\.all\s*\(/.test(m.lineText) || /\.map\s*\(/.test(m.lineText)) continue;
      count++;
      bug('HIGH','reliability','Missing Await', m.file, m.line,
        `Call to async function "${m.groups[1]}" may be missing await. Email/push may not be sent if the function returns before the promise resolves.`,
        `Prepend await to the call, or explicitly handle the returned Promise with .catch().`);
    }
  }

  if (count === 0) pass('TypeScript Safety', 'No unsafe "as any" casts or unguarded snap.data()! patterns found.');
}

// ─── CHECK 13: Orphaned or stub modules ───────────────────────────────────────

function checkOrphanedModules() {
  const indexContent = readSafe(path.join(FN_SRC, 'index.ts')) || '';

  const tsFiles = walkFiles(FN_SRC, '.ts').filter(f =>
    !f.endsWith('index.ts') &&
    !f.includes(`${path.sep}lib${path.sep}`) &&
    !f.endsWith('.d.ts')
  );
  for (const f of tsFiles) {
    const rel = path.relative(FN_SRC, f).replace(/\\/g, '/').replace(/\.ts$/, '');
    const importPath = `./${rel}`;
    if (!indexContent.includes(importPath)) {
      const content = readSafe(f) || '';
      if (/export\s+(const|function|class|async)/.test(content)) {
        bug('MEDIUM','consistency','Orphaned Module', relPath(f), null,
          `Module "${importPath}" has exports but is not imported from index.ts — its functions are never deployed.`,
          `Export the functions from this module in functions/src/index.ts, or delete the file if it is no longer needed.`);
      }
    }
  }
}

// ─── CHECK 14: Navigation graph completeness ──────────────────────────────────

function checkNavGraph() {
  const navFile   = path.join(KT_UI, 'navigation/ProHostNavGraph.kt');
  const navContent = readSafe(navFile);
  if (!navContent) {
    bug('HIGH','consistency','Navigation Graph','ProHostNavGraph.kt', null,
      'ProHostNavGraph.kt not found — cannot verify all screens are registered.','Verify file location.');
    return;
  }

  const screenFiles = walkFiles(KT_SCREENS, '.kt');
  const missing = screenFiles.filter(f => {
    const name = path.basename(f, '.kt');
    return !navContent.includes(name) && !navContent.includes(name.replace('Screen', ''));
  });

  if (missing.length > 0) {
    missing.forEach(f => bug('HIGH','consistency','Navigation Graph', relPath(f), null,
      `Screen "${path.basename(f, '.kt')}" is not referenced in ProHostNavGraph.kt — it may be unreachable from the app.`,
      `Add a navigation route for this screen in ProHostNavGraph.kt.`));
  } else {
    pass('Navigation Graph', `All ${screenFiles.length} screens are referenced in ProHostNavGraph.kt.`);
  }
}

// ─── CHECK 15: Role-permission guards on write operations ─────────────────────

function checkRoleGuards() {
  // Firestore rules should guard writes with role checks
  const rulesContent = readSafe(FIRESTORE_RULES) || '';

  // workspace_listings create — must be PRO_HOST or ADMIN
  if (!rulesContent.includes('PRO_HOST') && !rulesContent.includes('liveRole')) {
    bug('HIGH','security','Role Guard Missing','firestore.rules', null,
      'workspace_listings create rule does not enforce PRO_HOST role — any authenticated user could create listings.',
      'Ensure the create rule includes: liveRole(request.auth.uid) == "PRO_HOST" || isAdmin().');
  } else {
    pass('Role Guard', 'workspace_listings create rule enforces PRO_HOST or ADMIN role.');
  }

  // Suspended users should not be able to write
  if (rulesContent.includes('isSuspended()')) {
    pass('Suspension Guard', 'Firestore rules enforce isSuspended() check on write operations.');
  } else {
    bug('HIGH','security','Suspension Guard Missing','firestore.rules', null,
      'Firestore rules do not call isSuspended() — suspended users can still write data.',
      'Add !isSuspended() to all create/update rules where users submit data.');
  }
}

// ─── CHECK 16: Payment flow plumbing ─────────────────────────────────────────

function checkPaymentFlow() {
  // Whish payment has been completely replaced by Google Play Billing.
  // Check that PlayBillingManager.kt exists and is wired into the app.
  const billingManagerFile = path.join(KT_DATA, 'billing/PlayBillingManager.kt');
  const clientPath = path.join(KT_DATA, 'auth/FirebaseFunctionsClient.kt');
  const clientContent = readSafe(clientPath) || '';

  const hasBillingManager = !!readSafe(billingManagerFile);
  const indexContent = readSafe(path.join(FN_SRC, 'index.ts')) || '';
  const hasRtdnExport = indexContent.includes('playBillingRtdn');

  if (!hasBillingManager) {
    bug('CRITICAL','consistency','Google Play Billing Manager Missing', 'PlayBillingManager.kt', null,
      'PlayBillingManager.kt not found — the Pro Host upgrade / subscription purchase flow has no billing implementation.',
      'Implement PlayBillingManager.kt integrating the Google Play Billing Library.');
  } else if (!hasRtdnExport) {
    bug('HIGH','consistency','Play Billing RTDN Not Exported', 'functions/src/index.ts', null,
      'playBillingRtdn Cloud Function is not exported from index.ts — subscription status updates from Google Play will not be processed.',
      'Export playBillingRtdn from functions/src/index.ts.');
  } else {
    pass('Payment Flow', 'Google Play Billing: PlayBillingManager.kt present and playBillingRtdn is exported.');
  }
}

// ─── CHECK 17: Billing purchase flow ─────────────────────────────────────────

function checkBillingFlow() {
  const billingFile = path.join(KT_DATA, 'billing/PlayBillingManager.kt');
  const content = readSafe(billingFile);
  if (!content) {
    bug('HIGH','reliability','Billing Manager Missing', 'PlayBillingManager.kt', null,
      'PlayBillingManager.kt not found — Google Play Billing is not implemented.','Verify file location.');
    return;
  }
  // Check for acknowledge purchase (required by Google Play — without it purchases are refunded after 3 days)
  if (!content.includes('acknowledgePurchase') && !content.includes('acknowledgement')) {
    bug('CRITICAL','reliability','Purchase Acknowledgement Missing', relPath(billingFile), null,
      'PlayBillingManager does not call acknowledgePurchase(). Google Play automatically refunds unacknowledged purchases after 3 days. Pro Host subscriptions will be cancelled unexpectedly.',
      'Call billingClient.acknowledgePurchase() for every non-SUBSCRIBED purchaseState purchase, before updating the backend.');
  } else {
    pass('Purchase Acknowledgement', 'PlayBillingManager.kt calls acknowledgePurchase().');
  }
}

// ─── CHECK 18: Firebase Auth token refresh ────────────────────────────────────

function checkAuthTokenRefresh() {
  // After signInWithCustomToken, the custom claim won't be on the first token.
  // App should forceRefresh the token after assignInitialRole or login.
  const authFlow = path.join(KT_DATA, 'auth/AuthFlow.kt');
  const content = readSafe(authFlow) || '';
  if (!content.includes('forceRefresh') && !content.includes('getIdToken')) {
    bug('HIGH','reliability','ID Token Refresh Missing', relPath(authFlow), null,
      'AuthFlow.kt does not call forceRefresh on the Firebase ID token after login. Custom claims (role, etc.) set by Cloud Functions will not be visible to the app until the token naturally expires (~1 hour). Role-gated UI may show the wrong state.',
      'After signInWithCustomToken and assignInitialRole complete, call: FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await() to force-refresh the token and pick up new custom claims.');
  } else {
    pass('ID Token Refresh', 'AuthFlow.kt force-refreshes the ID token after login.');
  }
}

// ─── CHECK 19: KYC screen state ───────────────────────────────────────────────

function checkKycScreen() {
  const kycFile = path.join(KT_SCREENS, 'KycScreen.kt');
  const content = readSafe(kycFile) || '';
  const issues  = [];
  if (!content.includes('isLoading') && !content.includes('CircularProgress')) issues.push('no loading state');
  if (!content.includes('errorMessage') && !content.includes('isError'))       issues.push('no error state');
  if (issues.length > 0) {
    bug('MEDIUM','ux','KYC Screen Completeness', relPath(kycFile), null,
      `KYC screen is missing: ${issues.join(', ')}. Users have no feedback during the KYC submission process.`,
      `Add isLoading + errorMessage state to KycViewModel and show them in KycScreen.`);
  } else {
    pass('KYC Screen Completeness', 'KycScreen has loading and error states.');
  }
}

// ─── CHECK 20: OwnerAnalytics financial stats completeness ────────────────────

function checkOwnerAnalytics() {
  const analyticsFile = path.join(KT_SCREENS, 'OwnerAnalyticsScreen.kt');
  const content = readSafe(analyticsFile) || '';
  const financialTerms = ['revenue','earning','income','financial','monthly','lastMonth','MoM','cumulative'];
  const hasFinancial = financialTerms.some(t => content.toLowerCase().includes(t.toLowerCase()));
  if (!hasFinancial) {
    bug('MEDIUM','ux','Analytics Financial Stats', relPath(analyticsFile), null,
      `OwnerAnalyticsScreen.kt has no financial metrics (revenue, earnings, monthly comparisons). The user-requested "financial states of divisions/spaces performance" and "current monthly vs last month" stats are not yet implemented.`,
      `Add financial stat cards: current-month revenue, last-month revenue, MoM change, per-space/division breakdown. Source data from booking_requests.totalAmountUsd grouped by spaceId and month.`);
  } else {
    pass('Analytics Financial Stats', 'OwnerAnalyticsScreen includes financial metrics.');
  }
}

// ─── RUN ALL CHECKS ───────────────────────────────────────────────────────────

process.stdout.write('\n');
console.log('╔══════════════════════════════════════════════════════╗');
console.log('║         NIGHTHAWK — ProHost Static Debugger          ║');
console.log('╚══════════════════════════════════════════════════════╝\n');

const checks = [
  ['Cloud Function Names',          checkCloudFunctionNames],
  ['Firestore Rules Coverage',      checkFirestoreRules],
  ['Empty Catch Blocks',            checkEmptyCatch],
  ['Forced Unwraps (!!)',           checkForcedUnwrap],
  ['Screen Loading States',         checkScreenLoading],
  ['Screen Error States',           checkScreenError],
  ['Screen Empty States',           checkScreenEmptyState],
  ['Coroutine Error Handling',      checkCoroutineErrors],
  ['Hardcoded Secrets',             checkHardcodedSecrets],
  ['Deep Link Registration',        checkDeepLinks],
  ['AuthStep Completeness',         checkAuthStep],
  ['TypeScript Safety',             checkTypeScriptSafety],
  ['Orphaned Modules',              checkOrphanedModules],
  ['Navigation Graph',              checkNavGraph],
  ['Role/Permission Guards',        checkRoleGuards],
  ['Payment Flow Plumbing',         checkPaymentFlow],
  ['Billing Acknowledgement',       checkBillingFlow],
  ['Auth Token Refresh',            checkAuthTokenRefresh],
  ['KYC Screen Completeness',       checkKycScreen],
  ['Analytics Financial Stats',     checkOwnerAnalytics],
];

for (const [label, fn] of checks) {
  process.stdout.write(`  Checking: ${label} ...`);
  try { fn(); }
  catch (e) { bug('HIGH','internal','Check Error', null, null, `Check "${label}" threw: ${e.message}`, 'Fix the debugger script itself.'); }
  process.stdout.write(' done\n');
}

// Sort by severity
findings.sort((a, b) => (SEV_ORDER[a.severity] ?? 99) - (SEV_ORDER[b.severity] ?? 99));

const summary = {
  total:    findings.length,
  critical: findings.filter(f => f.severity === 'CRITICAL').length,
  high:     findings.filter(f => f.severity === 'HIGH').length,
  medium:   findings.filter(f => f.severity === 'MEDIUM').length,
  low:      findings.filter(f => f.severity === 'LOW').length,
  info:     findings.filter(f => f.severity === 'INFO').length,
  passes:   passes.length,
};

// ─── JSON Report ──────────────────────────────────────────────────────────────

const jsonReport = { generatedAt: new Date().toISOString(), trigger: 'NIGHTHAWK', summary, findings, passes };
fs.writeFileSync(OUT_JSON, JSON.stringify(jsonReport, null, 2), 'utf8');

// ─── HTML Report ──────────────────────────────────────────────────────────────

const SEV_COLOR = {
  CRITICAL: '#D32F2F',
  HIGH:     '#E64A19',
  MEDIUM:   '#F57C00',
  LOW:      '#1976D2',
  INFO:     '#616161',
};
const SEV_BG = {
  CRITICAL: '#FFEBEE',
  HIGH:     '#FBE9E7',
  MEDIUM:   '#FFF3E0',
  LOW:      '#E3F2FD',
  INFO:     '#F5F5F5',
};

function findingRow(f) {
  const c = SEV_COLOR[f.severity] || '#333';
  const bg = SEV_BG[f.severity] || '#fafafa';
  return `
  <tr style="background:${bg}">
    <td><span class="badge" style="background:${c}">${esc(f.severity)}</span></td>
    <td><code class="tag">${esc(f.category)}</code></td>
    <td><strong>${esc(f.check)}</strong></td>
    <td>${f.file ? `<code>${esc(f.file)}${f.line ? ':' + f.line : ''}</code>` : '—'}</td>
    <td>${esc(f.message)}</td>
    <td class="suggestion">${f.suggestion ? esc(f.suggestion) : '—'}</td>
  </tr>`;
}

function passRow(p) {
  return `
  <tr>
    <td><span class="badge" style="background:#388E3C">PASS</span></td>
    <td colspan="4">${esc(p.message)}</td>
    <td>—</td>
  </tr>`;
}

const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>NIGHTHAWK — ProHost Debug Report</title>
<style>
  :root {
    --brand:#FF6B35; --brand2:#FF8C42;
    --bg:#F4F4F6; --card:#fff;
    --text:#1A1A1A; --sub:#666;
    --border:#E0E0E0;
  }
  @media (prefers-color-scheme: dark) {
    :root:not([data-theme="light"]) {
      --bg:#111; --card:#1E1E1E; --text:#F0F0F0; --sub:#aaa; --border:#333;
      color-scheme: dark;
    }
  }
  :root[data-theme="dark"] { --bg:#111; --card:#1E1E1E; --text:#F0F0F0; --sub:#aaa; --border:#333; color-scheme: dark; }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { background: var(--bg); color: var(--text); font: 14px/1.6 system-ui, -apple-system, sans-serif; padding: 0 16px 40px; }
  header { background: linear-gradient(135deg,var(--brand),var(--brand2)); color:#fff; padding: 28px 32px; margin: 0 -16px 32px; }
  header h1 { font-size: 24px; font-weight: 700; letter-spacing: -.3px; }
  header p  { font-size: 13px; opacity: .85; margin-top: 4px; }
  .meta { font-size: 12px; opacity: .7; margin-top: 8px; font-family: monospace; }
  .stats { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 32px; }
  .stat { background: var(--card); border: 1px solid var(--border); border-radius: 10px; padding: 16px 20px; flex: 1 1 110px; text-align: center; }
  .stat .num { font-size: 28px; font-weight: 700; }
  .stat .lbl { font-size: 11px; color: var(--sub); text-transform: uppercase; letter-spacing: .6px; margin-top: 2px; }
  .stat.crit .num { color: #D32F2F; }
  .stat.high .num { color: #E64A19; }
  .stat.med  .num { color: #F57C00; }
  .stat.low  .num { color: #1976D2; }
  .stat.pass .num { color: #388E3C; }
  h2 { font-size: 17px; font-weight: 600; margin: 32px 0 12px; border-bottom: 1px solid var(--border); padding-bottom: 8px; }
  table { width: 100%; border-collapse: collapse; background: var(--card); border-radius: 10px; overflow: hidden; border: 1px solid var(--border); font-size: 13px; }
  th { background: var(--brand); color: #fff; padding: 10px 12px; text-align: left; font-weight: 600; font-size: 12px; text-transform: uppercase; letter-spacing: .4px; }
  td { padding: 10px 12px; border-bottom: 1px solid var(--border); vertical-align: top; }
  tr:last-child td { border-bottom: none; }
  .badge { display: inline-block; padding: 2px 8px; border-radius: 4px; color: #fff; font-size: 11px; font-weight: 700; letter-spacing: .4px; white-space: nowrap; }
  .tag { background: rgba(0,0,0,.07); border-radius: 4px; padding: 2px 6px; font-size: 11px; }
  code { font-family: 'SF Mono', monospace; font-size: 12px; background: rgba(0,0,0,.06); border-radius: 3px; padding: 1px 4px; }
  .suggestion { color: var(--sub); font-size: 12px; }
  .passes table { margin-top: 0; }
  @media (max-width: 700px) {
    th:nth-child(4), td:nth-child(4),
    th:nth-child(6), td:nth-child(6) { display: none; }
  }
</style>
</head>
<body>
<header>
  <h1>🦅 NIGHTHAWK — ProHost Static Debug Report</h1>
  <p>${summary.total} issues · ${summary.passes} checks passed</p>
  <p class="meta">Generated: ${new Date().toISOString()}</p>
</header>

<div class="stats">
  <div class="stat crit"><div class="num">${summary.critical}</div><div class="lbl">Critical</div></div>
  <div class="stat high"><div class="num">${summary.high}</div><div class="lbl">High</div></div>
  <div class="stat med"><div class="num">${summary.medium}</div><div class="lbl">Medium</div></div>
  <div class="stat low"><div class="num">${summary.low}</div><div class="lbl">Low</div></div>
  <div class="stat"><div class="num">${summary.info}</div><div class="lbl">Info</div></div>
  <div class="stat pass"><div class="num">${summary.passes}</div><div class="lbl">Passing</div></div>
</div>

<h2>Findings</h2>
<table>
  <thead>
    <tr>
      <th>Severity</th><th>Category</th><th>Check</th><th>Location</th><th>Issue</th><th>Suggestion</th>
    </tr>
  </thead>
  <tbody>
    ${findings.map(findingRow).join('\n')}
    ${findings.length === 0 ? '<tr><td colspan="6" style="text-align:center;padding:24px;color:var(--sub)">No issues found 🎉</td></tr>' : ''}
  </tbody>
</table>

<h2>Passing Checks</h2>
<div class="passes">
<table>
  <thead><tr><th>Status</th><th colspan="4">Check</th><th></th></tr></thead>
  <tbody>
    ${passes.map(passRow).join('\n')}
  </tbody>
</table>
</div>

</body>
</html>`;

fs.writeFileSync(OUT_HTML, html, 'utf8');

// ─── Console summary ──────────────────────────────────────────────────────────

console.log('\n' + '─'.repeat(56));
console.log(`  CRITICAL : ${summary.critical}`);
console.log(`  HIGH     : ${summary.high}`);
console.log(`  MEDIUM   : ${summary.medium}`);
console.log(`  LOW      : ${summary.low}`);
console.log(`  INFO     : ${summary.info}`);
console.log(`  PASSED   : ${summary.passes}`);
console.log('─'.repeat(56));
console.log(`\n  HTML report → ${OUT_HTML}`);
console.log(`  JSON report → ${OUT_JSON}\n`);

if (summary.critical > 0) {
  console.log(`⚠️  ${summary.critical} CRITICAL issue(s) require immediate attention before release.\n`);
}
