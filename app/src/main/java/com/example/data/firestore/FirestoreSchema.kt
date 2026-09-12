package com.example.data.firestore

/**
 * ProHost Cloud Firestore & Data Connect Schema Definitions
 * 
 * Formalized NoSQL collections, indexes, and document contracts supporting:
 * 1. Professional Workspace Listings with Subdivisions and Operating Schedules
 * 2. Member User Profiles with Lebanese Syndicate Verification Tiers & Accreditations
 * 3. Flexible Multi-Tier Subscription & Rental Formulas (Shift, Day-per-Week, Hourly, Monthly)
 * 4. Booking Requests & Access Passes with Real-Time State Sync
 * 5. Credential Documents with Verification Lifecycle Hashes
 * 6. Whish Money Financial Audit Trails & Security Logs
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
        // One document per hashtag (doc id = the lowercased tag text), incremented
        // on every publish that carries it — feeds the Target Disciplines autosuggest
        // in CreateListingDialog and an Admin analytics view, spec section 1.4.
        const val HASHTAG_USAGE = "hashtag_usage"
    }

    // Document Fields Contracts
    object WorkspaceFields {
        const val ID = "id"
        const val TITLE = "title"
        const val SPACE_TYPE = "spaceType"
        const val GOVERNORATE = "governorate"
        const val DISTRICT = "district"
        const val STREET_ADDRESS = "streetAddress"
        const val FLOOR_INFO = "floorInfo"
        const val LAT = "lat"
        const val LNG = "lng"
        const val IS_SHARED = "isShared"
        const val COMPLEMENTARY_SPECIALTIES = "complementarySpecialties"
        const val RESIDENT_PRACTITIONERS = "residentPractitioners"
        const val ESSENTIAL_FACILITIES = "essentialFacilities"
        const val EQUIPMENT = "equipment"
        const val RENTAL_FORMULAS = "rentalFormulas"
        const val SUBDIVISIONS = "subdivisions"
        const val RULES = "rules"
        const val SCHEDULE = "schedule"
        const val OWNER_ID = "ownerId"
        const val OWNER_NAME = "ownerName"
        const val OWNER_PHONE = "ownerPhone"
        const val OWNER_EMAIL = "ownerEmail"
        const val IS_VERIFIED = "isVerified"
        const val IS_ACTIVE_SUBSCRIPTION = "isActiveSubscription"
        const val SUBSCRIPTION_EXPIRY_MILLIS = "subscriptionExpiryMillis"
        const val IMAGE_URLS = "imageUrls"
        const val VIDEO_TOUR_DURATION_SEC = "videoTourDurationSec"
        const val BASE_MONTHLY_RATE_USD = "baseMonthlyRateUsd"
        const val AVATAR_ENGAGEMENT_VIEWS = "avatarEngagementViews"
        const val AVATAR_INQUIRY_CLICKS = "avatarInquiryClicks"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
    }

    object UserFields {
        const val ID = "id"
        const val EMAIL = "email"
        const val FULL_NAME = "fullName"
        const val ROLE = "role"
        const val SPECIALTY = "specialty"
        const val PHONE = "phone"
        const val AFFILIATION = "affiliation"
        const val SYNDICATE_NUMBER = "syndicateNumber"
        const val GOVERNORATE = "governorate"
        const val IS_VERIFIED = "isVerified"
        const val VERIFICATION_STATUS = "verificationStatus"
        const val VERIFICATION_TIER = "verificationTier"
        const val VERIFICATION_NOTES = "verificationNotes"
        const val TRUST_SCORE = "trustScore"
        const val SUBSCRIPTION_EXPIRY_MILLIS = "subscriptionExpiryMillis"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
    }

    object FormulaFields {
        const val ID = "id"
        const val TITLE = "title"
        const val TYPE = "type"
        const val BILLING_INTERVAL = "billingInterval"
        const val PRICE_USD = "priceUsd"
        const val DESCRIPTION = "description"
        const val DAYS_PER_WEEK = "daysPerWeek"
        const val HOURS_PER_DAY = "hoursPerDay"
        const val START_HOUR = "startHour"
        const val END_HOUR = "endHour"
        const val TARGET_SPECIALTIES = "targetSpecialties"
        const val INCLUDED_PERKS = "includedPerks"
        const val DISCOUNT_PERCENT = "discountPercent"
        const val IS_FEATURED = "isFeatured"
        const val IS_ACTIVE = "isActive"
        const val SPACE_ID = "spaceId"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
    }

    object SystemMetadataFields {
        const val SCHEMA_VERSION = "schemaVersion"
        const val UPDATED_AT = "updatedAt"
        const val IS_PACKAGING_GOVERNANCE_ACTIVE = "isPackagingGovernanceActive"
        const val GOVERNANCE_TAG = "governanceTag"
        const val MONTHLY_SUBSCRIPTION_FEE_USD = "monthlySubscriptionFeeUsd"
        const val PAYG_PRIVATE_OFFICE_USD = "paygPrivateOfficeUsd"
        const val PAYG_CENTER_USD = "paygCenterUsd"
        const val PAYG_POLYCLINIC_USD = "paygPolyclinicUsd"
        const val PAYG_COWORKING_USD = "paygCoworkingUsd"
        const val PAYG_EXECUTIVE_BOARDROOM_USD = "paygExecutiveBoardroomUsd"
        const val PAYG_CONSULTATION_SUITE_USD = "paygConsultationSuiteUsd"
        const val PACKAGE_2_LIMIT = "package2Limit"
        const val PACKAGE_2_MONTHLY_FEE_USD = "package2MonthlyFeeUsd"
        const val PACKAGE_3_MONTHLY_FEE_USD = "package3MonthlyFeeUsd"
        const val MERCHANT_CHANNEL_ID = "merchantChannelId"
        const val MERCHANT_SOURCE = "merchantSource"
    }

    object CredentialFields {
        const val ID = "id"
        const val USER_ID = "userId"
        const val TYPE = "type"
        const val FILE_NAME = "fileName"
        const val FILE_SIZE_KB = "fileSizeKb"
        const val UPLOADED_AT = "uploadedAt"
        const val STATUS = "status"
        const val DOCUMENT_NUMBER = "documentNumber"
        const val ISSUING_AUTHORITY = "issuingAuthority"
        const val EXPIRY_DATE = "expiryDate"
        const val REJECTION_REASON = "rejectionReason"
        const val FILE_URI = "fileUri"
        const val VERIFICATION_HASH = "verificationHash"
        const val REVIEWER_NOTES = "reviewerNotes"
        const val UPDATED_AT = "updatedAt"
    }
}
