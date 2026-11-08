package com.example.ui.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * P2.5c: the intents AndroidPlatformActions starts equal the Intents the screens used to build themselves (action, data, flags, extras),
 * started from the attached Activity; without an Activity the application context is used with FLAG_ACTIVITY_NEW_TASK.
 * The "old" intents below are copied from the call sites they replaced (legal PDF link, consent dialog privacy link, agreement and WhatsApp links,
 * support email, permission settings, app-language settings).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidPlatformActionsTest {
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        com.example.util.FileProviderTestSupport.reset()
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        AndroidPlatformActions.init(ApplicationProvider.getApplicationContext())
        AndroidPlatformActions.attach(activity)
    }

    @After
    fun tearDown() = AndroidPlatformActions.detach(activity)

    private fun started(): Intent = shadowOf(activity).nextStartedActivity ?: error("no activity was started")

    @Test
    fun openUrlIsAViewIntentWithoutFlags() {
        AndroidPlatformActions.openUrl("https://wa.me/96170123456?text=Hello%20there")
        val old = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/96170123456?text=Hello%20there"))
        val got = started()
        assertTrue(old.filterEquals(got))
        assertEquals(0, got.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun openUrlWithNewTaskAddsTheFlagLikeTheConsentDialogDid() {
        AndroidPlatformActions.openUrl("https://pro-host.tech/privacy", newTask = true)
        val old = Intent(Intent.ACTION_VIEW, Uri.parse("https://pro-host.tech/privacy")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val got = started()
        assertTrue(old.filterEquals(got))
        assertEquals(old.flags, got.flags)
    }

    @Test
    fun sendMailIsASendtoIntentOnTheMailtoUri() {
        val mailto = "mailto:admin@pro-host.tech?subject=ProHost%20Support&body=Account%3A%20X"
        AndroidPlatformActions.sendMail(mailto)
        val old = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse(mailto) }
        val got = started()
        assertTrue(old.filterEquals(got))
        assertEquals(Intent.ACTION_SENDTO, got.action)
        assertNotNull(got.data)
    }

    @Test
    fun openAppSettingsIsTheDetailsScreenOfThisPackageInItsOwnTask() {
        AndroidPlatformActions.openAppSettings()
        val old = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val got = started()
        assertTrue(old.filterEquals(got))
        assertEquals(old.flags, got.flags)
    }

    @Test
    fun appLanguageSettingsOnApi34IsThePerAppLocaleScreen() {
        AndroidPlatformActions.openAppLanguageSettings()
        val old = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).setData(Uri.fromParts("package", activity.packageName, null))
        assertTrue(old.filterEquals(started()))
    }

    @Test
    @Config(sdk = [30])
    fun appLanguageSettingsBelowApi33IsTheDeviceLocaleScreen() {
        AndroidPlatformActions.openAppLanguageSettings()
        assertTrue(Intent(Settings.ACTION_LOCALE_SETTINGS).filterEquals(started()))
    }

    @Test
    fun withoutAnAttachedActivityTheApplicationContextIsUsedWithNewTask() {
        AndroidPlatformActions.detach(activity)
        AndroidPlatformActions.openUrl("https://pro-host.tech/")
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val got = shadowOf(app).nextStartedActivity ?: error("not started from the application context")
        assertTrue(Intent(Intent.ACTION_VIEW, Uri.parse("https://pro-host.tech/")).filterEquals(got))
        assertTrue(got.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertFalse(shadowOf(activity).nextStartedActivity != null)
    }

    @Test
    fun addCalendarEventIsTheOldInsertIntentInsideAChooser() {
        AndroidPlatformActions.addCalendarEvent("Add reminders to", "Rent due: Suite", "Line 1\nLine 2", 1_790_000_000_000L, 1_790_086_400_000L, "FREQ=MONTHLY;COUNT=3")
        val chooser = started()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Add reminders to", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())
        @Suppress("DEPRECATION")
        val inner = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: error("no inner intent")
        // the old call site, verbatim
        val old = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "Rent due: Suite")
            .putExtra(CalendarContract.Events.DESCRIPTION, "Line 1\nLine 2")
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 1_790_000_000_000L)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, 1_790_086_400_000L)
            .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
            .putExtra(CalendarContract.Events.HAS_ALARM, 1)
            .putExtra(CalendarContract.Events.RRULE, "FREQ=MONTHLY;COUNT=3")
        assertTrue(old.filterEquals(inner))
        for (key in old.extras!!.keySet()) assertEquals(key, old.extras!!.get(key), inner.extras!!.get(key))
        assertEquals(old.extras!!.keySet(), inner.extras!!.keySet())
        assertEquals(0, chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK) // started from the Activity: no NEW_TASK
    }

    @Test
    fun addCalendarEventWithoutARuleHasNoRrule() {
        AndroidPlatformActions.addCalendarEvent("t", "title", "d", 1L, 2L, null)
        @Suppress("DEPRECATION")
        val inner = started().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertFalse(inner.hasExtra(CalendarContract.Events.RRULE))
    }

    @Test
    fun openFileIsAViewIntentOnTheFileProviderUriWithReadGrantInItsOwnTask() {
        val dir = java.io.File(activity.getExternalFilesDir("documents"), "").apply { mkdirs() }
        val pdf = java.io.File(dir, "ProHost-terms.pdf").apply { writeText("%PDF-test") }
        AndroidPlatformActions.openFile(com.example.util.PickedFile(pdf.absolutePath), "application/pdf")
        val got = started()
        // the old LegalPdfGenerator.buildOpenIntent, verbatim
        val oldUri = androidx.core.content.FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", pdf)
        val old = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(oldUri, "application/pdf")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        assertTrue(old.filterEquals(got))
        assertEquals(old.flags, got.flags)
        assertEquals("${activity.packageName}.fileprovider", got.data?.authority)
    }
}
