---
name: kmp-migration
description: Rules and procedure for moving ProHost to Kotlin Multiplatform + Compose Multiplatform (Android + iOS) with zero UI/UX change. Use for any work under docs/migration/, the shared/ module, iOS host, seams (expect/actual or interfaces), or when asked which phase/unit a task belongs to.
---

# KMP + Compose Multiplatform migration

Authority: `CLAUDE.md` section "KMP + Compose Multiplatform migration" → `docs/migration/MASTER_PLAN.md` (phases P0-P10) → `DECISIONS.md` → `PARITY.md`.
Owner rules: (1) no change to UI/UX/layout/behaviour — list and ask, never slip in; (2) learn → plan → execute → compare → confirm → next;
(3) phase ends with a database ↔ backend ↔ frontend ↔ admin web audit (`integration-audit` skill); (4) the AI agent leaves this repo but stays linked
(`agent-adapter`); (5) Admin Console is a web app, never in the mobile builds (`admin-web-parity`).

## Start of every task
1. `git fetch origin`; read the CLAUDE.md section, `MASTER_PLAN.md` §5 for the phase, the unit's row in `PARITY.md`, `DECISIONS.md` open items, `docs/OWNER_TODO.md`.
2. Say which phase and unit the task belongs to. If it needs a UI/behaviour change, stop and add it to `DECISIONS.md` for the owner.

## Unit procedure (one screen / sheet / component group / logic file at a time)
- **Learn**: read the Kotlin source; list states, string keys, branches, callables, collections, Android APIs. Update the Class cell if the guess was wrong.
- **Plan**: five lines in the PR/commit text: what moves, which seam, which goldens/tests prove it.
- **Execute** (Android first, smallest diff). Order inside a unit: remove `Context`/`Uri`/`java.*`/`R.*` → cut the seam → move the file → fix imports.
- **Compare**: `./gradlew`-free in the sandbox (no Android SDK) → run in CI: Migration Checks (vectors, parity table), Design Screenshots with `store_as=<unit>`
  then `python3 scripts/migration/diff_shots.py --golden <golden-v1047 dir> --candidate <new dir> --only <prefixes>` (see `parity-audit`).
- **Confirm**: tick Goldens/Tests/Android diff = 0 in PARITY.md; anything different goes to DECISIONS.md "Deviations". **Proceed** only then.

## Code rules for anything headed to `shared/`
- No `android.*`, `androidx.activity`, `Context`, `Uri`, `R.*`, `java.util.Calendar/Locale/UUID/Date`, `java.text.*`, `String.format`, `System.currentTimeMillis`,
  `Log`, `Toast`. Use: kotlinx-datetime, `kotlin.uuid`, `kotlin.io.encoding.Base64`, `Res.*` (composeResources), the seams below.
- Seams = interfaces in commonMain with an Android implementation that is today's code, and an iOS implementation (Swift where a native SDK is needed):
  StringProvider, Messenger (`UiMessages`), PlatformActions, Clipboard, MediaPicker/FileExport (`PickedFile`, not `Uri`), ImageCodec, Permissions, Location+Geocoder,
  MapView, Google/Apple sign-in, Auth/Functions/Firestore/Storage gateways (keep `fromFirestoreMap`/`toFirestoreMap` payloads byte-identical), SubscriptionStore
  (Play | StoreKit 2), Push, DeepLinkRouter, Haptics, SoundPlayer, KeyValueStore, Logger, LegalPdf/HtmlView.
- Pricing/date/rounding logic that exists in TypeScript too: change both and `node scripts/migration/export_vectors.js`; Kotlin runs `app/src/test/.../vectors/VectorsTest.kt`.
- Keep every CLAUDE.md product rule (billing, auth, verification, IDs, per-attendee pricing, saves…): moving code does not change them.

## Build facts (verified 8 Nov 2026)
AGP 9.4.1, Kotlin 2.2.10, Gradle 9.7.1 (wrapper and CI), Compose BOM 2025.02.00 (will change with CMP), Billing 9.1.0, `android.newDsl=false` +
`android.builtInKotlin=false` stay until the P3 split (then: `com.android.kotlin.multiplatform.library` for `shared`, `com.android.application` for `androidApp`,
no `org.jetbrains.kotlin.android`). Crashlytics + google-services + R8 + signing stay in `androidApp` (removing Crashlytics crashed v1.0.33). The sandbox has no
Android SDK or Mac: Gradle and Xcode run in GitHub Actions. Tag pushes are refused by the git proxy: baselines are identified by SHA + `refs/design-shots/<name>`.
Never dispatch `build-and-deploy.yml` from a branch: its deploy job runs on dispatch. Use `migration-checks.yml` / `design-screenshots.yml`.
