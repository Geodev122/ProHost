package com.example.util

import kotlin.concurrent.Volatile

/** The running app's version, installed by the platform at start (Android: `BuildConfig.VERSION_NAME` in ProHostApplication). */
object AppInfo {
    @Volatile
    var versionName: String = ""
}
