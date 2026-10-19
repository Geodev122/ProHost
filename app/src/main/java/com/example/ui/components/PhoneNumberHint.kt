package com.example.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity

/**
 * Android's Phone Number Hint: Google Play services shows the system sheet listing this device's SIM
 * numbers, and the person picks one (or dismisses it). It needs no permission and shares a number only
 * when the person chooses it — the consent-based way to prefill the WhatsApp number for email and
 * Google sign-ups (a Google account itself never exposes a phone number).
 *
 * Returns a launcher; [onNumber] receives an E.164 number. [onUnavailable] runs when the device has
 * no SIM number to offer or Play services can't show the sheet, so callers fall back to typing.
 */
@Composable
fun rememberPhoneNumberHint(onNumber: (String) -> Unit, onUnavailable: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val activity: Activity? = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }
    val latestOnNumber = rememberUpdatedState(onNumber)
    val latestOnUnavailable = rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || activity == null) return@rememberLauncherForActivityResult
        runCatching { Identity.getSignInClient(activity).getPhoneNumberFromIntent(result.data) }
            .onSuccess { number -> if (number.isNotBlank()) latestOnNumber.value(number) }
            .onFailure { Log.w("PhoneNumberHint", "No number returned: ${it.message}") }
    }
    return {
        if (activity == null) {
            latestOnUnavailable.value()
        } else {
            Identity.getSignInClient(activity)
                .getPhoneNumberHintIntent(GetPhoneNumberHintIntentRequest.builder().build())
                .addOnSuccessListener { pending ->
                    runCatching { launcher.launch(IntentSenderRequest.Builder(pending).build()) }
                        .onFailure { latestOnUnavailable.value() }
                }
                .addOnFailureListener {
                    Log.w("PhoneNumberHint", "Phone Number Hint unavailable: ${it.message}")
                    latestOnUnavailable.value()
                }
        }
    }
}

/** "Use this phone's number" under a [PhoneNumberField]: fills country and number from the system picker. */
@Composable
fun UseDeviceNumberButton(
    fallbackCountry: com.example.data.model.Country,
    onPicked: (country: com.example.data.model.Country, localNumber: String) -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val pick = rememberPhoneNumberHint(
        onNumber = { e164 ->
            com.example.analytics.AnalyticsTracker.phoneHint("used", "whatsapp_step")
            val (country, local) = com.example.data.model.splitE164(e164, fallbackCountry)
            onPicked(country, local)
        },
        onUnavailable = { com.example.analytics.AnalyticsTracker.phoneHint("unavailable", "whatsapp_step") }
    )
    androidx.compose.material3.TextButton(onClick = pick, modifier = modifier) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Default.SimCard,
            contentDescription = null,
            modifier = androidx.compose.ui.Modifier.size(16.dp)
        )
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(6.dp))
        androidx.compose.material3.Text("Use this phone's number", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}
