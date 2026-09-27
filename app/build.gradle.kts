import java.util.Properties
import java.io.FileInputStream
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "app.geonajjar.prohost"
    minSdk = 24
    targetSdk = 36
    versionCode = 21
    versionName = "1.0.20"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(FileInputStream(localPropertiesFile))
    }
    
    val mapsApiKey = System.getenv("MAPS_API_KEY") ?: localProperties.getProperty("MAPS_API_KEY") ?: ""
    manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
  }

  signingConfigs {
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(FileInputStream(localPropertiesFile))
    }

    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: localProperties.getProperty("keystore.path") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD") ?: localProperties.getProperty("keystore.password")
      keyAlias = System.getenv("KEY_ALIAS") ?: localProperties.getProperty("key.alias") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD") ?: localProperties.getProperty("key.password")
    }
    val rootDebugKeystore = file("${rootDir}/debug.keystore")
    if (rootDebugKeystore.exists()) {
      create("debugConfig") {
        storeFile = rootDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  packaging {
    jniLibs {
      keepDebugSymbols.add("**/*.so")
    }
  }

  buildTypes {
    release {
      ndk {
        debugSymbolLevel = "FULL"
      }
      isCrunchPngs = false
      // Was false with proguardFiles(...) already configured below — dead
      // configuration that shipped every release build fully unobfuscated and
      // unshrunk. proguard-rules.pro's enum-safety rules below specifically
      // protect this app's heavy reliance on Enum.valueOf(String) round-
      // tripping every Firestore-stored field (SpaceListing/AppUser/BookingRequest
      // etc. all decode via `runCatching { SomeEnum.valueOf(str) }` in their
      // fromFirestoreMap functions) — R8 can otherwise optimize small enums into
      // plain ints, breaking that lookup silently at runtime. A real on-device
      // release build + full manual smoke test (registration, listing publish,
      // booking request/accept, Whish payment, Admin Console) is required before
      // this is trusted — this environment has no Android SDK to run R8 itself.
      isMinifyEnabled = true
      // Paired with isMinifyEnabled — confirmed via repo-wide grep that the app
      // never looks up a resource dynamically by name (Resources.getIdentifier),
      // so there's no reflective-by-name resource this could strip unexpectedly.
      // Same standing caveat as isMinifyEnabled above: needs a real release build
      // to confirm nothing is missing at runtime.
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
      // Release builds attest with Play Integrity only; never a debug token.
      buildConfigField("String", "APP_CHECK_DEBUG_TOKEN", "\"\"")
    }
    debug {
      signingConfig = signingConfigs.findByName("debugConfig") ?: signingConfigs.getByName("debug")
      // One shared App Check debug token for every tester's debug APK (CI secret
      // APP_CHECK_DEBUG_TOKEN, registered once in Firebase console > App Check >
      // Manage debug tokens). Empty = each install generates its own token.
      val localProps = Properties()
      rootProject.file("local.properties").takeIf { it.exists() }?.let { f ->
          FileInputStream(f).use { localProps.load(it) }
      }
      val appCheckDebugToken = System.getenv("APP_CHECK_DEBUG_TOKEN")
          ?: localProps.getProperty("APP_CHECK_DEBUG_TOKEN") ?: ""
      buildConfigField("String", "APP_CHECK_DEBUG_TOKEN", "\"$appCheckDebugToken\"")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
      freeCompilerArgs.addAll(
        "-Xskip-metadata-version-check",
        "-Xno-call-assertions",
        "-Xno-param-assertions",
        "-Xno-receiver-assertions"
      )
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  lint {
    checkReleaseBuilds = false
    abortOnError = false
    disable.add("InvalidFragmentVersionForActivityResult")
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.firebase.ai)  // unused — auto-inits and may crash without config
  // Uncomment to use Firestore:
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.crashlytics)
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.storage)

  // Firebase Auth: phone-number SMS OTP for new-user signup and PIN reset;
  // Google Sign-In (play-services-auth) for one-tap sign-in via Google account.
  implementation(libs.firebase.auth)
  implementation(libs.play.services.auth)
  implementation(libs.firebase.functions)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.google.identity)
  implementation(libs.firebase.appcheck.playintegrity)
  debugImplementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.play.app.update)
  implementation(libs.play.app.update.ktx)
  implementation(libs.play.billing)
  implementation(libs.play.billing.ktx)
  implementation(libs.play.integrity)
  implementation(libs.play.services.location)
  implementation(libs.maps.compose)
  implementation(libs.play.services.maps)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
