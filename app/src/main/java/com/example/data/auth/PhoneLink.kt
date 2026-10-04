package com.example.data.auth

/**
 * Whether the signed-in Firebase Auth account has a phone number linked — the proof that
 * KYC phone verification happened (KycScreen / PhoneVerificationSection link the SMS
 * credential). Safe without Firebase (unit tests): false.
 */
object PhoneLink {
    fun isLinked(): Boolean = runCatching {
        !com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.phoneNumber.isNullOrBlank()
    }.getOrDefault(false)
}
