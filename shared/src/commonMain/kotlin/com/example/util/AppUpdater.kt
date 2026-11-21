package com.example.util

import kotlinx.coroutines.flow.StateFlow

enum class UpdateState {
    IDLE,
    CHECKING,
    UPDATE_AVAILABLE_FLEXIBLE,
    UPDATE_AVAILABLE_IMMEDIATE,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
    UP_TO_DATE
}

/** In-app update (Android: Play's in-app update API in `InAppUpdateManager`; iOS: none, the App Store updates the app). */
interface AppUpdater {
    val updateState: StateFlow<UpdateState>
    val downloadProgress: StateFlow<Float>
    fun checkForAppUpdate(preferImmediate: Boolean = false)
    fun completeUpdate()
}
