package com.example.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * The app's bundled images that shared UI draws. [key] is the Android drawable name: Android keeps loading its own
 * `res/drawable*` file by that name (same pixels, nothing duplicated in the APK); iOS bundles a copy under
 * `shared/src/iosMain/composeResources/drawable`.
 */
enum class AppImage(val key: String) {
    LOGIN_LOCKUP("prohost_login_lockup"),
    CHECKMARK_LOGO("prohost_checkmark_logo"),
    OWNER_HERO("img_owner_hero"),
    WHATSAPP("ic_whatsapp"),
    BRAND_LOGO("ic_prohost_logo_brand"),
    GOOGLE("ic_google"),
    AUTH_BACKGROUND("auth_background"),
}

/** The painter for [image] on this platform (see [AppImage]). */
@Composable
expect fun appPainter(image: AppImage): Painter
