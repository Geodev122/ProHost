package com.example.ui.util

import kotlin.concurrent.Volatile

/**
 * Things only the operating system can do, as the app's view models and shared code need them: open a link, show the share sheet,
 * copy text. One interface, one implementation per platform (Android: `AndroidPlatformActions` in the app; iOS: `IosPlatformActions` in iosMain), so no view model holds a `Context`.
 * Failures (no app can handle the link, ...) are thrown, exactly as `Context.startActivity` threw before, so callers keep their own
 * try/catch fallbacks.
 */
interface PlatformActions {
    /** Opens [url] in whatever app handles it (ACTION_VIEW), e.g. a wa.me WhatsApp link. [newTask] starts it in its own task (the consent dialog's privacy link). */
    fun openUrl(url: String, newTask: Boolean = false)

    /**
     * Opens [url] from the current screen's Activity only (no new-task fallback); false when there is no such screen or no app
     * handles it, so the caller can fall back (Pro Host renewal: Play's subscription centre, else the Premium page).
     */
    fun openUrlFromScreen(url: String): Boolean = false

    /** Opens the email app on a `mailto:` URI (ACTION_SENDTO, so only email apps answer). */
    fun sendMail(mailtoUri: String)

    /** True when [e] means no app on this device can handle the request (Android: `ActivityNotFoundException`). */
    fun isNoHandler(e: Throwable): Boolean = false

    /** Opens a file this app wrote (e.g. a generated PDF) in the viewer for [mimeType], granting it read access (Android: FileProvider URI, own task). */
    fun openFile(file: com.example.util.PickedFile, mimeType: String)

    /** Opens this app's page in the system settings (permissions), in its own task. */
    fun openAppSettings()

    /** Opens the system's per-app language settings (API 33+) or the device language settings. */
    fun openAppLanguageSettings()

    /**
     * Hands an all-day event to the person's own calendar app through a chooser titled [chooserTitle] (no calendar permission needed). The event
     * starts at UTC midnight [allDayStartUtcMillis] (CalendarContract requirement) and ends at [allDayEndUtcMillis]; [rrule] repeats it
     * (e.g. `FREQ=MONTHLY;COUNT=3`). Throws `ActivityNotFoundException` when no calendar app can answer, as before.
     */
    fun addCalendarEvent(
        chooserTitle: String, title: String, description: String,
        allDayStartUtcMillis: Long, allDayEndUtcMillis: Long, rrule: String?
    )

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
