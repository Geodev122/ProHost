@file:JsModule("firebase/firestore")

package admin.firebase

import kotlin.js.Promise

external interface Firestore : JsAny

external interface DocRef : JsAny

external fun getFirestore(app: FirebaseApp): Firestore

external fun doc(db: Firestore, path: String): DocRef

external fun setDoc(ref: DocRef, data: JsAny, options: JsAny): Promise<JsAny?>

external fun deleteDoc(ref: DocRef): Promise<JsAny?>

external fun collection(db: Firestore, path: String): JsAny

external fun where(field: String, op: String, value: JsAny): JsAny

/** `query(ref, c1, c2, c3)`: the console only builds queries with exactly two filters and a limit. */
external fun query(ref: JsAny, c1: JsAny, c2: JsAny, c3: JsAny): JsAny

external fun limit(n: Int): JsAny

external fun getDocs(q: JsAny): Promise<JsAny>

external fun getDoc(ref: DocRef): Promise<JsAny>
