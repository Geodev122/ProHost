package com.example.data.auth

import androidx.annotation.StringRes
import com.example.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException

/** The sentence the sign-in screens show for [AuthError] (Android string resource; never Firebase's English text). */
@get:StringRes
val AuthError.message: Int
    get() = when (this) {
        AuthError.INVALID_CREDENTIALS -> R.string.err_invalid_credentials
        AuthError.EMAIL_IN_USE -> R.string.err_email_in_use
        AuthError.WEAK_PASSWORD -> R.string.password_doesnt_meet_rules
        AuthError.INVALID_EMAIL -> R.string.please_enter_a_valid_email_address
        AuthError.TOO_MANY_REQUESTS -> R.string.err_too_many_requests
        AuthError.USER_DISABLED -> R.string.err_user_disabled
        AuthError.NETWORK -> R.string.network_error_please_check_your_connection
        AuthError.UNAVAILABLE -> R.string.err_auth_unavailable
        AuthError.UNKNOWN -> R.string.sign_in_failed_please_try_again
    }

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
