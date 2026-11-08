package com.example.ui.viewmodel

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import com.example.R
import com.example.hermeticRepository
import com.example.ui.preview.mockBooking1
import com.example.data.model.publicCode
import com.example.ui.preview.mockSpace1
import com.example.ui.util.AndroidPlatformActions
import com.example.ui.util.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * P2.2e safety net, written BEFORE the change against the current code and kept with identical assertions afterwards:
 * the four platform actions of ProHostViewModel (share export, share listing, copy link, WhatsApp to a specialist)
 * must start the same Intents (action, type, extras, chooser title, URL) from the Activity, and copy the same clip.
 * Only the arrangement (setUp registers the Activity with the PlatformActions seam) and the `act*` helpers changed when the actions moved behind it; every assertion is the same as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlatformIntentsTest {
    private lateinit var activity: Activity
    private lateinit var vm: ProHostViewModel

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        UiText.init(activity.resources, activity.resources)
        AndroidPlatformActions.init(activity.applicationContext)
        AndroidPlatformActions.attach(activity)
        vm = ProHostViewModel(hermeticRepository())
    }

    @After
    fun tearDown() = AndroidPlatformActions.detach(activity)

    // --- arrangement helpers (the only lines that change with the seam) ---
    private fun actShareExport(format: String, content: String) = vm.shareExportData(format, content)
    private fun actShareListing(title: String, content: String) = vm.shareListing(title, content)
    private fun actCopyLink(url: String) = vm.copyListingLink(url)
    private fun actWhatsApp(request: com.example.data.model.BookingRequest) = vm.launchWhatsAppToPractitioner(request)
    private fun actWhatsAppInquiry(space: com.example.data.model.SpaceListing) = vm.launchWhatsAppInquiry(space)

    private fun nextIntent(): Intent = shadowOf(activity).nextStartedActivity ?: error("no activity was started")

    private fun assertChooser(intent: Intent, expectedTitle: String, subject: String, text: String) {
        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        assertEquals(expectedTitle, intent.getStringExtra(Intent.EXTRA_TITLE) ?: intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())
        @Suppress("DEPRECATION")
        val inner = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: error("chooser has no inner intent")
        assertEquals(Intent.ACTION_SEND, inner.action)
        assertEquals("text/plain", inner.type)
        assertEquals(subject, inner.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(text, inner.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun shareExportDataOpensAChooserWithTheExportSubjectAndContent() {
        actShareExport("CSV", "a,b\n1,2")
        assertChooser(nextIntent(), activity.getString(R.string.export_prohost_data), "ProHost - CSV Export", "a,b\n1,2")
    }

    @Test
    fun shareListingOpensAChooserWithTheListingTitleAndLink() {
        actShareListing("Achrafieh Suite", "https://pro-host.tech/listing/D-ABC123")
        assertChooser(nextIntent(), activity.getString(R.string.share_listing), "Achrafieh Suite", "https://pro-host.tech/listing/D-ABC123")
    }

    @Test
    fun copyListingLinkPutsTheLinkOnTheClipboardAndStartsNoActivity() {
        actCopyLink("https://pro-host.tech/listing/D-ABC123")
        val clip = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        assertNotNull(clip)
        assertEquals("ProHost Listing Link", clip!!.description.label.toString())
        assertEquals("https://pro-host.tech/listing/D-ABC123", clip.getItemAt(0).text.toString())
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun whatsAppToPractitionerViewsAWaMeLinkWithAnEncodedMessage() {
        actWhatsApp(mockBooking1.copy(practitionerPhone = "+961 70 123 456"))
        val intent = nextIntent()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        val url = intent.data.toString()
        assertTrue(url, url.startsWith("https://wa.me/96170123456?text="))
        assertTrue("message is URL-encoded", !url.substringAfter("text=").contains(' '))
    }

    @Test
    fun whatsAppWithoutANumberStartsNothing() {
        actWhatsApp(mockBooking1.copy(practitionerPhone = ""))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun whatsAppInquiryToTheHostViewsAWaMeLinkWithTheListingInTheMessage() {
        val space = mockSpace1.copy(ownerPhone = "+961 70 123 456")
        actWhatsAppInquiry(space)
        val intent = nextIntent()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        val uri = intent.data!!
        assertEquals("https", uri.scheme)
        assertEquals("wa.me", uri.host)
        assertEquals("/96170123456", uri.path)
        val text = uri.getQueryParameter("text").orEmpty()
        assertTrue(text, text.contains("*Space:* ${space.title}"))
        assertTrue(text, text.contains(space.publicCode))
    }

    @Test
    fun whatsAppInquiryWithoutAHostNumberStartsNothing() {
        actWhatsAppInquiry(mockSpace1.copy(ownerPhone = ""))
        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
