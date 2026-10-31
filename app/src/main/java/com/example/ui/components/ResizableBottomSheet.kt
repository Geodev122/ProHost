package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * A tall bottom sheet with a pinned [header] and a scrolling body, built on the same
 * standard Material sheet as every other sheet in the app (filters, requirements, …):
 * it overlays all content in its own window, opens half-way, and the person drags the
 * handle (or flings) between half and full height or down to close — Material moves the
 * whole sheet, so nothing else on screen shifts. Pass a state with
 * `skipPartiallyExpanded = false` to get the half-height stop.
 *
 * (It used to resize its own content height on every drag frame, which fought the
 * sheet's own positioning and made the bottom of the sheet slide away while dragging.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResizableBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    accentTint: Boolean = false,
    maxHeightFraction: Float = 0.92f,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetHeight = (LocalConfiguration.current.screenHeightDp * maxHeightFraction).dp
    ProHostBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        accentTint = accentTint,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().height(sheetHeight)) {
            header()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding(),
                content = content
            )
        }
    }
}
