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
// TAB 1: USERS DIRECTORY & GOVERNANCE
// =========================================================================
@Composable
internal fun AdminUsersDirectoryTab(
    uiState: com.example.ui.state.AdminUiState,
    adminViewModel: AdminViewModel
) {
    val exportCsvFile = rememberFileExportLauncher(mimeType = "text/csv")
    val exportScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Real totals (adminCounts): the console never loads the user collection.
        item {
            val counts = uiState.counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminMetricTile("Total Users", counts?.totalUsers?.toString() ?: "…", modifier = Modifier.weight(1f))
                AdminMetricTile("Pro Hosts", counts?.proHosts?.toString() ?: "…", modifier = Modifier.weight(1f))
                AdminMetricTile("Specialists", counts?.specialists?.toString() ?: "…", modifier = Modifier.weight(1f))
                AdminMetricTile("New (30d)", counts?.let { "+${it.newUsers30d}" } ?: "…", modifier = Modifier.weight(1f))
            }
        }

        item {
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProSectionHeader(
                            title = "User Directory",
                            subtitle = "Search anyone; export every user",
                            icon = Icons.Default.People
                        )

                        CustomButton(
                            text = if (uiState.isExportingUsers) "Exporting…" else "Export all (CSV)",
                            onClick = {
                                exportScope.launch {
                                    adminViewModel.buildUsersExportCsv()?.let { csv ->
                                        exportCsvFile("prohost_users_${java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())}.csv", csv)
                                    }
                                }
                            },
                            enabled = !uiState.isExportingUsers,
                            variant = CustomButtonVariant.PRIMARY,
                            icon = Icons.Default.Download,
                            compact = true
                        )
                    }

                    // Smart search: name, email, U-/L-/D-/B- code or Firebase UID (server-side).
                    InputField(
                        value = uiState.userSearchQuery,
                        onValueChange = { adminViewModel.setUserSearchQuery(it) },
                        label = "Name, email, U- code or UID",
                        leadingIcon = Icons.Default.Search,
                        trailingIcon = {
                            if (uiState.userSearchQuery.isNotBlank()) {
                                IconButton(onClick = { adminViewModel.setUserSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Role chips narrow the results.
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.selectedUserRoleFilter == null,
                                onClick = { adminViewModel.setUserRoleFilter(null) },
                                label = { Text("All roles") }
                            )
                        }
                        items(UserRole.entries) { role ->
                            FilterChip(
                                selected = uiState.selectedUserRoleFilter == role,
                                onClick = { adminViewModel.setUserRoleFilter(role) },
                                label = { Text(role.name.replace("_", " ")) }
                            )
                        }
                    }
                }
            }
        }

        when {
            uiState.isSearchingUsers -> item { ShimmerLoadingList(count = 2, itemHeight = 110.dp) }
            uiState.userSearchQuery.isBlank() -> item {
                ProEmptyState(
                    title = "Find a user",
                    description = "Type a name, email, U- code or UID. Nothing is loaded until you search.",
                    icon = Icons.Default.PersonSearch
                )
            }
            uiState.searchError != null -> item {
                ProHostAlertBanner(message = uiState.searchError, severity = ProHostAlertSeverity.ERROR)
            }
            uiState.filteredUsers.isEmpty() -> item {
                ProEmptyState(
                    title = "No users found",
                    description = "No account matches \"${uiState.userSearchQuery.trim()}\".",
                    icon = Icons.Default.PersonOff
                )
            }
        }

        // List of filtered users
        items(uiState.filteredUsers, key = { it.id }) { user ->
            ProSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            ProMemberAvatar(
                                name = user.fullName,
                                specialty = user.specialty,
                                isVerified = user.isVerified,
                                size = 44.dp,
                                imageUrl = user.profilePictureUrl
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = user.fullName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (user.isSuspended) {
                                Surface(color = StatusErrorContainer, shape = MaterialTheme.shapes.small) {
                                    Text(
                                        text = "SUSPENDED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusOnErrorContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            // Role Badge
                            Surface(
                                color = when (user.role) {
                                    UserRole.ADMIN -> StatusWarningContainer
                                    UserRole.PRO_HOST -> CarnationOrangeContainer
                                    UserRole.SPECIALIST -> OxfordBlueContainer
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = user.role.name.replace("_", " "),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when (user.role) {
                                        UserRole.ADMIN -> AmberWarning
                                        UserRole.PRO_HOST -> CarnationOrange
                                        UserRole.SPECIALIST -> OxfordBlue
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // User Details Grid
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Location:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                user.city.ifBlank { user.governorate.ifBlank { user.country } },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column {
                            Text("Phone Status:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (user.isVerified) "Verified" else "Unverified",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (user.isVerified) FreshGreen else StatusError
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Phone / WhatsApp:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(user.phone.ifBlank { "N/A" }, style = MaterialTheme.typography.bodySmall)
                        }
                        Column {
                            Text("Member Since:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                user.createdAtMillis?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(it)) } ?: "—",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Column {
                            Text("Last Sign-In:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                user.lastSignInAtMillis?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(it)) } ?: "—",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CustomButton(
                            text = "Profile",
                            onClick = { adminViewModel.openDossier(user.id) },
                            modifier = Modifier.weight(1f),
                            variant = CustomButtonVariant.PRIMARY,
                            icon = Icons.Default.Badge,
                            compact = true
                        )

                        // Edit Button
                        CustomButton(
                            text = "Edit",
                            onClick = { adminViewModel.openEditUserDialog(user) },
                            modifier = Modifier.weight(0.9f),
                            variant = CustomButtonVariant.OUTLINED,
                            icon = Icons.Default.Edit,
                            compact = true
                        )

                        // Grant Admin Button
                        if (user.role != UserRole.ADMIN) {
                            IconButton(
                                onClick = { adminViewModel.openGrantAdminDialog(user) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.AdminPanelSettings,
                                    contentDescription = "Grant Admin",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Suspend / Reactivate Button — the state between "exists" and
                        // "deleted"; never offered for an Admin account (the server
                        // rejects that anyway, but hiding it here avoids a confusing
                        // failed attempt).
                        if (user.role != UserRole.ADMIN) {
                            IconButton(
                                onClick = { adminViewModel.openSuspendUserDialog(user) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (user.isSuspended) Icons.Default.LockOpen else Icons.Default.Block,
                                    contentDescription = if (user.isSuspended) "Reactivate Account" else "Suspend Account",
                                    tint = if (user.isSuspended) FreshGreen else StatusError,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Revoke Pro Host Button — the downgrade path back to Specialist
                        // that never existed before; only meaningful for an account that
                        // currently holds the role.
                        if (user.role == UserRole.PRO_HOST) {
                            IconButton(
                                onClick = { adminViewModel.openRevokeProHostDialog(user) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.RemoveModerator,
                                    contentDescription = "Revoke Pro Host Role",
                                    tint = StatusError,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Delete Button
                        if (user.role != UserRole.ADMIN) {
                            IconButton(
                                onClick = { adminViewModel.openDeleteUserDialog(user) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = StatusError, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. Edit User Dialog
 */
@Composable
internal fun AdminEditUserDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    onSave: (AppUser) -> Unit
) {
    var fullName by remember { mutableStateOf(user.fullName) }
    var email by remember { mutableStateOf(user.email) }
    var phone by remember { mutableStateOf(user.phone) }
    var specialty by remember { mutableStateOf(user.specialty) }
    var country by remember { mutableStateOf(user.country) }
    var governorateArea by remember { mutableStateOf(user.governorate) }
    var city by remember { mutableStateOf(user.city) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(Spacing.sm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Edit User: ${user.publicCode}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                InputField(value = fullName, onValueChange = { fullName = it }, label = "Full Name", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(value = email, onValueChange = { email = it }, label = "Email", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(value = phone, onValueChange = { phone = it }, label = "Phone (WhatsApp)", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(
                    value = specialty,
                    onValueChange = { specialty = it },
                    label = "Specialty / Profession",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                InputField(value = country, onValueChange = { country = it }, label = "Country", modifier = Modifier.fillMaxWidth(), singleLine = true)
                InputField(
                    value = governorateArea,
                    onValueChange = { governorateArea = it },
                    label = "Governorate / Area",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                InputField(value = city, onValueChange = { city = it }, label = "City", modifier = Modifier.fillMaxWidth(), singleLine = true)
                // Role and phone-verified status both go exclusively through dedicated
                // Cloud Functions (grantAdminRole / assignInitialRole) — a direct write to
                // either from this generic edit form is denied by Firestore rules. Shown
                // read-only here; use the Grant Admin action on the user row instead.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Role: ${user.role.name.replace("_", " ")}  •  ${if (user.isVerified) "Phone Verified" else "Phone Unverified"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Change these from the user row's own actions, not here.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.OUTLINED
                    )
                    CustomButton(
                        text = "Save Changes",
                        onClick = {
                            val updated = user.copy(
                                fullName = fullName.trim(),
                                email = email.trim(),
                                phone = phone.trim(),
                                specialty = specialty.trim(),
                                country = country.trim(),
                                governorate = governorateArea.trim(),
                                city = city.trim()
                            )
                            onSave(updated)
                        },
                        modifier = Modifier.weight(1f),
                        variant = CustomButtonVariant.PRIMARY
                    )
                }
            }
        }
    }
}

/**
 * 3. Delete User Dialog
 */
@Composable
internal fun AdminDeleteUserDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ProHostDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError) },
        title = { Text("Delete User Record?") },
        text = {
            Text("Are you sure you want to permanently remove '${user.fullName}' (${user.email})'s profile from the pl" +
                "atform? Their sign-in credentials are not revoked by this action.")
        },
        confirmButton = {
            CustomButton(
                text = "Confirm Delete",
                onClick = onConfirm,
                variant = CustomButtonVariant.DANGER
            )
        },
        dismissButton = {
            CustomButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = CustomButtonVariant.OUTLINED
            )
        }
    )
}

/**
 * 3b. Grant Admin Confirmation Dialog
 */
@Composable
internal fun AdminGrantAdminDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ProHostDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Grant Admin Role?") },
        text = {
            Text("Are you sure you want to grant full Admin privileges to '${user.fullName}' (${user.email})? This giv" +
                "es them unrestricted access to governance, pricing, and user management.")
        },
        confirmButton = {
            CustomButton(
                text = "Confirm Grant",
                onClick = onConfirm,
                variant = CustomButtonVariant.SUCCESS
            )
        },
        dismissButton = {
            CustomButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = CustomButtonVariant.OUTLINED
            )
        }
    )
}

@Composable
internal fun AdminSuspendUserDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val suspending = !user.isSuspended
    ProHostDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (suspending) Icons.Default.Block else Icons.Default.LockOpen,
                contentDescription = null,
                tint = if (suspending) StatusError else FreshGreen
            )
        },
        title = { Text(if (suspending) "Suspend Account?" else "Reactivate Account?") },
        text = {
            Text(
                if (suspending) {
                    "'${user.fullName}' (${user.email}) will be signed out and unable to sign back in, create listings, or submit booking " +
                    "requests until reactivated. Their data and history are kept — this is not a deletion."
                } else {
                    "'${user.fullName}' (${user.email}) will regain full access immediately."
                }
            )
        },
        confirmButton = {
            CustomButton(
                text = if (suspending) "Confirm Suspend" else "Confirm Reactivate",
                onClick = onConfirm,
                variant = if (suspending) CustomButtonVariant.DANGER else CustomButtonVariant.SUCCESS
            )
        },
        dismissButton = {
            CustomButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = CustomButtonVariant.OUTLINED
            )
        }
    )
}

@Composable
internal fun AdminRevokeProHostDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ProHostDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.RemoveModerator, contentDescription = null, tint = StatusError) },
        title = { Text("Revoke Pro Host Role?") },
        text = {
            Text(
                "'${user.fullName}' (${user.email}) will be downgraded back to Specialist immediately. Every listing they've published will be " +
                "marked inactive/expired (still visible in Discovery unless the specialist filters for active-subscription only, but shown as " +
                "expired — not deleted). This does not affect their ability to book workspaces as a Specialist."
            )
        },
        confirmButton = {
            CustomButton(
                text = "Confirm Revoke",
                onClick = onConfirm,
                variant = CustomButtonVariant.DANGER
            )
        },
        dismissButton = {
            CustomButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = CustomButtonVariant.OUTLINED
            )
        }
    )
}
