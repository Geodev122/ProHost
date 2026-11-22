package com.example.ui.util

/**
 * Android's `Resources.getString(id, args)` formatting for the patterns ProHost's strings use (`%1$s`, `%2$d`, `%s`, `%d`,
 * `%02d`, `%%`), for platforms that read the raw resource text and format it themselves (the web console). Without
 * arguments the text is returned unformatted, as Android's `getString(id)` does (a literal `%%` stays `%%`).
 * Equivalence with `String.format` is pinned by `AndroidFormatTest` (app unit tests).
 */
fun formatAndroidTemplate(template: String, args: Array<out Any?>): String {
    if (args.isEmpty()) return template
    val out = StringBuilder()
    var next = 0
    var i = 0
    while (i < template.length) {
        val c = template[i]
        if (c != '%' || i + 1 >= template.length) { out.append(c); i++; continue }
        if (template[i + 1] == '%') { out.append('%'); i += 2; continue }
        val m = SPEC.find(template, i)?.takeIf { it.range.first == i }
        if (m == null) { out.append(c); i++; continue }
        val position = m.groupValues[1].toIntOrNull()
        val pad = m.groupValues[2]
        val conversion = m.groupValues[3]
        val index = if (position != null) position - 1 else next++
        val value = args.getOrNull(index)
        val text = when (conversion) {
            "d" -> (value as? Number)?.toLong()?.toString() ?: value.toString()
            else -> value.toString()
        }
        out.append(
            if (pad.isNotEmpty()) text.padStart(pad.trimStart('0').toIntOrNull() ?: 0, if (pad.startsWith("0")) '0' else ' ') else text
        )
        i += m.value.length
    }
    return out.toString()
}

private val SPEC = Regex("%(?:(\\d+)\\$)?(0?\\d+)?([sd])")
