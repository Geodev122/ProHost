package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashCompleted: () -> Unit
) {
    var logoScale by remember { mutableFloatStateOf(0.7f) }
    var logoAlpha by remember { mutableFloatStateOf(0f) }
    var subtitleAlpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        // Premium spring animation
        animate(
            initialValue = 0.7f,
            targetValue = 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) { value, _ ->
            logoScale = value
        }
    }

    LaunchedEffect(Unit) {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(1000)
        ) { value, _ ->
            logoAlpha = value
        }
        delay(200)
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(800)
        ) { value, _ ->
            subtitleAlpha = value
        }
        
        delay(1200) // Stays visible during complete sequence
        onSplashCompleted()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumBackgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(Spacing.xl)
        ) {
            // ProHost app icon - the same artwork shown when the app is installed, so
            // the splash screen is a seamless continuation of the icon tap. Deliberately
            // NOT R.mipmap.ic_launcher: on API 26+ that resource name resolves through
            // mipmap-anydpi-v26/ic_launcher.xml, an <adaptive-icon> — a type only the
            // launcher/PackageManager know how to inflate. Loading it via
            // painterResource() here crashes immediately (this screen is the very first
            // thing rendered), which is why the app never got past the splash screen.
            // img_app_icon is the same bitmap under a plain, non-adaptive-icon name.
            Image(
                painter = painterResource(id = com.example.R.drawable.img_app_icon),
                contentDescription = "ProHost",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(110.dp)
                    .scale(logoScale)
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            // Main Display Typography - two-tone "ProHost" wordmark, matching the mark
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = OxfordBlueDark)) { append("Pro") }
                    withStyle(SpanStyle(color = VibrantBlue)) { append("Host") }
                },
                fontFamily = ManropeDisplay,
                fontSize = MaterialTheme.typography.displayLarge.fontSize,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Professional Subtitle
            Text(
                text = "Specialist Workspace & Studio Exchange",
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = subtitleAlpha),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.5.dp
            )
        }

        // Professional platform branding at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "LEBANON RENTAL INFRASTRUCTURE NODE",
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "Licensed exchange • Secure local sqlite store enabled",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
