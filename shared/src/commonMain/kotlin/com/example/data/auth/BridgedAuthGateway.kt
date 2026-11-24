package com.example.data.auth

import com.example.util.resumeOnce
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The answer of one Firebase Auth call made by the platform: [errorCode] null = success, else the Android
 * `FirebaseAuthException.errorCode` name (`ERROR_WRONG_PASSWORD`, `ERROR_NETWORK_REQUEST_FAILED`, ...) with its [message].
 */
data class AuthReply(val errorCode: String?, val message: String?, val isNewUser: Boolean)

/**
 * Firebase Auth on the platform's SDK, as plain callbacks so a Swift class can implement it (iOS: `FirebaseAuthBridge.swift`).
 * Error codes are translated to the Android names so [AuthError.fromCode] reads them the same way on both phones.
 */
/** A single-Boolean callback. A named interface (not `(Boolean) -> Unit`) so Swift never has to build a Kotlin Boolean box. */
fun interface BoolCallback {
    fun done(value: Boolean)
}

interface AuthBridge {
    fun currentUid(): String?
    fun usesPassword(): Boolean
    fun signInWithPassword(email: String, password: String, completion: (AuthReply) -> Unit)
    fun createAccount(email: String, password: String, completion: (AuthReply) -> Unit)
    fun signInWithGoogle(idToken: String, completion: (AuthReply) -> Unit)
    fun sendPasswordResetEmail(email: String, completion: (AuthReply) -> Unit)
    /** Reloads the user; true when the email is verified (and the ID token was then refreshed). */
    fun reloadEmailVerified(completion: BoolCallback)
    fun signOut()
    /** Email and phone number of the signed-in user (null when signed out or not set). */
    /** Firebase sign-in with an Apple credential (`OAuthProvider.appleCredential`). */
    fun signInWithApple(idToken: String, rawNonce: String, givenName: String?, familyName: String?, completion: (AuthReply) -> Unit) {
        completion(AuthReply("ERROR_OPERATION_NOT_ALLOWED", null, false))
    }
    fun currentEmail(): String? = null
    fun currentPhoneNumber(): String? = null
    /** The ID token's `role` claim, fetching a fresh token first when [forceRefresh] (Android `getIdToken(force)`). */
    fun readRoleClaim(forceRefresh: Boolean, completion: (TokenReply) -> Unit) { completion(TokenReply(null, null)) }
    /** Forces a fresh ID token for the signed-in user (no-op when signed out). */
    fun refreshIdToken(completion: (TokenReply) -> Unit) { completion(TokenReply(null, null)) }
}

/** An ID-token answer from the platform: [value] (e.g. the role claim) or the [error] message of a failure. */
class TokenReply(val value: String?, val error: String?)

/** The signed-in account behind [bridge] as [SignedInUser]; token failures throw, as Firebase's `getIdToken` does on Android. */
fun signedInUserOf(bridge: AuthBridge): SignedInUser? {
    val uid = bridge.currentUid() ?: return null
    return SignedInUser(uid = uid, email = bridge.currentEmail(), phoneNumber = bridge.currentPhoneNumber()) { force ->
        awaitToken { done -> bridge.readRoleClaim(force, done) }
    }
}

/** Forces a fresh ID token through [bridge]; throws on failure. */
suspend fun refreshIdTokenOf(bridge: AuthBridge) {
    awaitToken { done -> bridge.refreshIdToken(done) }
}

private suspend fun awaitToken(call: ((TokenReply) -> Unit) -> Unit): String? {
    val reply = suspendCancellableCoroutine<TokenReply> { cont -> call { cont.resumeOnce(it) } }
    reply.error?.let { throw IllegalStateException(it) }
    return reply.value
}

/** [AuthGateway] over an [AuthBridge], with the outcomes of the Android `FirebaseAuthService` (`BridgedAuthGatewayTest`). Used by iOS. */
class BridgedAuthGateway(private val bridge: AuthBridge) : AuthGateway {
    override val signedInUid: String? get() = bridge.currentUid()
    override val usesPassword: Boolean get() = bridge.usesPassword()

    private suspend fun run(isNewUserOnSuccess: Boolean? = null, call: ((AuthReply) -> Unit) -> Unit): AuthOutcome {
        val reply = suspendCancellableCoroutine { cont -> call { cont.resumeOnce(it) } }
        val code = reply.errorCode ?: return AuthOutcome.Success(isNewUser = isNewUserOnSuccess ?: reply.isNewUser)
        val error = AuthError.fromCode(code).let {
            // Newer SDKs put the server reason in the message ("PASSWORD_DOES_NOT_MEET_REQUIREMENTS: …"), as on Android.
            if (it == AuthError.UNKNOWN && reply.message?.contains("PASSWORD_DOES_NOT_MEET_REQUIREMENTS") == true) AuthError.WEAK_PASSWORD else it
        }
        return AuthOutcome.Failure(error, code)
    }

    override suspend fun signInWithPassword(email: String, password: String): AuthOutcome =
        run(isNewUserOnSuccess = false) { bridge.signInWithPassword(email, password, it) }

    override suspend fun createAccount(email: String, password: String): AuthOutcome =
        run(isNewUserOnSuccess = true) { bridge.createAccount(email, password, it) }

    override suspend fun signInWithGoogle(idToken: String): AuthOutcome = run { bridge.signInWithGoogle(idToken, it) }

    override suspend fun signInWithApple(idToken: String, rawNonce: String, givenName: String?, familyName: String?): AuthOutcome =
        run { bridge.signInWithApple(idToken, rawNonce, givenName, familyName, it) }

    override suspend fun sendPasswordResetEmail(email: String): AuthOutcome =
        run(isNewUserOnSuccess = false) { bridge.sendPasswordResetEmail(email, it) }

    override suspend fun refreshEmailVerified(): Boolean {
        if (bridge.currentUid() == null) return false
        return suspendCancellableCoroutine { cont -> bridge.reloadEmailVerified(BoolCallback { cont.resumeOnce(it) }) }
    }

    override fun signOut() = bridge.signOut()
}
