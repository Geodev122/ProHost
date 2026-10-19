package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.findCountryByName
import com.example.data.model.formatToE164
import com.example.data.model.isPlausibleE164
import com.example.data.model.splitE164
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Full-screen gate shown when someone without a WhatsApp number opens a booking or listing
 * workspace. Saving the number is the whole step (no SMS code); [onKycComplete] returns the
 * person to the feature they were opening.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KycScreen(
    currentPhone: String,
    onSave: suspend (e164: String) -> String?,
    onKycComplete: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WhatsApp number", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onDismiss != null) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WhatsAppNumberSection(currentPhone = currentPhone, onSave = onSave, onSaved = onKycComplete)
        }
    }
}

/**
 * The one phone step: the person's WhatsApp number, saved to the profile as typed (no SMS
 * code — Firebase phone verification never delivered reliably). Used by [KycScreen] and the
 * in-place RequirementsSheet. [onSave] stores the E.164 number and returns an error message,
 * or null on success.
 */
@Composable
fun WhatsAppNumberSection(
    currentPhone: String,
    onSave: suspend (e164: String) -> String?,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // Always starts from the number already on the profile (registration form, or an earlier save).
    val initial = remember(currentPhone) { splitE164(currentPhone, findCountryByName("Lebanon")) }
    var phoneCountry by rememberSaveable(
        currentPhone,
        stateSaver = Saver(save = { it.name }, restore = { findCountryByName(it) })
    ) { mutableStateOf(initial.first) }
    var phoneNumber by rememberSaveable(currentPhone) { mutableStateOf(initial.second) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val e164Phone = formatToE164(phoneCountry, phoneNumber)

    ModernCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(20.dp),
        elevation = 2.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Your WhatsApp number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Hosts and guests reach you on WhatsApp. It's never shown publicly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        PhoneNumberField(
            country = phoneCountry,
            onCountryChange = { phoneCountry = it; errorMessage = null },
            number = phoneNumber,
            onNumberChange = { phoneNumber = it; errorMessage = null },
            modifier = Modifier.fillMaxWidth()
        )
        // The same number as the profile (prefilled above); with none yet, Android's number picker can fill it.
        UseDeviceNumberButton(
            fallbackCountry = phoneCountry,
            onPicked = { country, local -> phoneCountry = country; phoneNumber = local; errorMessage = null }
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(Spacing.sm))
            ProHostAlertBanner(message = it, severity = ProHostAlertSeverity.ERROR)
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        ProPrimaryButton(
            text = if (saving) "Saving…" else "Save number",
            onClick = {
                if (phoneNumber.filter { it.isDigit() }.length < 6 || !isPlausibleE164(e164Phone)) {
                    errorMessage = "Please enter a valid number with its country code."
                    return@ProPrimaryButton
                }
                saving = true
                scope.launch {
                    val error = onSave(e164Phone)
                    saving = false
                    if (error == null) onSaved() else errorMessage = error
                }
            },
            enabled = !saving,
            isLoading = saving,
            icon = Icons.Default.Check,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
