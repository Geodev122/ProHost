package com.example.analytics

import android.content.Context
import com.example.data.model.AppUser
import com.example.data.model.SpaceListing
import com.example.data.model.Subdivision
import com.example.data.model.UserRole

/**
 * The single entry point for GA4 events. Nothing is sent until [AnalyticsConsent] grants
 * it; parameters are sanitised (PII-looking keys/values dropped, strings trimmed to GA4's
 * 100-char limit). Identity is the server display code (U-XXXXXX), never the Firebase UID,
 * email or phone. Crashlytics stays separate and anonymous.
 */
object AnalyticsTracker {
    @Volatile internal var sink: AnalyticsSink? = null
    @Volatile internal var enabled: Boolean = false
    @Volatile private var lastScreen: String? = null
    @Volatile private var lastUserFingerprint: String? = null
    @Volatile var appInstanceId: String? = null
        private set

    private val blockedKey = Regex("(?i)(e?mail|phone|full_?name|first_?name|last_?name|uid|password|token|address)")
    private val emailLike = Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
    private val phoneLike = Regex("^\\+?[0-9][0-9 ()\\-]{6,}$")

    private var buildType: String = "release"

    fun init(context: Context, buildType: String) {
        this.buildType = buildType
        if (sink == null) sink = FirebaseAnalyticsSink(context)
        AnalyticsConsent.load(context)
    }

    internal fun applyConsent(granted: Boolean, reset: Boolean) {
        enabled = granted
        val s = sink ?: return
        s.setAnalyticsConsent(granted)
        s.setCollectionEnabled(granted)
        if (reset) {
            s.setUserId(null)
            s.resetData()
            lastScreen = null
            lastUserFingerprint = null
            appInstanceId = null
        }
        if (granted) {
            s.setUserProperty(UserProp.BUILD_TYPE, buildType)
            s.appInstanceId { appInstanceId = it }
        }
    }

    /** Resolves the GA app-instance id (needed for server-side events); null without consent. */
    fun fetchAppInstanceId(onResult: (String?) -> Unit) {
        val s = sink
        if (!enabled || s == null) { onResult(null); return }
        s.appInstanceId { id -> appInstanceId = id; onResult(id) }
    }

    // ---- core -------------------------------------------------------------------------

    internal fun log(name: String, params: Map<String, Any?> = emptyMap()) {
        if (!enabled) return
        val s = sink ?: return
        runCatching { s.logEvent(name, sanitize(params)) }
    }

    internal fun sanitize(params: Map<String, Any?>): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for ((key, raw) in params) {
            if (raw == null || out.size >= 25) continue
            if (key != Param.ITEM_ID && blockedKey.containsMatchIn(key)) continue
            val value: Any = when (raw) {
                is String -> cleanString(raw) ?: continue
                is Boolean -> if (raw) "true" else "false"
                is Int -> raw.toLong()
                is Long, is Double -> raw
                is Float -> raw.toDouble()
                is Enum<*> -> raw.name
                is List<*> -> raw.filterIsInstance<Map<*, *>>().take(10).map { item ->
                    @Suppress("UNCHECKED_CAST")
                    sanitize(item as Map<String, Any?>)
                }
                else -> cleanString(raw.toString()) ?: continue
            }
            out[key.take(40)] = value
        }
        return out
    }

    private fun cleanString(s: String): String? {
        val t = s.trim()
        if (t.isEmpty() || emailLike.containsMatchIn(t) || phoneLike.matches(t)) return null
        return t.take(100)
    }

    // ---- identity ---------------------------------------------------------------------

    fun setUser(user: AppUser?) {
        if (!enabled) return
        val s = sink ?: return
        if (user == null) {
            if (lastUserFingerprint != null) s.setUserId(null)
            lastUserFingerprint = null
            return
        }
        val now = System.currentTimeMillis()
        val planStatus = when {
            user.ownerPackageId == null -> "none"
            (user.ownerPackageExpiryMillis ?: Long.MAX_VALUE) > now -> "active"
            else -> "expired"
        }
        val props = linkedMapOf(
            UserProp.ROLE to user.role.name,
            UserProp.COUNTRY to user.country.ifBlank { null },
            UserProp.GOVERNORATE to user.governorate.ifBlank { null },
            UserProp.SPECIALTY to user.specialty.ifBlank { null }?.take(36),
            UserProp.KYC_COMPLETE to user.isKycComplete.toString(),
            UserProp.IS_VERIFIED to user.isVerified.toString(),
            UserProp.PLAN_ID to (user.ownerPackageId?.take(36) ?: "none"),
            UserProp.PLAN_STATUS to planStatus,
            UserProp.LISTING_COUNT to bucketCount(user.activeListingCount),
            UserProp.ACCOUNT_AGE to bucketAge(user.createdAtMillis, now),
            UserProp.IS_DEMO to user.isDemo.toString()
        )
        val fingerprint = user.displayCode + props.values.joinToString("|")
        if (fingerprint == lastUserFingerprint) return
        lastUserFingerprint = fingerprint
        // Display code only once the server has assigned it — never the doc id/UID.
        if (user.displayCode.isNotBlank()) s.setUserId(user.displayCode)
        props.forEach { (k, v) -> s.setUserProperty(k, v) }
    }

    fun setSignupMethod(method: String) {
        if (enabled) sink?.setUserProperty(UserProp.SIGNUP_METHOD, method)
    }

    internal fun bucketCount(n: Int): String = when {
        n <= 0 -> "0"
        n == 1 -> "1"
        n <= 5 -> "2-5"
        else -> "6+"
    }

    internal fun bucketAge(createdAt: Long?, now: Long): String? {
        createdAt ?: return null
        val days = (now - createdAt) / 86_400_000L
        return when {
            days < 7 -> "<7d"
            days < 30 -> "<30d"
            days < 90 -> "<90d"
            else -> "90d+"
        }
    }

    // ---- navigation & acquisition -----------------------------------------------------

    fun screen(name: String, screenClass: String = "ProHostAppRoot") {
        if (!enabled || name == lastScreen) return
        lastScreen = name
        log(Event.SCREEN_VIEW, mapOf(Param.SCREEN_NAME to name, Param.SCREEN_CLASS to screenClass))
    }

    fun deepLinkOpen(source: String, target: String?) =
        log(Event.DEEP_LINK_OPEN, mapOf(Param.SOURCE to source, Param.TARGET to target))

    fun notificationOpen(type: String?, target: String?) =
        log(Event.NOTIFICATION_OPEN, mapOf(Param.NOTIFICATION_TYPE to (type ?: "unknown"), Param.TARGET to target))

    fun notificationPermission(granted: Boolean) =
        log(Event.NOTIFICATION_PERMISSION, mapOf(Param.GRANTED to granted))

    // ---- auth -------------------------------------------------------------------------

    fun authStart(method: String) = log(Event.AUTH_START, mapOf(Param.METHOD to method))
    fun authFallback(from: String, to: String) = log(Event.AUTH_FALLBACK, mapOf(Param.FROM to from, Param.TO to to))
    fun authError(method: String, code: String?) =
        log(Event.AUTH_ERROR, mapOf(Param.METHOD to method, Param.CODE to (code ?: "unknown")))
    fun login(method: String) = log(Event.LOGIN, mapOf(Param.METHOD to method))
    fun signUp(method: String, role: UserRole) {
        setSignupMethod(method)
        log(Event.SIGN_UP, mapOf(Param.METHOD to method, Param.ROLE to role.name))
    }
    fun logout() {
        log(Event.LOGOUT)
        setUser(null)
    }
    fun accountDeleted() {
        log(Event.ACCOUNT_DELETED)
        setUser(null)
    }
    fun kycStart(source: String) = log(Event.KYC_START, mapOf(Param.SOURCE to source))
    fun kycComplete() = log(Event.KYC_COMPLETE)

    // ---- discovery --------------------------------------------------------------------

    fun search(term: String, resultCount: Int) =
        log(Event.SEARCH, mapOf(Param.SEARCH_TERM to term.lowercase(), Param.RESULT_COUNT to resultCount))

    fun filterApply(type: String, value: String, resultCount: Int? = null) =
        log(Event.FILTER_APPLY, mapOf(Param.FILTER_TYPE to type, Param.VALUE_LABEL to value, Param.RESULT_COUNT to resultCount))

    fun filterReset() = log(Event.FILTER_RESET)
    fun mapToggle(isMap: Boolean) = log(Event.MAP_TOGGLE, mapOf(Param.VIEW to if (isMap) "map" else "list"))

    fun viewItemList(listName: String, spaces: List<SpaceListing>) {
        if (!enabled) return
        log(Event.VIEW_ITEM_LIST, mapOf(
            Param.ITEM_LIST_NAME to listName,
            Param.RESULT_COUNT to spaces.size,
            Param.ITEMS to spaces.take(10).mapIndexed { i, sp -> sp.toAnalyticsItem(index = i) }
        ))
    }

    fun selectItem(listName: String, space: SpaceListing, sub: Subdivision?, index: Int?) {
        if (!enabled) return
        log(Event.SELECT_ITEM, mapOf(
            Param.ITEM_LIST_NAME to listName,
            Param.ITEMS to listOf(space.toAnalyticsItem(sub, index))
        ))
    }

    fun viewItem(space: SpaceListing, sub: Subdivision?) {
        if (!enabled) return
        val item = space.toAnalyticsItem(sub)
        log(Event.VIEW_ITEM, mapOf(
            Param.CURRENCY to "USD",
            Param.VALUE to item[Param.PRICE],
            Param.SUBDIVISION_TYPE to sub?.type?.name,
            Param.ITEMS to listOf(item)
        ))
    }

    fun selectRoom(space: SpaceListing, sub: Subdivision) {
        if (!enabled) return
        log(Event.SELECT_ROOM, mapOf(
            Param.ITEM_ID to space.toAnalyticsItem()[Param.ITEM_ID],
            Param.SUBDIVISION_TYPE to sub.type.name,
            Param.STRATEGY to sub.pricing.strategyType.name
        ))
    }

    fun viewAvailability(space: SpaceListing, sub: Subdivision?, openSlotCount: Int) {
        if (!enabled) return
        log(Event.VIEW_AVAILABILITY, mapOf(
            Param.ITEM_ID to space.toAnalyticsItem()[Param.ITEM_ID],
            Param.SUBDIVISION_TYPE to sub?.type?.name,
            Param.OPEN_SLOT_COUNT to openSlotCount
        ))
    }

    fun wishlist(space: SpaceListing, added: Boolean) {
        if (!enabled) return
        val item = space.toAnalyticsItem()
        log(if (added) Event.ADD_TO_WISHLIST else Event.REMOVE_FROM_WISHLIST, mapOf(
            Param.CURRENCY to "USD",
            Param.VALUE to item[Param.PRICE],
            Param.ITEMS to listOf(item)
        ))
    }

    fun share(space: SpaceListing, method: String) {
        if (!enabled) return
        log(Event.SHARE, mapOf(
            Param.METHOD to method,
            Param.CONTENT_TYPE to "listing",
            Param.ITEM_ID to space.toAnalyticsItem()[Param.ITEM_ID]
        ))
    }

    fun generateLead(space: SpaceListing, sub: Subdivision?, channel: String = "whatsapp") {
        if (!enabled) return
        val item = space.toAnalyticsItem(sub)
        log(Event.GENERATE_LEAD, mapOf(
            Param.CHANNEL to channel,
            Param.CURRENCY to "USD",
            Param.VALUE to item[Param.PRICE],
            Param.ITEM_ID to item[Param.ITEM_ID],
            Param.ITEM_CATEGORY to item[Param.ITEM_CATEGORY],
            Param.SUBDIVISION_TYPE to sub?.type?.name,
            Param.ITEMS to listOf(item)
        ))
    }

    fun contactSpecialist(channel: String = "whatsapp") =
        log(Event.CONTACT_SPECIALIST, mapOf(Param.CHANNEL to channel))

    // ---- bookings ---------------------------------------------------------------------

    fun beginBookingCheckout(space: SpaceListing, sub: Subdivision?) {
        if (!enabled) return
        val item = space.toAnalyticsItem(sub)
        log(Event.BEGIN_CHECKOUT, mapOf(
            Param.CONTENT_TYPE to "booking",
            Param.CURRENCY to "USD",
            Param.VALUE to item[Param.PRICE],
            Param.STRATEGY to item[Param.ITEM_CATEGORY3],
            Param.ITEMS to listOf(item)
        ))
    }

    fun bookingSlotSelect(strategy: String?, slotCount: Int) =
        log(Event.BOOKING_SLOT_SELECT, mapOf(Param.STRATEGY to strategy, Param.SLOT_COUNT to slotCount))

    fun bookingRequest(
        space: SpaceListing?,
        sub: Subdivision?,
        valueUsd: Double,
        strategy: String?,
        attendeeCount: Int?,
        isRebook: Boolean
    ) {
        if (!enabled) return
        log(Event.BOOKING_REQUEST, mapOf(
            Param.CURRENCY to "USD",
            Param.VALUE to valueUsd,
            Param.STRATEGY to strategy,
            Param.ATTENDEE_COUNT to attendeeCount,
            Param.PRICING_MODE to if (attendeeCount != null && attendeeCount > 0) "per_attendee" else "per_booking",
            Param.IS_REBOOK to isRebook,
            Param.ITEMS to listOfNotNull(space?.toAnalyticsItem(sub))
        ))
    }

    fun bookingRequestFailed(reason: String) = log(Event.BOOKING_REQUEST_FAILED, mapOf(Param.REASON to reason))
    fun bookingAccepted(valueUsd: Double?, strategy: String?) =
        log(Event.BOOKING_ACCEPTED, mapOf(Param.CURRENCY to "USD", Param.VALUE to valueUsd, Param.STRATEGY to strategy))
    fun bookingRejected(strategy: String?) = log(Event.BOOKING_REJECTED, mapOf(Param.STRATEGY to strategy))
    fun bookingCancelled(by: String, reasonCode: String?) =
        log(Event.BOOKING_CANCELLED, mapOf(Param.BY to by, Param.REASON to reasonCode))
    fun paymentAcknowledged(valueUsd: Double?) =
        log(Event.PAYMENT_ACKNOWLEDGED, mapOf(Param.CURRENCY to "USD", Param.VALUE to valueUsd))
    fun paymentReminderSent() = log(Event.PAYMENT_REMINDER_SENT)
    fun calendarReminderAdd() = log(Event.CALENDAR_REMINDER_ADD)

    // ---- host listings ----------------------------------------------------------------

    fun listingCreateStart() = log(Event.LISTING_CREATE_START)

    fun listingDraftSaved(space: SpaceListing) {
        if (!enabled) return
        log(Event.LISTING_DRAFT_SAVED, mapOf(Param.ITEM_CATEGORY to space.spaceType.name, Param.SUBDIVISION_COUNT to space.subdivisions.size))
    }

    fun listingPublish(space: SpaceListing) {
        if (!enabled) return
        val hasAttendee = space.subdivisions.any { com.example.ui.util.AttendeePricing.isPerAttendee(it) }
        log(Event.LISTING_PUBLISH, mapOf(
            Param.ITEM_CATEGORY to space.spaceType.name,
            Param.COUNTRY to space.country,
            Param.SUBDIVISION_COUNT to space.subdivisions.size,
            Param.HAS_ATTENDEE_PRICING to hasAttendee,
            Param.ITEMS to listOf(space.toAnalyticsItem())
        ))
    }

    fun listingPublishBlocked(reason: String) = log(Event.LISTING_PUBLISH_BLOCKED, mapOf(Param.REASON to reason))
    fun listingUpdate(space: SpaceListing) {
        if (!enabled) return
        log(Event.LISTING_UPDATE, mapOf(
            Param.ITEM_ID to space.toAnalyticsItem()[Param.ITEM_ID],
            Param.SUBDIVISION_COUNT to space.subdivisions.size
        ))
    }
    fun listingDelete() = log(Event.LISTING_DELETE)
    fun listingStatusChange(from: String?, to: String) = log(Event.LISTING_STATUS_CHANGE, mapOf(Param.FROM to from, Param.TO to to))
    fun listingVerificationRequest() = log(Event.LISTING_VERIFICATION_REQUEST)
    fun subdivisionAdd(sub: Subdivision) = log(Event.SUBDIVISION_ADD, mapOf(
        Param.SUBDIVISION_TYPE to sub.type.name,
        Param.STRATEGY to sub.pricing.strategyType.name,
        Param.PRICING_MODE to if (com.example.ui.util.AttendeePricing.isPerAttendee(sub)) "per_attendee" else "per_booking"
    ))
    fun subdivisionRemove() = log(Event.SUBDIVISION_REMOVE)
    fun blackoutAdd() = log(Event.BLACKOUT_ADD)

    // ---- subscriptions / Play Billing -------------------------------------------------

    // ---- ProHost Premium ----------------------------------------------------------------
    // [plan] is "monthly" / "yearly" (PlayCatalog.planInterval). Success, restore, renewal and
    // expiry are logged by the server, which is the only place that knows they happened.

    fun premiumPageViewed(source: String, plansLoaded: Int) =
        log(Event.PREMIUM_PAGE_VIEWED, mapOf(Param.SOURCE to source, Param.PLANS_LOADED to plansLoaded))
    fun premiumPlanViewed(plan: String) = log(Event.PREMIUM_PLAN_VIEWED, mapOf(Param.PLAN to plan))
    fun premiumPlansLoadFailed(reason: String) = log(Event.PREMIUM_PLANS_LOAD_FAILED, mapOf(Param.REASON to reason))

    fun premiumCheckoutStarted(plan: String?, priceMicros: Long?, currency: String?) =
        log(Event.PREMIUM_CHECKOUT_STARTED, mapOf(
            Param.PLAN to plan,
            Param.PRODUCT_ID to com.example.data.billing.PlayCatalog.PRODUCT_ID,
            Param.CURRENCY to currency,
            Param.VALUE to priceMicros?.let { it / 1_000_000.0 }
        ))

    /** [reason]: "user_cancelled", "billing_error", "server_activation", … */
    fun premiumPurchaseFailed(plan: String?, reason: String, responseCode: Int? = null) =
        log(Event.PREMIUM_PURCHASE_FAILED, mapOf(Param.PLAN to plan, Param.REASON to reason, Param.RESPONSE_CODE to responseCode))
    fun premiumPurchasePending(plan: String?) = log(Event.PREMIUM_PURCHASE_PENDING, mapOf(Param.PLAN to plan))
    fun premiumRestoreResult(result: String) = log(Event.PREMIUM_RESTORE_RESULT, mapOf(Param.RESULT to result))

    /** SHA-256 of the Play order id, first 24 hex chars — mirrored by functions/src/lib/ga4.ts. */
    fun transactionId(orderId: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(orderId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)

    fun redeemCodeOpen() = log(Event.REDEEM_CODE_OPEN)
    fun manageSubscriptionOpen() = log(Event.MANAGE_SUBSCRIPTION_OPEN)
    fun orderHistoryOpen() = log(Event.ORDER_HISTORY_OPEN)

    // ---- admin & errors ---------------------------------------------------------------

    fun adminAction(action: String, success: Boolean) =
        log(Event.ADMIN_ACTION, mapOf(Param.ACTION to action, Param.RESULT to if (success) "success" else "failure"))

    /** [code] is a short machine code (exception class or Firebase error code) — never a message. */
    fun appError(area: String, code: String) = log(Event.APP_ERROR, mapOf(Param.AREA to area, Param.CODE to code))

    fun errorCode(e: Throwable): String = when (e) {
        is com.google.firebase.firestore.FirebaseFirestoreException -> e.code.name
        is com.google.firebase.functions.FirebaseFunctionsException -> e.code.name
        is com.google.firebase.auth.FirebaseAuthException -> e.errorCode
        else -> e.javaClass.simpleName.ifBlank { "Exception" }
    }
}
