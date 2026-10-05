package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.*
import com.example.ui.components.*
import androidx.compose.foundation.BorderStroke
import com.example.ui.state.AdminUiEvent
import com.example.ui.state.AdminUiState
import com.example.ui.theme.*
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.ProHostViewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =========================================================================
// TAB 0: PACKAGES — Force Upgrade only. Google Play is the only billing authority:
// no plans, prices, durations or renewals are managed here.
// =========================================================================
@Composable
internal fun AdminPackagesTab(
    uiState: com.example.ui.state.AdminUiState,
    adminViewModel: AdminViewModel
) {
    LaunchedEffect(Unit) { adminViewModel.refreshBilling() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AdminBillingRescueCard(uiState = uiState, adminViewModel = adminViewModel) }
        item { AdminPendingPaymentsCard(payments = uiState.pendingPayments, uiState = uiState, adminViewModel = adminViewModel) }
        item {
            AdminGrantAccessCard(adminViewModel = adminViewModel)
        }

        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProSectionHeader(
                        title = "Google Play Billing",
                        subtitle = "Read-only — managed in Play Console",
                        icon = Icons.Default.Info
                    )
                    Text(
                        "Subscription ${com.example.data.billing.PlayCatalog.PRODUCT_ID} with base plans " +
                            "${com.example.data.billing.PlayCatalog.BASE_PLAN_MONTHLY} (monthly) and " +
                            "${com.example.data.billing.PlayCatalog.BASE_PLAN_YEARLY} (yearly). Prices, offers, " +
                            "renewals and refunds come only from Google Play; roles follow automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { adminViewModel.runBillingSync() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync with Google Play now", style = MaterialTheme.typography.labelLarge)
                    }

                    HorizontalDivider()

                    var tagInput by remember(uiState.pricingState.governanceTag) { mutableStateOf(uiState.pricingState.governanceTag) }
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text("Admin Governance Control Tag") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { adminViewModel.updateGovernanceTag(tagInput) }) {
                                Icon(Icons.Default.Check, contentDescription = "Save Tag", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }
        }

        // Quick Export Hub Shortcuts — writes a real file (Storage Access Framework
        // "Save As") instead of the old clipboard-copy/share-sheet-only dialog.
        item {
            val exportCsvFile = rememberFileExportLauncher(mimeType = "text/csv")
            val exportScope = rememberCoroutineScope()

            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProSectionHeader(
                        title = "One-Click System Exports",
                        subtitle = "Every user, built on the server",
                        icon = Icons.Default.CloudDownload
                    )

                    // Server-side export of every user (adminExportUsers): UID, U- code, role,
                    // package and listing codes. The console never holds the whole directory.
                    CustomButton(
                        text = if (uiState.isExportingUsers) "Exporting…" else "All users (CSV)",
                        onClick = {
                            exportScope.launch {
                                adminViewModel.buildUsersExportCsv()?.let { csv ->
                                    exportCsvFile("prohost_users_${java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())}.csv", csv)
                                }
                            }
                        },
                        enabled = !uiState.isExportingUsers,
                        modifier = Modifier.fillMaxWidth(),
                        variant = CustomButtonVariant.PRIMARY,
                        icon = Icons.Default.Download,
                        compact = true
                    )
                }
            }
        }
    }

}
