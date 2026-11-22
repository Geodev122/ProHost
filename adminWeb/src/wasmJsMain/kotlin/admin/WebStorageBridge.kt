package admin

import admin.firebase.Storage
import admin.firebase.deleteObject
import admin.firebase.getDownloadURL
import admin.firebase.listAll
import admin.firebase.ref
import admin.firebase.uploadBytes
import admin.firebase.ListResult
import admin.firebase.StorageRef
import com.example.data.gateway.StorageBridge
import com.example.data.gateway.UploadResult
import com.example.util.PickedFile
import com.example.web.WebFiles
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

private const val BUCKET = "prohost-f766f.firebasestorage.app"

private fun uploadMeta(type: String?): JsAny = if (type == null) emptyMeta() else typedMeta(type)
private fun typedMeta(type: String): JsAny = js("({ contentType: type })")
private fun emptyMeta(): JsAny = js("({})")

/**
 * The shared [com.example.data.gateway.BridgedStorageGateway]'s platform half for the web console (Firebase Storage JS), so
 * the listing wizard uploads to the same paths, content types and return values as the app. Images arrive already
 * compressed by [WebFiles] (the browser decodes asynchronously), so [compressJpeg] passes them through.
 */
class WebStorageBridge(private val storage: Storage) : StorageBridge {
    private val scope = MainScope()

    private fun put(path: String, data: JsAny, contentType: String?, onProgress: (Float) -> Unit, completion: (UploadResult) -> Unit) {
        scope.launch {
            val result = try {
                val r = ref(storage, path)
                uploadBytes(r, data, uploadMeta(contentType)).await<JsAny>()
                onProgress(1f)
                UploadResult(getDownloadURL(r).await<JsString>().toString(), "gs://$BUCKET/$path", null)
            } catch (e: Throwable) {
                UploadResult(null, null, e.message ?: "upload failed")
            }
            completion(result)
        }
    }

    override fun uploadBytes(path: String, bytes: ByteArray, contentType: String?, onProgress: (Float) -> Unit, completion: (UploadResult) -> Unit) =
        put(path, WebFiles.toJs(bytes), contentType, onProgress, completion)

    override fun uploadPickedFile(path: String, file: PickedFile, contentType: String?, onProgress: (Float) -> Unit, completion: (UploadResult) -> Unit) {
        val bytes = WebFiles.bytesOf(file)
        if (bytes == null) completion(UploadResult(null, null, "file not available")) else put(path, WebFiles.toJs(bytes), contentType, onProgress, completion)
    }

    override fun compressJpeg(bytes: ByteArray, maxDim: Int, quality: Int): ByteArray? = bytes

    override fun deletePrefix(path: String, completion: (Boolean) -> Unit) {
        scope.launch {
            val ok = try { purge(ref(storage, path)); true } catch (_: Throwable) { false }
            completion(ok)
        }
    }

    private suspend fun purge(r: StorageRef) {
        val res = listAll(r).await<ListResult>()
        for (i in 0 until res.items.length) {
            val item = res.items[i] ?: continue
            try { deleteObject(item).await<JsAny?>() } catch (_: Throwable) { /* best effort, like Android */ }
        }
        for (i in 0 until res.prefixes.length) res.prefixes[i]?.let { purge(it) }
    }
}
