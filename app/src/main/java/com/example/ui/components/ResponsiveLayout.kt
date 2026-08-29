package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard ProSpace Responsive Content Container.
 * Restricts maximum content width on wide screens (tablets, foldables, DeX, landscape)
 * to 840dp while centering horizontally and occupying 100% on phones.
 */
@Composable
fun ProResponsiveContentContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 840.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth),
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

/**
 * Responsive 2-Column Adaptive Pane.
 * Switches between horizontal Row (side-by-side on tablets/foldables >= breakpoint)
 * and vertical Column on standard phones (< breakpoint).
 */
@Composable
fun ProAdaptiveTwoPane(
    modifier: Modifier = Modifier,
    breakpoint: Dp = 680.dp,
    spacing: Dp = 16.dp,
    primaryWeight: Float = 1f,
    secondaryWeight: Float = 1f,
    primaryPane: @Composable BoxScope.() -> Unit,
    secondaryPane: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= breakpoint) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .weight(primaryWeight)
                        .fillMaxHeight(),
                    content = primaryPane
                )
                Box(
                    modifier = Modifier
                        .weight(secondaryWeight)
                        .fillMaxHeight(),
                    content = secondaryPane
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), content = primaryPane)
                Box(modifier = Modifier.fillMaxWidth(), content = secondaryPane)
            }
        }
    }
}
