package com.example.ui.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.lang.ref.WeakReference

/**
 * Things only the operating system can do, as the app's view models and shared code need them: open a link, show the share sheet,
 * copy text. One interface, one implementation per platform (Android below; iOS later), so no view model holds a `Context`.
 * Failures (no app can handle the link, ...) are thrown, exactly as `Context.startActivity` threw before, so callers keep their own
 * try/catch fallbacks.
 */
interface PlatformActions {
    /** Opens [url] in whatever app handles it (ACTION_VIEW), e.g. a wa.me WhatsApp link. */
    fun openUrl(url: String)

    /** Shows the system share sheet titled [chooserTitle] for plain [text] with an optional [subject]. */
    fun shareText(chooserTitle: String, subject: String, text: String)

    /** Puts [text] on the clipboard under the user-visible [label]. */
    fun copyText(label: String, text: String)

    companion object {
        /** Registered once by `ProHostApplication`; tests set their own. */
        @Volatile
        var current: PlatformActions? = null

        fun require(): PlatformActions = current ?: error("PlatformActions is not initialised (ProHostApplication.onCreate)")
    }
}

/**
 * Android implementation. Intents are started from the **current Activity** (the one `MainActivity` attaches), which is what happened when the
 * screens passed `LocalContext.current` (chooser, task and transition behaviour unchanged); only if no Activity is attached (never in normal use)
 * does it fall back to the application context with FLAG_ACTIVITY_NEW_TASK. The Activity is held weakly so a rotation or finish never leaks it.
 */
object AndroidPlatformActions : PlatformActions {
    private var app: Context? = null
    private var activity: WeakReference<Activity>? = null

    fun init(application: Context) {
        app = application.applicationContext
        PlatformActions.current = this
    }

    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    /** Detaches only if [activity] is still the attached one (a new instance may already have attached during a rotation). */
    fun detach(activity: Activity) {
        if (this.activity?.get() === activity) this.activity = null
    }

    private fun context(): Context = activity?.get() ?: app ?: error("AndroidPlatformActions is not initialised")

    private fun start(intent: Intent) {
        val current = activity?.get()
        if (current != null) {
            current.startActivity(intent)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            checkNotNull(app) { "AndroidPlatformActions is not initialised" }.startActivity(intent)
        }
    }

    override fun openUrl(url: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    override fun shareText(chooserTitle: String, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        start(Intent.createChooser(intent, chooserTitle))
    }

    override fun copyText(label: String, text: String) {
        val clipboard = context().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
