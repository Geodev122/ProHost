package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.Spacing

@Composable
fun ExportDataDialog(
    csvContent: String,
    jsonContent: String,
    auditTextContent: String,
    onDismiss: () -> Unit,
    onShare: (format: String, content: String) -> Unit
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableIntStateOf(0) } // 0: CSV, 1: JSON, 2: Audit Text

    val currentContent = when (selectedFormat) {
        0 -> csvContent
        1 -> jsonContent
        else -> auditTextContent
    }

    val formatLabel = when (selectedFormat) {
        0 -> "CSV (Spreadsheet)"
        1 -> "JSON (Structured)"
        else -> "Audit Text Summary (.txt)"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = "Multi-Format Export Hub",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tabs for format selection
                TabRow(
                    selectedTabIndex = selectedFormat,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(MaterialTheme.shapes.medium)
                ) {
                    Tab(
                        selected = selectedFormat == 0,
                        onClick = { selectedFormat = 0 },
                        text = { Text("CSV", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedFormat == 1,
                        onClick = { selectedFormat = 1 },
                        text = { Text("JSON", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedFormat == 2,
                        onClick = { selectedFormat = 2 },
                        text = { Text("Audit TXT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Text(
                    text = "Live Monospace Preview ($formatLabel):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Monospace scrollable preview window
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.md)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = currentContent,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Actions: Copy & Native Android Share Sheet
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProOutlinedButton(
                        text = "Copy",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(formatLabel, currentContent)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "$formatLabel copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        icon = Icons.Default.ContentCopy,
                        modifier = Modifier.weight(1f)
                    )

                    ProPrimaryButton(
                        text = "Share File / Sheet",
                        onClick = { onShare(formatLabel, currentContent) },
                        icon = Icons.Default.Share,
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        }
    }
}
