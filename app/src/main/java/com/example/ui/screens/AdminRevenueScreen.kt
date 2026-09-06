package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.WhishTransaction
import com.example.ui.theme.*
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Admin-only screen — now reads from [AdminViewModel] instead of the shared
 * ProSpaceViewModel god object (ViewModel-split effort). This was the only screen
 * still reaching into ProSpaceViewModel for Admin-specific data (transactions +
 * revenue CSV export) despite AdminConsoleScreen already having its own dedicated
 * ViewModel for everything else Admin does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRevenueScreen(
    adminViewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
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

    val totalVolume = filteredTransactions.sumOf { it.amountUsd }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = OxfordBlue,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Package Revenue & Performance",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Monitor package purchases, Whish verification transactions, and financial yields",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LightGray
                        )
                    }
                    Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = CarnationOrange, modifier = Modifier.size(36.dp))
                }
            }
        }

        // Metrics Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
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
                    Text(text = "Total Settlement Volume", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                    Text(text = "$${String.format(Locale.US, "%.2f", totalVolume)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = FreshGreen)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Verified Transactions", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                    Text(text = "${filteredTransactions.size} Records", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OxfordBlue)
                }
            }
        }

        // Export to Excel / CSV button
        Button(
            onClick = {
                val csvContent = adminViewModel.exportRevenueCsv(null, null)
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("ProHost Revenue CSV", csvContent)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Revenue Data Exported to Excel/CSV & Copied to Clipboard!", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = FreshGreen),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export to Excel / CSV (Package ID, User ID, Price, Date, Expiration)", fontWeight = FontWeight.Bold, color = PureWhite)
        }

        // Search & Filter
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search transactions by ID, User, Phone or Package") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Text(
            text = "TRANSACTION VERIFICATION AUDIT TRAIL",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CoolGray,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No transactions found matching criteria.", color = CoolGray)
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tx.id,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = OxfordBlue
                )
                Surface(
                    color = if (tx.status.name == "SUCCESS") FreshGreen.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
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
                color = OxfordBlue
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Package ID:", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                    Text(text = tx.spaceId, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Amount:", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                    Text(text = "$${String.format(Locale.US, "%.2f", tx.amountUsd)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = CarnationOrange)
                }
            }

            HorizontalDivider(color = LightGray.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Payer / Phone:", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                    Text(text = "${tx.payerName} (${tx.payerPhone})", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Date Bought / Expiry:", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                    Text(text = "$dateStr → $expiryStr", style = MaterialTheme.typography.bodySmall, color = CoolGray)
                }
            }
        }
    }
}
