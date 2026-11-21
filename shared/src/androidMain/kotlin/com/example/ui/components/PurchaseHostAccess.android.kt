package com.example.ui.components

import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.ui.viewmodel.PurchaseHost

@Composable
actual fun rememberPurchaseHost(): PurchaseHost? {
    // The lookup the Premium screen did: the composition's Activity, else the Activity behind the context.
    val context = LocalContext.current
    val activityFromLocal = androidx.activity.compose.LocalActivity.current
    val activity: android.app.Activity? = activityFromLocal ?: run {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is android.app.Activity) return@run ctx
            ctx = ctx.baseContext
        }
        null
    }
    return remember(activity) { activity?.let { PurchaseHost(it) } }
}
