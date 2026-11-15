package com.example.ui.screens

import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource

/**
 * This is the app's OWN first screen — distinct from the OS-level SplashScreen API (API
 * 31+, themes.xml's windowSplashScreenBackground) that briefly shows before this Composable
 * ever runs. That system splash only supports a solid background color, never a full-bleed
 * image (an Android 12+ platform limitation, not a choice) — this screen is where the real
 * splash.png artwork appears, so the two together read as one continuous launch image
 * instead of two different-looking screens. No separate "second screen" with its own
 * icon/wordmark/loading text exists anymore — this single screen carries the loading state
 * for however long onSplashCompleted takes to fire. Dark mode uses drawable-night (all densities) splash.png on the matching
 * values-night splash_background.
 */
@Composable
fun SplashScreen(
    onSplashCompleted: () -> Unit,
    onError: (() -> Unit)? = null
) {
    SplashContent(
        painter = painterResource(id = com.example.R.drawable.splash),
        contentDescription = stringResource(R.string.prohost),
        onSplashCompleted = onSplashCompleted
    )
}
