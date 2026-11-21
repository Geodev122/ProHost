package com.example.data.auth

import kotlin.concurrent.Volatile

/**
 * The platform's current Firebase Auth session, for the app-wide view model (session restore, sign-out,
 * token refresh after a role change). Installed at app start; the defaults mean "nobody signed in".
 */
object SessionAuth {
    /** The signed-in user, or null. May throw when the platform SDK is not initialised (callers guard it). */
    @Volatile
    var currentUser: () -> SignedInUser? = { null }

    /** Forces a fresh ID token for the signed-in user (no-op when nobody is signed in); throws on failure. */
    @Volatile
    var refreshIdToken: suspend () -> Unit = {}

    @Volatile
    var signOut: () -> Unit = {}

    /** Forgets the platform's remembered sign-in credential (Android Credential Manager), in the background. */
    @Volatile
    var clearCredentials: () -> Unit = {}
}
