package com.example.ui.util

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

@SuppressLint("DiscouragedApi") // by-name lookup on purpose: one key for Android and iOS
@Composable
actual fun appPainter(image: AppImage): Painter {
    val context = LocalContext.current
    val id = remember(image) { context.resources.getIdentifier(image.key, "drawable", context.packageName) }
    return painterResource(id)
}
