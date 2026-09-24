package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.TransactionStatus
import com.example.data.model.WhishTransaction
import com.example.ui.components.CustomButton
import com.example.ui.components.CustomButtonVariant
import com.example.ui.components.DateRangePickerRow
import com.example.ui.components.rememberFileExportLauncher
import com.example.ui.theme.*
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Admin-only screen — now reads from [AdminViewModel] instead of the shared
 * ProHostViewModel god object (ViewModel-split effort). This was the only screen
 * still reaching into ProHostViewModel for Admin-specific data (transactions +
 * revenue CSV export) despite AdminConsoleScreen already having its own dedicated
 * ViewModel for everything else Admin does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRevenueScreen(
    adminViewModel: AdminViewModel = viewModel()
) {
    val uiState by adminViewModel.uiState.collectAsState()
    val transactions = uiState.allTransactions

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterStatus by remember { mutableStateOf("ALL") }

    val filteredTransactions = transactions.filter { tx ->
        val matchesQuery = searchQuery.isBlank() ||
                tx.id.contains(searchQuery, ignoreCase = true) ||
                tx.orderId.contains(searchQuery, ignoreCase = true) ||
                tx.payerName.contains(searchQuery, ignoreCase = true) ||
                tx.payerPhone.contains(searchQuery, ignoreCase = true) ||
                tx.spaceId.contains(searchQuery, ignoreCase = true)

        val matchesStatus = when (selectedFilterStatus) {
            "SUCCESS" -> tx.status.name == "SUCCESS"
            "PENDING" -> tx.status.name == "PENDING"
            else -> true
        }
        matchesQuery && matchesStatus
    }

    // Deliberately independent of the search/status filter above — the current filter's
    // default "ALL" branch matched every status including FAILED/PENDING, so "Total
    // Settlement Volume" was silently counting money that never actually settled. This
    // tile always reflects real settled (SUCCESS-only) volume, matching the "Whish
    // Volume"/"Whish Settled" tiles shown elsewhere in the Admin Console.
    val settledVolume = transactions.filter { it.status == TransactionStatus.SUCCESS }.sumOf { it.amountUsd }

    var exportFromMillis by remember { mutableStateOf<Long?>(null) }
    var exportToMillis by remember { mutableStateOf<Long?>(null) }
    val exportCsvFile = rememberFileExportLauncher(mimeType = "text/csv")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = OxfordBlue,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Package Revenue & Performance",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = PureWhite
                    )
                    Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = CarnationOrange, modifier = Modifier.size(36.dp))
                }
            }
        }

        // Metrics Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Total Settlement Volume", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "$${String.format(Locale.US, "%.2f", settledVolume)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = FreshGreen)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Verified Transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${filteredTransactions.size} Records", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Export date range + real CSV file export (Storage Access Framework "Save As")
        DateRangePickerRow(
            fromMillis = exportFromMillis,
            toMillis = exportToMillis,
            onFromChange = { exportFromMillis = it },
            onToChange = { exportToMillis = it }
        )
        CustomButton(
            text = "Export to CSV (Package ID, User ID, Price, Date, Expiration)",
            onClick = {
                val csvContent = adminViewModel.exportRevenueCsv(exportFromMillis, exportToMillis)
                exportCsvFile("prohost_revenue.csv", csvContent)
            },
            variant = CustomButtonVariant.SUCCESS,
            icon = Icons.Default.Download,
            modifier = Modifier.fillMaxWidth()
        )

        // Search & Filter
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search transactions by ID, User, Phone or Package") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )

        Text(
            text = "TRANSACTION VERIFICATION AUDIT TRAIL",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No transactions found matching criteria.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            filteredTransactions.forEach { tx ->
                TransactionCard(tx = tx)
            }
        }
    }
}

@Composable
fun TransactionCard(tx: WhishTransaction) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    val dateStr = dateFormat.format(Date(tx.timestamp))
    val expiryStr = dateFormat.format(Date(tx.timestamp + (tx.daysGranted.toLong() * 24 * 60 * 60 * 1000)))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tx.id,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = if (tx.status.name == "SUCCESS") FreshGreen.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = tx.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (tx.status.name == "SUCCESS") FreshGreen else AmberWarning,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = tx.spaceTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Package ID:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = tx.spaceId, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Amount:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "$${String.format(Locale.US, "%.2f", tx.amountUsd)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = CarnationOrange)
                }
            }

            HorizontalDivider(color = LightGray.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Payer / Phone:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${tx.payerName} (${tx.payerPhone})", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Date Bought / Expiry:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "$dateStr → $expiryStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
