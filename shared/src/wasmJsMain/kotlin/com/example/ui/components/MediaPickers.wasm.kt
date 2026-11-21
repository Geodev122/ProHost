package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.util.PickedFile

// Milestone C3: PHPicker (images), UIDocumentPicker (documents), UIImagePickerController (camera).

@Composable
actual fun rememberImagePicker(onPicked: (PickedFile) -> Unit): () -> Unit = {}

@Composable
actual fun rememberVisualMediaImagePicker(onPicked: (PickedFile) -> Unit): () -> Unit = {}

@Composable
actual fun rememberImagesPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit = {}

@Composable
actual fun rememberDocumentPicker(onPicked: (file: PickedFile, displayName: String) -> Unit): () -> Unit = {}

@Composable
actual fun rememberCameraCapture(onCaptured: (PickedFile) -> Unit): () -> Unit = {}
