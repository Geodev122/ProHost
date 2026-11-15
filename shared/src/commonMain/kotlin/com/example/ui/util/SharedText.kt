package com.example.ui.util

/**
 * Translated text for code in `:shared`, looked up by resource name ("err_invalid_credentials"), so shared code needs no
 * platform resource ids. Android sets [lookup] / [plural] in `ProHostApplication` over the app's string resources (same result
 * as `uiText` / `uiPlural`); iOS will set them over its own strings. Before a platform sets them, every text is "" — exactly
 * what `UiText.get` returns before initialisation.
 */
object SharedText {
    var lookup: (name: String, args: Array<out Any?>) -> String = { _, _ -> "" }
    var plural: (name: String, count: Int, args: Array<out Any?>) -> String = { _, _, _ -> "" }
}

/** The string resource [name] in the app language, formatted with [args]. */
fun sharedText(name: String, vararg args: Any?): String = SharedText.lookup(name, args)

/** The plural resource [name] for [count] in the app language, formatted with [args]. */
fun sharedPlural(name: String, count: Int, vararg args: Any?): String = SharedText.plural(name, count, args)
