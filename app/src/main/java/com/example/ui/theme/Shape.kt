package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Material shape roles mapped onto [Radius], so components using MaterialTheme.shapes follow the same scale. */
val Shapes = Shapes(
    extraSmall = Radius.Xs,
    small = RoundedCornerShape(8.dp),
    medium = Radius.Sm,
    large = Radius.Md,
    extraLarge = Radius.Lg
)

/** Top-rounded shape shared by every bottom sheet. */
val SheetShape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg)

/** Drawer panel: rounds only the edge facing the content. */
val DrawerShape = RoundedCornerShape(topEnd = Radius.lg, bottomEnd = Radius.lg)
