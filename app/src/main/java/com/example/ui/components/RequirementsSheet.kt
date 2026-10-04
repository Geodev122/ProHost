package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppUser
import com.example.data.model.findCountryByName
import com.example.ui.screens.PhoneVerificationSection
import com.example.ui.theme.Spacing
import com.example.ui.theme.proColors
import com.example.ui.viewmodel.ProHostViewModel
import kotlinx.coroutines.launch

/**
 * The one verification flow. Booking ([AppUser.canTransact]): a profile photo and a verified
 * phone. Hosting ([AppUser.canHost], [requireAddress]): the same plus country and city.
 * Opened in place over the screen that asked, showing only the missing steps; [onReady]
 * fires once all are done so the caller resumes exactly where the person was (their
 * selected slots or chosen plan are never lost).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequirementsSheet(
    user: AppUser,
    viewModel: ProHostViewModel,
    onDismiss: () -> Unit,
    onReady: () -> Unit,
    requireAddress: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val needsPhoto = user.profilePictureUrl.isNullOrBlank()
    val needsPhone = !user.hasVerifiedPhone(com.example.data.auth.PhoneLink.isLinked())
    val needsAddress = requireAddress && (user.country.isBlank() || user.city.isBlank())
    var uploading by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var addressCountry by remember { mutableStateOf(findCountryByName(user.country.ifBlank { "Lebanon" })) }
    var addressCity by remember { mutableStateOf(user.city) }
    var savingAddress by remember { mutableStateOf(false) }
    var addressError by remember { mutableStateOf<String?>(null) }

    // All done (the live profile updated): hand control back to the caller.
    LaunchedEffect(needsPhoto, needsPhone, needsAddress) {
        if (!needsPhoto && !needsPhone && !needsAddress) onReady()
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        photoError = null
        scope.launch {
            photoError = viewModel.updateProfilePhoto(context, uri)
            uploading = false
        }
    }

    ProHostBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            ProSectionHeader(
                title = "Almost there",
                subtitle = if (requireAddress) {
                    "Needed once before you host. Your chosen plan is kept."
                } else {
                    "Hosts need these once before your first request. Your selection is kept."
                },
                icon = Icons.Default.VerifiedUser
            )

            RequirementRow(
                done = !needsPhoto,
                title = "Profile photo",
                detail = "Hosts use it to recognise who's coming."
            )
            if (needsPhoto) {
                photoError?.let { ProHostAlertBanner(message = it, severity = ProHostAlertSeverity.ERROR) }
                ProPrimaryButton(
                    text = if (uploading) "Uploading…" else "Add a photo",
                    onClick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !uploading,
                    isLoading = uploading,
                    icon = Icons.Default.AddAPhoto,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            RequirementRow(
                done = !needsPhone,
                title = "Verified phone",
                detail = "For booking updates and so the host can reach you."
            )
            if (needsPhone) {
                PhoneVerificationSection(onVerified = { /* the profile listener flips needsPhone */ })
            }

            if (requireAddress) {
                RequirementRow(
                    done = !needsAddress,
                    title = "Country and city",
                    detail = "Shown on your listings so specialists know where you host."
                )
                if (needsAddress) {
                    CountryDropdownField(
                        selectedCountry = addressCountry,
                        onCountrySelected = { addressCountry = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = addressCity,
                        onValueChange = { addressCity = it },
                        label = { Text("City") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    addressError?.let { ProHostAlertBanner(message = it, severity = ProHostAlertSeverity.ERROR) }
                    ProPrimaryButton(
                        text = if (savingAddress) "Saving…" else "Save address",
                        onClick = {
                            savingAddress = true
                            addressError = null
                            scope.launch {
                                addressError = viewModel.updateAddress(context, addressCountry.name, addressCity.trim())
                                savingAddress = false
                            }
                        },
                        enabled = !savingAddress && addressCity.isNotBlank(),
                        isLoading = savingAddress,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (uploading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Saving your photo…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RequirementRow(done: Boolean, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = if (done) "Done" else "To do",
            tint = if (done) MaterialTheme.proColors.success else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
