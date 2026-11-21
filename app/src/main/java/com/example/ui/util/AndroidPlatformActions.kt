package com.example.ui.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.lang.ref.WeakReference

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

    override fun openUrl(url: String, newTask: Boolean) =
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })

    override fun openUrlFromScreen(url: String): Boolean {
        val current = activity?.get() ?: return false
        return try {
            current.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun openFile(file: com.example.util.PickedFile, mimeType: String) {
        val ctx = context()
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(file.handle))
        start(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    override fun isNoHandler(e: Throwable): Boolean = e is android.content.ActivityNotFoundException

    override fun sendMail(mailtoUri: String) = start(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse(mailtoUri) })

    override fun openAppSettings() = start(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context().packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    override fun openAppLanguageSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 33) {
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS).setData(Uri.fromParts("package", context().packageName, null))
        } else {
            Intent(Settings.ACTION_LOCALE_SETTINGS)
        }
        start(intent)
    }

    override fun addCalendarEvent(
        chooserTitle: String, title: String, description: String,
        allDayStartUtcMillis: Long, allDayEndUtcMillis: Long, rrule: String?
    ) {
        val insert = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, allDayStartUtcMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, allDayEndUtcMillis)
            .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
            .putExtra(CalendarContract.Events.HAS_ALARM, 1)
        if (rrule != null) insert.putExtra(CalendarContract.Events.RRULE, rrule)
        // A chooser lets people with several calendar apps pick the one they use (start() adds NEW_TASK when there is no Activity).
        start(Intent.createChooser(insert, chooserTitle))
    }

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
