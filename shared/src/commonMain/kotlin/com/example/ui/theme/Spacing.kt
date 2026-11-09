package com.example.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ProHost spacing scale. Values match the app's actual de-facto padding/
 * spacedBy distribution (a grep of every literal .dp spacing call site
 * clustered heavily on 4/8/12/16/24) and the same 5 steps Shape.kt already
 * uses for corner radii, so shape and spacing move on one shared rhythm.
 *
 * Existing call sites are not migrated automatically - replace
 * `padding(16.dp)`-style literals with `Spacing.lg` etc. as screens are
 * touched, rather than in one blanket sweep that would risk relayouting
 * every screen without a way to visually verify each one in this
 * environment.
 */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}
