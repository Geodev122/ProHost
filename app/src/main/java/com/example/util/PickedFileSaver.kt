package com.example.util

/** Saves a nullable [PickedFile] across configuration changes and process death (as its handle string; empty = none). */
val PickedFileSaver: androidx.compose.runtime.saveable.Saver<PickedFile?, String> = androidx.compose.runtime.saveable.Saver(
    save = { it?.handle ?: "" },
    restore = { handle -> handle.takeIf { it.isNotEmpty() }?.let(::PickedFile) }
)
