package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.example.util.PickedFile
import com.example.web.WebFiles
import kotlinx.coroutines.launch

// Web console: the browser's file chooser (WebFiles). Images are re-encoded like Android's upload path at pick time.
// The camera hand-off has no browser equivalent here: it opens the same image chooser.

@Composable
private fun rememberWebImages(multiple: Boolean, onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onPicked)
    return {
        scope.launch {
            val picked = WebFiles.choose("image/*", multiple).mapNotNull { WebFiles.registerImage(it) }
            if (picked.isNotEmpty()) callback.value(picked)
        }
    }
}

@Composable
actual fun rememberImagePicker(onPicked: (PickedFile) -> Unit): () -> Unit = rememberWebImages(false) { onPicked(it.first()) }

@Composable
actual fun rememberVisualMediaImagePicker(onPicked: (PickedFile) -> Unit): () -> Unit = rememberWebImages(false) { onPicked(it.first()) }

@Composable
actual fun rememberImagesPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit = rememberWebImages(true, onPicked)

@Composable
actual fun rememberDocumentPicker(onPicked: (file: PickedFile, displayName: String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onPicked)
    return {
        scope.launch {
            val file = WebFiles.choose("application/pdf,image/*", false).firstOrNull() ?: return@launch
            val picked = WebFiles.registerRaw(file) ?: return@launch
            callback.value(picked, WebFiles.displayName(file))
        }
    }
}

@Composable
actual fun rememberCameraCapture(onCaptured: (PickedFile) -> Unit): () -> Unit = rememberWebImages(false) { onCaptured(it.first()) }
