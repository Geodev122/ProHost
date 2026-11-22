package com.example.web

import com.example.shared.resources.Res
import com.example.shared.resources.allPluralStringResources
import com.example.shared.resources.allStringResources
import com.example.ui.util.SharedText
import com.example.ui.util.formatAndroidTemplate
import com.example.util.FileAccess
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * Seams the web console installs before it shows shared screens (the listing wizard). Compose resources load
 * asynchronously in a browser, so every string is read once here and [SharedText] answers from memory with Android's
 * formatting ([formatAndroidTemplate]); unknown keys give "" like Android's lookup by name. Plurals keep the English
 * one/other forms (the console is English).
 */
object WebSeams {
    private var installed = false

    @OptIn(ExperimentalResourceApi::class)
    suspend fun install() {
        if (installed) return
        installed = true
        FileAccess.current = WebFiles
        val strings = HashMap<String, String>()
        for ((name, res) in Res.allStringResources) strings[name] = runCatching { getString(res) }.getOrDefault("")
        val one = HashMap<String, String>()
        val other = HashMap<String, String>()
        for ((name, res) in Res.allPluralStringResources) {
            one[name] = runCatching { getPluralString(res, 1) }.getOrDefault("")
            other[name] = runCatching { getPluralString(res, 2) }.getOrDefault("")
        }
        SharedText.lookup = { name, args -> strings[name]?.let { formatAndroidTemplate(it, args) } ?: "" }
        SharedText.plural = { name, count, args -> (if (count == 1) one[name] else other[name])?.let { formatAndroidTemplate(it, args) } ?: "" }
    }
}
