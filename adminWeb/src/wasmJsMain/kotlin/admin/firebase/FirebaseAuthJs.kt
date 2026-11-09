@file:JsModule("firebase/auth")

package admin.firebase

import kotlin.js.Promise

external interface User : JsAny {
  val email: String?
  fun getIdTokenResult(forceRefresh: Boolean): Promise<IdTokenResult>
}

external interface IdTokenResult : JsAny {
  val claims: JsAny
}

external interface Auth : JsAny {
  val currentUser: User?
  fun authStateReady(): Promise<JsAny?>
}

external interface UserCredential : JsAny {
  val user: User
}

external fun getAuth(app: FirebaseApp): Auth

external fun signInWithEmailAndPassword(auth: Auth, email: String, password: String): Promise<UserCredential>

external fun signOut(auth: Auth): Promise<JsAny?>
