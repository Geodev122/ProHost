package com.example.data.auth

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
interface AuthBridge {
    fun currentUid(): String?
    fun usesPassword(): Boolean
    fun signInWithPassword(email: String, password: String, completion: (AuthReply) -> Unit)
    fun createAccount(email: String, password: String, completion: (AuthReply) -> Unit)
    fun signInWithGoogle(idToken: String, completion: (AuthReply) -> Unit)
    fun sendPasswordResetEmail(email: String, completion: (AuthReply) -> Unit)
    /** Reloads the user; true when the email is verified (and the ID token was then refreshed). */
    fun reloadEmailVerified(completion: (Boolean) -> Unit)
    fun signOut()
}

/** [AuthGateway] over an [AuthBridge], with the outcomes of the Android `FirebaseAuthService` (`BridgedAuthGatewayTest`). Used by iOS. */
class BridgedAuthGateway(private val bridge: AuthBridge) : AuthGateway {
    override val signedInUid: String? get() = bridge.currentUid()
    override val usesPassword: Boolean get() = bridge.usesPassword()

    private suspend fun run(isNewUserOnSuccess: Boolean? = null, call: ((AuthReply) -> Unit) -> Unit): AuthOutcome {
        val reply = suspendCancellableCoroutine { cont -> call { cont.resume(it) } }
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

    override suspend fun sendPasswordResetEmail(email: String): AuthOutcome =
        run(isNewUserOnSuccess = false) { bridge.sendPasswordResetEmail(email, it) }

    override suspend fun refreshEmailVerified(): Boolean {
        if (bridge.currentUid() == null) return false
        return suspendCancellableCoroutine { cont -> bridge.reloadEmailVerified { cont.resume(it) } }
    }

    override fun signOut() = bridge.signOut()
}
