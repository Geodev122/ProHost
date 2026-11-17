package com.example.data.gateway

import com.example.data.auth.AccountSuspendedException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared (iOS) callable gateway must send exactly what the Android client sends
 * (`FirebaseFunctionsClient`): same callable names, payload maps and timeouts, and read results the same way.
 */
class CallableFunctionsGatewayTest {

    private class RecordingTransport(private val reply: CallableReply = CallableReply(emptyMap(), null, null)) : CallableTransport {
        val calls = mutableListOf<Triple<String, Map<String, Any?>?, Int>>()
        override fun call(name: String, data: Map<String, Any?>?, timeoutSeconds: Int, completion: (CallableReply) -> Unit) {
            calls += Triple(name, data, timeoutSeconds)
            completion(reply)
        }
    }

    private fun gw(t: CallableTransport) = CallableFunctionsGateway(t)

    @Test fun payloadsMatchTheAndroidClient() = runBlocking {
        val t = RecordingTransport(CallableReply(mapOf("role" to "SPECIALIST", "expiryMillis" to 5L, "affected" to 2), null, null))
        val g = gw(t)
        g.ensureInitialRole()
        g.ensureInitialRole(mapOf("fullName" to "A"), "tok")
        g.recordAuditLog("LOGIN", "d", "INFO")
        g.verifyAndRestorePurchase("pt", "package_pro_mrr", true)
        g.sendPaymentReminder("b1")
        g.changeSlotPrice("s1", "room", "HOURLY", "Mon|9", 25.0, mapOf("b1" to "NOW"))
        g.requestListingVerification("s1")
        g.updatePricing(mapOf("governanceTag" to "t"))
        g.setListingVerification("s1", true)
        g.setListingSubscriptionActive("s1", false)
        g.reportContent(mapOf("reason" to "SCAM"))
        g.blockUser(null, "s1")
        g.unblockUser("u2")
        g.deleteOwnAccount()
        g.sendPasswordResetLink("a@b.c")
        g.sendVerificationEmailLink()
        assertEquals(
            listOf(
                Triple("assignInitialRole", null, 0),
                Triple("assignInitialRole", mapOf("registration" to mapOf("fullName" to "A"), "integrityToken" to "tok"), 0),
                Triple("recordClientAuditLog", mapOf("actionType" to "LOGIN", "details" to "d", "severity" to "INFO"), 0),
                Triple("verifyAndRestorePurchase", mapOf("purchaseToken" to "pt", "productId" to "package_pro_mrr", "fromCheckout" to true), 0),
                Triple("sendPaymentReminder", mapOf("bookingId" to "b1"), 0),
                Triple(
                    "changeSlotPrice",
                    mapOf(
                        "spaceId" to "s1",
                        "ref" to mapOf("scopeId" to "room", "kind" to "HOURLY", "key" to "Mon|9"),
                        "newPrice" to 25.0,
                        "decisions" to mapOf("b1" to "NOW")
                    ),
                    0
                ),
                Triple("requestListingVerification", mapOf("spaceId" to "s1"), 0),
                Triple("updatePricing", mapOf("governanceTag" to "t"), 0),
                Triple("setListingVerification", mapOf("spaceId" to "s1", "verified" to true), 0),
                Triple("setListingSubscriptionActive", mapOf("spaceId" to "s1", "active" to false), 0),
                Triple("reportContent", mapOf("reason" to "SCAM"), 0),
                Triple("blockUser", mapOf("listingId" to "s1"), 0),
                Triple("unblockUser", mapOf("targetUid" to "u2"), 0),
                Triple("deleteOwnAccount", null, 180),
                Triple("sendPasswordResetLink", mapOf("email" to "a@b.c"), 0),
                Triple("sendVerificationEmailLink", null, 0)
            ),
            t.calls
        )
    }

    @Test fun resultsAreReadLikeAndroid() = runBlocking {
        val g = gw(RecordingTransport(CallableReply(mapOf("role" to "PRO_HOST", "expiryMillis" to 1234, "affected" to 3, "now" to 1, "nextTerm" to 1, "keep" to 1), null, null)))
        assertEquals("PRO_HOST", g.ensureInitialRole().getOrNull())
        assertEquals(1234L, g.verifyAndRestorePurchase("p", "x").getOrNull())
        assertEquals(SlotPriceChangeResult(3, 1, 1, 1), g.changeSlotPrice("s", "r", "k", "y", 1.0, emptyMap()).getOrNull())
        assertTrue(gw(RecordingTransport()).ensureInitialRole().exceptionOrNull() is IllegalStateException)
    }

    @Test fun suspensionBecomesAccountSuspendedException() = runBlocking {
        val g = gw(RecordingTransport(CallableReply(null, "PERMISSION_DENIED", "This account is suspended.")))
        assertTrue(g.ensureInitialRole().exceptionOrNull() is AccountSuspendedException)
        val other = gw(RecordingTransport(CallableReply(null, "UNAVAILABLE", "down"))).sendPaymentReminder("b").exceptionOrNull()
        assertTrue(other is CallableException && other.code == "UNAVAILABLE")
    }
}
