package admin

import admin.firebase.Auth
import admin.firebase.FirebaseApp
import admin.firebase.Functions
import admin.firebase.Firestore
import admin.firebase.ListResult
import admin.firebase.Storage
import admin.firebase.StorageRef
import admin.firebase.collection
import admin.firebase.deleteObject
import admin.firebase.getDoc
import admin.firebase.getDocs
import admin.firebase.getStorage
import admin.firebase.limit
import admin.firebase.orderBy
import admin.firebase.query2
import admin.firebase.listAll
import admin.firebase.query
import admin.firebase.ref
import admin.firebase.where
import admin.firebase.deleteDoc
import admin.firebase.doc
import admin.firebase.getAuth
import admin.firebase.getFirestore
import admin.firebase.setDoc
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
  /** `setDoc(path, json, { merge: true })` as the signed-in admin (rules `isAdmin()` decides). */
  suspend fun mergeDoc(path: String, jsonData: String)
  suspend fun deleteDoc(path: String)
  /** One document as JSON (with its `id`), or null when it does not exist. */
  suspend fun readDoc(path: String): String?
  /** Listings waiting for verification review: `isVerified == false and verificationRequestedAt > 0`, newest 50, as a JSON array. */
  suspend fun reviewQueue(): String
  /** Top 50 hashtags by `count` from `hashtag_usage`, as a JSON array. */
  suspend fun hashtagUsage(): String
  /** Deletes every object under a Storage path (walks prefixes, like the Android purge). */
  suspend fun purgeStorage(path: String)
}

class AdminAuthException(message: String) : Exception(message)

/** Public web-app config of project prohost-f766f (not secrets; the API key is referrer-restricted in Google Cloud). Analytics is deliberately NOT initialised (opt-in policy). */
private fun webConfig(): JsAny = js(
  "({ apiKey: 'AIzaSyAdA9X7BNgq2_f4cQ4SCUVuI4L-1aYeYe4', authDomain: 'prohost-f766f.firebaseapp.com', projectId: 'prohost-f766f', " +
    "storageBucket: 'prohost-f766f.firebasestorage.app', messagingSenderId: '646730915838', appId: '1:646730915838:web:f061104df7ca44d7c8eaaf' })"
)

private fun claimOf(result: JsAny, name: String): String = js("String(result.claims[name] === undefined ? '' : result.claims[name])")

private fun invoke(callable: JsAny, json: String): Promise<JsString> = js("callable(JSON.parse(json)).then(function (r) { return JSON.stringify(r.data === undefined ? null : r.data); })")

private fun parseObj(json: String): JsAny = js("JSON.parse(json)")
private fun mergeOption(): JsAny = js("({ merge: true })")

private fun docsJson(snap: JsAny): String = js("JSON.stringify(snap.docs.map(function (d) { var o = d.data(); o.id = d.id; return o; }))")
private fun docJson(snap: JsAny): String = js("(snap.exists() ? JSON.stringify(Object.assign({ id: snap.id }, snap.data())) : '')")

private fun errorCode(e: Throwable): String = (e.message ?: "").substringAfter("(", "").substringBefore(")")

class FirebaseWebGateway : AdminGateway {
  private val app: FirebaseApp = initializeApp(webConfig())
  private val auth: Auth = getAuth(app)
  private val firestore: Firestore = getFirestore(app)
  private val storage: Storage = getStorage(app)
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

  override suspend fun mergeDoc(path: String, jsonData: String) { setDoc(doc(firestore, path), parseObj(jsonData), mergeOption()).await<JsAny?>() }

  override suspend fun deleteDoc(path: String) { deleteDoc(doc(firestore, path)).await<JsAny?>() }

  override suspend fun readDoc(path: String): String? = docJson(getDoc(doc(firestore, path)).await<JsAny>()).ifEmpty { null }

  override suspend fun reviewQueue(): String = docsJson(
    getDocs(query(collection(firestore, "workspace_listings"), where("isVerified", "==", parseObj("false")), where("verificationRequestedAt", ">", parseObj("0")), limit(50))).await<JsAny>()
  )

  override suspend fun hashtagUsage(): String = docsJson(
    getDocs(query2(collection(firestore, "hashtag_usage"), orderBy("count", "desc"), limit(50))).await<JsAny>()
  )

  override suspend fun purgeStorage(path: String) = purge(ref(storage, path))

  private suspend fun purge(r: StorageRef) {
    val res = listAll(r).await<ListResult>()
    for (i in 0 until res.items.length) {
      val item = res.items[i] ?: continue
      try { deleteObject(item).await<JsAny?>() } catch (_: Throwable) { /* best effort, like Android */ }
    }
    for (i in 0 until res.prefixes.length) res.prefixes[i]?.let { purge(it) }
  }

  private suspend fun classify(forceRefresh: Boolean = false): AdminSession {
    val user = auth.currentUser ?: return AdminSession.SignedOut
    val role = claimOf(user.getIdTokenResult(forceRefresh).await<admin.firebase.IdTokenResult>(), "role")
    val email = user.email.orEmpty()
    return if (role == "ADMIN") AdminSession.Admin(email) else AdminSession.NotAdmin(email)
  }
}
