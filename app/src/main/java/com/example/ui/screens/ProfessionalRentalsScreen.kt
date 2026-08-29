package com.example.ui.screens

import androidx.compose.runtime.Composable
import com.example.data.model.SpaceListing
import com.example.ui.viewmodel.ProSpaceViewModel

/**
 * Delegating wrapper for MyBookingsScreen.
 * Retained for backward compatibility while eliminating UI duplication.
 */
@Deprecated(
    message = "Use MyBookingsScreen directly instead.",
    replaceWith = ReplaceWith("MyBookingsScreen(viewModel, onNavigateToDiscovery, onSelectSpace)")
)
@Composable
fun ProfessionalRentalsScreen(
    viewModel: ProSpaceViewModel,
    onNavigateToDiscovery: () -> Unit,
    onSelectSpace: (SpaceListing) -> Unit
) {
    MyBookingsScreen(
        viewModel = viewModel,
        onNavigateToDiscovery = onNavigateToDiscovery,
        onSelectSpace = onSelectSpace
    )
}
