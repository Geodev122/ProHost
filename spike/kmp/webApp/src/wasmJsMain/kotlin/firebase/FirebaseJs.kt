@file:JsModule("firebase/app")

package firebase

/** Kotlin/Wasm bindings for the few Firebase JS calls the spike needs (round 6). */
external interface FirebaseApp : JsAny {
    val name: String
}

external fun initializeApp(options: JsAny): FirebaseApp
