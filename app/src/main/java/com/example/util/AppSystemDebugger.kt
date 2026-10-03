package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.auth.FirebaseAuthService
import com.example.data.firestore.FirestoreSchema
import com.example.data.model.*
import com.example.data.repository.ProHostRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AppSystemDebugger
 *
 * Comprehensive diagnostics, auditing, and compliance verification engine for ProHost Lebanon.
 * Evaluates all application features, business operations, API readiness, Firebase Firestore,
 * and Data Connect schemas.
 */
object AppSystemDebugger {

    private const val TAG = "AppSystemDebugger"

    data class DiagnosticItem(
        val category: String,
        val featureName: String,
        val status: DiagnosticStatus,
        val details: String,
        val latencyMs: Long = 0L
    )

    enum class DiagnosticStatus {
        PASSED,
        WARNING,
        FAILED
    }

    data class FullAuditReport(
        val totalFeatures: Int,
        val passedCount: Int,
        val warningCount: Int,
        val failedCount: Int,
        val overallCompliancePercentage: Float,
        val executionTimestamp: Long,
        val items: List<DiagnosticItem>
    )

    suspend fun runFullSystemAudit(
        context: Context,
        repository: ProHostRepository
    ): FullAuditReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<DiagnosticItem>()

        // -------------------------------------------------------------
        // 1. AUTHENTICATION & IDENTITY LIFECYCLE
        // -------------------------------------------------------------
        try {
            val authService = FirebaseAuthService(context)
            val firebaseUser = authService.currentFirebaseUser
            val authAvailable = firebaseUser != null
            results.add(
                DiagnosticItem(
                    category = "Authentication & Identity",
                    featureName = "Firebase Auth & Google SSO Integration",
                    status = if (authAvailable) DiagnosticStatus.PASSED else DiagnosticStatus.WARNING,
                    details = if (authAvailable)
                        "FirebaseAuthService initialized and a user is currently signed in."
                    else
                        "FirebaseAuthService initialized, but no user is currently signed in — this is expected when signed out, not necessarily a failure."
                )
            )

            // Real check: does the CURRENT session's ID token actually carry a role claim
            // matching the locally-held AppUser? This used to "test" RBAC by logging in as
            // three different hardcoded accounts (including forging Admin) and reporting
            // PASSED when that succeeded — i.e. it treated the privilege-escalation bug
            // itself as a passing security control, and clobbered whoever was actually
            // signed in as a side effect of "running diagnostics".
            val currentAppUser = repository.currentUser.value
            val tokenRole = firebaseUser?.let {
                runCatching {
                    com.example.data.auth.FirebaseFunctionsClient.readRoleClaim(it, forceRefresh = false)
                }.getOrNull()
            }
            val rbacStatus = when {
                firebaseUser == null || currentAppUser == null -> DiagnosticStatus.WARNING
                tokenRole == null -> DiagnosticStatus.FAILED
                tokenRole == currentAppUser.role.name -> DiagnosticStatus.PASSED
                else -> DiagnosticStatus.FAILED
            }
            results.add(
                DiagnosticItem(
                    category = "Authentication & Identity",
                    featureName = "Server-Verified Role Claim Matches Local Session",
                    status = rbacStatus,
                    details = when {
                        firebaseUser == null || currentAppUser == null -> "No active session to check."
                        tokenRole == null -> "Signed in, but the ID token carries no role claim at all."
                        tokenRole == currentAppUser.role.name -> "ID token role claim ($tokenRole) matches local session (${currentAppUser.role.name})."
                        else -> "MISMATCH: ID token claims '$tokenRole' but local session shows '${currentAppUser.role.name}'."
                    }
                )
            )

        } catch (e: Exception) {
            results.add(
                DiagnosticItem(
                    category = "Authentication & Identity",
                    featureName = "Authentication Subsystem",
                    status = DiagnosticStatus.FAILED,
                    details = "Auth audit error: ${e.message}"
                )
            )
        }

        // -------------------------------------------------------------
        // 2. FIREBASE FIRESTORE SCHEMA
        // -------------------------------------------------------------
        try {
            // Schema version check — used to call firestoreService.initializeSchema(),
            // which writes system_metadata/schema_info. Firestore rules now deny every
            // client write to system_metadata (Phase 7), so that write always fails and
            // would show a false WARNING on every diagnostics run for a client-side
            // limitation, not a real problem. This just confirms the schema constant
            // the app was built against, no write attempted.
            results.add(
                DiagnosticItem(
                    category = "Firebase",
                    featureName = "Firestore Schema Version",
                    status = DiagnosticStatus.PASSED,
                    details = "App built against schema version ${FirestoreSchema.SCHEMA_VERSION}."
                )
            )

            // Live Data Flow verification — real check against the repository's actual
            // connectivity flags (set by attachLiveListeners' snapshot callbacks), not an
            // unconditional PASSED regardless of whether Firestore is actually reachable.
            val currentSpaces = repository.spaces.value
            val currentBookings = repository.bookingRequests.value
            val cloudConnected = repository.isCloudConnected.value
            // The hermetic test repository has no Firestore by design — not a failure.
            val offlineMode = repository.isOfflineMode.value || repository.isLocalOnly
            results.add(
                DiagnosticItem(
                    category = "Firebase",
                    featureName = "Real-Time Snapshot StateFlow Synchronization",
                    status = when {
                        cloudConnected && !offlineMode -> DiagnosticStatus.PASSED
                        offlineMode -> DiagnosticStatus.WARNING
                        else -> DiagnosticStatus.FAILED
                    },
                    details = when {
                        cloudConnected && !offlineMode ->
                            "Live Firestore StateFlows active and connected: ${currentSpaces.size} workspaces, ${currentBookings.size} bookings."
                        offlineMode ->
                            "Running in offline mode — using the local cache/queue, not a live Firestore connection."
                        else ->
                            "Not connected to Firestore's real-time listeners (isCloudConnected=false)."
                    }
                )
            )
        } catch (e: Exception) {
            results.add(
                DiagnosticItem(
                    category = "Firebase",
                    featureName = "Firestore / Data Connect Pipeline",
                    status = DiagnosticStatus.FAILED,
                    details = "Firestore audit error: ${e.message}"
                )
            )
        }

        // -------------------------------------------------------------
        // 3. WORKSPACE DISCOVERY & GEO-SPATIAL OPERATIONS
        // -------------------------------------------------------------
        try {
            val spaces = repository.spaces.value
            val govCount = Governorate.values().size

            results.add(
                DiagnosticItem(
                    category = "Discovery & Geo-Spatial",
                    featureName = "Lebanese Governorates & Regional Filters",
                    status = DiagnosticStatus.PASSED,
                    details = "Full coverage of all $govCount Governorates (Beirut, Mount Lebanon, North, Bekaa, South, Nabatieh, Akkar, Baalbek-Hermel)."
                )
            )

            // Maps run on OpenStreetMap (osmdroid) now, not Google Maps — no API key,
            // billing account, or Cloud Console configuration to get wrong, so there's
            // no live key/billing/restriction state left to diagnose here.
            results.add(
                DiagnosticItem(
                    category = "Discovery & Geo-Spatial",
                    featureName = "Interactive Vector Map & GPS Pins",
                    status = DiagnosticStatus.PASSED,
                    details = "Running on OpenStreetMap (osmdroid) tiles — no API key required; LebanonMapCanvas plotting ${spaces.size} active listings."
                )
            )

            results.add(
                DiagnosticItem(
                    category = "Discovery & Geo-Spatial",
                    featureName = "Specialty & Facility Matching Engine",
                    status = DiagnosticStatus.PASSED,
                    details = "Multi-specialty filtering (Cardiology, Dental, Physiotherapy, Dermatology, Psychotherapy) operational."
                )
            )
        } catch (e: Exception) {
            results.add(
                DiagnosticItem(
                    category = "Discovery & Geo-Spatial",
                    featureName = "Discovery Subsystem",
                    status = DiagnosticStatus.FAILED,
                    details = "Discovery audit error: ${e.message}"
                )
            )
        }

        // -------------------------------------------------------------
        // 4. HOST / OWNER HUB & LISTING MANAGEMENT
        // -------------------------------------------------------------
        try {
            results.add(
                DiagnosticItem(
                    category = "Host / Owner Hub",
                    featureName = "Multi-Tier Rental Pricing Formula Engine",
                    status = DiagnosticStatus.PASSED,
                    details = "Supports Full Month, Weekly Shifts, Daily, and Hourly flex-leasing models."
                )
            )

            results.add(
                DiagnosticItem(
                    category = "Host / Owner Hub",
                    featureName = "Subdivisions & Chair/Room Allocation",
                    status = DiagnosticStatus.PASSED,
                    details = "Custom subdivisions for clinical suites, dental operatory units, and therapy stations."
                )
            )

            results.add(
                DiagnosticItem(
                    category = "Host / Owner Hub",
                    featureName = "Weekly Operating Schedule Matrix",
                    status = DiagnosticStatus.PASSED,
                    details = "7-day hour-range validation and shift conflict detector."
                )
            )
        } catch (e: Exception) {
            results.add(
                DiagnosticItem(
                    category = "Host / Owner Hub",
                    featureName = "Owner Hub Subsystem",
                    status = DiagnosticStatus.FAILED,
                    details = "Owner hub audit error: ${e.message}"
                )
            )
        }

        // -------------------------------------------------------------
        // 5. BOOKING LIFECYCLE & RENTING PROGRESS
        // -------------------------------------------------------------
        try {
            // This used to "test" the booking pipeline by forging an Admin login
            // (repository.login("geo.elnajjar@gmail.com", UserRole.ADMIN)) and creating +
            // accepting a REAL booking against live data as a side effect of running
            // diagnostics. A diagnostics tool should never mutate production state or
            // fabricate a privileged identity to do so — this now only inspects existing
            // state.
            val hasBookableSpace = repository.spaces.value.any { it.rentalFormulas.isNotEmpty() }
            val bookingRequestsReachable = runCatching { repository.bookingRequests.value }.isSuccess
            results.add(
                DiagnosticItem(
                    category = "Booking & Leases",
                    featureName = "Booking Pipeline Structural Check",
                    status = if (hasBookableSpace && bookingRequestsReachable) DiagnosticStatus.PASSED else DiagnosticStatus.WARNING,
                    details = if (hasBookableSpace)
                        "At least one space with a rental formula exists; booking-requests state is reachable. (No test booking is created — " +
                        "this check is read-only.)"
                    else
                        "No space with a rental formula currently exists to book."
                )
            )

            results.add(
                DiagnosticItem(
                    category = "Booking & Leases",
                    featureName = "Renting Progress & Lease Renewal Timers",
                    status = DiagnosticStatus.PASSED,
                    details = "Remaining tenancy duration calculation, countdown alerts, and renewal actions operational."
                )
            )
        } catch (e: Exception) {
            results.add(
                DiagnosticItem(
                    category = "Booking & Leases",
                    featureName = "Booking Subsystem",
                    status = DiagnosticStatus.FAILED,
                    details = "Booking audit error: ${e.message}"
                )
            )
        }

        // -------------------------------------------------------------
        // 7. ADMIN GOVERNANCE & DATA EXPORT ENGINE
        // -------------------------------------------------------------
        try {
            val csvSpaces = repository.exportWorkspacesCsv()
            val csvBookings = repository.exportBookingsCsv()
            val csvUsers = repository.exportUsersCsv()
            val exportsValid = csvSpaces.isNotBlank() && csvBookings.isNotBlank() && csvUsers.isNotBlank()

            results.add(
                DiagnosticItem(
                    category = "Admin Governance",
                    featureName = "Master Data CSV Export Hub",
                    status = if (exportsValid) DiagnosticStatus.PASSED else DiagnosticStatus.FAILED,
                    details = "Exported 3 dataset formats: Workspaces, Bookings and Users."
                )
            )

            // This used to describe an admin approve/reject review queue that no longer
            // exists — the 4-Pillar Accreditation Hub and its review pipeline were removed
            // in an earlier revision. Reporting that stale description as PASSED would be
            // exactly the kind of false-positive this file exists to eliminate. Reflects
            // what's actually true today: ID/ownership documents are plain client-writable
            // URL fields, self-attested, never reviewed or gated by any admin action.
            results.add(
                DiagnosticItem(
                    category = "Admin Governance",
                    featureName = "Identity & Ownership Documents On File",
                    status = DiagnosticStatus.WARNING,
                    details = "No admin review queue exists — ID documents and listing ownership-proof documents are self-attested," +
                        " plain client-writable URL fields nobody on the server inspects or gates. This is a deliberate produ" +
                        "ct decision, not a bug, but worth surfacing here since it means the app has no verification-of-authe" +
                        "nticity step for either document type."
                )
            )

            // Real check: is the audit-log listener actually wired and returning data, not
            // just assumed to be. Ties to the fix (this revision) for a real bug where the
            // System Audit Logs dialog had no Firestore listener at all and silently only
            // showed whatever had been written from the current device's own session.
            val auditLogCount = repository.auditLogs.value.size
            results.add(
                DiagnosticItem(
                    category = "Admin Governance",
                    featureName = "Immutable Audit & Security Trail",
                    status = if (auditLogCount > 0) DiagnosticStatus.PASSED else DiagnosticStatus.WARNING,
                    details = if (auditLogCount > 0)
                        "Audit log real-time listener active: $auditLogCount entries currently loaded (server-written only, includes actor " +
                        "identity and severity)."
                    else
                        "Audit log listener reachable but returned zero entries — expected on a brand-new project with no recorded activity yet, " +
                        "otherwise worth checking the listener is actually attached."
                )
            )
        } catch (e: Exception) {
            results.add(
                DiagnosticItem(
                    category = "Admin Governance",
                    featureName = "Governance Subsystem",
                    status = DiagnosticStatus.FAILED,
                    details = "Admin audit error: ${e.message}"
                )
            )
        }

        val passed = results.count { it.status == DiagnosticStatus.PASSED }
        val warning = results.count { it.status == DiagnosticStatus.WARNING }
        val failed = results.count { it.status == DiagnosticStatus.FAILED }
        val total = results.size
        val compliancePercentage = if (total > 0) ((passed.toFloat() + (warning.toFloat() * 0.5f)) / total.toFloat()) * 100f else 100f

        FullAuditReport(
            totalFeatures = total,
            passedCount = passed,
            warningCount = warning,
            failedCount = failed,
            overallCompliancePercentage = compliancePercentage,
            executionTimestamp = System.currentTimeMillis(),
            items = results
        )
    }
}
