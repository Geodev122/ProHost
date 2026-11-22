package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.example.ios.IosPlatform
import com.example.util.PickedFile

// iOS: PHPicker (images), UIDocumentPicker (documents), UIImagePickerController (camera) through IosPlatform.mediaPicker.

@Composable
actual fun rememberImagePicker(onPicked: (PickedFile) -> Unit): () -> Unit = rememberSinglePhoto(onPicked)

@Composable
actual fun rememberVisualMediaImagePicker(onPicked: (PickedFile) -> Unit): () -> Unit = rememberSinglePhoto(onPicked)

@Composable
actual fun rememberImagesPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        {
            IosPlatform.mediaPicker?.pickPhotos(0) { urls ->
                if (urls.isNotEmpty()) callback(urls.map { PickedFile(it) })
            }
            Unit
        }
    }
}

@Composable
actual fun rememberDocumentPicker(onPicked: (file: PickedFile, displayName: String) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        {
            IosPlatform.mediaPicker?.pickDocumentFile { url, name ->
                if (url != null) callback(PickedFile(url), name?.takeIf { it.isNotBlank() } ?: "document")
            }
            Unit
        }
    }
}

@Composable
actual fun rememberCameraCapture(onCaptured: (PickedFile) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onCaptured)
    return remember {
        {
            IosPlatform.mediaPicker?.capturePhoto { url -> if (url != null) callback(PickedFile(url)) }
            Unit
        }
    }
}

@Composable
private fun rememberSinglePhoto(onPicked: (PickedFile) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        {
            IosPlatform.mediaPicker?.pickPhotos(1) { urls -> urls.firstOrNull()?.let { callback(PickedFile(it)) } }
            Unit
        }
    }
}
