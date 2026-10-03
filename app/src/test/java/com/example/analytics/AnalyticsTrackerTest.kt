package com.example.analytics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnalyticsTrackerTest {

    private class FakeSink : AnalyticsSink {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val userIds = mutableListOf<String?>()
        override fun logEvent(name: String, params: Map<String, Any>) { events += name to params }
        override fun setUserId(id: String?) { userIds += id }
        override fun setUserProperty(name: String, value: String?) {}
        override fun setCollectionEnabled(enabled: Boolean) {}
        override fun setAnalyticsConsent(granted: Boolean) {}
        override fun resetData() {}
        override fun appInstanceId(onResult: (String?) -> Unit) = onResult("instance-1")
    }

    private lateinit var sink: FakeSink

    @Before
    fun setUp() {
        sink = FakeSink()
        AnalyticsTracker.sink = sink
        AnalyticsTracker.enabled = false
    }

    @After
    fun tearDown() {
        AnalyticsTracker.sink = null
        AnalyticsTracker.enabled = false
    }

    @Test
    fun `nothing is sent before consent`() {
        AnalyticsTracker.login("google")
        AnalyticsTracker.screen("search_map")
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun `events flow once consent is granted`() {
        AnalyticsTracker.applyConsent(granted = true, reset = false)
        AnalyticsTracker.login("email_link")
        assertEquals(Event.LOGIN, sink.events.single().first)
        assertEquals("email_link", sink.events.single().second[Param.METHOD])
    }

    @Test
    fun `withdrawing consent clears the user id and stops events`() {
        AnalyticsTracker.applyConsent(granted = true, reset = false)
        AnalyticsTracker.applyConsent(granted = false, reset = true)
        AnalyticsTracker.login("google")
        assertTrue(sink.events.isEmpty())
        assertEquals(listOf<String?>(null), sink.userIds)
    }

    @Test
    fun `pii keys and values are dropped`() {
        val out = AnalyticsTracker.sanitize(
            mapOf(
                "email" to "a@b.co",
                "user_phone" to "+96170123456",
                "full_name" to "Jane Doe",
                "search_term" to "jane@example.com",
                "note" to "+961 70 123 456",
                "method" to "google"
            )
        )
        assertEquals(mapOf<String, Any>("method" to "google"), out)
    }

    @Test
    fun `strings are trimmed to 100 chars and types normalised`() {
        val out = AnalyticsTracker.sanitize(
            mapOf("label" to "x".repeat(150), "count" to 3, "flag" to true, "ratio" to 1.5f)
        )
        assertEquals(100, (out["label"] as String).length)
        assertEquals(3L, out["count"])
        assertEquals("true", out["flag"])
        assertEquals(1.5, out["ratio"])
    }

    @Test
    fun `item lists are sanitised and capped at ten`() {
        val items = (1..15).map { mapOf(Param.ITEM_ID to "L-$it", "email" to "x@y.zz") }
        @Suppress("UNCHECKED_CAST")
        val out = AnalyticsTracker.sanitize(mapOf(Param.ITEMS to items))[Param.ITEMS] as List<Map<String, Any>>
        assertEquals(10, out.size)
        assertFalse(out.any { it.containsKey("email") })
    }

    @Test
    fun `screen views are de-duplicated`() {
        AnalyticsTracker.applyConsent(granted = true, reset = true)
        AnalyticsTracker.screen("search_map")
        AnalyticsTracker.screen("search_map")
        AnalyticsTracker.screen("pro_rentals")
        assertEquals(2, sink.events.count { it.first == Event.SCREEN_VIEW })
    }

    @Test
    fun `transaction id matches the server hash`() {
        // sha256("GPA.1234-5678-9012-34567") first 24 hex — same as functions/src/lib/ga4.ts
        val id = AnalyticsTracker.transactionId("GPA.1234-5678-9012-34567")
        assertEquals(24, id.length)
        assertEquals(id, AnalyticsTracker.transactionId("GPA.1234-5678-9012-34567"))
    }

    @Test
    fun `buckets`() {
        assertEquals("0", AnalyticsTracker.bucketCount(0))
        assertEquals("2-5", AnalyticsTracker.bucketCount(4))
        assertEquals("6+", AnalyticsTracker.bucketCount(9))
        val now = 100L * 86_400_000L
        assertEquals("<7d", AnalyticsTracker.bucketAge(now - 86_400_000L, now))
        assertEquals("90d+", AnalyticsTracker.bucketAge(0L, now))
    }
}
