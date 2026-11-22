package com.example.web

import com.example.data.gateway.BridgedStorageGateway
import com.example.util.FileAccess
import com.example.util.PickedFile
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Files picked in the browser (web console only). A pick reads the file once; images are scaled and re-encoded here with
 * Android's rule (longest side at most [BridgedStorageGateway.MAX_IMAGE_DIM] px, JPEG [BridgedStorageGateway.JPEG_QUALITY]),
 * because a browser can only decode images asynchronously and `StorageBridge.compressJpeg` is synchronous. The bytes live
 * in memory under a `web:<n>` handle until the page is closed.
 */
object WebFiles : FileAccess {
    private class Entry(val bytes: ByteArray, val name: String, val type: String)

    private val entries = HashMap<String, Entry>()
    private var counter = 0

    /** The picked file's bytes, as image bytes already compressed, or the raw file. */
    fun bytesOf(file: PickedFile): ByteArray? = entries[file.handle]?.bytes

    fun register(bytes: ByteArray, name: String, type: String): PickedFile {
        val handle = "web:${++counter}"
        entries[handle] = Entry(bytes, name, type)
        return PickedFile(handle)
    }

    override fun extension(file: PickedFile, fallback: String): String {
        val e = entries[file.handle] ?: return fallback
        val fromType = when (e.type.lowercase()) {
            "application/pdf" -> "pdf"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            else -> ""
        }
        if (fromType.isNotEmpty()) return fromType
        val fromName = e.name.substringAfterLast('.', "").lowercase()
        return fromName.ifEmpty { fallback }
    }

    override suspend fun readBytes(file: PickedFile): ByteArray? = bytesOf(file)

    override suspend fun sizeBytes(file: PickedFile): Long? = bytesOf(file)?.size?.toLong()

    /** Opens the browser's file chooser; resolves with the chosen files (empty when cancelled). */
    suspend fun choose(accept: String, multiple: Boolean): List<JsAny> {
        val files = pickJs(accept, multiple).await<JsAny>()
        return (0 until lengthJs(files)).mapNotNull { atJs(files, it) }
    }

    /** One chosen image, scaled and re-encoded as JPEG like Android's upload path; null when it is no image. */
    suspend fun registerImage(file: JsAny): PickedFile? = try {
        val bytes = toBytes(compressJs(file, BridgedStorageGateway.MAX_IMAGE_DIM, BridgedStorageGateway.JPEG_QUALITY).await<JsAny>())
        register(bytes, nameJs(file).substringBeforeLast('.') + ".jpg", "image/jpeg")
    } catch (e: Throwable) {
        null
    }

    /** One chosen document (PDF or image), kept as it is. */
    suspend fun registerRaw(file: JsAny): PickedFile? = try {
        register(toBytes(rawJs(file).await<JsAny>()), nameJs(file), typeJs(file))
    } catch (e: Throwable) {
        null
    }

    fun displayName(file: JsAny): String = nameJs(file)

    fun toBytes(u8: JsAny): ByteArray {
        val n = lengthJs(u8)
        val out = ByteArray(n)
        for (i in 0 until n) out[i] = byteJs(u8, i).toByte()
        return out
    }

    /** A JS `Uint8Array` with [bytes], for an upload. */
    fun toJs(bytes: ByteArray): JsAny {
        val u8 = newU8Js(bytes.size)
        for (i in bytes.indices) setByteJs(u8, i, bytes[i].toInt() and 0xFF)
        return u8
    }
}

private fun pickJs(accept: String, multiple: Boolean): Promise<JsAny> = js(
    """new Promise(function (resolve) {
      var i = document.createElement('input');
      i.type = 'file'; i.accept = accept; i.multiple = multiple; i.style.display = 'none';
      var done = false;
      function finish(list) { if (done) return; done = true; resolve(list); if (i.parentNode) i.parentNode.removeChild(i); }
      i.addEventListener('change', function () { finish(Array.prototype.slice.call(i.files || [])); });
      i.addEventListener('cancel', function () { finish([]); });
      document.body.appendChild(i);
      i.click();
    })"""
)

private fun compressJs(file: JsAny, maxDim: Int, quality: Int): Promise<JsAny> = js(
    """createImageBitmap(file).then(function (b) {
      var s = Math.min(1, maxDim / Math.max(b.width, b.height));
      var c = document.createElement('canvas');
      c.width = Math.max(1, Math.round(b.width * s)); c.height = Math.max(1, Math.round(b.height * s));
      c.getContext('2d').drawImage(b, 0, 0, c.width, c.height);
      return new Promise(function (r, f) { c.toBlob(function (blob) { blob ? r(blob) : f(new Error('encode failed')); }, 'image/jpeg', quality / 100); });
    }).then(function (blob) { return blob.arrayBuffer(); }).then(function (ab) { return new Uint8Array(ab); })"""
)

private fun rawJs(file: JsAny): Promise<JsAny> = js("file.arrayBuffer().then(function (ab) { return new Uint8Array(ab); })")
private fun nameJs(file: JsAny): String = js("String(file.name || 'file')")
private fun typeJs(file: JsAny): String = js("String(file.type || '')")
private fun lengthJs(a: JsAny): Int = js("a.length")
private fun atJs(a: JsAny, i: Int): JsAny? = js("a[i]")
private fun byteJs(a: JsAny, i: Int): Int = js("a[i]")
private fun newU8Js(n: Int): JsAny = js("new Uint8Array(n)")
private fun setByteJs(a: JsAny, i: Int, v: Int) { js("a[i] = v") }
