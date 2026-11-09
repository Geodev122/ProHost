@file:JsModule("firebase/app")

package admin.firebase

/** Kotlin/Wasm bindings for the few Firebase JS calls the admin console needs. */
external interface FirebaseApp : JsAny {
  val name: String
}

external fun initializeApp(options: JsAny): FirebaseApp
