package com.example.data.auth

/**
 * The signed-in account as the shared sign-in flow needs it, independent of the platform's Firebase SDK
 * (Android FirebaseUser, iOS bridge). [readRoleClaim] returns the ID token's `role` custom claim; with
 * forceRefresh = true it fetches a fresh token from Firebase, otherwise it reads the cached one.
 */
class SignedInUser(
    val uid: String,
    val email: String?,
    val phoneNumber: String?,
    val readRoleClaim: suspend (forceRefresh: Boolean) -> String?,
)
