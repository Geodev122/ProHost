plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "spike.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "spike.kmp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1"
    }
    testOptions {
        unitTests { isIncludeAndroidResources = true }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.10.1")

    // Round 5: same Robolectric + Roborazzi versions as the real app's design harness, Compose test artifacts at the Jetpack Compose
    // version that Compose Multiplatform 1.12.2 is built on (1.12.1).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.12.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.40.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.12.1")
}
