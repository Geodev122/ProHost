package com.example.data.auth

import androidx.annotation.StringRes
import com.example.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException

/**
 * What went wrong with an email/password or Google sign-in, in terms the screen can explain.
 * Firebase's own messages are English developer text ("The supplied auth credential is
 * incorrect, malformed or has expired") and must never reach people.
 */
enum class AuthError(@StringRes val message: Int) {
    /** Wrong email or password (enumeration protection merges "no such user" into this). */
    INVALID_CREDENTIALS(R.string.err_invalid_credentials),
    EMAIL_IN_USE(R.string.err_email_in_use),
    WEAK_PASSWORD(R.string.password_doesnt_meet_rules),
    INVALID_EMAIL(R.string.please_enter_a_valid_email_address),
    TOO_MANY_REQUESTS(R.string.err_too_many_requests),
    USER_DISABLED(R.string.err_user_disabled),
    NETWORK(R.string.network_error_please_check_your_connection),
    UNAVAILABLE(R.string.err_auth_unavailable),
    UNKNOWN(R.string.sign_in_failed_please_try_again);

    companion object {
        /** Pure mapping of Firebase Auth error codes (unit-tested). */
        fun fromCode(code: String?): AuthError = when (code?.uppercase()) {
            "ERROR_INVALID_CREDENTIAL", "INVALID_LOGIN_CREDENTIALS", "ERROR_INVALID_LOGIN_CREDENTIALS",
            "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_USER_MISMATCH" -> INVALID_CREDENTIALS
            "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_CREDENTIAL_ALREADY_IN_USE",
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> EMAIL_IN_USE
            "ERROR_WEAK_PASSWORD", "ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS",
            "PASSWORD_DOES_NOT_MEET_REQUIREMENTS" -> WEAK_PASSWORD
            "ERROR_INVALID_EMAIL", "ERROR_MISSING_EMAIL" -> INVALID_EMAIL
            "ERROR_TOO_MANY_REQUESTS" -> TOO_MANY_REQUESTS
            "ERROR_USER_DISABLED" -> USER_DISABLED
            "ERROR_NETWORK_REQUEST_FAILED" -> NETWORK
            "ERROR_OPERATION_NOT_ALLOWED", "ERROR_INTERNAL_ERROR" -> UNAVAILABLE
            else -> UNKNOWN
        }

        fun from(t: Throwable): AuthError = when (t) {
            is FirebaseNetworkException -> NETWORK
            is FirebaseTooManyRequestsException -> TOO_MANY_REQUESTS
            is FirebaseAuthException -> fromCode(t.errorCode).let {
                // Newer SDKs put the server reason in the message ("PASSWORD_DOES_NOT_MEET_REQUIREMENTS: …").
                if (it == UNKNOWN && t.message?.contains("PASSWORD_DOES_NOT_MEET_REQUIREMENTS") == true) WEAK_PASSWORD else it
            }
            else -> UNKNOWN
        }
    }
}
