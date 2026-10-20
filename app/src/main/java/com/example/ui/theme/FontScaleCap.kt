package com.example.ui.theme

import android.content.Context
import android.content.res.Configuration

/**
 * The app follows the phone's text size only up to Android's "Large" step (owner's choice: text stays adequate and
 * every screen fits small phones). Applied to the activity's base context, so dialogs and sheets get it too.
 */
object FontScaleCap {
    const val MAX_FONT_SCALE = 1.15f

    fun wrap(base: Context): Context {
        val current = base.resources.configuration
        if (current.fontScale <= MAX_FONT_SCALE) return base
        val capped = Configuration(current).apply { fontScale = MAX_FONT_SCALE }
        return base.createConfigurationContext(capped)
    }
}
