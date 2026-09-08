package com.example.ui.components.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.ProSpaceRepository
import com.example.ui.components.ProCard
import com.example.ui.components.ProOutlinedButton
import com.example.ui.components.ProPrimaryButton
import com.example.ui.theme.*
import com.example.util.AppSystemDebugger
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SystemDebuggerDialog(
    repository: ProSpaceRepository,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<AppSystemDebugger.FullAuditReport?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    fun runAudit() {
        coroutineScope.launch {
            isRunning = true
            report = AppSystemDebugger.runFullSystemAudit(context, repository)
            isRunning = false
        }
    }

    LaunchedEffect(Unit) {
        runAudit()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = OxfordBlue.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = OxfordBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Column {
                            Text(
                                "System & Firebase Debugger",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Data Connect & Architecture Compliance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Status Overview Card
                if (report != null) {
                    val r = report!!
                    ProCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "System Health: ${String.format(Locale.US, "%.1f", r.overallCompliancePercentage)}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (r.failedCount == 0) FreshGreen else CarnationOrange
                                    )
                                    Text(
                                        text = "Audited at ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(r.executionTimestamp))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BadgePill(count = r.passedCount, label = "Pass", color = FreshGreen)
                                    if (r.warningCount > 0) {
                                        BadgePill(count = r.warningCount, label = "Warn", color = AmberWarning)
                                    }
                                    if (r.failedCount > 0) {
                                        BadgePill(count = r.failedCount, label = "Fail", color = CrimsonRed)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.md))

                            // Progress Bar
                            LinearProgressIndicator(
                                progress = { r.overallCompliancePercentage / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (r.failedCount == 0) FreshGreen else CarnationOrange,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                } else if (isRunning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = OxfordBlue)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Category Filter Chips
                if (report != null) {
                    val categories = remember(report) {
                        listOf("All") + report!!.items.map { it.category }.distinct()
                    }
                    ScrollableTabRow(
                        selectedTabIndex = categories.indexOf(selectedCategoryFilter ?: "All").coerceAtLeast(0),
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        categories.forEach { cat ->
                            val isSelected = (selectedCategoryFilter == null && cat == "All") || selectedCategoryFilter == cat
                            Tab(
                                selected = isSelected,
                                onClick = {
                                    selectedCategoryFilter = if (cat == "All") null else cat
                                },
                                text = {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) OxfordBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Diagnostic Items List
                val displayedItems = remember(report, selectedCategoryFilter) {
                    if (report == null) emptyList()
                    else if (selectedCategoryFilter == null) report!!.items
                    else report!!.items.filter { it.category == selectedCategoryFilter }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedItems) { item ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (item.status) {
                                    AppSystemDebugger.DiagnosticStatus.PASSED -> FreshGreen.copy(alpha = 0.2f)
                                    AppSystemDebugger.DiagnosticStatus.WARNING -> AmberWarning.copy(alpha = 0.3f)
                                    AppSystemDebugger.DiagnosticStatus.FAILED -> CrimsonRed.copy(alpha = 0.3f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(18.dp)
                                        .background(
                                            when (item.status) {
                                                AppSystemDebugger.DiagnosticStatus.PASSED -> FreshGreen.copy(alpha = 0.15f)
                                                AppSystemDebugger.DiagnosticStatus.WARNING -> AmberWarning.copy(alpha = 0.15f)
                                                AppSystemDebugger.DiagnosticStatus.FAILED -> CrimsonRed.copy(alpha = 0.15f)
                                            },
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (item.status) {
                                            AppSystemDebugger.DiagnosticStatus.PASSED -> Icons.Default.Check
                                            AppSystemDebugger.DiagnosticStatus.WARNING -> Icons.Default.Warning
                                            AppSystemDebugger.DiagnosticStatus.FAILED -> Icons.Default.Close
                                        },
                                        contentDescription = null,
                                        tint = when (item.status) {
                                            AppSystemDebugger.DiagnosticStatus.PASSED -> FreshGreen
                                            AppSystemDebugger.DiagnosticStatus.WARNING -> AmberWarning
                                            AppSystemDebugger.DiagnosticStatus.FAILED -> CrimsonRed
                                        },
                                        modifier = Modifier.size(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.featureName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = item.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 10.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(Spacing.xs))

                                    Text(
                                        text = item.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }

                    Button(
                        onClick = { runAudit() },
                        enabled = !isRunning,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = OxfordBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRunning) "Running..." else "Re-Run Audit")
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgePill(count: Int, label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Text(
                "$count $label",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
