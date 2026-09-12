package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * This is the app's OWN first screen — distinct from the OS-level SplashScreen API (API
 * 31+, themes.xml's windowSplashScreenBackground) that briefly shows before this Composable
 * ever runs. That system splash only supports a solid background color, never a full-bleed
 * image (an Android 12+ platform limitation, not a choice) — this screen is where the real
 * splash.png artwork appears, so the two together read as one continuous launch image
 * instead of two different-looking screens. No separate "second screen" with its own
 * icon/wordmark/loading text exists anymore — this single screen carries the loading state
 * for however long onSplashCompleted takes to fire.
 */
@Composable
fun SplashScreen(
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
        // Minimum time this screen stays up, so it never flashes by faster than a human
        // can register it even when auth/profile state resolves instantly — not a proxy
        // for real loading progress, which is why the spinner below is indeterminate.
        delay(900)
        onSplashCompleted()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = com.example.R.drawable.splash),
            contentDescription = "ProHost",
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
