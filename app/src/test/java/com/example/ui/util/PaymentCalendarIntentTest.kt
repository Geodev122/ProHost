package com.example.ui.util

import android.app.Activity
import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import com.example.data.model.RentalFormulaType
import com.example.ui.preview.mockBooking1
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** P2.5c: PaymentCalendar now hands its event to PlatformActions.addCalendarEvent; the event content (dates, repeat rule) is unchanged. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaymentCalendarIntentTest {
    private lateinit var activity: Activity
    private var savedResources: android.content.res.Resources? = null

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        savedResources = UiText.resources
        UiText.init(activity.resources, activity.resources)
        AndroidPlatformActions.init(activity.applicationContext)
        AndroidPlatformActions.attach(activity)
    }

    @After
    fun tearDown() {
        AndroidPlatformActions.detach(activity)
        UiText.resources = savedResources
    }

    @Suppress("DEPRECATION")
    private fun insertIntent(): Intent =
        shadowOf(activity).nextStartedActivity!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!

    @Test
    fun aMonthlyLeaseRepeatsMonthlyFromUtcMidnightOfTheStartDate() {
        val booking = mockBooking1.copy(
            startDate = "2026-10-15", durationMonths = 3, totalAmountUsd = 900.0,
            formula = mockBooking1.formula.copy(type = RentalFormulaType.FULL_MONTH)
        )
        PaymentCalendar.addPaymentReminders(booking, "B-123456")
        val insert = insertIntent()
        assertEquals(1_792_022_400_000L, insert.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0)) // 2026-10-15T00:00Z
        assertEquals(1_792_022_400_000L + 86_400_000L, insert.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))
        assertTrue(insert.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        assertEquals("FREQ=MONTHLY;COUNT=3", insert.getStringExtra(CalendarContract.Events.RRULE))
        assertTrue(insert.getStringExtra(CalendarContract.Events.DESCRIPTION).orEmpty().contains("B-123456"))
    }

    @Test
    fun aSinglePaymentHasNoRepeatRule() {
        val booking = mockBooking1.copy(startDate = "2026-10-15", durationMonths = 1, totalAmountUsd = 300.0)
        PaymentCalendar.addPaymentReminders(booking, "B-123456")
        assertFalse(insertIntent().hasExtra(CalendarContract.Events.RRULE))
    }

    @Test
    fun noStartDateStartsNothing() {
        PaymentCalendar.addPaymentReminders(mockBooking1.copy(startDate = ""), "B-1")
        assertEquals(null, shadowOf(activity).nextStartedActivity)
    }
}
