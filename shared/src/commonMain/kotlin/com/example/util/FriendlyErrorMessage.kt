package com.example.util

import kotlin.concurrent.Volatile

/** Platform part of [friendlyErrorMessage]: a callable (Cloud Functions) error's own user text, or null for other errors. */
object FriendlyErrors {
    @Volatile
    var callableMessage: (e: Throwable, fallback: String) -> String? = { _, _ -> null }
}

/**
 * Returns [e]'s own message when it reads like something a user would actually
 * understand, or [fallback] when it looks technical (a raw Firebase/SDK exception
 * string, stack-trace fragment, or blank message) — so a network/Cloud-Function
 * failure never surfaces raw internals in a Toast/Snackbar. Cloud Function
 * failures go through the platform's callable error mapping ([FriendlyErrors.callableMessage]; Android:
 * `FirebaseFunctionsException.toUserMessage`).
 */
fun friendlyErrorMessage(e: Throwable, fallback: String): String {
    FriendlyErrors.callableMessage(e, fallback)?.let { return it }
    val message = e.message
    val looksTechnical = message.isNullOrBlank() ||
        message.matches(Regex("[A-Z_]+")) ||
        message.contains("Firebase", ignoreCase = true) ||
        message.contains("Exception", ignoreCase = true) ||
        message.contains("com.google", ignoreCase = true)
    return if (looksTechnical) fallback else message!!
}
