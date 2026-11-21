package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.util.PickedFile

/**
 * The pickers the screens use: composable functions that return a plain "open it" function and hand the result back as a [PickedFile],
 * so no screen sees a platform launcher or URI. Android: the system contracts (GetContent, PickVisualMedia, TakePicture) exactly as before;
 * iOS: PHPicker / UIDocumentPicker / camera (milestone C3; until then the launchers do nothing).
 */

/** One image from the system content picker (`GetContent` with the image MIME filter); [onPicked] is not called when the person cancels. */
@Composable
expect fun rememberImagePicker(onPicked: (PickedFile) -> Unit): () -> Unit

/** One image from the system photo picker (`PickVisualMedia`, images only). */
@Composable
expect fun rememberVisualMediaImagePicker(onPicked: (PickedFile) -> Unit): () -> Unit

/** Several images at once (`GetMultipleContents` with the image MIME filter); [onPicked] is not called with an empty list. */
@Composable
expect fun rememberImagesPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit

/** A PDF or image from the system file picker; also resolves the file's display name (falls back to the last path segment, then "document"). */
@Composable
expect fun rememberDocumentPicker(onPicked: (file: PickedFile, displayName: String) -> Unit): () -> Unit

/**
 * Takes a photo with the camera app into a temporary file (no camera permission needed: the system camera app does the capture). Shows the same
 * "no camera app" message as before when none can answer.
 */
@Composable
expect fun rememberCameraCapture(onCaptured: (PickedFile) -> Unit): () -> Unit
