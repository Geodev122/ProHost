package com.example.data.gateway

import com.example.data.auth.AuthBridge
import com.example.data.auth.AuthError
import com.example.data.auth.AuthOutcome
import com.example.data.auth.AuthReply
import com.example.data.auth.BridgedAuthGateway
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The shared (iOS) auth gateway must give the same outcomes as the Android `FirebaseAuthService` for the same Firebase answers. */
class BridgedAuthGatewayTest {
    private class FakeBridge(var reply: AuthReply, var uid: String? = "u1") : AuthBridge {
        override fun currentUid() = uid
        override fun usesPassword() = true
        override fun signInWithPassword(email: String, password: String, completion: (AuthReply) -> Unit) = completion(reply)
        override fun createAccount(email: String, password: String, completion: (AuthReply) -> Unit) = completion(reply)
        override fun signInWithGoogle(idToken: String, completion: (AuthReply) -> Unit) = completion(reply)
        override fun sendPasswordResetEmail(email: String, completion: (AuthReply) -> Unit) = completion(reply)
        override fun reloadEmailVerified(completion: (Boolean) -> Unit) = completion(true)
        override fun signOut() { uid = null }
    }

    @Test fun successFlagsMatchAndroid() = runBlocking {
        val b = FakeBridge(AuthReply(null, null, isNewUser = true))
        val g = BridgedAuthGateway(b)
        assertEquals(AuthOutcome.Success(false), g.signInWithPassword("a", "b"))
        assertEquals(AuthOutcome.Success(true), g.createAccount("a", "b"))
        assertEquals(AuthOutcome.Success(true), g.signInWithGoogle("t"))
        assertEquals(AuthOutcome.Success(false), g.sendPasswordResetEmail("a"))
    }

    @Test fun errorsMapThroughAndroidCodeNames() = runBlocking {
        val cases = mapOf(
            "ERROR_WRONG_PASSWORD" to AuthError.INVALID_CREDENTIALS,
            "ERROR_EMAIL_ALREADY_IN_USE" to AuthError.EMAIL_IN_USE,
            "ERROR_NETWORK_REQUEST_FAILED" to AuthError.NETWORK,
            "ERROR_TOO_MANY_REQUESTS" to AuthError.TOO_MANY_REQUESTS,
            "ERROR_SOMETHING_NEW" to AuthError.UNKNOWN
        )
        for ((code, expected) in cases) {
            val out = BridgedAuthGateway(FakeBridge(AuthReply(code, "x", false))).signInWithPassword("a", "b")
            assertEquals(code, AuthOutcome.Failure(expected, code), out)
        }
        val weak = BridgedAuthGateway(FakeBridge(AuthReply("ERROR_X", "PASSWORD_DOES_NOT_MEET_REQUIREMENTS: min 8", false))).createAccount("a", "b")
        assertEquals(AuthOutcome.Failure(AuthError.WEAK_PASSWORD, "ERROR_X"), weak)
    }

    @Test fun emailVerifiedNeedsASignedInUser() = runBlocking {
        val b = FakeBridge(AuthReply(null, null, false), uid = null)
        assertFalse(BridgedAuthGateway(b).refreshEmailVerified())
    }
}
