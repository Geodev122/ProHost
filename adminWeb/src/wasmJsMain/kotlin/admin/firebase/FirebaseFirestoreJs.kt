@file:JsModule("firebase/firestore")

package admin.firebase

import kotlin.js.Promise

external interface Firestore : JsAny

external interface DocRef : JsAny

external fun getFirestore(app: FirebaseApp): Firestore

external fun doc(db: Firestore, path: String): DocRef

external fun setDoc(ref: DocRef, data: JsAny, options: JsAny): Promise<JsAny?>

external fun deleteDoc(ref: DocRef): Promise<JsAny?>
