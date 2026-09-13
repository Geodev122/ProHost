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
    const val DATA_CONNECT_SERVICE_ID = "prospace-dataconnect-medlb"
    const val DEFAULT_DATABASE_ID = "(default)"

    // Core Collections
    object Collections {
        const val WORKSPACE_LISTINGS = "workspace_listings"
        const val USER_PROFILES = "user_profiles"
        const val SUBSCRIPTION_FORMULAS = "subscription_formulas"
        const val BOOKING_REQUESTS = "booking_requests"
        const val WHISH_TRANSACTIONS = "whish_transactions"
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
    }
}
