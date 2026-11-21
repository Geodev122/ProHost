package com.example.ui.util

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun appString(resource: StringResource, vararg formatArgs: Any): String = stringResource(resource, *formatArgs)

@Composable
actual fun appPlural(resource: PluralStringResource, quantity: Int, vararg formatArgs: Any): String =
    pluralStringResource(resource, quantity, *formatArgs)
