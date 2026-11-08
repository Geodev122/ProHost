---
name: integration-audit
description: Audit database ↔ backend (Cloud Functions, rules) ↔ frontend (Android/iOS/shared) ↔ admin web consistency for ProHost. Use at the end of every migration phase, before releases, and when a callable, collection, rule or mirrored Kotlin/TypeScript logic changes.
---

# Integration audit (run at every phase end; write `docs/migration/audits/<phase>.md`)

1. **Callables**: list names in `functions/src/index.ts` exports vs every call in `FirebaseFunctionsClient.kt` (including names routed through `callAdmin(...)` and
   `runAdminMaintenance(name)` — NIGHTHAWK misses these) vs the iOS `FunctionsClient.swift` and `adminWeb`. 34 are called by the Android app today. Region `europe-west1`.
   Every callable uses `onCall` from `lib/callable.ts`. Unauthenticated email callables use `takeEmailSendSlot` and throw `HttpsError`.
2. **Firestore**: every collection/path the client touches (`FirestoreService.kt`, `FirestoreSchema.kt`, two direct writes in `BillingController.kt` and `ProHostViewModel.kt`)
   vs `firestore.rules` vs `tests/rules/firestore.rules.test.js` (36 tests, no positive admin cases yet). Server-only collections stay denied. `isAdmin()` stays claim-only.
3. **Storage**: paths in `FirebaseStorageService.kt` vs `storage.rules` (profile_pictures, listing_verification_docs, booking_agreements, listings, legal_documents).
4. **Data model**: `docs/DATA_MODEL.md` vs `toFirestoreMap`/`fromFirestoreMap` in `DataModels.kt`; server-owned fields never echoed on listing saves (`keepingServerOwnedFields`).
5. **Mirrored logic** (must be identical; shared vectors in `tests/vectors`): attendeePricing, priceChange, bookingTerms, listingPrices, legacyPricing, listingLocation/effectiveCountry,
   playCatalog (ids, status mapping), roles, consent version (`LegalContent.kt` EFFECTIVE_DATE = `CURRENT_CONSENT_VERSION`), password policy (Kotlin, Swift, Firebase config), audit action allow-lists.
   `node scripts/migration/export_vectors.js --check` and the Kotlin `VectorsTest`. Known divergences are listed in DECISIONS.md; do not "fix" them without the owner.
6. **Flows**: `tests/e2e/flows.test.js` and `auth.test.js` (CI job "End-to-end Backend Flows"; locally the emulators are proxied by HTTPS_PROXY — run in CI).
7. **Admin web**: every ADMIN_PARITY.md row's callable/collection exists, is admin-gated, and is exercised.
8. **Agent**: adapter contract tests (agent mocks vs ProHost implementation); `readOnlyAccess.test.ts`; Maintenance `igsmoke`/`officeleadsmoke`.
9. **Live checks** (read-only): Maintenance `prodcheck` (deployed vs exported functions, indexes, billing, public pages), Firebase MCP tools if connected (functions list/logs, rules,
   Crashlytics), `docs/LOG_QUERIES.md`. Never write production data in an audit.
Report: table of finding · severity · evidence (file:line) · owner decision needed. Findings that change behaviour go to DECISIONS.md, not into the migration commit.
