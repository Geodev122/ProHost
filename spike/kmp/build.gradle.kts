// Plugin versions come from settings.gradle.kts (pluginManagement), so they can be overridden with -Pspike.*.
// Declaring each plugin once here (apply false) keeps the Kotlin Gradle plugin on a single classpath for all subprojects.
plugins {
    id("org.jetbrains.kotlin.multiplatform") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("org.jetbrains.compose") apply false
    id("com.android.kotlin.multiplatform.library") apply false
    id("com.android.application") apply false
}
