package com.example.data.auth

/** Name of the string resource the sign-in screens show for [AuthError] (never Firebase's English text). */
val AuthError.messageKey: String
    get() = when (this) {
        AuthError.INVALID_CREDENTIALS -> "err_invalid_credentials"
        AuthError.EMAIL_IN_USE -> "err_email_in_use"
        AuthError.WEAK_PASSWORD -> "password_doesnt_meet_rules"
        AuthError.INVALID_EMAIL -> "please_enter_a_valid_email_address"
        AuthError.TOO_MANY_REQUESTS -> "err_too_many_requests"
        AuthError.USER_DISABLED -> "err_user_disabled"
        AuthError.NETWORK -> "network_error_please_check_your_connection"
        AuthError.UNAVAILABLE -> "err_auth_unavailable"
        AuthError.UNKNOWN -> "sign_in_failed_please_try_again"
    }
