package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

class InAppUpdateManager(private val activity: ComponentActivity) {
    private val tag = "InAppUpdateManager"
    private var appUpdateManager: AppUpdateManager? = null

    private val _updateState = MutableStateFlow(UpdateState.IDLE)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private var appUpdateInfo: AppUpdateInfo? = null

    private val updateResultLauncher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                Log.w(tag, "Update flow failed or cancelled with result code: ${result.resultCode}")
                _updateState.value = UpdateState.IDLE
            }
        }

    private val installStateUpdatedListener = InstallStateUpdatedListener { state: InstallState ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val bytesDownloaded = state.bytesDownloaded()
                val totalBytes = state.totalBytesToDownload()
                val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
                _downloadProgress.value = progress
                _updateState.value = UpdateState.DOWNLOADING
                Log.d(tag, "In-app update downloading: ${(progress * 100).toInt()}%")
            }
            InstallStatus.DOWNLOADED -> {
                _downloadProgress.value = 1.0f
                _updateState.value = UpdateState.DOWNLOADED
                Log.i(tag, "In-app update downloaded successfully. Ready for completeUpdate().")
            }
            InstallStatus.FAILED -> {
                _updateState.value = UpdateState.FAILED
                Log.e(tag, "In-app update installation failed: ${state.installErrorCode()}")
            }
            InstallStatus.CANCELED -> {
                _updateState.value = UpdateState.IDLE
                Log.d(tag, "In-app update cancelled by user")
            }
            InstallStatus.INSTALLED -> {
                _updateState.value = UpdateState.UP_TO_DATE
                Log.i(tag, "In-app update installed successfully")
            }
            else -> {
                // Pending, Installing, Unknown
            }
        }
    }

    init {
        try {
            appUpdateManager = AppUpdateManagerFactory.create(activity)
            appUpdateManager?.registerListener(installStateUpdatedListener)
        } catch (e: Exception) {
            Log.w(tag, "Failed to initialize AppUpdateManager: ${e.message}")
        }
    }

    /**
     * Checks for available updates from Google Play Store.
     * @param preferImmediate If true or if updatePriority >= 4, prompts immediate fullscreen update.
     */
    fun checkForAppUpdate(preferImmediate: Boolean = false) {
        val manager = appUpdateManager ?: return
        _updateState.value = UpdateState.CHECKING

        manager.appUpdateInfo.addOnSuccessListener { info ->
            appUpdateInfo = info
            val availability = info.updateAvailability()
            val priority = info.updatePriority()

            Log.d(tag, "AppUpdateInfo: availability=$availability, priority=$priority, availableVersionCode=${info.availableVersionCode()}")

            if (availability == UpdateAvailability.UPDATE_AVAILABLE) {
                val isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                val isFlexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)

                if ((preferImmediate || priority >= 4) && isImmediateAllowed) {
                    _updateState.value = UpdateState.UPDATE_AVAILABLE_IMMEDIATE
                    startImmediateUpdate(info)
                } else if (isFlexibleAllowed) {
                    _updateState.value = UpdateState.UPDATE_AVAILABLE_FLEXIBLE
                    startFlexibleUpdate(info)
                } else if (isImmediateAllowed) {
                    _updateState.value = UpdateState.UPDATE_AVAILABLE_IMMEDIATE
                    startImmediateUpdate(info)
                }
            } else if (availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // An update is already in progress
                startImmediateUpdate(info)
            } else {
                _updateState.value = UpdateState.UP_TO_DATE
            }
        }.addOnFailureListener { exception ->
            Log.w(tag, "Check for update failed: ${exception.message}")
            _updateState.value = UpdateState.IDLE
        }
    }

    /**
     * Start immediate update flow
     */
    fun startImmediateUpdate(info: AppUpdateInfo? = appUpdateInfo) {
        val manager = appUpdateManager ?: return
        val updateInfo = info ?: return
        try {
            manager.startUpdateFlowForResult(
                updateInfo,
                updateResultLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
            )
        } catch (e: Exception) {
            Log.e(tag, "Error starting immediate update flow: ${e.message}")
        }
    }

    /**
     * Start flexible update flow in background
     */
    fun startFlexibleUpdate(info: AppUpdateInfo? = appUpdateInfo) {
        val manager = appUpdateManager ?: return
        val updateInfo = info ?: return
        try {
            manager.startUpdateFlowForResult(
                updateInfo,
                updateResultLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
            )
        } catch (e: Exception) {
            Log.e(tag, "Error starting flexible update flow: ${e.message}")
        }
    }

    /**
     * Trigger completion and restart of the app after flexible download completes
     */
    fun completeUpdate() {
        val manager = appUpdateManager ?: return
        try {
            manager.completeUpdate()
        } catch (e: Exception) {
            Log.e(tag, "Error executing completeUpdate(): ${e.message}")
        }
    }

    /**
     * Call on Activity onResume to handle resumed in-progress updates
     */
    fun onResume() {
        val manager = appUpdateManager ?: return
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                _updateState.value = UpdateState.DOWNLOADED
            } else if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startImmediateUpdate(info)
            }
        }
    }

    /**
     * Call on Activity onDestroy to unregister listeners
     */
    fun onDestroy() {
        try {
            appUpdateManager?.unregisterListener(installStateUpdatedListener)
        } catch (e: Exception) {
            Log.w(tag, "Error unregistering installStateUpdatedListener: ${e.message}")
        }
    }
}
