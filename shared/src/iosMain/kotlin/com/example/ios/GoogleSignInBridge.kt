package com.example.ios

/** What the Google sign-in sheet returned on iOS: an [idToken], a cancel, or an [error]. Built by Swift. */
class GoogleSignInReply(
    val idToken: String?,
    val displayName: String?,
    val givenName: String?,
    val familyName: String?,
    val pictureUri: String?,
    val cancelled: Boolean,
    val error: String?
)

/** Google Sign-In for iOS (Swift `GoogleSignInBridgeImpl`, GoogleSignIn SDK). [nonce] goes into the ID token unchanged. */
interface GoogleSignInBridge {
    fun signIn(nonce: String, completion: (GoogleSignInReply) -> Unit)
}
