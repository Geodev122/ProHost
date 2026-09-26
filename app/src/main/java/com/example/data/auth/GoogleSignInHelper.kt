@file:Suppress("DEPRECATION")

package com.example.data.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

object GoogleSignInHelper {

    fun getSignInIntent(context: Context): Intent {
        val webClientId = context.getString(com.example.R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        // Sign out first so the account picker always shows (no silent auto-select).
        val client = GoogleSignIn.getClient(context, gso)
        client.signOut()
        return client.signInIntent
    }

    fun parseResult(data: Intent?): Result<GoogleSignInAccount> = runCatching {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        task.getResult(ApiException::class.java)
    }

    fun friendlyError(e: Throwable): String {
        if (e is ApiException) return when (e.statusCode) {
            12501 -> "Sign-in was cancelled."
            12502 -> "Sign-in is already in progress — please wait."
            7 -> "Network error — check your connection and try again."
            10 -> "Google Sign-In is not configured correctly. Contact support."
            else -> "Google sign-in failed (code ${e.statusCode}). Please try again."
        }
        return "Google sign-in failed. Please try again."
    }
}
