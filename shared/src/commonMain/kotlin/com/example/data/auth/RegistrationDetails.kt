package com.example.data.auth

/**
 * Groups the registration-form fields shared by [completeVerifiedRegistration] and
 * [completeGoogleRegistration] so neither signature needs a long flat parameter list.
 */
data class RegistrationDetails(
    val fullName: String,
    val email: String,
    val phone: String,
    val specialty: String,
    val profilePictureUrl: String?,
    val country: String,
    val governorate: String,
    val city: String,
    val tosAccepted: Boolean
)
