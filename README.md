# ProHost (ProSpace) - Android Application

ProHost is a modern Android application built with Jetpack Compose and Material 3, designed for workspace listings, rental bookings, host identity verification, and administrative management.

---

## 🛠️ Tech Stack & Architecture

- **UI Framework**: Jetpack Compose (Material3)
- **Language**: Kotlin 2.1.10 with Kotlin Serialization
- **Architecture**: MVVM with Kotlin Coroutines & Flow
- **Backend / Cloud Services**:
  - Firebase Authentication, Firestore, Storage, Messaging, Cloud Functions
- **Maps & Location**: OpenStreetMap (`osmdroid`) & Play Services Location
- **Build System**: Gradle with Version Catalog (`gradle/libs.versions.toml`)

---

## 📋 Prerequisites

1. **JDK**: Java 11 or higher (JDK 17 recommended)
2. **Android SDK**: `compileSdk = 35`, `minSdk = 24`, `targetSdk = 35`
3. **Android Studio**: Android Studio Ladybug (2024.2+) or newer
4. **Firebase Configuration**: Ensure `app/google-services.json` is present in the `app/` directory.

---

## 🚀 Building the Project

### Debug APK
To build the debug APK:
```bash
./gradlew :app:assembleDebug
```
The output APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

### Debug App Bundle (AAB)
To generate a debug Android App Bundle:
```bash
./gradlew :app:bundleDebug
```
The output `.aab` will be located at `app/build/outputs/bundle/debug/app-debug.aab`.

---

## 📦 Release Signing & Bundle (AAB) Instructions

When preparing production builds for Google Play Store or distribution, follow these guidelines:

### 1. Keystore Configuration & Defaults
The project is configured in `app/build.gradle.kts` to look for a release keystore at `${rootDir}/my-upload-key.jks` by default.

- **Default Keystore File**: `my-upload-key.jks` (placed in project root directory)
- **Alias**: `upload`
- **Security**: All `*.jks` keystore files are strictly ignored by `.gitignore` to prevent committing sensitive keys to version control.

### 2. Generating Release Android App Bundle (.aab)
Google Play Store requires `.aab` (Android App Bundle) format instead of `.apk` for new apps:
```bash
./gradlew :app:bundleRelease
```
The resulting file will be generated at:
`app/build/outputs/bundle/release/app-release.aab`

### 3. Generating Release APK
To build a signed release APK directly:
```bash
./gradlew :app:assembleRelease
```
The output file will be at:
`app/build/outputs/apk/release/app-release.apk`

### 4. Overriding Keystore Credentials via Environment Variables
You can override the keystore path and passwords dynamically during CI/CD or custom builds by setting environment variables:

Linux / macOS:
```bash
export KEYSTORE_PATH="/custom/path/to/upload-key.jks"
export STORE_PASSWORD="your_store_password"
export KEY_PASSWORD="your_key_password"
./gradlew :app:bundleRelease
```

Windows (PowerShell):
```powershell
$env:KEYSTORE_PATH="C:\custom\path\to\upload-key.jks"
$env:STORE_PASSWORD="your_store_password"
$env:KEY_PASSWORD="your_key_password"
.\gradlew :app:bundleRelease
```

---

## 💡 Best Practices Checklist Before Release

See [PLAYSTORE_READINESS.md](PLAYSTORE_READINESS.md) for the complete, production-grade checklist of security policies, crash-prevention rules, Play Integrity configuration, and Store Listing requirements.

1. **Versioning**: Update `versionCode` (increment sequentially) and `versionName` in `app/build.gradle.kts` -> `defaultConfig`.
2. **Compilation**: Run `./gradlew :app:compileDebugKotlin` and `./gradlew test` to ensure zero compilation or unit test failures.
3. **Firebase & Google Cloud**: Verify `app/google-services.json` is present and SHA-1 / SHA-256 fingerprints are added in Firebase Console for Google Sign-In & App Check.
4. **Code Shrinking & Obfuscation (R8/Proguard)**: Ensure `isMinifyEnabled = true` is verified in `buildTypes { release { ... } }` when deploying to production.
