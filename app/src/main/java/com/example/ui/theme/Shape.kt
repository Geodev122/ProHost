package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/** Top-rounded shape shared by every bottom sheet. */
val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/** Drawer panel: rounds only the edge facing the content. */
val DrawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
