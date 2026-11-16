package com.example.data.auth

import com.example.data.gateway.CallableException
import kotlin.concurrent.Volatile

/** A callable failure as the error texts need it: the Functions code name ("NOT_FOUND", ...), the message and whether a transport error caused it. */
data class CallableFailure(val code: String, val message: String?, val transportCause: Boolean)

/**
 * Recognises callable failures. Shared code knows [CallableException] (the bridged iOS gateway); Android registers
 * Firebase's `FirebaseFunctionsException` in [describe] (`AndroidCallableFailures.install`).
 */
object CallableFailures {
    @Volatile
    var describe: (Throwable) -> CallableFailure? = ::describeShared

    fun describeShared(t: Throwable): CallableFailure? =
        (t as? CallableException)?.let { CallableFailure(it.code, it.message, transportCause = false) }
}

/**
 * True when a callable failed because the backend itself is unreachable or missing
 * (not deployed, region mismatch, outage) rather than because it rejected the input.
 */
fun Throwable.isCallableUnavailable(): Boolean {
    val code = CallableFailures.describe(this)?.code ?: return false
    return code == "NOT_FOUND" || code == "UNAVAILABLE" || code == "INTERNAL" ||
        code == "UNKNOWN" || code == "DEADLINE_EXCEEDED" || code == "UNIMPLEMENTED"
}

/**
 * Maps a callable/SDK failure to text that is safe to show. The SDK's message for a
 * transport failure is just the code name ("NOT_FOUND", "INTERNAL"), which is
 * meaningless to users; a function's own HttpsError message is already user-facing.
 */
fun Throwable.toUserMessage(fallback: String): String {
    val e = CallableFailures.describe(this)
        ?: return if (message?.contains("network", ignoreCase = true) == true) {
            "Network error. Please check your connection and try again."
        } else {
            fallback
        }
    val serverMessage = e.message?.takeIf { it.isNotBlank() && it != e.code }
    return when (e.code) {
        "UNAUTHENTICATED" -> "Your session could not be verified. Please sign in again."
        "PERMISSION_DENIED" -> serverMessage ?: "You don't have permission to do that."
        "RESOURCE_EXHAUSTED" -> serverMessage ?: "Too many attempts. Please wait a minute and try again."
        "INVALID_ARGUMENT", "FAILED_PRECONDITION", "ALREADY_EXISTS", "OUT_OF_RANGE" ->
            serverMessage ?: fallback
        "NOT_FOUND", "UNIMPLEMENTED" ->
            "This service is temporarily unavailable. Please try again shortly."
        // A function may throw UNAVAILABLE itself with a real explanation (billing: "your
        // payment is safe…"); only a transport failure (it carries the IOException as its
        // cause) gets the generic connection text.
        "UNAVAILABLE", "DEADLINE_EXCEEDED" ->
            serverMessage?.takeIf { !e.transportCause && it.contains(' ') }
                ?: "Can't reach the server right now. Please check your connection and try again."
        else -> fallback
    }
}
