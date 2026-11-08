@file:JsModule("firebase/functions")

package firebase

external interface Functions : JsAny

external fun getFunctions(app: FirebaseApp, regionOrCustomDomain: String): Functions

external fun httpsCallable(functions: Functions, name: String): JsAny
