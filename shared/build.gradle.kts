// Shared Kotlin Multiplatform module (migration plan P3): code that has no Android types moves here in small, parity-gated steps.
// Packages are unchanged (com.example.*), so the :app module compiles against it without any import change.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.jetbrains.compose)
}

kotlin {
  androidLibrary {
    namespace = "com.example.shared"
    compileSdk = 37
    minSdk = 24
    // Compose resources (strings, later fonts and drawables) are packaged as Android assets/resources.
    androidResources { enable = true }
  }
  // iOS targets compile on macOS runners only; on Linux they are skipped.
  // The iOS app links this module as a static framework (ios/project.yml runs :shared:embedAndSignAppleFrameworkForXcode).
  listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
      baseName = "ProHostShared"
      isStatic = true
    }
  }
  // Web target: only :adminWeb consumes it (admin console, plan P1); the mobile apps never see it.
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs { browser() }

  sourceSets {
    commonMain.dependencies {
      api(libs.kotlinx.coroutines.core)
      // Dates in shared logic (IsoDate, booking terms, dated slots). Wraps java.time on Android: the app keeps core-library desugaring on.
      api(libs.kotlinx.datetime)
      // ViewModel + viewModelScope for the shared view models (Discovery first).
      api(libs.jetbrains.lifecycle.viewmodel)
      // viewModel(), LocalLifecycleOwner and BackHandler for shared screens (same versions CMP 1.12.2 resolves).
      api(libs.jetbrains.lifecycle.viewmodel.compose)
      api(libs.jetbrains.lifecycle.runtime.compose)
      api(libs.jetbrains.ui.backhandler)
      // Compose Multiplatform: exposed with api because the app's screens (and later the shared ones) use these types directly.
      api(compose.runtime)
      api(compose.foundation)
      api(compose.material3)
      api(compose.ui)
      api(compose.components.resources)
      api("org.jetbrains.compose.material:material-icons-extended:${libs.versions.materialIconsExtended.get()}")
    }
    // Coil 3 (multiplatform images; Coil 3.4.0 = newest built with Kotlin 2.3) over Ktor 3.1, one engine per platform.
    commonMain.dependencies {
      api(libs.coil3.compose)
      api(libs.coil3.network.ktor3)
    }
    androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
    iosMain.dependencies { implementation(libs.ktor.client.darwin) }
    wasmJsMain.dependencies { implementation(libs.ktor.client.js) }
  }
}

compose.resources {
  publicResClass = true
  packageOfResClass = "com.example.shared.resources"
}
