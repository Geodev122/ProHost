package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.WhishPayApi
import com.example.data.auth.FirebaseAuthService
import com.example.data.crypto.WhishSecurity
import com.example.data.firestore.FirestoreSchema
import com.example.data.firestore.FirestoreService
import com.example.data.model.*
import com.example.data.repository.ProSpaceRepository
import com.example.util.AppSystemDebugger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AppFeatureComplianceAndDebuggerTest
 *
 * Full system test suite verifying:
 * 1. App Feature and operations compliance.
 * 2. Firebase Firestore schema & Data Connect compliance.
 * 3. Whish Money and Auth APIs readiness and execution.
 * 4. Master repository data flows and export pipelines.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFeatureComplianceAndDebuggerTest {

    private lateinit var context: Context
    private lateinit var repository: ProSpaceRepository
    private lateinit var firestoreService: FirestoreService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = ProSpaceRepository()
        firestoreService = FirestoreService.getInstance()
    }

    @Test
    fun `test full system debugger and compliance audit engine`() = runBlocking {
        val auditReport = AppSystemDebugger.runFullSystemAudit(context, repository)

        println("=== PROSPACE SYSTEM AUDIT & COMPLIANCE REPORT ===")
        println("Total Features Checked: ${auditReport.totalFeatures}")
        println("Passed: ${auditReport.passedCount}")
        println("Warnings: ${auditReport.warningCount}")
        println("Failed: ${auditReport.failedCount}")
        println("Overall Compliance: ${auditReport.overallCompliancePercentage}%")
        println("-------------------------------------------------")

        auditReport.items.forEach { item ->
            println("[${item.status}] ${item.category} :: ${item.featureName} -> ${item.details}")
        }
        println("=================================================")

        assertTrue("Overall compliance must be at least 90%", auditReport.overallCompliancePercentage >= 90f)
        assertEquals("There should be 0 failed critical features", 0, auditReport.failedCount)
    }

    @Test
    fun `test firebase firestore schema contract and collections`() {
        assertEquals("2.0.0", FirestoreSchema.SCHEMA_VERSION)
        assertEquals("workspace_listings", FirestoreSchema.Collections.WORKSPACE_LISTINGS)
        assertEquals("user_profiles", FirestoreSchema.Collections.USER_PROFILES)
        assertEquals("booking_requests", FirestoreSchema.Collections.BOOKING_REQUESTS)
        assertEquals("subscription_formulas", FirestoreSchema.Collections.SUBSCRIPTION_FORMULAS)
        assertEquals("whish_transactions", FirestoreSchema.Collections.WHISH_TRANSACTIONS)
        assertEquals("audit_security_logs", FirestoreSchema.Collections.AUDIT_SECURITY_LOGS)
    }

    @Test
    fun `test firestore service compliance auditor`() {
        val report = firestoreService.runDataConnectComplianceAudit()
        assertTrue("Data Connect compliance report must pass", report.isAllCompliant)
        assertTrue("Must include all core schema checks", report.checks.isNotEmpty())
    }

    @Test
    fun `test whish money security and api integrity`() {
        val signature = WhishSecurity.generateSignature(
            channel = WhishSecurity.CHANNEL_ID,
            amount = 150.0,
            currency = "USD",
            orderId = "TEST-ORDER-777"
        )
        assertNotNull(signature)
        assertEquals(64, signature.length)

        // Verify API client initialization
        assertNotNull(WhishPayApi.service)
    }

    @Test
    fun `test multi-tier rental formulas and booking lifecycle`() {
        val spaces = repository.spaces.value
        assertTrue("Repository must load seed workspaces", spaces.isNotEmpty())

        val space = spaces.first()
        val formula = space.rentalFormulas.first()

        val practitioner = repository.login(uid = "uid-dr-sami", email = "dr.sami@prospace.lb", verifiedRole = UserRole.PROFESSIONAL)
        val booking = repository.createBookingRequest(
            space = space,
            formula = formula,
            practitioner = practitioner,
            startDate = "2026-09-01",
            durationMonths = 2,
            notes = "Dermatology session clinic booking",
            selectedDays = formula.daysOfWeek,
            selectedStartHour = formula.startHour,
            selectedEndHour = formula.endHour,
            selectedShift = "Morning Shift",
            calculatedTotalUsd = formula.rateUsd * 2
        )

        assertNotNull(booking)
        assertEquals(BookingRequestStatus.PENDING, booking.status)

        // Accept booking
        val accepted = repository.acceptBookingRequest(booking.id)
        assertTrue(accepted)

        val updated = repository.bookingRequests.value.find { it.id == booking.id }
        assertEquals(BookingRequestStatus.ACCEPTED, updated?.status)
    }

    @Test
    fun `test master csv exports across all core data domains`() {
        val spacesCsv = repository.exportWorkspacesCsv()
        val bookingsCsv = repository.exportBookingsCsv()
        val usersCsv = repository.exportUsersCsv()
        val txCsv = repository.exportTransactionsCsv()

        assertTrue("Workspaces CSV must have headers and content", spacesCsv.contains("Space ID,Title"))
        assertTrue("Bookings CSV must have headers and content", bookingsCsv.contains("Booking ID,Space ID"))
        assertTrue("Users CSV must have headers and content", usersCsv.contains("User ID,Full Name"))
        assertTrue("Transactions CSV must have headers and content", txCsv.contains("Transaction ID,Order ID"))
    }
}
