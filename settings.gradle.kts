pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  // The Kotlin Wasm/JS plugin (used by :adminWeb) adds its Node.js and Yarn download repositories to the root project, which
  // FAIL_ON_PROJECT_REPOS refuses. PREFER_PROJECT keeps every other module on the repositories below (none of them declares its own).
  repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "ProSpace"

include(":app", ":shared", ":adminWeb")
