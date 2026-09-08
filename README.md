# ProHost (ProSpace) - Android Application

ProHost is a modern Android application built with Jetpack Compose and Material 3, designed for workspace listings, rental bookings, host identity verification, and administrative management.

---

## 🛠️ Tech Stack & Architecture

- **UI Framework**: Jetpack Compose (Material3)
- **Language**: Kotlin 2.1.10 with Kotlin Serialization
- **Architecture**: MVVM with Kotlin Coroutines & Flow
- **Backend / Cloud Services**:
  - Firebase Authentication, Firestore, Storage, Messaging, Cloud Functions
- **Maps & Location**: Google Maps SDK for Android (`maps-compose`) & Play Services Location
- **Build System**: Gradle with Version Catalog (`gradle/libs.versions.toml`) and Secrets Gradle Plugin

---

## 📋 Prerequisites

1. **JDK**: Java 11 or higher (JDK 17 recommended)
2. **Android SDK**: `compileSdk = 35`, `minSdk = 24`, `targetSdk = 35`
3. **Android Studio**: Android Studio Ladybug (2024.2+) or newer
4. **Firebase Configuration**: Ensure `app/google-services.json` is present in the `app/` directory.

---

## 🔑 Environment & Secrets Setup

The project uses the **Secrets Gradle Plugin** to manage sensitive keys via environment files.

1. Copy `.env.example` to `.env` in the root project directory:
   ```bash
   cp .env.example .env
   ```
2. Configure your keys inside `.env`:
   ```env
   MAPS_API_KEY=YOUR_ACTUAL_GOOGLE_MAPS_API_KEY
   GEMINI_API_KEY=YOUR_GEMINI_API_KEY  # Optional
   ```

> ⚠️ **Note**: Never commit `.env` or sensitive API keys to Git. The `.env` file is excluded in `.gitignore`.

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

## 📦 Recommendations for Future Release Bundle (AAB) & APK Builds

When preparing production builds for Google Play Store or distribution, follow these guidelines:

### 1. Generating Release Android App Bundle (.aab)
Google Play Store requires `.aab` (Android App Bundle) format instead of `.apk` for new apps:
```bash
./gradlew :app:bundleRelease
```
The resulting file will be generated at:
`app/build/outputs/bundle/release/app-release.aab`

### 2. Release Signing Configuration
Release signing is configured in `app/build.gradle.kts` to pull credentials securely from environment variables:

Set the following environment variables before building release binaries:
- `KEYSTORE_PATH`: Path to your `.jks` release keystore (e.g., `/path/to/my-upload-key.jks`)
- `STORE_PASSWORD`: Keystore store password
- `KEY_PASSWORD`: Key alias password

Example command on Linux/macOS:
```bash
export KEYSTORE_PATH="/path/to/upload-key.jks"
export STORE_PASSWORD="your_store_password"
export KEY_PASSWORD="your_key_password"
./gradlew :app:bundleRelease
```

Example command on Windows (PowerShell):
```powershell
$env:KEYSTORE_PATH="C:\path\to\upload-key.jks"
$env:STORE_PASSWORD="your_store_password"
$env:KEY_PASSWORD="your_key_password"
.\gradlew :app:bundleRelease
```

### 3. Versioning Strategy
Before generating a release bundle for Google Play:
- Update `versionCode` (must be incremented sequentially for every Play Store submission).
- Update `versionName` (semantic version, e.g., `"1.0.1"`).
Location: `app/build.gradle.kts` -> `defaultConfig`.

### 4. Code Shrinking & Obfuscation (R8/Proguard)
Ensure `isMinifyEnabled = true` is verified in `buildTypes { release { ... } }` when deploying to production to reduce bundle size and obfuscate bytecode. Test all serialization classes to ensure necessary Keep rules are present in `app/proguard-rules.pro`.

### 5. Best Practices Checklist Before Release
- [ ] Run `./gradlew :app:compileDebugKotlin` and `./gradlew test` to ensure zero compilation or unit test failures.
- [ ] Verify `google-services.json` is configured for production Firebase project.
- [ ] Verify `MAPS_API_KEY` in `.env` has appropriate HTTP / Android package SHA-1 restrictions in Google Cloud Console.
- [ ] Test the release `.aab` locally using [bundletool](https://developer.android.com/tools/bundletool) or Play Console Internal Testing track before publishing.
