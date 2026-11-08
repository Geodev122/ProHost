import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    androidLibrary {
        namespace = "spike.shared"
        compileSdk = 37
        minSdk = 24
        androidResources { enable = true }
    }
    iosArm64()
    iosSimulatorArm64()
    jvm("desktop") // fast host target for logic/resource tests on Linux
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        // The JVM host target needs the Skia native runtime on its test classpath (round 3: skiko LibraryLoadException).
        val desktopTest by getting {
            dependencies { implementation(compose.desktop.currentOs) }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "spike.shared.resources"
}
