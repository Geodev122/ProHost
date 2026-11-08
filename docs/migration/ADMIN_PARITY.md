# Admin Console parity checklist (web rebuild)

Source of truth: the Android admin (Kotlin under `app/src/main/java/com/example/`: `ui/screens/admin/*`, `ui/screens/AdminConsoleScreen.kt`, `AdminAnalyticsScreen.kt`,
`ui/viewmodel/AdminViewModel.kt`, `ui/state/AdminUiState.kt`, `ui/components/AdminGrantAccessCard.kt`, `ui/components/dialogs/SystemDebuggerDialog.kt`, `util/AppSystemDebugger.kt`,
`data/auth/AdminDirectoryModels.kt`, `data/demo/DemoDataGenerator.kt`, plus the admin branches of the drawer, profile and navigation) and the admin callables in `functions/src`.
Inventory taken 8 Nov 2026 from the source at `d30c331`. The Android admin is removed **only after every row is ticked and the owner has walked through the web console**
(see `.claude/skills/admin-web-parity/SKILL.md`). Legend: C = callable, FS = direct Firestore, ST = Storage, L = live listener, 1S = one-shot read.
Columns: **Web** = built in adminWeb · **Proof** = emulator test + screenshot vs Android golden · **Owner** = walked through.

## Cross-cutting
| # | Feature | Android source | Backend | Web | Proof | Owner |
|---|---|---|---|---|---|---|
| 1 | Admin-only gate: sign-in, `assignInitialRole`, token refresh, `claims.role == "ADMIN"` | `FirebaseFunctionsClient.kt` (`readRoleClaim`), `AuthFlow.kt` | Firebase Auth, C `assignInitialRole` | ☐ | ☐ | ☐ |
| 2 | Console header: admin email · "Central Node", online/offline pill, offline banner | `AdminConsoleScreen.kt` | repository offline state | ☐ | ☐ | ☐ |
| 3 | Seven scrollable tabs with count badges (Users, Listings & Bookings), 900 dp content width; filter FAB on the Users tab (All, Active 30d, Expired, Verified, Pending review) sharing state with the chips | `AdminConsoleScreen.kt` (FAB at 280-317) | C `adminCounts` | ☐ | ☐ | ☐ |
| 4 | Admin profile "Security ID": identity card with the gold ADMIN tag, governance tiles (spaces, regions), super-admin identity form (photo, name, clearance text, phone, country/city) | `SpecialistProfileScreen.kt` (admin variant) | FS own profile, ST `profile_pictures` | ☐ | ☐ | ☐ |
| 5 | Toast / event system and friendly error mapping (`toUserMessage`) | `AdminViewModel.kt` | n/a | ☐ | ☐ | ☐ |
| 6 | Per-tab analytics events `admin_<tab>`, `admin_action {action,result}` | `ScreenNames.kt`, `AnalyticsTracker` | GA4 | ☐ | ☐ | ☐ |
| 7 | Admin notification centre (`ADMIN_SUBSCRIPTION`, `ADMIN_LEAD`, `LISTING_VERIFICATION_REQUEST`) opens the console | `DrawerDialogsHandler.kt`, `ProHostMessagingService.kt` | FS `user_profiles/{uid}/notifications` (L), FCM (no web push today) | ☐ | ☐ | ☐ |

## Tab 0 — Packages
| # | Feature | Android source | Backend | Web | Proof | Owner |
|---|---|---|---|---|---|---|
| 8 | Billing health card (Play API state, last RTDN, server self-test time, catalog lines per base plan and offer, `catalogError`, setup instructions) | `AdminBillingRescue.kt` | C `billingHealthCheck` | ☐ | ☐ | ☐ |
| 9 | "Check again" and "Send server self-test" (publishes to the RTDN topic, waits 8 s, refreshes) | same | C `billingRtdnSelfTest`, Pub/Sub | ☐ | ☐ | ☐ |
| 10 | Payments needing attention (N): user, product, since, attempts, hours left before the 3-day refund (red < 24 h), order id, hint, last error | same | C `adminBillingPending` | ☐ | ☐ | ☐ |
| 11 | Retry now (only `pending` and not `needsAdmin`) | same | C `adminActivatePurchase{kind,id}` | ☐ | ☐ | ☐ |
| 12 | Activate for… / for another account (account picker with search) | same | C `adminSearch(users)`, C `adminActivatePurchase{targetUid}` | ☐ | ☐ | ☐ |
| 13 | Reassignment confirmation dialog (`needs_confirm`) and result handling (`granted`, `granted_not_acknowledged`, `retired_plan`, `play_error`) | same, `AdminViewModel.kt` | C `adminActivatePurchase{confirmReassign}` | ☐ | ☐ | ☐ |
| 14 | Force Upgrade → ProHost: lookup by email or U- code, verification panel (blocks/warnings), confirm, success banner, clear | `AdminGrantAccessCard.kt` | C `lookupUserForGrant`, C `forceProHostUpgrade` | ☐ | ☐ | ☐ |
| 15 | "Sync with Google Play now" (checked / changed / failed, config-error toast) | `AdminPackagesTab.kt` | C `runBillingSync` | ☐ | ☐ | ☐ |
| 16 | Admin Governance Control Tag field (≤ 200 chars) | same | C `updatePricing{governanceTag}` | ☐ | ☐ | ☐ |
| 17 | One-click export: All users (CSV), pages of 500, `prohost_users_yyyyMMdd.csv`, columns uid, displayCode, fullName, email, phone, role, country, city, packageId, packageExpiryMillis, billingStatus, entitlementSource, listingCodes, createdAtMillis | same, `AdminDirectoryModels.kt` | C `adminExportUsers` | ☐ | ☐ | ☐ |

## Tab 1 — Users
| # | Feature | Android source | Backend | Web | Proof | Owner |
|---|---|---|---|---|---|---|
| 18 | Metric tiles: Total Users, Pro Hosts, Specialists, New (30d) | `AdminUsersTab.kt` | C `adminCounts` | ☐ | ☐ | ☐ |
| 19 | Smart search (name, email, U- code or UID), debounce 350 ms, empty state "Find a user" | same | C `adminSearch(users)` | ☐ | ☐ | ☐ |
| 20 | Role filter chips (All, Specialist, Pro Host, Admin) | same | client-side | ☐ | ☐ | ☐ |
| 21 | User card: avatar, name, email, SUSPENDED chip, role badge, location, phone status, phone, member since, last sign-in | same | search result | ☐ | ☐ | ☐ |
| 22 | Dossier sheet (U- code + UID, email + verified, phone, providers, location, package section, listings with D- room codes, payments not active, Play subscription history with order ids, rental history, actions) | `AdminDossierSheet.kt` | C `adminUserDossier` | ☐ | ☐ | ☐ |
| 23 | Edit user dialog (fullName, email, phone, specialty, country, governorate, city; role and phone-verified read-only) | `AdminUsersTab.kt` | FS merge `user_profiles/{uid}`, C `recordClientAuditLog` (`USER_UPDATED`) | ☐ | ☐ | ☐ |
| 24 | Delete user record (profile only; credentials not revoked) | same | FS delete `user_profiles/{uid}`, audit `USER_DELETED` | ☐ | ☐ | ☐ |
| 25 | Grant Admin (confirm) | same | C `grantAdminRole` | ☐ | ☐ | ☐ |
| 26 | Suspend / Reactivate (confirm, text differs per direction) | same | C `setAccountSuspended` | ☐ | ☐ | ☐ |
| 27 | Revoke Pro Host (confirm; PRO_HOST only) | same | C `revokeProHostRole` | ☐ | ☐ | ☐ |
| 28 | Export all (CSV) | same | C `adminExportUsers` | ☐ | ☐ | ☐ |

## Tab 2 — Listings & Bookings
| # | Feature | Android source | Backend | Web | Proof | Owner |
|---|---|---|---|---|---|---|
| 29 | Metric tiles: Listings, Published, To review, Bookings | `AdminListingsTab.kt` | C `adminCounts` | ☐ | ☐ | ☐ |
| 30 | Search listings + bookings (title, L-/D-/B- code, id or email) | same | C `adminSearch(all)` | ☐ | ☐ | ☐ |
| 31 | Category chips (from schema, fallback legacy enum) and status chips (All, Active, Expired, Verified, Pending review) | same | schema (L), client-side | ☐ | ☐ | ☐ |
| 32 | Default list "Awaiting verification review (N)" (50 rows) | `FirestoreService.kt` | L `workspace_listings where isVerified==false and verificationRequestedAt>0` | ☐ | ☐ | ☐ |
| 33 | Booking result cards with renter and host profile buttons | `AdminListingsTab.kt` | search result, C `adminUserDossier` | ☐ | ☐ | ☐ |
| 34 | View verification document (short-lived signed URL) | same | C `adminVerificationDocUrl` | ☐ | ☐ | ☐ |
| 35 | Activate / Deactivate listing | same | C `setListingSubscriptionActive` | ☐ | ☐ | ☐ |
| 36 | Verify / Unverify (pushes the owner, clears the request) | same | C `setListingVerification` | ☐ | ☐ | ☐ |
| 37 | Edit listing: the full 3-step wizard (space, details, availability/divisions, per-attendee tiers, photos, map picker); Save as Draft hidden; status preserved | `CreateListingDialog.kt`, `SubdivisionForm.kt`, `ListingLocationMapPicker.kt` | FS `workspace_listings` (`keepingServerOwnedFields`), ST `listings/**`, audit `LISTING_UPDATED` — **needs shared owner screens (P6-W5)** | ☐ | ☐ | ☐ |
| 38 | Delete listing (cancels active bookings; Storage purge; cleanup trigger) | `AdminListingsTab.kt` | FS delete, ST purge, trigger `onWorkspaceListingDeletedCleanup`, audit `LISTING_DELETED` | ☐ | ☐ | ☐ |
| 39 | Host profile shortcut | same | C `adminUserDossier` | ☐ | ☐ | ☐ |

## Tab 3 — Analytics
| # | Feature | Android source | Backend | Web | Proof | Owner |
|---|---|---|---|---|---|---|
| 40 | Range presets (7d, 30d, 90d, 12m, All), from/to date pickers (end inclusive), country filter, refresh; default last 30 days | `AdminAnalyticsScreen.kt`, `DateRangePickerRow.kt` | C `getAdminAnalytics` | ☐ | ☐ | ☐ |
| 41 | Pro Host upgrades chart (auto bucket ≤ 45 d day, ≤ 200 d week, else month; UTC; zero-filled; peak and axis labels) | same | same | ☐ | ☐ | ☐ |
| 42 | "Fill from audit log" backfill of upgrade dates | same | C `backfillProHostUpgradeDates` | ☐ | ☐ | ☐ |
| 43 | Booking mix by renting strategy + per-attendee stats | same | C `getAdminAnalytics` | ☐ | ☐ | ☐ |
| 44 | Listing performance: by country (tap to filter), city (top 10 / all), space type (expand division types) | same | same | ☐ | ☐ | ☐ |

## Tab 4 — Schema
| # | Feature | Android source | Backend | Web | Proof | Owner |
|---|---|---|---|---|---|---|
| 45 | Overview stat tiles (Space Types, Division Types, Facilities, Amenities, Strategies, Packages) | `AdminSchemaTab.kt` | FS `schema_architecture/main` (L) | ☐ | ☐ | ☐ |
| 46 | CRUD + enable switch for Space Types, Division Types, Facilities, Amenities, Rental Strategies (CUSTOM chip, delete only when not system default) | same, `ProHostRepository.kt` | FS `schema_architecture/main`, audit | ☐ | ☐ | ☐ |
| 47 | Edit dialog: name, description; space type max subdivisions and map marker colour (12 presets + hex); amenity group and division-type scope | same | FS | ☐ | ☐ | ☐ |
| 48 | Add Node dialog (category chips, id `SCH-<cat>-<6hex>`) | same | FS | ☐ | ☐ | ☐ |
| 49 | Reset to defaults, Add missing built-in entries, Seed Defaults (11 amenities) — port `DefaultSchema.kt` | same, `DefaultSchema.kt` | FS (audit `SCHEMA_RESET_DEFAULTS`; `SCHEMA_DEFAULTS_MERGED` is rejected server-side today) | ☐ | ☐ | ☐ |
| 50 | Attendee tier templates CRUD + toggle (name, description, price/person, min, max, inclusions) | same | FS (no audit today) | ☐ | ☐ | ☐ |
| 51 | Target disciplines usage list (top 50 hashtags, refresh) | same | FS `hashtag_usage` 1S | ☐ | ☐ | ☐ |
| 52 | Firestore collections contract reference card | same | static | ☐ | ☐ | ☐ |

## Tab 5 — Security
| # | Feature | Android source | Backend | Web | Proof | Owner |
|---|---|---|---|---|---|---|
| 53 | Live audit log list (newest first, severity chip, timestamp, action, details, actor/IP/ID) | `AdminSecurityTab.kt` | L `audit_security_logs` (≤ 500) | ☐ | ☐ | ☐ |
| 54 | Audit CSV export with date range (header lines + quoted rows; exports only the loaded rows today) | same, `RepositoryCsv.kt` | client-side | ☐ | ☐ | ☐ |
| 55 | Legal documents: 4 slots (privacy, terms, revocation: html; re-rental template: pdf), versioned upload to Storage + Firestore, audit `LEGAL_DOCUMENT_PUBLISHED` | same | ST `legal_documents/{docId}/v{n}.{ext}`, FS `legal_documents` + `/versions`, C `recordClientAuditLog` | ☐ | ☐ | ☐ |
| 56 | System Audit Logs dialog from the drawer (date chips, x of y, export) | `DrawerDialogsHandler.kt` | same as 53 | ☐ | ☐ | ☐ |

## Tab 6 — Demo Control
| # | Feature | Android source | Backend | Web | Proof | Owner |
|---|---|---|---|---|---|---|
| 57 | Demo metrics (listings, requests, users) | `AdminDemoTab.kt` | C `adminCounts` | ☐ | ☐ | ☐ |
| 58 | Generate demo content (debug builds only today; needs a server callable on web or stays mobile-debug) | same, `DemoDataGenerator.kt` | FS writes (decision needed) | ☐ | ☐ | ☐ |
| 59 | Purge demo content (confirm with counts) | same | C `purgeDemoContent` | ☐ | ☐ | ☐ |
| 60 | Assign display codes to existing records | same | C `backfillDisplayCodes` | ☐ | ☐ | ☐ |
| 61 | Index names for admin search | same | C `backfillSearchNames` | ☐ | ☐ | ☐ |
| 62 | Mark email/Google sign-ups email-verified | same | C `backfillEmailVerified` | ☐ | ☐ | ☐ |
| 63 | Migrate legacy listing fields | same | C `migrateLegacyListingFields` | ☐ | ☐ | ☐ |

## Outside the seven tabs
| # | Feature | Android source | Backend | Web | Proof | Owner |
|---|---|---|---|---|---|---|
| 64 | System & Firebase debugger (debug builds only): health score, categories, Re-Run Audit | `SystemDebuggerDialog.kt`, `AppSystemDebugger.kt` | local checks; role claim vs session (web: Diagnostics page — decision 4) | ☐ | ☐ | ☐ |
| 65 | Admin-as-Pro-Host tools: all listings, all requests, renting progress, all bookings, change price, payment reminder, create listing | `OwnerHubScreen.kt`, `OwnerRentalRequestsScreen.kt`, `OwnerRentingProgressScreen.kt`, `MyBookingsScreen.kt`, `ChangePriceSheet.kt` | FS, C `changeSlotPrice`, C `sendPaymentReminder` — **needs shared owner screens (P6-W5)** | ☐ | ☐ | ☐ |
| 66 | GA4 API secret configuration (callable exists, no UI today) | `functions/src/admin/ga4Config.ts` | C `configureGa4ApiSecret` | ☐ | ☐ | ☐ |
| 67 | Mailing console: address book, messages, write email, sent emails + retry | `public/admin.html` | C `mailingAdmin*` (code-locked session; stays live as is) | ☐ | ☐ | ☐ |
| 68 | WhatsApp alerts configuration and test | same | C `mailingAdminWhatsApp` | ☐ | ☐ | ☐ |
| 69 | Maintenance tasks (CI only: audit, prodcheck, playcatalog, playofferfix, …) — stay CI tasks, listed here so none is lost | `scripts/maintenance/run.js` | Admin SDK, `maintenance_requests` trigger | ☐ | ☐ | ☐ |
| 70 | IG Control Room — moves with the agent project (P9) | `tools/control-room/` | Google Sheets, `igSheetSync` | ☐ | ☐ | ☐ |

## Backend facts the web app depends on
- Admin check everywhere is `request.auth.token.role === "ADMIN"` (functions) / `isAdmin()` claim-only (rules). `grantAdminRole` is the only way to create more admins; the first admin is created out of band.
- All admin callables are `onCall` from `lib/callable.ts`, region `europe-west1`, CORS open for callables; App Check unenforced.
- Direct writes the web app needs under today's rules: `schema_architecture`, `legal_documents` (+ `/versions`, immutable), `user_profiles` (admin update/delete), `workspace_listings`
  (admin create/update/delete excluding server-owned fields), Storage `legal_documents/**` (html/pdf < 10 MB), `listings/**`.
- Server-only collections the admin reaches through callables only: `subscriptions`, `play_billing_pending`, `play_billing_unresolved`, `play_purchase_links`, `display_codes`, `app_config/*`,
  `maintenance_requests`, `mailing_*`, `mail`.
- Known quirks to decide (DECISIONS.md): audit CSV exports only 500 loaded rows; `SCHEMA_DEFAULTS_MERGED`, `DEMO_CONTENT_SEEDED`, `DEMO_CONTENT_PURGED` are sent but rejected by the server allow-list;
  attendee-package changes are not audited; "Delete user" leaves the Auth account; dead code (`AdminExportDataDialog`, `AdminUiEvent.PricingUpdated`, `admins` count unused); no positive admin cases in `tests/rules`.
