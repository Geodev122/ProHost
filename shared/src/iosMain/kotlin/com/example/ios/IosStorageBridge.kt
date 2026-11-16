package com.example.ios

import com.example.data.auth.BoolCallback
import com.example.data.gateway.StorageBridge
import com.example.data.gateway.UploadResult
import com.example.util.PickedFile
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

fun interface ProgressCallback { fun progress(fraction: Double) }
fun interface UploadCallback { fun done(downloadUrl: String?, gsUri: String?, error: String?) }

/** What Swift implements (`FirebaseStorageBridge.swift`): Objective-C friendly types only (NSData, Double, named callbacks). */
interface IosStorageNative {
    fun uploadData(path: String, data: NSData, contentType: String?, progress: ProgressCallback, completion: UploadCallback)
    /** [fileUrl] is the `file://` URL string of a picked file (`PickedFile.handle` on iOS). */
    fun uploadFileUrl(path: String, fileUrl: String, contentType: String?, progress: ProgressCallback, completion: UploadCallback)
    fun compressJpeg(data: NSData, maxDim: Int, quality: Int): NSData?
    fun deletePrefix(path: String, completion: BoolCallback)
}

/** The shared [StorageBridge] over the Swift [IosStorageNative] (byte arrays copied once into/out of NSData). */
class IosStorageBridge(private val native: IosStorageNative) : StorageBridge {
    override fun uploadBytes(path: String, bytes: ByteArray, contentType: String?, onProgress: (Float) -> Unit, completion: (UploadResult) -> Unit) =
        native.uploadData(path, bytes.toNSData(), contentType, { onProgress(it.toFloat()) }) { url, gs, err -> completion(UploadResult(url, gs, err)) }

    override fun uploadPickedFile(path: String, file: PickedFile, contentType: String?, onProgress: (Float) -> Unit, completion: (UploadResult) -> Unit) =
        native.uploadFileUrl(path, file.handle, contentType, { onProgress(it.toFloat()) }) { url, gs, err -> completion(UploadResult(url, gs, err)) }

    override fun compressJpeg(bytes: ByteArray, maxDim: Int, quality: Int): ByteArray? =
        native.compressJpeg(bytes.toNSData(), maxDim, quality)?.toByteArray()

    override fun deletePrefix(path: String, completion: (Boolean) -> Unit) = native.deletePrefix(path, BoolCallback { completion(it) })
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val n = length.toInt()
    val out = ByteArray(n)
    if (n > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
