// Throw-away spike for the KMP + Compose Multiplatform migration (docs/migration/MASTER_PLAN.md, P0).
// Self-contained: not included by the real settings.gradle.kts, not built by any product workflow.
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    val kotlin = providers.gradleProperty("spike.kotlin").getOrElse("2.2.10")
    val cmp = providers.gradleProperty("spike.cmp").getOrElse("1.12.2")
    val agp = providers.gradleProperty("spike.agp").getOrElse("9.4.1")
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version kotlin
        id("org.jetbrains.kotlin.plugin.compose") version kotlin
        id("org.jetbrains.compose") version cmp
        id("com.android.kotlin.multiplatform.library") version agp
        id("com.android.application") version agp
    }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "spike"
include(":shared", ":androidApp", ":webApp")
