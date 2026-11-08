// Shared Kotlin Multiplatform module (migration plan P3): code that has no Android types moves here in small, parity-gated steps.
// Packages are unchanged (com.example.*), so the :app module compiles against it without any import change.
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
  androidLibrary {
    namespace = "com.example.shared"
    compileSdk = 37
    minSdk = 24
  }
  // iOS targets compile on macOS runners only; on Linux they are skipped.
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(libs.kotlinx.coroutines.core)
    }
  }
}
