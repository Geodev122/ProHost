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

    /** The Toast shown when no Snackbar host is on screen. Registered once by [init]; the only Android-specific part of this object. */
    @Volatile
    private var toastFallback: ((CharSequence, Boolean) -> Unit)? = null

    /** Called from `ProHostApplication.onCreate`: the fallback Toast uses the application context, as it always did. */
    fun init(applicationContext: Context) {
        val app = applicationContext.applicationContext
        toastFallback = { text, long -> toast(app, text, long) }
    }

    /** Test hook: replaces the Toast fallback (no Android needed). */
    internal fun setToastFallback(fallback: ((CharSequence, Boolean) -> Unit)?) {
        toastFallback = fallback
    }

    /** Context-free: view models and shared code call this. */
    fun show(text: CharSequence?, long: Boolean = false, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        deliver(text, long, actionLabel, onAction) { t, l -> toastFallback?.invoke(t, l) }
    }

    /** Existing call sites; same behaviour as before (the Toast fallback is shown on [context]'s application context). */
    fun show(context: Context, text: CharSequence?, long: Boolean = false, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        deliver(text, long, actionLabel, onAction) { t, l -> toast(context.applicationContext, t, l) }
    }

    private inline fun deliver(text: CharSequence?, long: Boolean, actionLabel: String?, noinline onAction: (() -> Unit)?, fallback: (CharSequence, Boolean) -> Unit) {
        if (text.isNullOrBlank()) return
        val message = Message(text.toString(), long, actionLabel, onAction)
        if (hosts > 0 && _messages.tryEmit(message)) return
        fallback(text, long)
    }

    private fun toast(context: Context, text: CharSequence, long: Boolean) {
        Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
