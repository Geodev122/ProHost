package com.example.ios

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.ComposeUIViewController
import com.example.shared.resources.Res
import com.example.shared.resources.manrope_bold
import com.example.shared.resources.manrope_extrabold
import com.example.shared.resources.manrope_semibold
import com.example.shared.resources.prohost
import com.example.shared.resources.roboto_bold
import com.example.shared.resources.roboto_medium
import com.example.shared.resources.roboto_regular
import com.example.shared.resources.splash
import com.example.ui.screens.SplashContent
import com.example.ui.theme.ProHostThemeBase
import com.example.ui.theme.proHostTypography
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIViewController

/** The iOS app's Compose root, hosted by the Swift app (`ios/ProHost/App/RootView.swift`). Screens join here as they move to `:shared`. */
fun MainViewController(): UIViewController = ComposeUIViewController { ProHostIosApp() }

@Composable
fun ProHostIosApp() {
    val typography = proHostTypography(display = manropeFamily(), body = robotoFamily())
    ProHostThemeBase(darkTheme = isSystemInDarkTheme(), typography = typography) {
        SplashContent(
            painter = painterResource(Res.drawable.splash),
            contentDescription = stringResource(Res.string.prohost),
            onSplashCompleted = {}
        )
    }
}

@Composable
private fun manropeFamily(): FontFamily = FontFamily(
    Font(Res.font.manrope_semibold, FontWeight.SemiBold),
    Font(Res.font.manrope_bold, FontWeight.Bold),
    Font(Res.font.manrope_extrabold, FontWeight.ExtraBold)
)

@Composable
private fun robotoFamily(): FontFamily = FontFamily(
    Font(Res.font.roboto_regular, FontWeight.Normal),
    Font(Res.font.roboto_medium, FontWeight.Medium),
    Font(Res.font.roboto_bold, FontWeight.Bold)
)
