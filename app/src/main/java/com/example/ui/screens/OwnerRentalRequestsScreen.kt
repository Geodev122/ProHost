package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.PremiumBackgroundGradient
import com.example.ui.viewmodel.ProSpaceViewModel

@Composable
fun OwnerRentalRequestsScreen(
    viewModel: ProSpaceViewModel
) {
    val incomingRequests by viewModel.ownerIncomingRequests.collectAsState()
    val spaces by viewModel.spaces.collectAsState()
    val isOffline by viewModel.isOfflineMode.collectAsState()
    val syncStatus by viewModel.syncStatusMessage.collectAsState()
    val pendingOfflineTx by viewModel.pendingOfflineTransactions.collectAsState()

    OwnerRentalRequestsScreenContent(
        incomingRequests = incomingRequests,
        spaces = spaces,
        isOffline = isOffline,
        syncStatus = syncStatus,
        pendingOfflineCount = pendingOfflineTx.size,
        onRetrySync = { viewModel.retryOfflineSync() },
        viewModel = viewModel
    )
}

@Composable
fun OwnerRentalRequestsScreenContent(
    incomingRequests: List<BookingRequest>,
    spaces: List<SpaceListing>,
    isOffline: Boolean = false,
    syncStatus: String? = null,
    pendingOfflineCount: Int = 0,
    onRetrySync: () -> Unit = {},
    viewModel: ProSpaceViewModel,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PremiumBackgroundGradient),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            NetworkSyncResilienceBanner(
                isOffline = isOffline,
                statusMessage = syncStatus,
                pendingOfflineCount = pendingOfflineCount,
                onRetrySync = onRetrySync
            )
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
