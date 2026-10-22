package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.FakeFunctionsClient
import com.example.R
import com.example.analytics.AnalyticsSink
import com.example.analytics.AnalyticsTracker
import com.example.analytics.Event
import com.example.analytics.Param
import com.example.data.auth.AccountSuspendedException
import com.example.data.auth.AuthError
import com.example.data.auth.AuthGateway
import com.example.data.auth.AuthOutcome
import com.example.data.auth.RegistrationDetails
import com.example.data.auth.SessionFinisher
import com.example.data.model.AppUser
import com.example.data.model.UserRole
import com.example.hermeticRepository
import com.example.ui.util.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Email + password and Google sign-in routing, Forgot password and registration, without Firebase. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthViewModelTest {

    private class FakeGateway : AuthGateway {
        var outcome: AuthOutcome = AuthOutcome.Success(isNewUser = false)
        var resetOutcome: AuthOutcome = AuthOutcome.Success(isNewUser = false)
        override var signedInUid: String? = null
        val calls = mutableListOf<String>()
        var signedOut = false

        private fun result(call: String): AuthOutcome {
            calls += call
            if (outcome is AuthOutcome.Success) signedInUid = "uid-1"
            return outcome
        }

        override suspend fun signInWithPassword(email: String, password: String) = result("signIn:$email")
        override suspend fun createAccount(email: String, password: String) = result("create:$email")
        override suspend fun signInWithGoogle(idToken: String) = result("google")
        override suspend fun sendPasswordResetEmail(email: String): AuthOutcome { calls += "firebaseReset:$email"; return resetOutcome }
        override suspend fun refreshEmailVerified() = false
        override val usesPassword = true
        override fun signOut() { signedOut = true; signedInUid = null }
    }

    private class FakeSession : SessionFinisher {
        var user = AppUser(id = "uid-1", email = "a@b.co", fullName = "Rana Haddad", role = UserRole.SPECIALIST, specialty = "", phone = "")
        var loginError: Exception? = null
        var profileComplete = false
        var registered: RegistrationDetails? = null
        var discarded = false
        override suspend fun completeLogin(): AppUser { loginError?.let { throw it }; return user }
        override suspend fun completeRegistration(details: RegistrationDetails): AppUser { registered = details; return user.copy(fullName = details.fullName) }
        override suspend fun uploadProfilePicture(uri: Uri): String? = null
        override suspend fun existingProfileComplete() = profileComplete
        override suspend fun discardIncompleteSession() { discarded = true }
        override suspend fun syncEmailVerified() {}
    }

    private class Functions : FakeFunctionsClient() {
        var resetResult: Result<Unit> = Result.success(Unit)
        val resets = mutableListOf<String>()
        override suspend fun sendPasswordResetLink(email: String): Result<Unit> { resets += email; return resetResult }
    }

    private class Sink : AnalyticsSink {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun logEvent(name: String, params: Map<String, Any>) { events += name to params }
        override fun setUserId(id: String?) {}
        override fun setUserProperty(name: String, value: String?) {}
        override fun setCollectionEnabled(enabled: Boolean) {}
        override fun setAnalyticsConsent(granted: Boolean) {}
        override fun resetData() {}
        override fun appInstanceId(onResult: (String?) -> Unit) = onResult(null)
    }

    private lateinit var gateway: FakeGateway
    private lateinit var session: FakeSession
    private lateinit var functions: Functions
    private lateinit var sink: Sink
    private lateinit var vm: AuthViewModel
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        UiText.init(context.resources)
        sink = Sink()
        AnalyticsTracker.sink = sink
        AnalyticsTracker.applyConsent(granted = true, reset = false)
        gateway = FakeGateway()
        session = FakeSession()
        functions = Functions()
        vm = AuthViewModel(hermeticRepository(), gateway, functions, session)
    }

    @After
    fun tearDown() {
        AnalyticsTracker.applyConsent(granted = false, reset = false)
        AnalyticsTracker.sink = null
        Dispatchers.resetMain()
    }

    private fun text(id: Int) = context.getString(id)

    @Test
    fun `returning user with a complete profile goes straight in`() {
        var routed: Boolean? = null
        vm.signIn("  Rana@Example.COM ", "clinic2026") { routed = it }
        assertEquals(false, routed)
        assertEquals(listOf("signIn:rana@example.com"), gateway.calls)
        assertFalse(vm.isAuthenticating.value)
        assertNull(vm.authErrorMessage.value)
        assertTrue(sink.events.any { it.first == Event.LOGIN && it.second[Param.METHOD] == "password" })
    }

    @Test
    fun `signed in but never registered goes to the registration form`() {
        session.user = session.user.copy(fullName = "")
        var routed: Boolean? = null
        vm.signIn("rana@example.com", "clinic2026") { routed = it }
        assertEquals(true, routed)
        assertTrue(session.discarded)
    }

    @Test
    fun `wrong credentials show the generic message and keep the person on the form`() {
        gateway.outcome = AuthOutcome.Failure(AuthError.INVALID_CREDENTIALS, "ERROR_INVALID_CREDENTIAL")
        var routed: Boolean? = null
        vm.signIn("rana@example.com", "wrongpass1") { routed = it }
        assertNull(routed)
        assertEquals(text(R.string.err_invalid_credentials), vm.authErrorMessage.value)
        assertEquals(AuthError.INVALID_CREDENTIALS, vm.lastAuthError.value)
        assertFalse(vm.isAuthenticating.value)
    }

    @Test
    fun `invalid email and empty password never reach Firebase`() {
        vm.signIn("not-an-email", "clinic2026") {}
        assertEquals(text(R.string.please_enter_a_valid_email_address), vm.authErrorMessage.value)
        vm.signIn("rana@example.com", "") {}
        assertEquals(text(R.string.enter_your_password), vm.authErrorMessage.value)
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun `create account checks the password rules before calling Firebase`() {
        vm.createAccount("rana@example.com", "short") {}
        assertEquals(AuthError.WEAK_PASSWORD, vm.lastAuthError.value)
        assertTrue(gateway.calls.isEmpty())
    }

    @Test
    fun `a new account goes to the registration form`() {
        gateway.outcome = AuthOutcome.Success(isNewUser = true)
        var routed: Boolean? = null
        vm.createAccount("new@example.com", "clinic2026") { routed = it }
        assertEquals(true, routed)
        assertEquals(listOf("create:new@example.com"), gateway.calls)
        assertTrue(sink.events.any { it.first == Event.REGISTRATION_START && it.second[Param.METHOD] == "password" })
    }

    @Test
    fun `email already in use is explained`() {
        gateway.outcome = AuthOutcome.Failure(AuthError.EMAIL_IN_USE, "ERROR_EMAIL_ALREADY_IN_USE")
        vm.createAccount("taken@example.com", "clinic2026") {}
        assertEquals(text(R.string.err_email_in_use), vm.authErrorMessage.value)
    }

    @Test
    fun `a suspended account is signed out with the server's message`() {
        session.loginError = AccountSuspendedException("This account is suspended.")
        var routed: Boolean? = null
        vm.signIn("rana@example.com", "clinic2026") { routed = it }
        assertNull(routed)
        assertTrue(gateway.signedOut)
        assertEquals("This account is suspended.", vm.authErrorMessage.value)
    }

    @Test
    fun `google sign-in of a brand-new user registers first`() {
        gateway.outcome = AuthOutcome.Success(isNewUser = true)
        var routed: Boolean? = null
        vm.startGoogleSignIn("id-token") { routed = it }
        assertEquals(true, routed)
        assertTrue(sink.events.any { it.first == Event.AUTH_START && it.second[Param.METHOD] == "google" })
    }

    @Test
    fun `forgot password sends the branded email and never reveals whether the account exists`() {
        var sent = false
        vm.requestPasswordReset(" Rana@Example.com", onSent = { sent = true })
        assertTrue(sent)
        assertEquals(listOf("rana@example.com"), functions.resets)
        assertTrue(gateway.calls.isEmpty())
        assertNull(vm.authErrorMessage.value)
    }

    @Test
    fun `forgot password shows the server's own error`() {
        functions.resetResult = Result.failure(IllegalStateException("limit"))
        var sent = false
        vm.requestPasswordReset("rana@example.com", onSent = { sent = true })
        assertFalse(sent)
        assertEquals(text(R.string.couldnt_send_reset_email), vm.authErrorMessage.value)
    }

    @Test
    fun `forgot password with an invalid email asks for a valid one`() {
        vm.requestPasswordReset("rana@", onSent = {})
        assertEquals(text(R.string.please_enter_a_valid_email_address), vm.authErrorMessage.value)
        assertTrue(functions.resets.isEmpty())
    }

    @Test
    fun `registration submits the form through the server and reports success`() {
        gateway.signedInUid = "uid-1"
        var done = false
        vm.completePendingRegistration(
            AuthViewModel.PendingRegistration(
                fullName = "Rana Haddad", email = "rana@example.com", phoneE164 = "+96170123456",
                specialty = "Physiotherapy", country = "Lebanon", governorate = "", city = "Beirut",
                profilePictureUri = null, tosAccepted = true
            )
        ) { done = true }
        assertTrue(done)
        assertEquals("Rana Haddad", session.registered?.fullName)
        assertTrue(session.registered?.tosAccepted == true)
        assertTrue(sink.events.any { it.first == Event.SIGN_UP })
    }

    @Test
    fun `registration without a session asks to sign in again`() {
        vm.completePendingRegistration(
            AuthViewModel.PendingRegistration("R", "r@e.co", "", "", "", "", "", null, true)
        ) { error("must not finish") }
        assertEquals(text(R.string.your_verified_session_expired_please_sign), vm.authErrorMessage.value)
        assertNull(session.registered)
    }

    @Test
    fun `email plausibility`() {
        assertTrue(AuthViewModel.isPlausibleEmail("a.b+c@clinic.health"))
        assertFalse(AuthViewModel.isPlausibleEmail("a@b"))
        assertFalse(AuthViewModel.isPlausibleEmail("a b@c.com"))
    }
}
