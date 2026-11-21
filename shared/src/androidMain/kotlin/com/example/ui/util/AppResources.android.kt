package com.example.ui.util

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

@SuppressLint("DiscouragedApi") // by-name lookup on purpose: one key for Android resources and Compose resources
@Composable
actual fun appString(resource: StringResource, vararg formatArgs: Any): String {
    val context = LocalContext.current
    val id = remember(resource.key) { context.resources.getIdentifier(resource.key, "string", context.packageName) }
    if (id == 0) return org.jetbrains.compose.resources.stringResource(resource, *formatArgs)
    return if (formatArgs.isEmpty()) androidx.compose.ui.res.stringResource(id)
    else androidx.compose.ui.res.stringResource(id, *formatArgs)
}

@SuppressLint("DiscouragedApi")
@Composable
actual fun appPlural(resource: PluralStringResource, quantity: Int, vararg formatArgs: Any): String {
    val context = LocalContext.current
    val id = remember(resource.key) { context.resources.getIdentifier(resource.key, "plurals", context.packageName) }
    if (id == 0) return org.jetbrains.compose.resources.pluralStringResource(resource, quantity, *formatArgs)
    return if (formatArgs.isEmpty()) androidx.compose.ui.res.pluralStringResource(id, quantity)
    else androidx.compose.ui.res.pluralStringResource(id, quantity, *formatArgs)
}
