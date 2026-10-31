package com.example.ui.util

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The app's one feedback channel for "something happened" messages. The shell shows them as a Snackbar above the
 * bottom nav (with an optional action such as Undo); when no shell is on screen (sign-in, splash) they fall back to
 * a Toast, so a message is never lost.
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
    internal var hosts = 0

    fun show(context: Context, text: CharSequence?, long: Boolean = false, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        if (text.isNullOrBlank()) return
        val message = Message(text.toString(), long, actionLabel, onAction)
        if (hosts > 0 && _messages.tryEmit(message)) return
        Toast.makeText(context.applicationContext, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
