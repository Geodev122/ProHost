package com.example.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation levels: level0 flat · level1 cards on a page · level2 selected tile / scrolled app bar · level3 floating
 * nav pill, FAB and sheets. Prefer a surfaceContainer step over a shadow where both would work.
 */
object Elevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
}
