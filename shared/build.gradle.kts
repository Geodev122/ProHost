// Shared Kotlin Multiplatform module (migration plan P3): code that has no Android types moves here in small, parity-gated steps.
// Packages are unchanged (com.example.*), so the :app module compiles against it without any import change.
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
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(libs.kotlinx.coroutines.core)
      // Compose Multiplatform: exposed with api because the app's screens (and later the shared ones) use these types directly.
      api(compose.runtime)
      api(compose.foundation)
      api(compose.material3)
      api(compose.ui)
      api(compose.components.resources)
      api("org.jetbrains.compose.material:material-icons-extended:${libs.versions.materialIconsExtended.get()}")
    }
  }
}

compose.resources {
  publicResClass = true
  packageOfResClass = "com.example.shared.resources"
}
