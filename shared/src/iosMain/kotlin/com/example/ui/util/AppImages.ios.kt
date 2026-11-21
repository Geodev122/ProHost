package com.example.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.example.shared.resources.Res
import com.example.shared.resources.auth_background
import com.example.shared.resources.ic_google
import com.example.shared.resources.ic_prohost_logo_brand
import com.example.shared.resources.ic_whatsapp
import com.example.shared.resources.img_owner_hero
import com.example.shared.resources.prohost_checkmark_logo
import com.example.shared.resources.prohost_login_lockup
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun appPainter(image: AppImage): Painter = painterResource(
    when (image) {
        AppImage.LOGIN_LOCKUP -> Res.drawable.prohost_login_lockup
        AppImage.CHECKMARK_LOGO -> Res.drawable.prohost_checkmark_logo
        AppImage.OWNER_HERO -> Res.drawable.img_owner_hero
        AppImage.WHATSAPP -> Res.drawable.ic_whatsapp
        AppImage.BRAND_LOGO -> Res.drawable.ic_prohost_logo_brand
        AppImage.GOOGLE -> Res.drawable.ic_google
        AppImage.AUTH_BACKGROUND -> Res.drawable.auth_background
    }
)
