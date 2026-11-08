package com.example.ui.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
}
