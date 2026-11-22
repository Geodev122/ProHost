package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppUser
import com.example.data.model.UserRole
import com.example.data.model.COUNTRIES
import com.example.ui.components.CountryDropdownField
import com.example.ui.components.InputField
import com.example.ui.components.PhoneNumberField
import com.example.ui.components.ProHostFullScreenTopAppBar
import com.example.ui.components.ProPrimaryButton
import com.example.ui.components.ProSurfaceCard
import com.example.ui.components.ProfilePicturePickerField
import com.example.ui.components.TrackScreen
import com.example.data.model.findCountryByName
import com.example.data.model.formatToE164
import com.example.ui.theme.Spacing
import com.example.ui.theme.proHostScreenBackground
import com.example.ui.util.UiMessages
import com.example.ui.util.sharedText
import com.example.ui.viewmodel.ProHostViewModel
import kotlinx.coroutines.launch

/**
 * Profile › More › Profile details (owner, 22 Nov 2026): the profile form as its own page, with a back arrow, that closes
 * after a successful save. Same fields and save path as the card it replaces on the Profile page.
 */
@Composable
fun ProfileDetailsPage(viewModel: ProHostViewModel, user: AppUser, onClose: () -> Unit) {
    TrackScreen("profile_details")
    val coroutineScope = rememberCoroutineScope()
    var name by remember(user) { mutableStateOf(user.fullName) }
    var specialty by remember(user) { mutableStateOf(user.specialty) }
    var phone by remember(user) { mutableStateOf(user.phone) }
    // Country-code picker + local digits: the saved number's longest matching dial code, Lebanon when none matches.
    var phoneCountry by remember(user) {
        mutableStateOf(
            COUNTRIES.filter { user.phone.trim().startsWith(it.dialCode) }.maxByOrNull { it.dialCode.length }
                ?: COUNTRIES.first { it.isoCode == "LB" }
        )
    }
    var phoneLocal by remember(user) { mutableStateOf(user.phone.trim().removePrefix(phoneCountry.dialCode).trim()) }
    var selectedCountry by remember(user) { mutableStateOf(findCountryByName(user.country)) }
    var governorateArea by remember(user) { mutableStateOf(user.governorate) }
    var city by remember(user) { mutableStateOf(user.city) }
    var isSaving by remember { mutableStateOf(false) }
    var pictureError by remember { mutableStateOf<String?>(null) }
    var pendingPicture by remember { mutableStateOf<com.example.util.PickedFile?>(null) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().testTag("profile_details_page"), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().proHostScreenBackground()) {
                ProHostFullScreenTopAppBar(
                    title = sharedText("profile_details"),
                    onMenuClick = onClose,
                    isBackNavigation = true
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ProSurfaceCard(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                            Text(
                                sharedText("profile_details_subtitle"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                ProfilePicturePickerField(
                                    pictureFile = pendingPicture,
                                    existingUrl = user.profilePictureUrl,
                                    onPictureSelected = { pendingPicture = it }
                                )
                                Column {
                                    Text(sharedText("profile_picture_2"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (user.profilePictureUrl != null || pendingPicture != null) sharedText("tap_to_replace") else sharedText("tap_to_add_a_photo"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            pictureError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                            InputField(
                                value = name, onValueChange = { name = it }, label = sharedText("full_name"),
                                leadingIcon = Icons.Default.Person, modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            InputField(
                                value = specialty,
                                onValueChange = { specialty = it },
                                label = when (user.role) {
                                    UserRole.SPECIALIST -> sharedText("profession_job_title_optional")
                                    UserRole.PRO_HOST -> sharedText("host_category_commercial_real_estate_cowor")
                                    UserRole.ADMIN -> sharedText("super_administrator_role_clearance")
                                },
                                leadingIcon = Icons.Default.Work, modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            PhoneNumberField(
                                country = phoneCountry,
                                onCountryChange = { phoneCountry = it; phone = formatToE164(it, phoneLocal) },
                                number = phoneLocal,
                                onNumberChange = { phoneLocal = it; phone = formatToE164(phoneCountry, it) },
                                label = sharedText("whatsapp_contact_number"),
                                modifier = Modifier.fillMaxWidth()
                            )
                            CountryDropdownField(selectedCountry = selectedCountry, onCountrySelected = { selectedCountry = it }, modifier = Modifier.fillMaxWidth())
                            InputField(
                                value = governorateArea, onValueChange = { governorateArea = it }, label = sharedText("governorate_area"),
                                leadingIcon = Icons.Default.LocationOn, modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            InputField(
                                value = city, onValueChange = { city = it }, label = sharedText("city"),
                                leadingIcon = Icons.Default.LocationCity, modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            ProPrimaryButton(
                                text = if (isSaving) sharedText("saving_2") else sharedText("save_profile_changes"),
                                enabled = !isSaving,
                                isLoading = isSaving,
                                icon = Icons.Default.Save,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    isSaving = true
                                    pictureError = null
                                    coroutineScope.launch {
                                        try {
                                            var pictureUrl: String? = null
                                            var pictureFailed = false
                                            pendingPicture?.let { picked ->
                                                pictureUrl = com.example.data.repository.RepositoryDefaults.storage().uploadProfilePicture(user.id, picked)
                                                pictureFailed = pictureUrl == null
                                            }
                                            val success = viewModel.updateProfile(
                                                name, specialty, phone, selectedCountry.name, governorateArea, city, pictureUrl
                                            )
                                            isSaving = false
                                            when {
                                                !success -> UiMessages.show(sharedText("failed_to_update_profile_please_try_again"))
                                                // The rest saved; only the photo didn't, so stay on the page and say so.
                                                pictureFailed -> {
                                                    pictureError = sharedText("profile_saved_but_the_new_photo_couldn_t_b")
                                                    UiMessages.show(sharedText("profile_saved_photo_upload_failed_please_r"), long = true)
                                                }
                                                else -> {
                                                    UiMessages.show(sharedText("profile_updated_successfully"))
                                                    onClose()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            isSaving = false
                                            UiMessages.show(sharedText("an_error_occurred_please_try_again"))
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
