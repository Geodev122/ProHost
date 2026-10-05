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

@Composable
internal fun AdminDemoControlTab(
    uiState: AdminUiState,
    adminViewModel: AdminViewModel,
    viewModel: ProHostViewModel
) {
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }
    // Server totals (adminCounts) — the console no longer loads every document.
    val demoSpacesCount = uiState.counts?.demoListings ?: 0
    val demoUsersCount = uiState.counts?.demoUsers ?: 0
    val demoBookingsCount = uiState.counts?.demoBookings ?: 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Text("Demo Content Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Generate or purge legitimate demo listings, fake rental requests, and demo specialist/host accounts for testing, client showcases, and UI verification before going live in production.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Active Demo Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Demo Listings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$demoSpacesCount", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Fake Requests", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$demoBookingsCount", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = FreshGreen)
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Demo Users", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$demoUsersCount", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AmberWarning)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Seeding writes fake users/listings to the live project, so release builds can't.
                        if (com.example.BuildConfig.DEBUG) {
                            CustomButton(
                                text = "Generate Demo Content",
                                onClick = { adminViewModel.seedDemoContent() },
                                modifier = Modifier.weight(1f),
                                variant = CustomButtonVariant.PRIMARY,
                                icon = Icons.Default.AutoAwesome
                            )
                        }

                        CustomButton(
                            text = "Purge Demo Content",
                            onClick = { showPurgeConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            variant = CustomButtonVariant.DANGER,
                            icon = Icons.Default.DeleteSweep
                        )
                    }
                    // Short public codes (U-/L-/D-/B-) are assigned automatically to new
                    // documents; this fills in everything created before codes existed.
                    CustomButton(
                        text = "Assign Display Codes to Existing Records",
                        onClick = { adminViewModel.backfillDisplayCodes() },
                        modifier = Modifier.fillMaxWidth(),
                        variant = CustomButtonVariant.OUTLINED,
                        icon = Icons.Default.Tag
                    )
                    // One-time maintenance for the search-first console and the email rule.
                    CustomButton(
                        text = "Index Names for Admin Search",
                        onClick = { adminViewModel.runMaintenance("backfillSearchNames", "Search index") },
                        modifier = Modifier.fillMaxWidth(),
                        variant = CustomButtonVariant.OUTLINED,
                        icon = Icons.Default.Search
                    )
                    CustomButton(
                        text = "Mark Email/Google Sign-ups Email-Verified",
                        onClick = { adminViewModel.runMaintenance("backfillEmailVerified", "Email verification backfill") },
                        modifier = Modifier.fillMaxWidth(),
                        variant = CustomButtonVariant.OUTLINED,
                        icon = Icons.Default.MarkEmailRead
                    )
                }
            }
        }
    }

    if (showPurgeConfirmDialog) {
        ProHostDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Purge All Demo Content?") },
            text = {
                Text("This will permanently delete all $demoSpacesCount demo listings, $demoBookingsCount fake rental requests, and $demoUsersCount demo user accounts from Firestore and local memory before production.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPurgeConfirmDialog = false
                        adminViewModel.purgeDemoContent()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Purge Everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
