package com.example.util

import com.example.data.auth.toUserMessage

/**
 * Returns [e]'s own message when it reads like something a user would actually
 * understand, or [fallback] when it looks technical (a raw Firebase/SDK exception
 * string, stack-trace fragment, or blank message) — so a network/Cloud-Function
 * failure never surfaces raw internals in a Toast/Snackbar. Cloud Function
 * failures go through com.example.data.auth.toUserMessage, which
 * understands callable error codes; phone-auth's own FirebaseAuthService.
 * friendlyPhoneAuthMessage() has real Firebase-error-code matching logic that's
 * kept separate rather than folded in here.
 */
fun friendlyErrorMessage(e: Throwable, fallback: String): String {
    if (e is com.google.firebase.functions.FirebaseFunctionsException) {
        return e.toUserMessage(fallback)
    }
    val message = e.message
    val looksTechnical = message.isNullOrBlank() ||
        message.matches(Regex("[A-Z_]+")) ||
        message.contains("Firebase", ignoreCase = true) ||
        message.contains("Exception", ignoreCase = true) ||
        message.contains("com.google", ignoreCase = true)
    return if (looksTechnical) fallback else message!!
}
