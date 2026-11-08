@file:JsModule("firebase/auth")

package firebase

external interface Auth : JsAny {
    val currentUser: JsAny?
}

external fun getAuth(app: FirebaseApp): Auth
