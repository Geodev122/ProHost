package com.example.util

import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resume

/**
 * Resumes only while the continuation is still waiting. Native callbacks (Sign in with Apple, Google, Firebase on iOS) can
 * fire twice or after the screen left; a second `resume` throws IllegalStateException, which terminates a Kotlin/Native app.
 */
fun <T> CancellableContinuation<T>.resumeOnce(value: T) {
    if (isActive) resume(value)
}
