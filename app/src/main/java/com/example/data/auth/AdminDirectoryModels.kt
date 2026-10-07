package com.example.data.auth

import com.example.data.model.AppUser
import com.example.data.model.BookingRequest
import com.example.data.model.RentalBookingRequest
import com.example.data.model.SpaceListing

// Parsed responses of functions/src/admin/adminDirectory.ts. Documents arrive as plain JSON
// (timestamps already millis) and go through the same fromFirestoreMap parsers as live reads.

private fun Map<String, Any?>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: 0
private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()
private fun Map<String, Any?>.str(key: String): String = this[key] as? String ?: ""

@Suppress("UNCHECKED_CAST")
private fun Any?.mapList(): List<Map<String, Any?>> =
    (this as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()

private fun Map<String, Any?>.docId(): String = this["id"] as? String ?: ""

data class AdminCounts(
    val totalUsers: Int = 0,
    val specialists: Int = 0,
    val proHosts: Int = 0,
    val admins: Int = 0,
    val newUsers30d: Int = 0,
    val totalListings: Int = 0,
    val activeListings: Int = 0,
    val draftListings: Int = 0,
    val pendingReview: Int = 0,
    val totalBookings: Int = 0,
    val pendingBookings: Int = 0,
    val acceptedBookings: Int = 0,
    val demoUsers: Int = 0,
    val demoListings: Int = 0,
    val demoBookings: Int = 0
) {
    companion object {
        fun fromMap(m: Map<String, Any?>) = AdminCounts(
            totalUsers = m.int("totalUsers"), specialists = m.int("specialists"), proHosts = m.int("proHosts"),
            admins = m.int("admins"), newUsers30d = m.int("newUsers30d"),
            totalListings = m.int("totalListings"), activeListings = m.int("activeListings"),
            draftListings = m.int("draftListings"), pendingReview = m.int("pendingReview"),
            totalBookings = m.int("totalBookings"), pendingBookings = m.int("pendingBookings"),
            acceptedBookings = m.int("acceptedBookings"),
            demoUsers = m.int("demoUsers"), demoListings = m.int("demoListings"), demoBookings = m.int("demoBookings")
        )
    }
}

data class AdminSearchResult(
    val users: List<AppUser> = emptyList(),
    val listings: List<SpaceListing> = emptyList(),
    val bookings: List<RentalBookingRequest> = emptyList()
) {
    val isEmpty: Boolean get() = users.isEmpty() && listings.isEmpty() && bookings.isEmpty()

    companion object {
        fun fromMap(m: Map<String, Any?>) = AdminSearchResult(
            users = m["users"].mapList().map { AppUser.fromFirestoreMap(it.docId(), it) },
            listings = m["listings"].mapList().map { SpaceListing.fromFirestoreMap(it.docId(), it) },
            bookings = m["bookings"].mapList().map { BookingRequest.fromFirestoreMap(it.docId(), it) }
        )
    }
}

data class DossierRoom(val id: String, val name: String, val displayCode: String)

data class DossierListing(
    val id: String,
    val displayCode: String,
    val title: String,
    val status: String,
    val isVerified: Boolean,
    val rooms: List<DossierRoom>
)

/** One Play subscription record. Amounts live in Play Console (looked up by [orderId]). */
data class DossierSubscription(
    val basePlanId: String,
    val status: String,
    val orderId: String,
    val startDate: Long?,
    val expiryDate: Long?,
    val autoRenewing: Boolean
)

data class AdminUserDossier(
    val profile: AppUser,
    val authEmail: String?,
    val authEmailVerified: Boolean,
    val authPhone: String?,
    val providers: List<String>,
    val authDisabled: Boolean,
    val authMissing: Boolean,
    val listings: List<DossierListing>,
    val subscriptions: List<DossierSubscription>,
    val rentals: List<RentalBookingRequest>,
    val hostBookingsCount: Int
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(m: Map<String, Any?>): AdminUserDossier? {
            val profileMap = m["profile"] as? Map<String, Any?> ?: return null
            val auth = m["auth"] as? Map<String, Any?> ?: emptyMap()
            return AdminUserDossier(
                profile = AppUser.fromFirestoreMap(profileMap.docId(), profileMap),
                authEmail = auth["email"] as? String,
                authEmailVerified = auth["emailVerified"] as? Boolean ?: false,
                authPhone = auth["phoneNumber"] as? String,
                providers = (auth["providers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                authDisabled = auth["disabled"] as? Boolean ?: false,
                authMissing = auth["missing"] as? Boolean ?: false,
                listings = m["listings"].mapList().map { l ->
                    DossierListing(
                        id = l.str("id"), displayCode = l.str("displayCode"), title = l.str("title"),
                        status = l.str("status"), isVerified = l["isVerified"] as? Boolean ?: false,
                        rooms = l["rooms"].mapList().map { r -> DossierRoom(r.str("id"), r.str("name"), r.str("displayCode")) }
                    )
                },
                subscriptions = m["subscriptions"].mapList().map { s ->
                    DossierSubscription(
                        basePlanId = s.str("basePlanId"), status = s.str("status"), orderId = s.str("orderId"),
                        startDate = s.long("startDate"), expiryDate = s.long("expiryDate"),
                        autoRenewing = s["autoRenewing"] as? Boolean ?: false
                    )
                },
                rentals = m["rentals"].mapList().map { BookingRequest.fromFirestoreMap(it.docId(), it) },
                hostBookingsCount = m.int("hostBookingsCount")
            )
        }
    }
}

/** One page of the user export; [rows] keep the server's column names. */
data class AdminExportPage(val rows: List<Map<String, Any?>>, val nextCursor: String?) {
    companion object {
        fun fromMap(m: Map<String, Any?>) = AdminExportPage(m["rows"].mapList(), m["nextCursor"] as? String)
    }

}

/** CSV for the admin user directory export (header + one row per user). */
object AdminUsersCsv {
    val COLUMNS = listOf(
        "uid", "displayCode", "fullName", "email", "phone", "role", "country", "city",
        "packageId", "packageExpiryMillis", "billingStatus", "entitlementSource", "listingCodes", "createdAtMillis"
    )

    private fun cell(value: Any?): String {
        val text = when (value) {
            null -> ""
            is List<*> -> value.joinToString(" ")
            is Number -> value.toLong().toString()
            else -> value.toString()
        }
        return if (text.any { it == ',' || it == '"' || it == '\n' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    fun build(rows: List<Map<String, Any?>>): String = buildString {
        appendLine(COLUMNS.joinToString(","))
        rows.forEach { row -> appendLine(COLUMNS.joinToString(",") { cell(row[it]) }) }
    }
}

/** billingHealthCheck: can the server use the Play Developer API, and is RTDN arriving? */
data class BillingHealth(
    val playApi: String,
    val httpStatus: Int?,
    val message: String,
    val serviceAccount: String?,
    val lastRtdnAt: Long?,
    /** Last server self-test (billingRtdnSelfTest) that reached playBillingRtdn. */
    val lastSelfTestAt: Long?,
    val pendingCount: Int,
    val unlinkedCount: Int,
    /** package_pro_mrr base plans as Google Play holds them: "id · STATE · P1M (monthly)". */
    val catalog: List<String> = emptyList(),
    val catalogError: String? = null
) {
    val isHealthy: Boolean get() = playApi == "ok"

    companion object {
        fun fromMap(m: Map<String, Any?>) = BillingHealth(
            playApi = m.str("playApi").ifBlank { "transient" },
            httpStatus = (m["httpStatus"] as? Number)?.toInt(),
            message = m.str("message"),
            serviceAccount = m["serviceAccount"] as? String,
            lastRtdnAt = m.long("lastRtdnAt"),
            lastSelfTestAt = m.long("lastSelfTestAt"),
            pendingCount = m.int("pendingCount"),
            unlinkedCount = m.int("unlinkedCount"),
            catalog = (m["catalog"] as? List<*>).orEmpty().mapNotNull { row ->
                val r = row as? Map<*, *> ?: return@mapNotNull null
                listOfNotNull(
                    r["basePlanId"] as? String,
                    r["state"] as? String,
                    (r["period"] as? String)?.let { p -> (r["interval"] as? String)?.let { "$p ($it)" } ?: p }
                ).joinToString(" · ")
            },
            catalogError = m["catalogError"] as? String
        )
    }
}

/** A paid Play purchase that isn't active yet (adminBillingPending). Tokens stay on the server. */
data class PendingPayment(
    val kind: String,
    val id: String,
    val userUid: String?,
    val userName: String,
    val userCode: String,
    val productId: String,
    val orderId: String,
    val lastError: String,
    val attempts: Int,
    val needsAdmin: Boolean,
    val createdAt: Long,
    val hoursLeft: Int?,
    val hint: String
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun listFrom(m: Map<String, Any?>): List<PendingPayment> = m["rows"].mapList().map { r ->
            val user = r["user"] as? Map<String, Any?> ?: emptyMap()
            PendingPayment(
                kind = r.str("kind"),
                id = r.str("id"),
                userUid = user["uid"] as? String,
                userName = user.str("name"),
                userCode = user.str("code"),
                productId = r.str("productId"),
                orderId = r.str("orderId"),
                lastError = r.str("lastError"),
                attempts = r.int("attempts"),
                needsAdmin = r["needsAdmin"] as? Boolean ?: false,
                createdAt = r.long("createdAt") ?: 0L,
                hoursLeft = (r["hoursLeft"] as? Number)?.toInt(),
                hint = r.str("hint")
            )
        }
    }
}
