package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.R
import com.example.ui.util.UiMessages
import com.example.ui.util.uiText
import com.example.util.PickedFile
import com.example.util.toPickedFile
import java.io.File

/**
 * The pickers the screens use, as composable functions that return a plain "open it" function and hand the result back as a [PickedFile]: no screen
 * sees an `ActivityResultLauncher` or a `Uri`. This file is the Android implementation (the same system contracts the screens used before, with the
 * same MIME filters and error messages); it becomes the `expect`/`actual` `MediaPicker` seam in P3 (iOS: PHPicker, UIDocumentPicker, camera).
 */

/** One image from the system content picker (`GetContent("image/*")`); [onPicked] is not called when the person cancels. */
@Composable
fun rememberImagePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) onPicked(uri.toPickedFile())
    }
    return { launcher.launch("image/*") }
}

/** One image from the system photo picker (`PickVisualMedia`, images only). */
@Composable
fun rememberVisualMediaImagePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) onPicked(uri.toPickedFile())
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** Several images at once (`GetMultipleContents("image/*")`); [onPicked] is not called with an empty list. */
@Composable
fun rememberImagesPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) onPicked(uris.map { it.toPickedFile() })
    }
    return { launcher.launch("image/*") }
}

/** A PDF or image from the system file picker; also resolves the file's display name (falls back to the last path segment, then "document"). */
@Composable
fun rememberDocumentPicker(onPicked: (file: PickedFile, displayName: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            var resolvedName: String? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) resolvedName = cursor.getString(nameIndex)
            }
            onPicked(uri.toPickedFile(), resolvedName ?: (uri.lastPathSegment ?: "document"))
        }
    }
    return { launcher.launch("application/pdf,image/*") }
}

private fun createCameraCaptureUri(context: Context): Uri {
    val photoFile = File.createTempFile("kyc_doc_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
}

/**
 * Takes a photo with the camera app into a temporary file (no camera permission needed: the system camera app does the capture). Shows the same
 * "no camera app" message as before when none can answer.
 */
@Composable
fun rememberCameraCapture(onCaptured: (PickedFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        val uri = pendingCameraUri
        if (success && uri != null) onCaptured(uri.toPickedFile())
        pendingCameraUri = null
    }
    return {
        val uri = createCameraCaptureUri(context)
        pendingCameraUri = uri
        try {
            launcher.launch(uri)
        } catch (e: android.content.ActivityNotFoundException) {
            pendingCameraUri = null
            UiMessages.show(uiText(R.string.no_camera_app_found_choose_a_file_instead), long = true)
        }
    }
}
