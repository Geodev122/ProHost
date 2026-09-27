package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.*
import com.example.data.repository.ProHostRepository
import com.example.ui.viewmodel.ProHostViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ProHost End-to-End Flow & Critical User Journey (CUJ) Tests
 * Covers all 3 core role workflows: Professional Practitioner, Space Owner, and Super Admin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProHostEndToEndLifecycleTest {

    private lateinit var repository: ProHostRepository
    private lateinit var viewModel: ProHostViewModel
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = ProHostRepository()
        repository.replaceSpacesForTest(demoSpaces())
        viewModel = ProHostViewModel(repository)
    }

    @Test
    fun `test complete booking lifecycle - discovery to owner approval with signed agreement`() = kotlinx.coroutines.runBlocking {
        // 1. Practitioner logs in
        val practitioner = repository.login(uid = "uid-dr-sami", email = "dr.sami@prospace.lb", verifiedRole = UserRole.SPECIALIST)
        assertNotNull(practitioner)
        assertEquals(UserRole.SPECIALIST, practitioner.role)

        // 2. Discover available space
        val spaces = repository.spaces.value
        assertTrue("Spaces should be populated", spaces.isNotEmpty())
        val targetSpace = spaces.first { it.rentalFormulas.isNotEmpty() }
        val selectedFormula = targetSpace.rentalFormulas.first()

        // 3. Submit Booking Application
        val (bookingRequest, _) = repository.createBookingRequest(
            space = targetSpace,
            formula = selectedFormula,
            practitioner = practitioner,
            startDate = "2026-09-01",
            durationMonths = 3,
            notes = "Require access for clinical cardiology consultations",
            selectedDays = selectedFormula.daysOfWeek,
            selectedStartHour = selectedFormula.startHour,
            selectedEndHour = selectedFormula.endHour,
            selectedShift = "Morning Shift",
            calculatedTotalUsd = selectedFormula.rateUsd * 3
        )

        assertNotNull(bookingRequest)
        assertEquals(BookingRequestStatus.PENDING, bookingRequest.status)
        assertEquals(selectedFormula.rateUsd * 3, bookingRequest.totalAmountUsd, 0.01)

        // 4. Verify Owner sees incoming request
        val ownerIncoming = repository.bookingRequests.value.filter { it.spaceId == targetSpace.id }
        assertTrue(ownerIncoming.any { it.id == bookingRequest.id })

        // 5. Owner Accepts Booking Application, uploading the signed agreement
        val agreementUrl = "https://storage.example.com/booking_agreements/${bookingRequest.id}/agreement.pdf"
        val accepted = repository.acceptBookingRequest(bookingRequest.id, agreementUrl)
        assertTrue(accepted)

        val updatedRequest = repository.bookingRequests.value.find { it.id == bookingRequest.id }
        assertNotNull(updatedRequest)
        assertEquals(BookingRequestStatus.ACCEPTED, updatedRequest?.status)
        assertEquals(agreementUrl, updatedRequest?.agreementUrl)

        // Verify space has resident practitioner added
        val updatedSpace = repository.spaces.value.find { it.id == targetSpace.id }
        assertTrue(updatedSpace?.residentPractitioners?.any { it.contains(practitioner.fullName) } == true)
    }

    @Test
    fun `editing an accepted booking and having the host accept it releases the original`() = kotlinx.coroutines.runBlocking {
        val practitioner = repository.login(uid = "uid-dr-edit", email = "dr.edit@prospace.lb", verifiedRole = UserRole.SPECIALIST)
        val space = repository.spaces.value.first { it.rentalFormulas.isNotEmpty() }
        val formula = space.rentalFormulas.first()

        val (original, _) = repository.createBookingRequest(
            space = space,
            formula = formula,
            practitioner = practitioner,
            startDate = "2026-09-01",
            durationMonths = 1,
            notes = "Original booking"
        )
        assertTrue(repository.acceptBookingRequest(original.id, "https://storage.example.com/original-agreement.pdf"))
        assertEquals(BookingRequestStatus.ACCEPTED, repository.bookingRequests.value.find { it.id == original.id }?.status)

        // Practitioner submits an edit referencing the original
        val (edit, _) = repository.createBookingRequest(
            space = space,
            formula = formula,
            practitioner = practitioner,
            startDate = "2026-10-01",
            durationMonths = 1,
            notes = "Edit request",
            replacesBookingId = original.id
        )
        assertEquals(original.id, edit.replacesBookingId)
        assertEquals(BookingRequestStatus.PENDING, edit.status)
        // The original stays ACCEPTED until the edit is actually accepted
        assertEquals(BookingRequestStatus.ACCEPTED, repository.bookingRequests.value.find { it.id == original.id }?.status)

        // Host accepts the edit — this must release (cancel) the original in the same operation
        assertTrue(repository.acceptBookingRequest(edit.id, "https://storage.example.com/edit-agreement.pdf"))
        assertEquals(BookingRequestStatus.ACCEPTED, repository.bookingRequests.value.find { it.id == edit.id }?.status)
        assertEquals(BookingRequestStatus.CANCELLED, repository.bookingRequests.value.find { it.id == original.id }?.status)
    }

    @Test
    fun `test space owner rejection workflow and audit logging`() = kotlinx.coroutines.runBlocking {
        val practitioner = repository.login(uid = "uid-dr-maya", email = "dr.maya@prospace.lb", verifiedRole = UserRole.SPECIALIST)
        val space = repository.spaces.value.first()
        val formula = space.rentalFormulas.first()

        val (request, _) = repository.createBookingRequest(
            space = space,
            formula = formula,
            practitioner = practitioner,
            startDate = "2026-10-01",
            durationMonths = 1,
            notes = "Need specialized pediatric space"
        )

        assertEquals(BookingRequestStatus.PENDING, request.status)

        // Owner declines with reason
        val reason = "Slot conflict with existing dermatology clinic"
        val declined = repository.rejectBookingRequest(request.id, note = reason)
        assertTrue(declined)

        val refreshed = repository.bookingRequests.value.find { it.id == request.id }
        assertEquals(BookingRequestStatus.REJECTED, refreshed?.status)
        assertEquals(reason, refreshed?.rejectionReason)

        // Verify audit trail captured event
        val auditLogs = repository.auditLogs.value
        assertTrue(auditLogs.any { it.actionType == "RENTAL_REQUEST_DECLINED" && it.details.contains(request.id) })
    }

    @Test
    fun `test super admin listing verification override`() = kotlinx.coroutines.runBlocking {
        // Admin login
        val admin = repository.login(uid = "uid-admin-test", email = "admin@prohost.test", verifiedRole = UserRole.ADMIN)
        assertEquals(UserRole.ADMIN, admin.role)

        val space = repository.spaces.value.first()
        val initialVerification = space.isVerified

        // Toggle verification
        repository.toggleListingVerification(space.id)
        val toggledSpace = repository.spaces.value.find { it.id == space.id }
        assertEquals(!initialVerification, toggledSpace?.isVerified)

    }


    @Test
    fun `fresh repository starts signed out, not pre-authenticated as Super Admin`() {
        val freshRepository = ProHostRepository()
        assertNull(
            "A new repository instance must start signed out — it must NOT default to a pre-authenticated Admin session",
            freshRepository.currentUser.value
        )
    }

    @Test
    fun `login role comes only from the verifiedRole argument, never inferred from email`() = kotlinx.coroutines.runBlocking {
        val user = repository.login(
            uid = "uid-arbitrary",
            email = "admin@pro-host.tech",
            verifiedRole = UserRole.SPECIALIST
        )
        assertEquals(
            "The role actually assigned must be exactly the verifiedRole argument, regardless of which email was used",
            UserRole.SPECIALIST,
            user.role
        )
    }
}
