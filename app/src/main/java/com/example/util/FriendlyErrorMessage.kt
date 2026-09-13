package com.example.util

/**
 * Returns [e]'s own message when it reads like something a user would actually
 * understand, or [fallback] when it looks technical (a raw Firebase/SDK exception
 * string, stack-trace fragment, or blank message) — so a network/Cloud-Function
 * failure never surfaces raw internals in a Toast/Snackbar. Generalizes the same
 * heuristic AuthViewModel.friendlyRegistrationErrorMessage() already uses for
 * registration errors specifically; phone-auth's own FirebaseAuthService.
 * friendlyPhoneAuthMessage() has real Firebase-error-code matching logic that's
 * kept separate rather than folded in here.
 */
fun friendlyErrorMessage(e: Throwable, fallback: String): String {
    val message = e.message
    val looksTechnical = message.isNullOrBlank() ||
        message.contains("Firebase", ignoreCase = true) ||
        message.contains("Exception", ignoreCase = true) ||
        message.contains("com.google", ignoreCase = true)
    return if (looksTechnical) fallback else message!!
}
