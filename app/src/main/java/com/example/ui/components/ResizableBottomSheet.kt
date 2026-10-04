package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SheetShape
import com.example.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * A bottom sheet that rises from the bottom of the screen and the person can resize:
 * dragging the handle/header moves it freely between [stops] (fractions of the screen
 * height) and it settles on the nearest one; dragging it below the lowest stop closes it.
 * Tapping the handle steps to the next height. The body scrolls on its own at every
 * height and never drags the sheet, so long content (day lists, slot grids) is always
 * reachable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResizableBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    accentTint: Boolean = false,
    stops: List<Float> = listOf(0.42f, 0.66f, 0.92f),
    initialStop: Int = 1,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val screenPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val stopPx = remember(screenPx, stops) { stops.sorted().map { it * screenPx } }
    var heightPx by remember { mutableFloatStateOf(stopPx[initialStop.coerceIn(stopPx.indices)]) }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
    }
    fun settleTo(target: Float) {
        scope.launch {
            animate(heightPx, target, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                heightPx = v
            }
        }
    }
    fun settle(velocity: Float) {
        // Positive velocity = dragging down. Project a little ahead so a flick carries.
        val projected = heightPx - velocity * 0.12f
        if (projected < stopPx.first() * 0.7f) close()
        else settleTo(stopPx.minBy { kotlin.math.abs(it - projected) })
    }
    fun stepUp() {
        val next = stopPx.firstOrNull { it > heightPx + 1f } ?: stopPx.first()
        settleTo(next)
    }

    val dragState = rememberDraggableState { delta ->
        heightPx = (heightPx - delta).coerceIn(stopPx.first() * 0.5f, stopPx.last())
    }
    // Leftover scroll from the body must not reach the sheet (it would drag it shut).
    val keepScrollInBody = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
        }
    }

    val surface = MaterialTheme.colorScheme.surface
    val container = if (accentTint) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f).compositeOver(surface) else surface
    val handleColor = if (accentTint) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
    val isTallest = heightPx >= stopPx.last() - 1f

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = container,
        contentColor = contentColorFor(surface),
        tonalElevation = 4.dp,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.40f),
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth().height(with(density) { heightPx.toDp() })) {
            // Drag zone: handle + pinned header.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> settle(velocity) }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { stepUp() }
                        .semantics {
                            stateDescription = if (isTallest) "Expanded" else "Partly expanded"
                            onClick(label = if (isTallest) "Shrink" else "Expand") { stepUp(); true }
                        }
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(handleColor)
                    )
                }
                header()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(keepScrollInBody)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding(),
                content = content
            )
        }
    }
}
