package com.example.data.auth

import com.google.firebase.functions.FirebaseFunctionsException

/** Android part of [CallableFailures]: Firebase's own callable exception, read exactly as the old Android-only helpers did. */
object AndroidCallableFailures {
    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        CallableFailures.describe = { t ->
            (t as? FirebaseFunctionsException)?.let { CallableFailure(it.code.name, it.message, transportCause = it.cause != null) }
                ?: CallableFailures.describeShared(t)
        }
    }
}
