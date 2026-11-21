package com.example.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppUser
import com.example.data.model.findCountryByName
import com.example.ui.screens.WhatsAppNumberSection
import com.example.ui.theme.Spacing
import com.example.ui.theme.proColors
import com.example.ui.viewmodel.ProHostViewModel
import kotlinx.coroutines.launch
import com.example.shared.resources.Res
import com.example.shared.resources.add_a_photo
import com.example.shared.resources.almost_there
import com.example.shared.resources.city
import com.example.shared.resources.country_and_city
import com.example.shared.resources.done
import com.example.shared.resources.profile_photo
import com.example.shared.resources.save_address
import com.example.shared.resources.saving
import com.example.shared.resources.saving_your_photo
import com.example.shared.resources.to_do
import com.example.shared.resources.uploading
import com.example.shared.resources.whatsapp_number
import com.example.ui.util.appString
import com.example.ui.util.sharedText

/**
 * The one verification flow. Booking ([AppUser.canTransact]): a profile photo and a WhatsApp
 * number. Hosting ([AppUser.canHost], [requireAddress]): the same plus country and city.
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
    val analyticsSource = if (requireAddress) "hosting" else "booking"
    com.example.ui.components.TrackScreen("requirements", params = mapOf(com.example.analytics.Param.SOURCE to analyticsSource))
    LaunchedEffect(Unit) {
        com.example.analytics.AnalyticsTracker.kycStart(analyticsSource)
        com.example.analytics.AnalyticsTracker.requirementsShown(analyticsSource, listOfNotNull(
            "photo".takeIf { user.profilePictureUrl.isNullOrBlank() },
            "whatsapp".takeIf { !user.hasWhatsAppNumber() },
            "address".takeIf { requireAddress && (user.country.isBlank() || user.city.isBlank()) }
        ).joinToString(","))
    }
    val scope = rememberCoroutineScope()
    val needsPhoto = user.profilePictureUrl.isNullOrBlank()
    val needsPhone = !user.hasWhatsAppNumber()
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

    val pickPhoto = rememberVisualMediaImagePicker { file ->
        uploading = true
        photoError = null
        scope.launch {
            photoError = viewModel.updateProfilePhoto(file)
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
                title = appString(Res.string.almost_there),
                subtitle = if (requireAddress) {
                    sharedText("needed_once_before_you_host_your_chosen_pl")
                } else {
                    sharedText("hosts_need_these_once_before_your_first_re")
                },
                icon = Icons.Default.VerifiedUser
            )

            RequirementRow(
                done = !needsPhoto,
                title = appString(Res.string.profile_photo),
                detail = sharedText("hosts_use_it_to_recognise_who_s_coming")
            )
            if (needsPhoto) {
                photoError?.let { ProHostAlertBanner(message = it, severity = ProHostAlertSeverity.ERROR) }
                ProPrimaryButton(
                    text = if (uploading) appString(Res.string.uploading) else appString(Res.string.add_a_photo),
                    onClick = {
                        pickPhoto()
                    },
                    enabled = !uploading,
                    isLoading = uploading,
                    icon = Icons.Default.AddAPhoto,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            RequirementRow(
                done = !needsPhone,
                title = appString(Res.string.whatsapp_number),
                detail = sharedText("so_the_host_can_reach_you_about_your_booki")
            )
            if (needsPhone) {
                WhatsAppNumberSection(
                    currentPhone = user.phone,
                    onSave = { viewModel.saveWhatsAppNumber(it, source = analyticsSource) },
                    onSaved = { /* the profile listener flips needsPhone */ }
                )
            }

            if (requireAddress) {
                RequirementRow(
                    done = !needsAddress,
                    title = appString(Res.string.country_and_city),
                    detail = sharedText("shown_on_your_listings_so_specialists_know")
                )
                if (needsAddress) {
                    CountryDropdownField(
                        selectedCountry = addressCountry,
                        onCountrySelected = { addressCountry = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    InputField(
                        value = addressCity,
                        onValueChange = { addressCity = it },
                        label = appString(Res.string.city),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    addressError?.let { ProHostAlertBanner(message = it, severity = ProHostAlertSeverity.ERROR) }
                    ProPrimaryButton(
                        text = if (savingAddress) appString(Res.string.saving) else appString(Res.string.save_address),
                        onClick = {
                            savingAddress = true
                            addressError = null
                            scope.launch {
                                addressError = viewModel.updateAddress(addressCountry.name, addressCity.trim())
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(appString(Res.string.saving_your_photo), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RequirementRow(done: Boolean, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = if (done) appString(Res.string.done) else appString(Res.string.to_do),
            tint = if (done) MaterialTheme.proColors.success else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
