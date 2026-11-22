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

/** What Apple's sheet returned: an [idToken] (with the name Apple sends only on the first sign-in), a cancel, or an [error]. */
class AppleSignInReply(
    val idToken: String?,
    val givenName: String?,
    val familyName: String?,
    val cancelled: Boolean,
    val error: String?
)

/** Sign in with Apple (Swift `AppleSignInBridgeImpl`, AuthenticationServices). The request carries SHA-256 of [rawNonce]. */
interface AppleSignInBridge {
    fun signInWithAppleSheet(rawNonce: String, completion: (AppleSignInReply) -> Unit)

    /**
     * Before account deletion (App Review 5.1.1(v)): when the signed-in user has the apple.com provider, asks Apple for a fresh
     * authorization code and revokes the user's Apple tokens through Firebase (`Auth.revokeToken`). [done] gets
     * [RevokeCallback.DONE] (revoked, or not an Apple account), [RevokeCallback.CANCELLED] or [RevokeCallback.FAILED].
     */
    fun revokeAppleTokenIfNeeded(done: RevokeCallback)
}

/** Outcome of [AppleSignInBridge.revokeAppleTokenIfNeeded] (a named interface: Swift sees a plain Int32). */
fun interface RevokeCallback {
    fun finished(outcome: Int)

    companion object {
        const val DONE = 0
        const val CANCELLED = 1
        const val FAILED = 2
    }
}
