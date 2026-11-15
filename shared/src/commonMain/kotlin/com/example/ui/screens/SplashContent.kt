package com.example.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The app's own first screen (the full-bleed splash artwork with a small spinner), shared by Android and iOS. Each platform
 * passes its own copy of the artwork: Android `res/drawable(-night)/splash.png` (see the app's `SplashScreen`), iOS
 * `iosMain/composeResources/drawable(-dark)/splash.png`.
 */
@Composable
fun SplashContent(
    painter: Painter,
    contentDescription: String,
    onSplashCompleted: () -> Unit
) {
    var contentAlpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(400)
        ) { value, _ ->
            contentAlpha = value
        }
        // Minimum time this screen stays up (after the 400 ms fade), so it never flashes by when auth/profile state resolves
        // instantly — not a proxy for real loading progress, which is why the spinner below is indeterminate.
        delay(300)
        onSplashCompleted()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .size(28.dp)
                .alpha(contentAlpha),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.35f),
            strokeWidth = 3.dp
        )
    }
}
