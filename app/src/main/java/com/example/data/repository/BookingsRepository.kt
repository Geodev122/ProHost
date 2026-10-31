package com.example.data.repository

import android.util.Log
import com.example.data.auth.toUserMessage
import com.example.data.auth.FirebaseFunctionsClient
import com.example.data.auth.RegistrationDetails
import com.example.data.demo.DemoDataGenerator
import com.example.data.firestore.FirestoreSchema
import com.example.data.firestore.FirestoreService
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Part of [ProHostRepository], split out by area. Every function runs against the shared
 * repository state (`with(repo)`), and ProHostRepository keeps a same-signature delegate
 * for each, so callers and tests are unchanged.
 */
internal class BookingsRepository(private val repo: ProHostRepository) {

    // --- Smart Booking & In-App Rental Request Engine ---
    /**
     * Awaits the real Firestore write instead of firing it off in the
     * background — the caller (ProHostViewModel.submitBookingRequest) used to
     * show "Rental Request Sent!" the instant this returned, whatever the
     * actual sync outcome, since the write itself ran fire-and-forget via
     * syncNewBookingToFirestore. A specialist on a bad connection saw
     * confirmed success for a request that never reached Firestore — and
     * therefore never reached the host — with nothing telling them to retry.
     * The request is still added to local state optimistically (so it shows
     * up immediately in "My Bookings" even mid-sync), but [synced] in the
     * returned pair tells the caller whether that actually landed, so it can
     * show a truthful toast instead of an unconditional one.
     */
    suspend fun createBookingRequest(
        space: SpaceListing,
        formula: RentalFormula,
        practitioner: AppUser,
        startDate: String,
        durationMonths: Int,
        notes: String,
        selectedDays: List<String> = emptyList(),
        selectedCalendarDates: List<String> = emptyList(),
        selectedStartHour: String = "",
        selectedEndHour: String = "",
        selectedShift: String = "",
        calculatedTotalUsd: Double = 0.0,
        subdivisionId: String? = null,
        subdivisionName: String? = null,
        replacesBookingId: String? = null,
        attendeeCount: Int = 0,
        selectedAttendeePackageId: String? = null,
        attendeePackageName: String? = null,
        attendeePackagePriceUsd: Double = 0.0
    ): Pair<RentalBookingRequest, Boolean> {
        return with(repo) {
            if (practitioner.id == space.ownerId) {
                throw IllegalArgumentException("A host cannot book their own listing.")
            }
            // Guard against duplicate submissions: reject if a PENDING request from this
            // practitioner for this space already exists in the local cache (M7).
            val hasPending = _bookingRequests.value.any { existing ->
                existing.spaceId == space.id &&
                existing.practitionerId == practitioner.id &&
                existing.status == BookingRequestStatus.PENDING &&
                existing.id != replacesBookingId
            }
            if (hasPending) {
                throw IllegalStateException("You already have a pending booking request for this space.")
            }
            // Was "REQ-LB-" + (1000..9999).random() — only ~9,000 distinct values,
            // no collision check, and saveBookingRequest below does a
            // .document(requestId).set(..., merge=true) — a collision wouldn't even
            // fail loudly, it would silently merge two unrelated bookings' fields
            // into one Firestore document. A UUID-derived id makes a collision
            // practically impossible (16^10 space) without losing the readable
            // "REQ-XXXXXXXXXX" shape the toasts/audit log already display.
            val requestId = "REQ-" + UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
            val totalUsd = if (calculatedTotalUsd > 0) calculatedTotalUsd else (formula.rateUsd * durationMonths)

            val daysChosen = if (selectedDays.isNotEmpty()) selectedDays else formula.daysOfWeek
            val startH = if (selectedStartHour.isNotBlank()) selectedStartHour else formula.startHour
            val endH = if (selectedEndHour.isNotBlank()) selectedEndHour else formula.endHour
            val shiftDesc = if (selectedShift.isNotBlank()) " [$selectedShift]" else ""

            val rangeString = "${daysChosen.joinToString(", ")} $startH - $endH$shiftDesc (Starting $startDate, $durationMonths Mon" +
                "th${if (durationMonths > 1) "s" else ""})"

            val request = RentalBookingRequest(
                id = requestId,
                spaceId = space.id,
                spaceTitle = space.title,
                spaceDistrict = space.district,
                governorate = space.governorate,
                ownerId = space.ownerId,
                ownerName = space.ownerName,
                ownerPhone = space.ownerPhone,
                practitionerId = practitioner.id,
                practitionerName = practitioner.fullName,
                practitionerEmail = practitioner.email,
                practitionerPhone = practitioner.phone,
                practitionerSpecialty = practitioner.specialty,
                formula = formula,
                startDate = startDate,
                selectedDays = daysChosen,
                selectedCalendarDates = selectedCalendarDates,
                selectedStartHour = startH,
                selectedEndHour = endH,
                selectedShift = selectedShift,
                selectedDateTimeRange = rangeString,
                durationMonths = durationMonths,
                totalAmountUsd = totalUsd,
                clinicalNotes = notes,
                status = BookingRequestStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                subdivisionId = subdivisionId,
                subdivisionName = subdivisionName,
                replacesBookingId = replacesBookingId,
                attendeeCount = attendeeCount,
                selectedAttendeePackageId = selectedAttendeePackageId,
                attendeePackageName = attendeePackageName,
                attendeePackagePriceUsd = attendeePackagePriceUsd
            )

            _bookingRequests.value = listOf(request) + _bookingRequests.value
            val synced = firestoreService.saveBookingRequest(request)
            if (synced) {
                _isOfflineMode.value = false
                _syncStatusMessage.value = "Booking Synced with Firebase Cloud"
            } else {
                _isOfflineMode.value = true
                _syncStatusMessage.value = "Offline: Booking Stored in Local Cache"
            }

            addAuditLog(
                actionType = if (replacesBookingId != null) "RENTAL_REQUEST_EDIT_SUBMITTED" else "RENTAL_REQUEST_SUBMITTED",
                details = if (replacesBookingId != null) {
                    "Edit request $requestId sent by ${practitioner.fullName} for '${space.title}', proposing to replace " +
                        "accepted booking #$replacesBookingId. New slot: $rangeString. Awaiting owner approval." +
                        (if (!synced) " [NOT YET SYNCED TO CLOUD]" else "")
                } else {
                    "Request $requestId sent by ${practitioner.fullName} for '${space.title}' (${formula.type.displayName}" +
                        ", $${totalUsd.toInt()} USD). Selected Slot: $rangeString. Awaiting owner WhatsApp/In-app approval." +
                        (if (!synced) " [NOT YET SYNCED TO CLOUD]" else "")
                },
                severity = if (synced) "INFO" else "WARN",
                actorEmail = practitioner.email
            )

            return request to synced
        }
    }

    /**
     * The already-ACCEPTED booking that [requestId] would collide with if accepted
     * now, or null when it's clear. Checked by the ViewModel before the agreement
     * upload (so a host isn't asked to upload a lease for a booking that can't be
     * accepted) and again inside [acceptBookingRequest] as the real guard.
     */
    fun findAcceptConflict(requestId: String): RentalBookingRequest? {
        return with(repo) {
            val request = _bookingRequests.value.find { it.id == requestId } ?: return null
            return com.example.ui.util.SpaceCalculationUtils.findAcceptConflict(request, _bookingRequests.value)
        }
    }

    /**
     * Owner accepting a booking means they've reached and evidenced a real agreement
     * with the specialist — [agreementUrl] is the signed lease they just uploaded to
     * Storage (see OwnerRentalRequestsScreen's Accept flow), kept on file as the
     * record of that, exactly like [SpaceListing.ownershipProofUrl]: self-attested,
     * never reviewed. There is no in-app payment settlement to track anymore — both
     * sides handle payment outside the app entirely (the old isExternalPaymentSettled
     * flag, and the "Pay Whish" flow that set it, are gone).
     *
     * If [request.replacesBookingId] is set, this acceptance is really an edit
     * superseding a previously accepted booking (see MyBookingsScreen's "Edit
     * Booking" action) — the server's conflict guard releases the superseded booking
     * (CANCELLED, supersededBy) in the accept's own transaction, so exactly one of the two is ever ACCEPTED and
     * availability — always derived live from ACCEPTED bookings + the space's
     * schedule, never a separately stored count — recalculates immediately.
     *
     * Never double-books: refuses (returns false) when [findAcceptConflict] finds an
     * ACCEPTED booking already holding the same room/space, day and hours.
     */
    suspend fun acceptBookingRequest(requestId: String, agreementUrl: String? = null): Boolean {
        return with(repo) {
            val request = _bookingRequests.value.find { it.id == requestId } ?: return false
            // Never double-book: two ACCEPTED bookings can't overlap on the same room/space,
            // day and hours (SpaceCalculationUtils.findAcceptConflict — the same rule the
            // specialist-facing screens hide locked slots with).
            if (findAcceptConflict(requestId) != null) return false
            val now = System.currentTimeMillis()

            val extraFields = if (agreementUrl != null) mapOf("agreementUrl" to agreementUrl) else emptyMap()
            val success = firestoreService.updateBookingStatus(
                requestId,
                BookingRequestStatus.ACCEPTED,
                extraFields = extraFields
            )
            if (!success) return false

            _bookingRequests.value = _bookingRequests.value.map {
                if (it.id == requestId) {
                    it.copy(status = BookingRequestStatus.ACCEPTED, reviewedAt = now, agreementUrl = agreementUrl ?: it.agreementUrl)
                } else it
            }

            // Add member to resident list if not present
            val memberString = "${request.practitionerName} (${request.practitionerSpecialty})"
            _spaces.value = _spaces.value.map { space ->
                if (space.id == request.spaceId && !space.residentPractitioners.contains(memberString)) {
                    space.copy(residentPractitioners = space.residentPractitioners + memberString)
                } else space
            }

            // The booking this edit replaces is released by the server (onBookingAcceptConflictGuard)
            // in the same transaction that confirms the accept: never two accepted versions, and
            // never none if the accept is reverted for a conflict. The snapshot listener shows it.

            addAuditLog(
                actionType = "RENTAL_REQUEST_ACCEPTED",
                details = "Owner ${request.ownerName} accepted $requestId by ${request.practitionerName}. Formula '${request.formula.scheduleDescription}" +
                    "' (${request.selectedDateTimeRange}) is now locked and marked unavailable for public display." +
                    (request.replacesBookingId?.let { " Replaces booking #$it, now released." } ?: ""),
                severity = "SECURE",
                actorEmail = request.ownerName
            )

            return true
        }
    }

    /**
     * Used to fire syncBookingStatusToFirestore (a detached coroutine.launch,
     * never awaited) and unconditionally return true — the same
     * fire-and-forget shape submitBookingRequest had before it was fixed
     * (see that function's own doc comment). A host declining a request saw
     * "Declined" toast success, and the local list flipped to REJECTED,
     * whether or not the Firestore write actually landed; a flaky connection
     * left the request still PENDING server-side while the host's own UI
     * insisted it was handled. Now suspend and await the real write, mirroring
     * acceptBookingRequest's own shape, and only report/apply success when it
     * genuinely succeeded.
     */
    suspend fun rejectBookingRequest(requestId: String, note: String? = null): Boolean {
        return with(repo) {
            val request = _bookingRequests.value.find { it.id == requestId } ?: return false
            val now = System.currentTimeMillis()
            val reason = note ?: "Declined by space owner"

            val success = firestoreService.updateBookingStatus(requestId, BookingRequestStatus.REJECTED, reason)
            if (!success) return false

            _bookingRequests.value = _bookingRequests.value.map {
                if (it.id == requestId) {
                    it.copy(status = BookingRequestStatus.REJECTED, reviewedAt = now, rejectionReason = reason)
                } else it
            }

            addAuditLog(
                actionType = "RENTAL_REQUEST_DECLINED",
                details = "Request $requestId declined by owner ${request.ownerName} (Reason: $reason). Hours remain available to the public.",
                severity = "WARN",
                actorEmail = request.ownerName
            )

            return true
        }
    }

    /** See rejectBookingRequest's doc comment — same fire-and-forget bug, same fix. */
    suspend fun cancelBookingRequest(requestId: String): Boolean {
        return with(repo) {
            val request = _bookingRequests.value.find { it.id == requestId } ?: return false

            // The requester withdraws it: firestore.rules pins the practitioner side to SPECIALIST
            // (a Pro Host cancelling a rental made before upgrading is that side too).
            val success = firestoreService.updateBookingStatus(
                requestId,
                BookingRequestStatus.CANCELLED,
                extraFields = mapOf("cancelledByRole" to "SPECIALIST")
            )
            if (!success) return false

            _bookingRequests.value = _bookingRequests.value.map {
                if (it.id == requestId) it.copy(status = BookingRequestStatus.CANCELLED, cancelledByRole = "SPECIALIST") else it
            }

            addAuditLog(
                actionType = "RENTAL_REQUEST_CANCELLED",
                details = "Request $requestId for '${request.spaceTitle}' cancelled by practitioner ${request.practitionerName} before host review.",
                severity = "INFO",
                actorEmail = request.practitionerEmail
            )

            return true
        }
    }

    /**
     * Early termination of an already-ACCEPTED booking — the gap flagged as a genuine
     * open item: previously the only cancellation path was for a not-yet-accepted
     * PENDING request (cancelBookingRequest above). Deliberately simple per the
     * current terms-of-use cancellation workflow: a reason code plus an optional
     * note, no refund/penalty logic (there's nothing to refund — rent settlement
     * never happens in-app). [cancelledByUid]/[cancelledByRole] identify who ended
     * it (only the booking's own practitioner, its owner, or an Admin may call this
     * — enforced by the caller checking against the loaded [BookingRequest] before
     * invoking it, same pattern as reject/accept). A real cross-device push notifies
     * whichever side didn't initiate the cancellation (onBookingRequestStatusChanged,
     * functions/src/notifications/bookingNotifications.ts).
     */
    suspend fun cancelAcceptedBooking(
        requestId: String,
        reasonCode: CancellationReasonCode,
        note: String?,
        cancelledByUid: String,
        cancelledByRole: String
    ): Boolean {
        return with(repo) {
            val request = _bookingRequests.value.find { it.id == requestId } ?: return false
            if (request.status != BookingRequestStatus.ACCEPTED) return false
            // Which side ended it decides the role (firestore.rules pins it): the requester is
            // always SPECIALIST — even a Pro Host cancelling a rental made before upgrading —
            // the listing's owner is PRO_HOST, anyone else is an admin.
            @Suppress("NAME_SHADOWING")
            val cancelledByRole = when (cancelledByUid) {
                request.practitionerId -> "SPECIALIST"
                request.ownerId -> "PRO_HOST"
                else -> "ADMIN"
            }

            val success = firestoreService.updateBookingStatus(
                requestId,
                BookingRequestStatus.CANCELLED,
                extraFields = mapOf(
                    "cancellationReasonCode" to reasonCode.name,
                    "cancellationNote" to note,
                    "cancelledByRole" to cancelledByRole
                )
            )
            if (!success) return false

            _bookingRequests.value = _bookingRequests.value.map {
                if (it.id == requestId) {
                    it.copy(
                        status = BookingRequestStatus.CANCELLED,
                        cancellationReasonCode = reasonCode.name,
                        cancellationNote = note,
                        cancelledByRole = cancelledByRole
                    )
                } else it
            }

            addAuditLog(
                actionType = "ACCEPTED_BOOKING_CANCELLED",
                details = "Accepted booking $requestId for '${request.spaceTitle}' (${request.practitionerName} / ${request.ownerName}" +
                    ") terminated early by $cancelledByRole. Reason: ${reasonCode.displayName}${if (!note.isNullOrBlank()) " — \"$note\"" else ""}" +
                    ".",
                severity = "WARN",
                actorEmail = if (cancelledByUid == request.practitionerId) request.practitionerEmail else request.ownerName
            )

            return true
        }
    }

    /**
     * Mutual, independent "Mark as Paid" acknowledgment — record-keeping only, since
     * rent settlement happens entirely outside the app. Each side can only ever set
     * their own flag (the caller decides which); this never touches the other side's.
     */
    suspend fun acknowledgePayment(requestId: String, asHost: Boolean): Boolean {
        return with(repo) {
            val field = if (asHost) "paymentAcknowledgedByHost" else "paymentAcknowledgedBySpecialist"
            val success = firestoreService.updateBookingFields(requestId, mapOf(field to true))
            if (success) {
                _bookingRequests.value = _bookingRequests.value.map {
                    if (it.id == requestId) {
                        if (asHost) it.copy(paymentAcknowledgedByHost = true) else it.copy(paymentAcknowledgedBySpecialist = true)
                    } else it
                }
            }
            return success
        }
    }

    suspend fun addBlackoutSlot(spaceId: String, slot: BlackoutSlot): Boolean {
        return with(repo) {
            val space = _spaces.value.find { it.id == spaceId } ?: return false
            val updated = space.copy(schedule = space.schedule.copy(blackoutSlots = space.schedule.blackoutSlots + slot))
            val success = saveUpdatedSpace(updated)
            if (success) {
                addAuditLog(
                    actionType = "SCHEDULE_BLACKOUT_ADDED",
                    details = "Owner added non-operating blackout slot (${slot.dayOfWeek} ${slot.startTime}-${slot.endTime}) to space $spaceId",
                    severity = "INFO"
                )
            }
            return success
        }
    }

    suspend fun removeBlackoutSlot(spaceId: String, slotId: String): Boolean {
        return with(repo) {
            val space = _spaces.value.find { it.id == spaceId } ?: return false
            val updated = space.copy(schedule = space.schedule.copy(blackoutSlots = space.schedule.blackoutSlots.filter { it.id != slotId }))
            return saveUpdatedSpace(updated)
        }
    }

    /**
     * Rooms/desks were previously only editable at listing-creation time
     * (CreateListingDialog's Step 2) — a host who published first and only later
     * realized they needed another room, or wanted to remove one, had no in-app way
     * to do it. These three funnel through the same saveUpdatedSpace protected-field
     * guard as the blackout/formula functions above; subdivisions live nested inside
     * the same workspace_listings document, so no firestore.rules change is needed.
     */
    suspend fun addSubdivision(spaceId: String, subdivision: Subdivision): Boolean {
        return with(repo) {
            val space = _spaces.value.find { it.id == spaceId } ?: return false
            val updated = space.copy(subdivisions = space.subdivisions + subdivision)
            val success = saveUpdatedSpace(updated)
            if (success) {
                addAuditLog(
                    actionType = "SUBDIVISION_ADDED",
                    details = "Added room/desk '${subdivision.name}' (${subdivision.type.displayName}) to space $spaceId",
                    severity = "INFO"
                )
            }
            return success
        }
    }

    suspend fun removeSubdivision(spaceId: String, subdivisionId: String): Boolean {
        return with(repo) {
            val space = _spaces.value.find { it.id == spaceId } ?: return false
            val updated = space.copy(subdivisions = space.subdivisions.filter { it.id != subdivisionId })
            val success = saveUpdatedSpace(updated)
            if (success) {
                addAuditLog(
                    actionType = "SUBDIVISION_REMOVED",
                    details = "Removed room/desk #$subdivisionId from space $spaceId",
                    severity = "INFO"
                )
            }
            return success
        }
    }

    // Best-effort, fire-and-forget engagement counters (real "Views"/"Inquiries" data,
    // replacing the old fixed 850/14 placeholders). Not offline-queued — this is a
    // low-stakes analytics counter, not a transaction, so silently no-op-ing while
    // offline is an acceptable tradeoff. The live workspace_listings snapshot
    // listener picks up the new value and refreshes _spaces automatically, so no
    // manual local-state patch is needed here. Mirrors addAuditLog's coroutineScope
    // fire-and-forget pattern rather than being a suspend fun.
    fun incrementSpaceViewCount(spaceId: String) {
        with(repo) {
            coroutineScope.launch { firestoreService.incrementSpaceCounter(spaceId, "avatarEngagementViews") }
        }
    }

    fun incrementSpaceInquiryCount(spaceId: String) {
        with(repo) {
            coroutineScope.launch { firestoreService.incrementSpaceCounter(spaceId, "avatarInquiryClicks") }
        }
    }
}
