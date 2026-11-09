@file:JsModule("firebase/storage")

package admin.firebase

import kotlin.js.JsArray
import kotlin.js.Promise

external interface Storage : JsAny

external interface StorageRef : JsAny

external interface ListResult : JsAny {
  val items: JsArray<StorageRef>
  val prefixes: JsArray<StorageRef>
}

external fun getStorage(app: FirebaseApp): Storage

external fun ref(storage: Storage, path: String): StorageRef

external fun listAll(ref: StorageRef): Promise<ListResult>

external fun deleteObject(ref: StorageRef): Promise<JsAny?>
