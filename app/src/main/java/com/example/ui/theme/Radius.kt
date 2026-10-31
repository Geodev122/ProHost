package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radii (docs/design/UI_AUDIT.md): xs chips/badges · sm cards/fields · md tiles/hero · lg sheets, dialogs and
 * the nav pill. Use these (or MaterialTheme.shapes) instead of a literal RoundedCornerShape(n.dp).
 */
object Radius {
    val xs: Dp = 6.dp
    val sm: Dp = 12.dp
    val md: Dp = 20.dp
    val lg: Dp = 28.dp

    val Xs = RoundedCornerShape(xs)
    val Sm = RoundedCornerShape(sm)
    val Md = RoundedCornerShape(md)
    val Lg = RoundedCornerShape(lg)
    val Full = RoundedCornerShape(percent = 50)
}
