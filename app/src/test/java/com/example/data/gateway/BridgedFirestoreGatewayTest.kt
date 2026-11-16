package com.example.data.gateway

import com.example.data.model.BookingRequestStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared (iOS) Firestore gateway must read and write exactly like the Android `FirestoreService`:
 * same collection paths, filters, ordering, limits, merge flags and written fields.
 */
class BridgedFirestoreGatewayTest {

    private class Rec : FirestoreBridge {
        val log = mutableListOf<String>()
        val writes = mutableListOf<Triple<String, String, Map<String, Any?>>>()
        var docs: Map<String, DocSnapshot> = emptyMap()
        var failWrites = false
        override fun listenQuery(
            collectionPath: String, filters: List<QueryFilter>, orderByField: String?, descending: Boolean, limit: Int,
            onSnapshot: (List<DocSnapshot>) -> Unit, onError: (String) -> Unit
        ): ListenerHandle {
            val f = filters.joinToString(",") { "${it.field}${it.op}${if (it.field == "expireAt") "now" else it.value}" }
            log += "Q $collectionPath [$f] ${orderByField ?: "-"}${if (descending) " desc" else ""} $limit"
            return ListenerHandle { log += "remove $collectionPath" }
        }
        override fun listenDocument(docPath: String, onSnapshot: (DocSnapshot?) -> Unit, onError: (String) -> Unit): ListenerHandle {
            log += "D $docPath"
            return ListenerHandle { }
        }
        override fun queryOnce(
            collectionPath: String, filters: List<QueryFilter>, orderByField: String?, descending: Boolean, limit: Int,
            completion: (List<DocSnapshot>?, String?) -> Unit
        ) {
            log += "once $collectionPath ${orderByField}${if (descending) " desc" else ""} $limit"
            completion(listOf(DocSnapshot("t1", mapOf("tag" to "yoga", "count" to 3L, "governorate" to "BEIRUT", "lastUsedAtMillis" to 9L))), null)
        }
        override fun getDocument(docPath: String, completion: (DocSnapshot?, String?) -> Unit) = completion(docs[docPath], null)
        override fun setDocument(docPath: String, data: Map<String, Any?>, merge: Boolean, completion: (String?) -> Unit) {
            writes += Triple(if (merge) "merge" else "set", docPath, data); completion(if (failWrites) "denied" else null)
        }
        override fun updateDocument(docPath: String, data: Map<String, Any?>, completion: (String?) -> Unit) {
            writes += Triple("update", docPath, data); completion(if (failWrites) "denied" else null)
        }
        override fun deleteDocument(docPath: String, completion: (String?) -> Unit) {
            writes += Triple("delete", docPath, emptyMap()); completion(null)
        }
        override fun batchUpdate(docPaths: List<String>, data: Map<String, Any?>, completion: (String?) -> Unit) {
            docPaths.forEach { writes += Triple("batch", it, data) }; completion(null)
        }
    }

    @Test fun liveListenersUseTheAndroidQueries() {
        val r = Rec()
        BridgedFirestoreGateway(r).attachLiveListeners(
            currentUid = "u1", isAdminCaller = true,
            onWorkspacesUpdated = {}, onUsersUpdated = {}, onBookingsUpdated = {}, publicListingsLimit = 100
        )
        assertEquals(
            listOf(
                "Q workspace_listings [status==ACTIVE,isOwnerSuspended==false,isOwnerPackageLapsed==false] - 100",
                "Q workspace_listings [ownerId==u1] - 0",
                "D user_profiles/u1",
                "Q user_profiles/u1/notifications [expireAt>now] expireAt desc 100",
                "Q booking_requests [ownerId==u1] - 0",
                "Q booking_requests [practitionerId==u1] - 0",
                "D schema_architecture/main",
                "Q audit_security_logs [] timestamp desc 500",
                "Q workspace_listings [isVerified==false,verificationRequestedAt>0] - 50"
            ),
            r.log
        )
    }

    @Test fun signedOutAttachesOnlyTheSchema() {
        val r = Rec()
        BridgedFirestoreGateway(r).attachLiveListeners(null, false, {}, {}, {})
        assertEquals(listOf("D schema_architecture/main"), r.log)
    }

    @Test fun writesMatchAndroid() = runBlocking {
        val r = Rec()
        val g = BridgedFirestoreGateway(r)
        assertTrue(g.incrementSpaceCounter("s1", "viewCount"))
        assertTrue(g.setSavedSpace("u1", "s1", saved = true))
        assertTrue(g.setSavedSpace("u1", "s1", saved = false))
        assertTrue(g.updateBookingStatus("b1", BookingRequestStatus.REJECTED, "busy"))
        assertTrue(g.recordHashtagUsage(listOf(" Yoga ", ""), "BEIRUT"))
        assertTrue(g.markNotificationsRead("u1", listOf("n1", "n2")))
        assertTrue(g.deleteWorkspace("s1"))
        assertTrue(g.saveFcmToken("u1", "tok"))
        val w = r.writes
        assertEquals(Triple("update", "workspace_listings/s1", mapOf<String, Any?>("viewCount" to FieldOp.Increment(1))), w[0])
        assertEquals("update", w[1].first); assertEquals(FieldOp.ArrayUnion(listOf("s1")), w[1].third["savedSpaceIds"]); assertTrue(w[1].third["updatedAt"] is Long)
        assertEquals(FieldOp.ArrayRemove(listOf("s1")), w[2].third["savedSpaceIds"])
        assertEquals("merge" to "booking_requests/b1", w[3].first to w[3].second)
        assertEquals(setOf("status", "reviewedAt", "rejectionReason"), w[3].third.keys); assertEquals("REJECTED", w[3].third["status"])
        assertEquals("merge" to "hashtag_usage/yoga", w[4].first to w[4].second)
        assertEquals(setOf("tag", "count", "lastUsedAtMillis", "governorate"), w[4].third.keys); assertEquals(FieldOp.Increment(1), w[4].third["count"])
        assertEquals(listOf("user_profiles/u1/notifications/n1", "user_profiles/u1/notifications/n2"), w.subList(5, 7).map { it.second })
        assertEquals(setOf("read", "readAt"), w[5].third.keys)
        assertEquals(Triple("delete", "workspace_listings/s1", emptyMap<String, Any?>()), w[7])
        assertEquals("merge" to "user_profiles/u1", w[8].first to w[8].second); assertEquals(setOf("fcmToken", "updatedAt"), w[8].third.keys)
    }

    @Test fun readsAndFailuresMatchAndroid() = runBlocking {
        val r = Rec()
        r.docs = mapOf(
            "workspace_listings/s1" to DocSnapshot("s1", mapOf("ownerId" to "o1", "title" to "Room")),
            "workspace_listings/ghost" to DocSnapshot("ghost", mapOf("favoriteCount" to 2L))
        )
        val g = BridgedFirestoreGateway(r)
        assertEquals("s1", g.fetchListing("s1")?.id)
        assertNull(g.fetchListing("ghost"))           // ownerless ghost docs are ignored, as on Android
        assertNull(g.fetchListing(""))
        assertEquals(listOf("yoga"), g.getTopHashtags(30))
        assertEquals(3, g.getHashtagAnalytics(50).single().count)
        assertTrue(r.log.contains("once hashtag_usage count desc 30"))
        r.failWrites = true
        assertFalse(g.saveFcmToken("u1", "t"))
    }
}
