package com.example.data.auth

import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code

/**
 * True when a callable failed because the backend itself is unreachable or missing
 * (not deployed, region mismatch, outage) rather than because it rejected the input.
 */
fun Throwable.isCallableUnavailable(): Boolean {
    val code = (this as? FirebaseFunctionsException)?.code ?: return false
    return code == Code.NOT_FOUND || code == Code.UNAVAILABLE || code == Code.INTERNAL ||
        code == Code.UNKNOWN || code == Code.DEADLINE_EXCEEDED || code == Code.UNIMPLEMENTED
}

/**
 * Maps a callable/SDK failure to text that is safe to show. The SDK's message for a
 * transport failure is just the code name ("NOT_FOUND", "INTERNAL"), which is
 * meaningless to users; a function's own HttpsError message is already user-facing.
 */
fun Throwable.toUserMessage(fallback: String): String {
    val e = this as? FirebaseFunctionsException
        ?: return if (message?.contains("network", ignoreCase = true) == true) {
            "Network error. Please check your connection and try again."
        } else {
            fallback
        }
    val serverMessage = e.message?.takeIf { it.isNotBlank() && it != e.code.name }
    return when (e.code) {
        Code.UNAUTHENTICATED -> "Your session could not be verified. Please sign in again."
        Code.PERMISSION_DENIED -> serverMessage ?: "You don't have permission to do that."
        Code.RESOURCE_EXHAUSTED -> serverMessage ?: "Too many attempts. Please wait a minute and try again."
        Code.INVALID_ARGUMENT, Code.FAILED_PRECONDITION, Code.ALREADY_EXISTS, Code.OUT_OF_RANGE ->
            serverMessage ?: fallback
        Code.NOT_FOUND, Code.UNIMPLEMENTED ->
            "This service is temporarily unavailable. Please try again shortly."
        // A function may throw UNAVAILABLE itself with a real explanation (billing: "your
        // payment is safe…"); only a transport failure (it carries the IOException as its
        // cause) gets the generic connection text.
        Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED ->
            serverMessage?.takeIf { e.cause == null && it.contains(' ') }
                ?: "Can't reach the server right now. Please check your connection and try again."
        else -> fallback
    }
}
