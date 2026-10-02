# Final Production Readiness & Release Guide

## 1. Production Audit Summary
Your app is fully prepped for the **v1.0.28 (Build 29)** release!

✅ **SDK Targets:** `compileSdk 37`, `minSdk 25`, `targetSdk 37` are successfully configured.
✅ **Version Bump:** Updated to `versionName = "1.0.28"` and `versionCode = 29`.
✅ **Data Safety & Backups:** Database is now explicitly included in the Android 12+ cloud backups and device transfers, per your request.
✅ **API Keys & Secrets:** Google Maps API key is successfully injected.
✅ **Billing:** 100% relying on Google Play Billing Client (`com.android.billingclient:billing`). Leftover Whish payment strings have been scrubbed.

*Note on Play Store API target:* Since `targetSdk 37` is a Developer Preview (Android 17), Google Play Console might throw a warning or error if they require targeting stable releases (currently API 34/35). If Play Console rejects the upload for targeting a preview SDK, you'll simply need to change `targetSdk` back to `35`.

## 2. Generate the Signed Release Bundle (.aab)

I have configured the build system, but **I cannot generate the final signed bundle for you yet** because the actual release Keystore (`my-upload-key.jks`) does not exist on your local disk, and the passwords in your `local.properties` are placeholders (`YOUR_KEYSTORE_PASSWORD`).

To physically generate the `.aab` for upload to the Play Console, please perform the following steps:

### Step A: Generate or Locate your Keystore
1. In Android Studio, go to the top menu: **Build > Generate Signed Bundle / APK...**
2. Select **Android App Bundle** and click **Next**.
3. Under the **Key store path**, if you don't have one, click **Create new...** and save it as `my-upload-key.jks` in the root of your project (`C:/Users/Psy/StudioProjects/ProHost/my-upload-key.jks`).
4. Remember the **Store password**, **Key alias** (e.g., `upload`), and **Key password**.

### Step B: Update `local.properties`
Open your `local.properties` file and replace the placeholders with the actual passwords you just created:
```properties
keystore.path=C\:\\Users\\Psy\\StudioProjects\\ProHost\\my-upload-key.jks
keystore.password=YourActualStorePassword
key.alias=upload
key.password=YourActualKeyPassword
```

### Step C: Build the Bundle
Once the keystore is securely in place, you can build the release bundle either:
* **Via Android Studio UI:** Finish the wizard in **Build > Generate Signed Bundle / APK...** (select `release` variant).
* **Via Terminal:** Run `./gradlew :app:bundleRelease` in the Android Studio terminal.

Your final file will be located at:
`app/build/outputs/bundle/release/app-release.aab`
You can upload this file directly to the Google Play Console!