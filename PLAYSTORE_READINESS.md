# Google Play Store Readiness & Codebase Audit Checklist
**Package Name**: `app.geonajjar.prohost`  
**Target SDK**: `35` | **Min SDK**: `24`  
**Firebase Project**: `prohost-f766f` (Project Number: `646730915838`)  

---

## 🔒 1. SECURITY & PRIVACY (Play Store Policy Mandatory Requirements)

### 🛑 MUST NOT HAVE:
- **No Hardcoded Secrets in Client Code**: Never embed private API keys (e.g., Whish Money secret key, GCP service account credentials) inside client Kotlin code. Secrets must remain exclusively in Firebase Cloud Functions environment variables / Secret Manager.
- **No Over-Permissioning**: Do not request unneeded Android permissions in `AndroidManifest.xml` (e.g., `READ_EXTERNAL_STORAGE` on Android 13+, `CAMERA`, `RECEIVE_SMS`, or `SYSTEM_ALERT_WINDOW` unless actively used).
- **No Unencrypted HTTP Connections**: No `http://` URLs in code or web views. All endpoints must be HTTPS (`https://`).
- **No PII Logging**: No printing sensitive user data (phone numbers, full names, authentication tokens) using `Log.d`, `Log.v`, or `println()` in release builds.

### ✅ MUST HAVE:
- **Account Deletion Flow (In-App + Web)**: Play Store policy **requires** both an in-app account deletion button (`Settings → Delete Account`, which calls `deleteOwnAccount`) AND a public web deletion request link (`https://pro-host.tech/delete-account`).
- **Working Privacy Policy URL**: A live, accessible Privacy Policy link (`https://pro-host.tech/privacy`) declared in both the Play Console and app drawer.
- **Target SDK 35 / Min SDK 24**: `targetSdk = 35` in `app/build.gradle.kts` to satisfy Google's latest target API requirement.
- **Accurate Play Console Data Safety Declaration**: Correctly declare collected data types:
  - *Location*: Approximate & Precise (for workspace search and mapping).
  - *Personal Info*: Name, Email, Phone Number, Speciality.
  - *Financial Info*: Whish payment transaction history.

---

## ⚡ 2. STABILITY, ANRs & CRASH PREVENTION

### 🛑 MUST NOT HAVE:
- **No Main Thread I/O**: Never perform network requests, Geocoder lookups, file reads, or heavy bitmap operations on `Dispatchers.Main`. Always use `withContext(Dispatchers.IO)`.
- **No Raw Technical Stack Traces to Users**: Do not show raw Firebase/SDK exception text (`FirebaseFunctionsException`, `NullPointerException`) to end-users in UI snackbars or dialogs.
- **No Memory Leaks**: Ensure all location listeners, lifecycle observers, and osmdroid `MapView` instances call `onResume()`, `onPause()`, and `onDetach()` on lifecycle disposal.

### ✅ MUST HAVE:
- **Graceful Network Offline Fallbacks**: If internet drops or Firebase is unreachable, the app must display friendly error states ("No Internet Connection") rather than crashing or freezing.
- **Process Death & State Restoration**: Jetpack Compose state (`rememberSaveable`) and ViewModels must survive process death and orientation changes cleanly.
- **Null-Safety Default Guards**: Every data model parsed from Firestore (`fromFirestoreMap`) must have fallback defaults for missing/null fields so old database schemas do not crash new app builds.

---

## 🔐 3. AUTHENTICATION & ROLE ENFORCEMENT

### 🛑 MUST NOT HAVE:
- **No Client-Trusted Role Elevation**: Never allow the Android client app to set its own `UserRole` in Firestore directly. Roles must be granted via Firebase Auth Custom Claims through Cloud Functions (`assignInitialRole`, `grantAdminRole`).

### ✅ MUST HAVE:
- **Phone Auth SMS Verification & Test Bypass**: Production relies on real SMS OTP verification, while dev/QA supports designated test numbers (`+961 70 888 999`) to allow Play Store review team testers to sign in without receiving a physical SMS.
- **FCM Token Registration**: Auto-sync device FCM token to user profile on every sign-in for reliable push notifications (`sendPaymentReminder`, booking alerts).

---

## 🛡️ 4. PLAY INTEGRITY, R8 SHRINKING & RELEASE SIGNING

### 🛑 MUST NOT HAVE:
- **No Broken Reflection via Over-Aggressive R8/Proguard**: R8 code shrinking must not strip model class getters/setters or Enum `.valueOf(String)` lookups used by Firestore serializers.

### ✅ MUST HAVE:
- **Google Play Integrity API**: Enabled for project `#646730915838` via `PlayIntegrityManager.kt` to detect pirated binaries or rooted/emulated fraud attempts.
- **Production R8 Rules**: `isMinifyEnabled = true` in `app/build.gradle.kts` with explicit keep rules in `app/proguard-rules.pro` for models, Room, Moshi, osmdroid, and Play Integrity.
- **Signed Release App Bundle (`app-release.aab`)**: Built with your upload key (`my-upload-key.jks`) and registered SHA-1 / SHA-256 fingerprints in Firebase & Play Console.

---

## 🎨 5. UI/UX & ASSET QUALITY

### 🛑 MUST NOT HAVE:
- **No Overlapping UI on Edge-to-Edge**: Ensure system bars (Status Bar, Navigation Bar) do not cover buttons, map controls, or search headers.
- **No Blank/Broken Map Surfaces**: Ensure OpenStreetMap tiles render properly with valid user-agent string (`app.geonajjar.prohost/1.0.0`).

### ✅ MUST HAVE:
- **Adaptive Launcher Icons**: High-resolution `ic_launcher.png` and `ic_launcher_round.png` across all density buckets (`mdpi` through `xxxhdpi`).
- **Store Assets Ready**:
  - `store-assets/ic_playstore_512x512.png` (512x512 Play Store App Icon on bright white background)
  - `store-assets/feature_graphic_1024x500.png` (1024x500 Feature Banner)
- **Responsive Layouts**: Screens format cleanly on small phones, large phones, and foldables/tablets.
