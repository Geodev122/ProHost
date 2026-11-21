package com.example.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException


/** The [AuthError] of a Firebase Android SDK exception. */
fun AuthError.Companion.from(t: Throwable): AuthError = when (t) {
    is FirebaseNetworkException -> AuthError.NETWORK
    is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_REQUESTS
    is FirebaseAuthException -> fromCode(t.errorCode).let {
        // Newer SDKs put the server reason in the message ("PASSWORD_DOES_NOT_MEET_REQUIREMENTS: …").
        if (it == AuthError.UNKNOWN && t.message?.contains("PASSWORD_DOES_NOT_MEET_REQUIREMENTS") == true) AuthError.WEAK_PASSWORD else it
    }
    else -> AuthError.UNKNOWN
}
