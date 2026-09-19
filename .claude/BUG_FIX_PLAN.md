# ProHost — Full Tester Simulation: Bug Fix Implementation Plan

Generated: 2026-09-19  
Last updated: 2026-09-20 (Play Billing UI + Admin Hardening pass)  
Branch: `claude/prohost-ui-refinement-sync-c7c985`  
Source: Simulated Google Play tester — 3-agent deep code review (auth/nav, screens/ViewModel, backend/rules)

---

## Summary

| Severity  | Count | Status    |
|-----------|-------|-----------|
| CRITICAL  | 3     | C1 FIXED  |
| HIGH      | 9     | Not fixed |
| MEDIUM    | 8     | Not fixed |
| LOW       | 6     | Not fixed |
| EXTRA     | 2     | X2 FIXED  |
| NEW       | 3     | Not fixed |
| **Total** | **31**| **2 fixed** |

### Fixed in 2026-09-20 session
- **BUG-C1** — Duplicate `val context` compile error in `OwnerHubScreen.kt:76` — FIXED (line removed)
- **BUG-X2** — `SubscriptionRenewalDialog` (Whish-oriented) was opening for expired Play subscribers — FIXED (expired Renew now navigates to `OwnerSubscriptionsScreen`; `SubscriptionRenewalDialog` removed from `OwnerHubScreen` entirely)
- **BUG-X1 (partial)** — `activity` null silent no-op in `OwnerSubscriptionsScreen` — FIXED for `OwnerHubScreen` Manage path via `onOpenSubscriptions` fallback; still needs `context.findActivity()` fix in `OwnerSubscriptionsScreen.kt:225,258`

---

## PHASE 1 — Critical: Fix Before Any Build Ships

### BUG-C1 · Duplicate `val context` — compile error
**File:** `app/src/main/java/com/example/ui/screens/OwnerHubScreen.kt:76`  
**Problem:** This session added `val context = androidx.compose.ui.platform.LocalContext.current` at line 76 for the Play Store intent. Line 43 already declares `val context = LocalContext.current` in the same function scope. Kotlin will not compile — the app is unshippable in its current state.  
**Fix:**
- [ ] Remove line 76: `val context = androidx.compose.ui.platform.LocalContext.current`
- [ ] Change the Play Store intent at lines 99-108 to use the existing `context` variable at line 43 (already available in scope — no change needed to the intent code itself)
- [ ] Verify the file compiles by running `./gradlew :app:compileDebugKotlin`

---

### BUG-C2 · `AppUser.toFirestoreMap()` writes protected Firestore fields — silent profile update failures
**File:** `app/src/main/java/com/example/data/model/DataModels.kt:1465–1484`  
**Problem:** `toFirestoreMap()` includes `role`, `isVerified`, `ownerPackageId`, `ownerPackageExpiryMillis`, `createdAtMillis`, `lastSignInAtMillis`, `isSuspended`, `tosAcceptedAtMillis`, `consentVersion`, `pinHash`, `pinSalt`, `pinSetAtMillis` — all fields blocked by `firestore.rules` protected-keys guard. After any Cloud Function updates one of these fields (e.g. `playBillingRtdn` sets `ownerPackageId`, `grantAdminRole` sets `role`), the next client profile save sends the stale old value. Firestore's `diff()` detects the protected field "changed" and the **entire write is rejected silently**. The user sees no error; their profile edit is lost. Affects city, avatar, name, phone updates.  
**Fix:**
- [ ] Create a separate `toEditableFieldsMap()` method on `AppUser` that only includes the fields a user is allowed to edit client-side: `fullName`, `phone` (if editable), `avatarUrl`, `email`, `city`, `specialty`, `bio`, `idDocumentUrl`, `proofOfOwnershipUrl`, `savedSpaceIds`, `fcmToken`, `lastSignInAtMillis`
- [ ] Replace all call sites of `toFirestoreMap()` used for user profile updates with `toEditableFieldsMap()`
- [ ] Keep `toFirestoreMap()` only for admin/server contexts where the full map is intentionally written (admin-created profiles, etc.) — or remove it entirely and make all writes field-specific
- [ ] Search for all usages: `grep -r "toFirestoreMap" app/src/` and audit each one
- [ ] Verify: after a Cloud Function updates `ownerPackageId`, a subsequent profile edit must succeed

---

### BUG-C3 · `deletePackagePlan()` has no subscriber guard — active paying subscribers locked out instantly
**File:** `app/src/main/java/com/example/ui/viewmodel/AdminViewModel.kt:164–173` and `app/src/main/java/com/example/ui/screens/AdminConsoleScreen.kt:560`  
**Problem:** No confirmation dialog. No subscriber check. `deletePackagePlan()` removes the plan from `package_plans/main` immediately. `withinListingLimit()` in `firestore.rules` resolves the plan as `null` for any user whose `ownerPackageId` still points to the deleted plan, even if their `ownerPackageExpiryMillis` is in the future. Affected users can no longer create or publish listings despite having a valid paid subscription.  
**Fix:**
- [ ] Before deleting, query `user_profiles` for users with `ownerPackageId == planId AND ownerPackageExpiryMillis > now` (count them)
- [ ] If count > 0: show a confirmation dialog: "X users have an active subscription to this plan. Deleting it will immediately lock them out of listing creation. Are you sure?"
- [ ] Add "AdminDeletePackagePlanDialog" composable with subscriber count, confirm button (DANGER variant), cancel button
- [ ] Alternative safer approach: add a `isArchived: Boolean` flag to `PackagePlan`. "Delete" sets `isArchived = true` and `isEnabled = false` — the plan remains in Firestore so existing subscribers keep working, but no new purchases can be made. Add a separate "Force Delete" path that requires zero active subscribers.
- [ ] Update `AdminViewModel.deletePackagePlan()` to check `_uiState.value.allUsers.count { it.ownerPackageId == planId && it.ownerPackageExpiryMillis > now }` before proceeding
- [ ] Update `AdminConsoleScreen` delete button to open the confirmation dialog

---

## PHASE 2 — High: Serious Malfunctions

### BUG-H1 · No PIN re-authentication on app resume (session lock missing)
**File:** `app/src/main/java/com/example/MainActivity.kt:64`  
**Problem:** `onResume()` only calls `inAppUpdateManager?.onResume()`. The PIN feature only gates at login — it does not lock the session when the app goes to background. Anyone who finds an already-unlocked device with the app backgrounded bypasses PIN entirely.  
**Fix:**
- [ ] Add a `_appWentToBackground` flag in `ProHostViewModel` or a dedicated `SessionLockViewModel`
- [ ] In `MainActivity.onPause()` set the flag to `true` and record the timestamp
- [ ] In `MainActivity.onResume()` (or in `ProHostAppRoot`): if flag is `true` and a PIN is set (`user.pinSetAtMillis != null`) and time-since-background > configurable threshold (default: 60 seconds), set `_isSessionLocked = true`
- [ ] Add a `PinLockScreen` composable displayed over the entire app when `isSessionLocked = true` — identical to the PIN-entry screen in `LoginAuthScreen` but without the OTP path
- [ ] `PinLockScreen` calls `viewModel.verifyPinAndUnlock(pin)` → calls `verifyPinAndIssueToken` Cloud Function → on success sets `_isSessionLocked = false`
- [ ] Handle the "Forgot PIN while locked" path: show the forgot-PIN OTP flow without signing out first (keep the session, just re-auth)

---

### BUG-H2 · No brute-force protection on PIN verification
**File:** `functions/src/auth/pinAuth.ts:47`  
**Problem:** `verifyPinAndIssueToken` has no per-user failed-attempt counter, no lockout after N failures. An attacker knowing the victim's phone number can enumerate all 1,000,000 PINs. Cloud Functions rate-limits are per-IP, not per-target-user.  
**Fix:**
- [ ] Add a `pinFailedAttempts` and `pinLockedUntilMillis` field to `user_profiles` (written by Admin SDK in the Cloud Function)
- [ ] In `verifyPinAndIssueToken`: read `pinFailedAttempts` and `pinLockedUntilMillis` first
- [ ] If `pinLockedUntilMillis > now`: throw `HttpsError("resource-exhausted", "Too many failed attempts. Try again in X minutes.")`
- [ ] On failed verify: increment `pinFailedAttempts` with `FieldValue.increment(1)`. After 5 failures: set `pinLockedUntilMillis = now + 15 * 60 * 1000` (15-min lockout). After 10 total: set `pinLockedUntilMillis = now + 24 * 60 * 60 * 1000` (24-hour lockout)
- [ ] On successful verify: reset `pinFailedAttempts = 0` and `pinLockedUntilMillis = null`
- [ ] Android client: show the lockout error message with remaining time, disable the PIN input
- [ ] Also add `maxInstances: 20` and consider a Cloud Armor rate-limit rule at the project level

---

### BUG-H3 · `completeVerifiedLogin` throws → user stuck on OTP screen with no error
**File:** `app/src/main/java/com/example/ui/viewmodel/AuthViewModel.kt:273–296`  
**Problem:** `pendingVerificationId` is cleared and `_isAuthenticating = false` is set before the `try` block. If `completeVerifiedLogin` throws (network error, timeout), the exception is swallowed by `viewModelScope.launch` (no `CoroutineExceptionHandler`). The user's OTP screen shows no spinner, no error, and "Verify Code" says "Please request a code first." They are stuck.  
**Fix:**
- [ ] Wrap the entire `finishPhoneVerification` body in a `try/catch(e: Exception)` 
- [ ] On `AccountSuspendedException`: existing handling (sign out, show suspended message)
- [ ] On `kotlinx.coroutines.TimeoutCancellationException`: `_authError.value = "Connection timed out. Please check your network and try again."`
- [ ] On generic `Exception`: `_authError.value = "Sign-in failed: ${e.message ?: "Unknown error"}. Please try again."`
- [ ] Do NOT clear `pendingVerificationId` before the try-block — clear it only on success so the user can retry with the same OTP if the network is briefly unavailable
- [ ] Restore `_isAuthenticating = false` in a `finally` block

---

### BUG-H4 · FCM booking deep-link declared but never consumed — notification taps do nothing
**File:** `app/src/main/java/com/example/ui/navigation/ProHostNavGraph.kt:111`  
**Problem:** `deepLinkBookingId` is passed from `MainActivity` through `ProHostAppRoot` into `ProHostNavGraph` but has zero code acting on it. Tapping a booking push notification navigates to the correct tab via `target_tab` but never opens the specific booking. The feature is wired at the OS level but is a no-op at the UI level.  
**Fix:**
- [ ] In `ProHostNavGraph`, add a `LaunchedEffect(deepLinkBookingId)` block that fires when the ID is non-null
- [ ] Inside the effect: find the booking in `viewModel.allBookingRequests.value` by ID
- [ ] Navigate to the appropriate screen: if the current user is PRO_HOST → navigate to `OwnerIncomingRequestsView` with that booking pre-selected; if SPECIALIST → navigate to their booking detail
- [ ] After navigation, clear the deep-link ID (call `viewModel.clearDeepLinkBookingId()` or similar) to prevent re-navigation on recomposition
- [ ] Test: send a test FCM message with `booking_id` extra, verify the booking detail screen opens

---

### BUG-H5 · `billingActivationPending` stuck forever if RTDN is dropped
**File:** `app/src/main/java/com/example/ui/screens/OwnerSubscriptionsScreen.kt:80` / `ProHostViewModel.kt`  
**Problem:** `_billingActivationPending` is set `true` when the Play billing sheet returns OK. It is only cleared by a Firestore `currentUser` update with a valid `ownerPackageExpiryMillis`. If the RTDN Pub/Sub notification is dropped or the Cloud Function fails, this never happens. The "Activating…" spinner banner is permanent with no dismiss.  
**Fix:**
- [ ] Add a timeout: in the `init {}` observer block (ProHostViewModel), also launch a timer coroutine that sets `_billingActivationPending = false` after 5 minutes if it hasn't been cleared by a Firestore update
- [ ] Add a dismiss/close button (×) to the activation-pending banner in `OwnerSubscriptionsScreen`
- [ ] On dismiss: call `viewModel.clearBillingActivationPending()` which sets the flag to `false`
- [ ] Add a "Contact support" link to the banner that appears after 2 minutes of pending state
- [ ] Implementation: use `viewModelScope.launch { delay(5 * 60 * 1000L); _billingActivationPending.value = false }` stored in a cancellable `Job`; cancel it in the `currentUser` observer when expiry is confirmed

---

### BUG-H6 · Missing `obfuscatedExternalAccountId` permanently ACKs Pub/Sub — RTDN dropped forever
**File:** `functions/src/billing/playBillingRtdn.ts:250–254`  
**Problem:** When `obfuscatedExternalAccountId` is missing (purchase predates the code using `setObfuscatedAccountId`), the handler logs an error and `return`s — resolving the Cloud Function successfully, causing Pub/Sub to ACK and never retry. For `SUBSCRIPTION_PURCHASED`: paying user never gets Pro Host access. For `SUBSCRIPTION_REVOKED`: cancelled subscription stays live.  
**Fix:**
- [ ] Instead of `return`, throw an `Error` so the function exits with a non-200 status code, causing Pub/Sub to redeliver after backoff
- [ ] BUT: for `SUBSCRIPTION_CANCELED` and `SUBSCRIPTION_EXPIRED` (access-ending events), retrying with no UID is also pointless. Add a secondary lookup: attempt to find the user by querying `user_profiles` where some stored token field matches `purchaseToken` (if you add token storage)
- [ ] Pragmatic fix: for `SUBSCRIPTION_REVOKED`/`SUBSCRIPTION_EXPIRED` with no UID, write a `play_billing_unresolved` Firestore collection entry with the full purchase details so an admin can manually resolve it: `{ purchaseToken, productId, orderId, notificationType, timestamp }`
- [ ] For `SUBSCRIPTION_PURCHASED` with no UID: throw (redeliver) — eventually the client will reconnect and the UID will be set
- [ ] Add an admin Cloud Function to query unresolved entries and resolve them manually

---

### BUG-H7 · `SUBSCRIPTION_DEFERRED` bypasses `grantSubscription()` — lapsed listings never restored
**File:** `functions/src/billing/playBillingRtdn.ts:294–300`  
**Problem:** The `SUBSCRIPTION_DEFERRED` case writes `ownerPackageExpiryMillis` directly via `db.set()`, skipping the lapsed-listings restore logic in `grantSubscription()`. A user whose package expired, had listings hidden (`isOwnerPackageLapsed: true`), and then received a promotional Play deferral gets their expiry extended but listings stay hidden.  
**Fix:**
- [ ] Change `SUBSCRIPTION_DEFERRED` to call `grantSubscription(uid, productId, expiryMs, orderId)` instead of the raw `db.set()` — `grantSubscription` already handles the "keep whichever expiry is later" logic, so this is safe
- [ ] The only difference was avoiding the role-promotion logic on deferral. If that's desired, add an `isRenewal: boolean` parameter to `grantSubscription()` that skips the role re-check when `true` (deferral and renewal are not new purchases)

---

### BUG-H8 · Owner can book their own listing — no guard anywhere
**File:** `app/src/main/java/com/example/data/repository/ProHostRepository.kt:1137` / `ProHostViewModel.kt:734`  
**Problem:** `createBookingRequest` never asserts `practitioner.id != space.ownerId`. No Firestore rule blocks it. A PRO_HOST can submit a booking to themselves, creating an absurd self-referential booking in their own inbox.  
**Fix:**
- [ ] In `submitBookingRequest` (ProHostViewModel): add guard `if (currentUser.value?.id == targetSpace.ownerId) { /* show error "You cannot book your own listing" */; return }`
- [ ] In `createBookingRequest` (ProHostRepository): assert `require(practitionerId != space.ownerId) { "Owner cannot book own listing" }`
- [ ] In `firestore.rules`: add to the `workspace_booking_requests` create rule: `request.resource.data.practitionerId != get(/databases/$(database)/documents/workspace_listings/$(request.resource.data.spaceId)).data.ownerId`
- [ ] In `SpaceDetailsScreen` / `RentalBookingDialog`: hide or disable the "Book" button entirely if `currentUser.id == space.ownerId`

---

### BUG-H9 · No file-size check before `readBytes()` — OOM on large images
**File:** `app/src/main/java/com/example/ui/components/CreateListingDialog.kt:200`  
**Problem:** Picks full file bytes with `openInputStream(uri)?.use { it.readBytes() }` — no size limit. A HEIC/RAW from a modern camera can be 30–100 MB. On low-RAM devices this causes an `OutOfMemoryError`.  
**Fix:**
- [ ] Before reading bytes, check file size via `context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }` 
- [ ] If size > 15 MB (15 * 1024 * 1024): show toast "This image is too large (max 15 MB). Please choose a smaller photo." and skip it
- [ ] After reading bytes (if within limit): apply JPEG compression using `BitmapFactory.decodeByteArray` → `Bitmap.compress(JPEG, 85, outputStream)` — reduce upload size and memory pressure
- [ ] Consider using a library like `Coil` or `uCrop` for image picking/compression instead of raw `readBytes()`
- [ ] Also add a total-upload-size guard: if total bytes across all selected images > 50 MB, show a warning

---

## PHASE 3 — Medium: Feature Broken or Misleading

### BUG-M1 · `checkPhoneRegistered` is an unauthenticated phone-enumeration endpoint
**File:** `functions/src/auth/pinAuth.ts:23`  
**Problem:** Returns `{isRegistered: true}` for any phone number with no auth token. Attackers can enumerate whether arbitrary phone numbers are ProHost users for spam/phishing.  
**Fix:**
- [ ] Remove the boolean response entirely — return `{ok: true}` whether or not the number is registered. The app's behavior stays the same: it proceeds to OTP regardless
- [ ] OR: add App Check enforcement to this callable. Non-Play-signed apps cannot call it without a valid Play Integrity token
- [ ] The client-side purpose of this call (deciding which screen to show: "Welcome back" vs "Register") can instead be determined after the OTP is verified, in `verifyPinAndIssueToken`'s response or a new field on the custom token claim

---

### BUG-M2 · 15-second session-restore timeout silently bounces user to login screen
**File:** `app/src/main/java/com/example/ui/viewmodel/ProHostViewModel.kt:93`  
**Problem:** On a slow (not fully offline) connection, the `TimeoutCancellationException` is caught generically, `isRestoringSession` goes false, and the user lands on `LoginAuthScreen` with no explanation. Their Firebase Auth session is still valid.  
**Fix:**
- [ ] In the `catch (e: Exception)` block in the init coroutine, distinguish `TimeoutCancellationException` from other exceptions
- [ ] On timeout: set `_sessionRestoreError.value = "Slow connection detected. Sign in again or wait and retry."` (new StateFlow)
- [ ] Show this message on `LoginAuthScreen` as a banner so the user knows why they landed here
- [ ] Consider increasing `SESSION_RESTORE_TIMEOUT_MS` to 30 seconds on first install, 15 thereafter
- [ ] Alternatively: don't time out at all on first cold start; only time out on background-foreground resume

---

### BUG-M3 · `SubscriptionRenewalDialog` (purchase flow) opens on Play Store intent failure for active subscribers
**File:** `app/src/main/java/com/example/ui/screens/OwnerHubScreen.kt:99–108`  
**Problem:** If `market://subscriptions` intent throws (no Play Store, emulator, etc.), the catch block shows the "Renew/buy" dialog — a purchase flow — to a user who wanted to *manage* their active subscription.  
**Fix:**
- [ ] On intent failure: show a `Toast` / `Snackbar`: "Could not open Google Play. Please manage your subscription in the Play Store app."
- [ ] Do NOT fall back to `showRenewalDialog = true`
- [ ] Also try the https fallback before showing the toast: `https://play.google.com/store/account/subscriptions?sku={productId}&package=app.geonajjar.prohost`
- [ ] Use `ContextWrapper` chain unwrapping (same pattern as `SubscriptionRenewalDialog.kt:44–51`) instead of `context as? Activity` for reliable Activity resolution

---

### BUG-M4 · Stale "Whish subscription status" subtitle in Owner Analytics
**File:** `app/src/main/java/com/example/ui/screens/OwnerAnalyticsScreen.kt:183`  
**Problem:** Section subtitle still reads "Whish subscription status" after migration to Google Play Billing.  
**Fix:**
- [ ] Change `subtitle = "Whish subscription status"` to `"Subscription & listing health"` (or similar)
- [ ] Grep for all other Whish references in user-visible text: `grep -r "Whish" app/src/main/java/com/example/ui/ --include="*.kt"` and update any remaining user-facing strings (excluding the admin transaction history, which is legitimately Whish-labeled)

---

### BUG-M5 · `activePlaySubscriberCount` counts all active package holders, not just Google Play
**File:** `app/src/main/java/com/example/ui/viewmodel/AdminViewModel.kt:75–83` / `AdminConsoleScreen.kt`  
**Problem:** There is no payment-method field on `AppUser`. The tile labeled "Active Play Billing Subscriptions" shows all active subscribers regardless of method.  
**Fix (short term):**
- [ ] Rename the tile label to "Active Subscribers" to be accurate with current data
- [ ] Update `AdminUiState.activePlaySubscriberCount` field name to `activeSubscriberCount`

**Fix (long term):**
- [ ] Add `ownerPackagePaymentMethod: String?` field to `AppUser` — written by `playBillingRtdn.ts` as `"GOOGLE_PLAY"` and by `entitlements.ts` as `"WHISH"`
- [ ] Use this field to filter for Play-only subscribers in AdminViewModel
- [ ] Show both counts side-by-side in the analytics tiles

---

### BUG-M6 · Unauthenticated read of `workspace_listings` exposes host phone/email
**File:** `firestore.rules:117–119`  
**Problem:** The first OR-branch of the `workspace_listings` read rule has no `isSignedIn()`. Unauthenticated REST requests can list all active listings including `ownerPhone`, `ownerEmail`, `ownerName`.  
**Fix:**
- [ ] Add `isSignedIn() &&` to the beginning of the first OR-branch of the `workspace_listings` allow read rule
- [ ] If anonymous discovery (no account) is intentionally supported, create a server-side read path (Cloud Function) that strips PII before returning listing data, and remove the direct Firestore public read
- [ ] After applying the rule change, test that `DiscoveryScreen` still loads listings for authenticated users

---

### BUG-M7 · Duplicate booking requests — no prevention or idempotency
**File:** `app/src/main/java/com/example/data/repository/ProHostRepository.kt:1137`  
**Problem:** Double-tap on "Send Rental Request" or re-opening the booking dialog creates two separate PENDING requests for the same slot. Both land in the owner's inbox.  
**Fix:**
- [ ] In `ProHostRepository.createBookingRequest()`: before writing, query for existing PENDING requests from the same `practitionerId` for the same `spaceId`/`subdivisionId` combination
- [ ] If one exists: return early or throw a specific error "You already have a pending request for this space"
- [ ] In `ProHostViewModel.submitBookingRequest()`: set a `_isBookingInFlight: MutableStateFlow<Boolean>` to `true` before the call, reset it in `finally`. Disable the submit button while `isBookingInFlight`
- [ ] Add Firestore rule: `allow create: if !exists(/databases/$(database)/documents/workspace_booking_requests/$(pendingRequestId))` (or a compound index query check)

---

### BUG-M8 · `togglePackagePlan()` is silent on success — no admin confirmation toast
**File:** `app/src/main/java/com/example/ui/viewmodel/AdminViewModel.kt:155–162`  
**Problem:** Every other admin write emits a success toast. Toggle does not. Admin has no visual confirmation the network write landed.  
**Fix:**
- [ ] In `togglePackagePlan()` success branch: `_events.emit(AdminUiEvent.ShowToast("Package ${if (isEnabled) "enabled" else "disabled"} successfully"))`

---

## PHASE 4 — Low: Minor / Cosmetic

### BUG-L1 · Forgot-PIN step indicator shows wrong steps
**File:** `app/src/main/java/com/example/ui/screens/LoginAuthScreen.kt:729`  
**Problem:** Shows "Verify → Profile → Set PIN" but the forgot-PIN flow is "Phone → Verify → Set PIN" — no Profile step.  
**Fix:**
- [ ] Add a `forgotPinSteps` list alongside the main `steps` list: `listOf("Phone", "Verify", "Set PIN")`
- [ ] Pass `if (isForgotPinReset) forgotPinSteps else steps` to `AuthStepIndicator`

---

### BUG-L2 · `setUserPin` uses `.update()` — throws NOT_FOUND if profile document was deleted
**File:** `functions/src/auth/pinAuth.ts:107`  
**Problem:** `.update()` throws `NOT_FOUND` if the document doesn't exist. Edge case: registration partially failed, leaving no profile doc.  
**Fix:**
- [ ] Change `userRef.update({ pinHash, pinSalt, pinSetAtMillis })` to `userRef.set({ pinHash, pinSalt, pinSetAtMillis }, { merge: true })`

---

### BUG-L3 · `expirePackages` `.limit(500)` misses tail on mass expiry
**File:** `functions/src/packages/expirePackages.ts:64`  
**Problem:** If more than 500 packages expire in one hourly window, the tail stays as active PRO_HOSTs for up to 1 hour.  
**Fix:**
- [ ] Use a `while (true)` loop with pagination: keep querying `.limit(500)` until the result is empty
- [ ] Or increase limit to 1000 (Firestore max for a single query)
- [ ] Add a metric log at the end: `logger.info("expirePackages: swept N packages")` to monitor scale

---

### BUG-L4 · `subscriptionExpiryMillis` defaults to fabricated +30 days for old documents
**File:** `app/src/main/java/com/example/data/model/DataModels.kt:1155`  
**Problem:** Old `SpaceListing` documents missing `subscriptionExpiryMillis` are materialized with `System.currentTimeMillis() + 30d` — a false "active" reading.  
**Fix:**
- [ ] Change the default to `null`: `subscriptionExpiryMillis = (data["subscriptionExpiryMillis"] as? Number)?.toLong()`
- [ ] Update all consuming code to handle `null` gracefully (treat as expired / use `isActiveSubscription` field instead)
- [ ] The `isActiveSubscription` field (line 1152, default `true`) has the same issue — change to default `false` and rely on server-written truth

---

### BUG-L5 · `hasLoadedBookingsOnce` / `hasLoadedSpacesOnce` never reset on sign-out
**File:** `app/src/main/java/com/example/data/repository/ProHostRepository.kt:120–129`  
**Problem:** On sign-out + sign-in as a different user, loading spinners never show for the second account because flags stayed `true` from the first session.  
**Fix:**
- [ ] In `ProHostRepository.logout()` or wherever sign-out clears state, also reset both flags: `_hasLoadedBookingsOnce.value = false; _hasLoadedSpacesOnce.value = false`

---

### BUG-L6 · "Renew in N days" shows "0 days" for expiry < 24 hours away
**File:** `app/src/main/java/com/example/ui/screens/OwnerRentingProgressScreen.kt:124` and `OwnerHubScreen.kt:422`  
**Problem:** Integer truncation. An expiry 23 hours away shows "0 days".  
**Fix:**
- [ ] Use ceiling division: `((it - now + 24L * 3600 * 1000 - 1) / (24L * 3600 * 1000)).toInt()`
- [ ] Or: if result is 0 and expiry > now, show "Less than 1 day" instead of "0 days"
- [ ] Apply consistent fix to both files

---

## Additional Issues (Identified but Not in Original Severity Ranking)

### BUG-X1 · `activity` null in `OwnerSubscriptionsScreen` → silent no-op, no error
**File:** `app/src/main/java/com/example/ui/screens/OwnerSubscriptionsScreen.kt:225, 258`  
**Problem:** `activity?.let { ... }` safe-calls on `context as? Activity`. In Compose Previews or wrapped contexts, `activity` is null and billing silently does nothing — the button appears to work but nothing happens.  
**Fix:**
- [ ] Use the `ContextWrapper` chain unwrap pattern from `SubscriptionRenewalDialog.kt:44–51`:
  ```kotlin
  fun Context.findActivity(): Activity? {
      var ctx = this
      while (ctx is ContextWrapper) {
          if (ctx is Activity) return ctx
          ctx = ctx.baseContext
      }
      return null
  }
  ```
- [ ] Replace `val activity = context as? Activity` with `val activity = context.findActivity()`
- [ ] If `activity == null`: show an error banner "Cannot launch Google Play on this device"
- [ ] Add this extension function to a `ContextExtensions.kt` utility file

---

### BUG-X2 · `SubscriptionRenewalDialog` still exists and is Whish-oriented — confuses Play subscribers
**File:** `app/src/main/java/com/example/ui/components/dialogs/SubscriptionRenewalDialog.kt`  
**Problem:** The dialog was built for the Whish era. It is still triggered for users with no active subscription (from `OwnerHubScreen`). If the admin has disabled Whish plans and only offers Play plans, this dialog shows a list of Play-priced packages but calls Whish checkout. Needs an audit.  
**Fix:**
- [ ] Read `SubscriptionRenewalDialog.kt` fully and verify whether it still calls `payOwnerPackageViaWhish` or has been migrated
- [ ] If it still calls Whish checkout: either migrate it to call `launchGooglePaySubscription` or redirect to `OwnerSubscriptionsScreen` instead
- [ ] For expired Play subscribers (most common case going forward), `OwnerHubScreen`'s "Renew" button should navigate directly to `OwnerSubscriptionsScreen`, not open any dialog

---

## PHASE 5 — New Bugs Found in 2026-09-20 Review

### BUG-N1 · `validityDays` is misleading for monthly Play subscriptions (Medium)
**Files:** `app/src/main/java/com/example/data/model/DataModels.kt:1550`, `functions/src/lib/entitlements.ts:173`  
**Problem:** `PackagePlan.validityDays` is only meaningful for Whish PAYG plans where the server grants a fixed time window. For Google Play subscriptions the actual renewal period is defined in Play Console (monthly), and the server sets `ownerPackageExpiryMillis` from `purchase.expiryTimeMillis` returned by the Play Developer API — it never reads `validityDays` from `entitlements.ts` for Play purchases. Yet `validityDays` is stored in Firestore alongside the plan and shows in the admin edit form, creating a false impression that editing it changes Play billing terms. An admin who edits `validityDays` from 30 to 60 on a Play plan will see nothing change for subscribers.  
**Fix:**
- [ ] In `AdminConsoleScreen.kt` package edit form: when `isPlayLinked`, change the `validityDays` field label to "Billing cycle reference only — Play manages the actual period" and make it read-only (disabled)
- [ ] In `PackagePlan.toFirestoreMap()` / `PackagePlan.fromFirestoreMap()` in `DataModels.kt`: document that for Play plans `validityDays` is informational only
- [ ] In the UI label wherever `validityDays` appears as "X days": gate on whether the plan has a Play product — if yes, show "Monthly (via Google Play)" instead of "30 days"
- [ ] `entitlements.ts` already ignores `validityDays` for Play purchases (Play path uses `expiryTimeMillis` from API) — just add a comment confirming this so future devs don't add `validityDays` logic there

---

### BUG-N2 · `priceUsd` on Play plans is informational only — admin UI doesn't say so (Low)
**Files:** `app/src/main/java/com/example/ui/screens/AdminConsoleScreen.kt:588–593`  
**Problem:** The `Price ($)` input in the package edit form for Play-linked plans allows free editing. The value is stored in Firestore and shown in `OwnerSubscriptionsScreen`/`OwnerHubScreen` as the displayed price. However the **actual charge** is whatever price was set in Google Play Console — if an admin changes `priceUsd` here to a different value, the app displays a price that contradicts what Play actually charges. This is a trust/legal issue if a subscriber pays a different amount than displayed.  
**Fix:**
- [ ] When `isPlayLinked`, change the `Price ($)` label to "Display price (must match Google Play Console price)"
- [ ] Add a supporting text warning: "The actual charge is handled by Google Play. Keep this in sync with your Play Console price."
- [ ] Consider making it read-only and sourcing price from Play Billing's `ProductDetails` at purchase time rather than from Firestore

---

### BUG-N3 · `OwnerSubscriptionsScreen` `activity` null still causes silent no-op on subscribe buttons (Medium)
**Files:** `app/src/main/java/com/example/ui/screens/OwnerSubscriptionsScreen.kt:29–30, 225, 258`  
**Problem:** `val activity = context as? Activity` (line 30) can be null in Compose previews or when the Context is a `ContextThemeWrapper`. The upsell button and each plan card's Subscribe button do `activity?.let { viewModel.launchGooglePaySubscription(it, productId) }` — when `activity` is null this is a completely silent no-op. The user taps "Subscribe via Google Play", nothing happens.  
**Fix:**
- [ ] Add a `Context.findActivity()` extension (see BUG-X1 fix recommendation):
  ```kotlin
  private fun Context.findActivity(): Activity? {
      var ctx = this
      while (ctx is ContextWrapper) { if (ctx is Activity) return ctx; ctx = ctx.baseContext }
      return null
  }
  ```
- [ ] Replace `val activity = context as? Activity` with `val activity = context.findActivity()`
- [ ] When `activity == null` at call site: emit a billing error: `"Cannot launch Google Play on this device"`
- [ ] This is the same as BUG-X1 but specifically scoped to `OwnerSubscriptionsScreen`

---

## Fix Order Recommendation

```
Phase 1 (done):    C1 ✓, X2 ✓ (partial X1)
Phase 1 (next):    C2 → C3
Phase 2 (sprint):  N3 → H1 → H3 → H4 → H5 → H7 → H8 → H9 → H2 → H6
Phase 3 (backlog): M6 → M7 → M1 → M3 → M4 → M5 → M2 → M8 → N1 → N2
Phase 4 (polish):  L2 → L1 → L6 → L3 → L4 → L5
```

M6 (Firestore unauthenticated read) is placed first in Phase 3 because it is a security/privacy issue that is easy to fix (one line in firestore.rules).

---

## Files Affected (Quick Reference)

| File | Bugs |
|------|------|
| `OwnerHubScreen.kt` | ~~C1~~, M3, H1(partial), L6 |
| `DataModels.kt` | C2, L4, N1(partial) |
| `AdminViewModel.kt` | C3, M5, M8 |
| `AdminConsoleScreen.kt` | C3, M5, N1, N2 |
| `MainActivity.kt` | H1 |
| `pinAuth.ts` | H2, M1, L2 |
| `AuthViewModel.kt` | H3 |
| `ProHostNavGraph.kt` | H4 |
| `OwnerSubscriptionsScreen.kt` | H5, N3 |
| `playBillingRtdn.ts` | H6, H7 |
| `ProHostRepository.kt` | H8, M7, L5 |
| `CreateListingDialog.kt` | H9 |
| `firestore.rules` | H8(partial), M6 |
| `ProHostViewModel.kt` | H5, M2, H3(partial) |
| `OwnerAnalyticsScreen.kt` | M4 |
| `expirePackages.ts` | L3 |
| `LoginAuthScreen.kt` | L1, M2(partial) |
| `OwnerRentingProgressScreen.kt` | L6 |
| `SubscriptionRenewalDialog.kt` | ~~X2~~ (removed from OwnerHubScreen) |
| `entitlements.ts` | N1(partial) |
