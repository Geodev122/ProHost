package com.example.ui.util

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.example.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * P2.2c: the view models now read messages with `uiText(...)` instead of `context.getString(...)`. Both must produce the same text in
 * English and Arabic, with and without format arguments (a sample of the converted keys: plain, one argument, two arguments).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiTextParityTest {
    private var saved: android.content.res.Resources? = null

    @Before
    fun remember() {
        saved = UiText.resources
    }

    @After
    fun restore() {
        UiText.resources = saved
    }

    private fun localized(tag: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }

    private fun check(tag: String) {
        val ctx = localized(tag)
        UiText.init(ctx.resources)
        assertEquals(ctx.getString(R.string.link_copied_to_clipboard), uiText(R.string.link_copied_to_clipboard))
        assertEquals(ctx.getString(R.string.saved_update_failed), uiText(R.string.saved_update_failed))
        assertEquals(ctx.getString(R.string.payment_reminder_sent_to_x, "Sam"), uiText(R.string.payment_reminder_sent_to_x, "Sam"))
        assertEquals(
            ctx.getString(R.string.can_t_accept_x_it_overlaps_accepted_bookin, "R-1", "B-2"),
            uiText(R.string.can_t_accept_x_it_overlaps_accepted_bookin, "R-1", "B-2")
        )
        assertTrue(uiText(R.string.link_copied_to_clipboard).isNotBlank())
    }

    @Test fun englishMatchesContextGetString() = check("en")

    @Test fun arabicMatchesContextGetString() = check("ar")

    @Test
    fun theTwoLanguagesReallyDiffer() {
        UiText.init(localized("en").resources)
        val en = uiText(R.string.link_copied_to_clipboard)
        UiText.init(localized("ar").resources)
        assertNotEquals(en, uiText(R.string.link_copied_to_clipboard))
    }
}
