package admin

import admin.firebase.Auth
import admin.firebase.FirebaseApp
import admin.firebase.Functions
import admin.firebase.getAuth
import admin.firebase.getFunctions
import admin.firebase.httpsCallable
import admin.firebase.initializeApp
import admin.firebase.signInWithEmailAndPassword
import admin.firebase.signOut
import kotlinx.coroutines.await
import kotlin.js.Promise

/** Who is looking at the console. Only [Admin] gets past the shell; the real enforcement stays in firestore.rules `isAdmin()` and the callables. */
sealed interface AdminSession {
  data object Loading : AdminSession
  data object SignedOut : AdminSession
  data class NotAdmin(val email: String) : AdminSession
  data class Admin(val email: String) : AdminSession
}

/** What the console needs from Firebase, as an interface so screens never see JavaScript (same seam idea as AuthGateway / FunctionsClient on mobile). */
interface AdminGateway {
  /** Restores a previous sign-in (Firebase persists it in the browser) and classifies it. */
  suspend fun restore(): AdminSession
  /** Email + password sign-in, role assignment, forced token refresh, claim check. Throws [AdminAuthException] with a user-facing message. */
  suspend fun signIn(email: String, password: String): AdminSession
  suspend fun signOut()
  /** Calls a ProHost callable with a JSON object and returns the result's `data` as JSON text. */
  suspend fun call(name: String, jsonData: String = "{}"): String
}

class AdminAuthException(message: String) : Exception(message)

/** Public web-app config of project prohost-f766f (not secrets; the API key is referrer-restricted in Google Cloud). Analytics is deliberately NOT initialised (opt-in policy). */
private fun webConfig(): JsAny = js(
  "({ apiKey: 'AIzaSyAdA9X7BNgq2_f4cQ4SCUVuI4L-1aYeYe4', authDomain: 'prohost-f766f.firebaseapp.com', projectId: 'prohost-f766f', " +
    "storageBucket: 'prohost-f766f.firebasestorage.app', messagingSenderId: '646730915838', appId: '1:646730915838:web:f061104df7ca44d7c8eaaf' })"
)

private fun claimOf(result: JsAny, name: String): String = js("String(result.claims[name] === undefined ? '' : result.claims[name])")

private fun invoke(callable: JsAny, json: String): Promise<JsString> = js("callable(JSON.parse(json)).then(function (r) { return JSON.stringify(r.data === undefined ? null : r.data); })")

private fun errorCode(e: Throwable): String = (e.message ?: "").substringAfter("(", "").substringBefore(")")

class FirebaseWebGateway : AdminGateway {
  private val app: FirebaseApp = initializeApp(webConfig())
  private val auth: Auth = getAuth(app)
  private val functions: Functions = getFunctions(app, "europe-west1") // region of every ProHost callable

  override suspend fun restore(): AdminSession {
    auth.authStateReady().await<JsAny?>()
    return classify()
  }

  override suspend fun signIn(email: String, password: String): AdminSession {
    try {
      signInWithEmailAndPassword(auth, email.trim(), password).await<JsAny>()
    } catch (e: Throwable) {
      // Same wording for a wrong email and a wrong password (email-enumeration protection stays on).
      throw AdminAuthException(
        when (errorCode(e).removePrefix("auth/")) {
          "too-many-requests" -> "Too many attempts. Wait a few minutes and try again."
          "network-request-failed" -> "Can't reach the server. Check your connection."
          "user-disabled" -> "This account has been suspended."
          else -> "Wrong email or password."
        }
      )
    }
    // Sets/repairs the role claim for this account (no Play Integrity token on the web; the field is optional), then re-reads the token.
    try { call("assignInitialRole", "{}") } catch (_: Throwable) { /* an existing admin keeps working; classify() decides */ }
    return classify(forceRefresh = true)
  }

  override suspend fun signOut() { signOut(auth).await<JsAny?>() }

  override suspend fun call(name: String, jsonData: String): String = invoke(httpsCallable(functions, name), jsonData).await<JsString>().toString()

  private suspend fun classify(forceRefresh: Boolean = false): AdminSession {
    val user = auth.currentUser ?: return AdminSession.SignedOut
    val role = claimOf(user.getIdTokenResult(forceRefresh).await<admin.firebase.IdTokenResult>(), "role")
    val email = user.email.orEmpty()
    return if (role == "ADMIN") AdminSession.Admin(email) else AdminSession.NotAdmin(email)
  }
}
