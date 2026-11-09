package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The outlined button as the app has always drawn it (Material 3 1.3): primary-coloured label and icon, `outline` border, the disabled
 * variants on `onSurface`. Material 3 1.4 moved these defaults to `onSurfaceVariant` / `outlineVariant`, which would have changed every outlined
 * button's look when Compose was upgraded (migration P3.4); this keeps them. Same signature as [androidx.compose.material3.OutlinedButton], so a call
 * site only needs this one imported (files in this package pick it up automatically).
 */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ProHostButtonDefaults.outlinedColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ProHostButtonDefaults.outlinedBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.OutlinedButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content
)

object ProHostButtonDefaults {
    @Composable
    fun outlinedColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    )

    @Composable
    fun outlinedBorder(enabled: Boolean = true): BorderStroke = BorderStroke(
        width = 1.dp,
        color = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    )
}
