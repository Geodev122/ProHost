package com.example.ui.util

import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The app's one feedback channel for "something happened" messages. The shell shows them as a Snackbar above the
 * bottom nav (with an optional action such as Undo); when no shell is on screen (sign-in, splash) they fall back to
 * the platform's fallback (Android: a Toast registered by `AndroidUiMessages.init`; iOS: none), so a message is never lost on Android.
 */
object UiMessages {
    data class Message(
        val text: String,
        val long: Boolean = false,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null
    )

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 8)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    /** Number of Snackbar hosts currently on screen (see ProHostSnackbarHost). */
    @Volatile
    var hosts = 0

    /** Shown when no Snackbar host is on screen. Registered once per platform. */
    @Volatile
    private var toastFallback: ((CharSequence, Boolean) -> Unit)? = null

    /** Registers (or, with null, clears) the fallback for messages shown while no Snackbar host is on screen; tests use it too. */
    fun setToastFallback(fallback: ((CharSequence, Boolean) -> Unit)?) {
        toastFallback = fallback
    }

    /** Context-free: view models, screens and shared code all call this. */
    fun show(text: CharSequence?, long: Boolean = false, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        if (text.isNullOrBlank()) return
        val message = Message(text.toString(), long, actionLabel, onAction)
        if (hosts > 0 && _messages.tryEmit(message)) return
        toastFallback?.invoke(text, long)
    }
}
