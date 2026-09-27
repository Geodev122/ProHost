package com.example.data.firestore

/**
 * ProHost Cloud Firestore collection-name constants — the single source of truth
 * `FirestoreService.kt` uses for every `.collection(...)` call. There is no
 * per-field constants object anymore (the WorkspaceFields/UserFields/FormulaFields/
 * SystemMetadataFields/CredentialFields objects that used to live here were dead
 * code with zero call sites — every real read/write already goes through
 * DataModels.kt's own toFirestoreMap()/fromFirestoreMap() string keys directly,
 * and several of those removed constants named fields that no longer exist at all,
 * like the accreditation system's verificationStatus/verificationTier/trustScore).
 */
object FirestoreSchema {

    const val SCHEMA_VERSION = "2.0.0"
    const val DEFAULT_DATABASE_ID = "(default)"

    // Core Collections
    object Collections {
        const val WORKSPACE_LISTINGS = "workspace_listings"
        const val USER_PROFILES = "user_profiles"
        const val BOOKING_REQUESTS = "booking_requests"
        const val AUDIT_SECURITY_LOGS = "audit_security_logs"
        const val SYSTEM_METADATA = "system_metadata"
        const val SCHEMA_ARCHITECTURE = "schema_architecture"
        // Admin-managed, purchasable Pro Host packages (PackagePlanCatalog, one doc
        // "main" holding a map of PackagePlan keyed by id) — replaces the old closed
        // OwnerPackageTier enum + PAYG credit system entirely.
        const val PACKAGE_PLANS = "package_plans"
        // One document per hashtag (doc id = the lowercased tag text), incremented
        // on every publish that carries it — feeds the Target Disciplines autosuggest
        // in CreateListingDialog and an Admin analytics view, spec section 1.4.
        const val HASHTAG_USAGE = "hashtag_usage"
        // Admin-uploaded, versioned HTML for the 3 legal documents (Privacy Policy /
        // Terms of Use / Revocation Policy) — see LegalDocumentVersion's own doc
        // comment for the parent-doc-plus-versions-subcollection shape.
        const val LEGAL_DOCUMENTS = "legal_documents"
        // Unresolvable Play Billing RTDN events (no obfuscatedExternalAccountId) —
        // written by playBillingRtdn Cloud Function (Admin SDK) for manual admin triage.
        const val PLAY_BILLING_UNRESOLVED = "play_billing_unresolved"
    }
}
