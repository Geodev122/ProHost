package com.example.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter

/** The admin web console shows none of the app's bundled images. */
@Composable
actual fun appPainter(image: AppImage): Painter = ColorPainter(Color.Transparent)
