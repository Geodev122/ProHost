package com.example.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * On sign-out the Credential Manager must forget the signed-in Google account too (Google's sign-in guidance), otherwise the
 * device keeps offering the previous account on the next sign-in. Fire-and-forget: it never delays or fails a sign-out.
 */
object GoogleCredentialState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun clearInBackground(context: Context? = null) {
        val ctx = context?.applicationContext ?: appContext ?: return
        scope.launch {
            try {
                CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                // No credential provider on this device, or nothing to clear: sign-out already happened.
                Log.d("GoogleCredentialState", "clearCredentialState: ${e.javaClass.simpleName}")
            }
        }
    }
}
