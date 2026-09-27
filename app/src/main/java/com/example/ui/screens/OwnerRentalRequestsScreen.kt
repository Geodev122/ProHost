package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.premiumBackgroundBrush
import com.example.ui.theme.Spacing
import com.example.ui.viewmodel.ProHostViewModel

@Composable
fun OwnerRentalRequestsScreen(
    viewModel: ProHostViewModel
) {
    val isLoading by viewModel.isRestoringSession.collectAsState()
    val errorMessage by viewModel.sessionRestoreError.collectAsState()
    val incomingRequests by viewModel.ownerIncomingRequests.collectAsState()
    val spaces by viewModel.spaces.collectAsState()

    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        errorMessage != null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Spacing.lg)
                )
            }
        }
        !isLoading && incomingRequests.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ProEmptyState(
                    title = "No Rental Requests",
                    description = "You have no incoming rental requests yet. Publish a listing to start receiving bookings.",
                    icon = Icons.Default.Inbox
                )
            }
        }
        else -> OwnerRentalRequestsScreenContent(
            incomingRequests = incomingRequests,
            spaces = spaces,
            viewModel = viewModel
        )
    }
}

@Composable
fun OwnerRentalRequestsScreenContent(
    incomingRequests: List<BookingRequest>,
    spaces: List<SpaceListing>,
    viewModel: ProHostViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(premiumBackgroundBrush()),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "${incomingRequests.size} Total Requests",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        item {
            OwnerIncomingRequestsView(
                requests = incomingRequests,
                spaces = spaces,
                viewModel = viewModel
            )
        }
    }
}
