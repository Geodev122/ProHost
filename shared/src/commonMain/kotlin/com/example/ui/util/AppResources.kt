package com.example.ui.util

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * The app's text in shared UI. Android resolves the same key through Android resources (`R.string` by name), so the locale,
 * per-app language, formatting and recomposition on configuration change stay exactly what `stringResource(R.string.x)` gave;
 * iOS and the web read Compose resources (generated from the same strings.xml by `scripts/i18n/to_compose_resources.py`).
 */
@Composable
expect fun appString(resource: StringResource, vararg formatArgs: Any): String

/** Counted text, see [appString]. */
@Composable
expect fun appPlural(resource: PluralStringResource, quantity: Int, vararg formatArgs: Any): String
