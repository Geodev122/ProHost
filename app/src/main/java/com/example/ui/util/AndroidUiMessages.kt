package com.example.ui.util

import android.content.Context
import android.widget.Toast

/** Android part of [UiMessages]: the fallback Toast (application context, as it always was) for messages shown while no Snackbar host is on screen. */
object AndroidUiMessages {
    /** Called from `ProHostApplication.onCreate`. */
    fun init(applicationContext: Context) {
        val app = applicationContext.applicationContext
        UiMessages.setToastFallback { text, long ->
            Toast.makeText(app, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }
}
