// ProHost Admin Console as a web app (migration plan P1). Compose for Web (Kotlin/Wasm), shares only theme tokens with :shared.
// NEVER a dependency of :app or iOS: the mobile builds must not contain admin code once the cutover gate (ADMIN_PARITY.md) passes.
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.jetbrains.compose)
}

kotlin {
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    outputModuleName.set("adminWeb")
    browser { commonWebpackConfig { outputFileName = "adminWeb.js" } }
    binaries.executable()
  }
  sourceSets {
    wasmJsMain.dependencies {
      implementation(project(":shared"))
      implementation(libs.kotlinx.coroutines.core)
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.components.resources)
      // Firebase Auth + callables over the JS SDK (no Hosting handler needed for email + password).
      implementation(npm("firebase", "12.0.0"))
    }
    wasmJsTest.dependencies { implementation(kotlin("test")) }
  }
}
