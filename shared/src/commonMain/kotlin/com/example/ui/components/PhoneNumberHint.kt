package com.example.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.shared.resources.Res
import com.example.shared.resources.use_this_phone_s_number
import com.example.ui.util.appString

/**
 * The platform's phone-number picker. Android: Phone Number Hint (Google Play services lists this device's SIM numbers, no
 * permission, shared only when the person picks one). iOS and the web have no such picker ([phoneNumberHintAvailable] false), so
 * the "Use this phone's number" button is not shown there and the person types the number.
 *
 * Returns a launcher; [onNumber] receives an E.164 number. [onUnavailable] runs when there is no number to offer, so callers fall
 * back to typing.
 */
@Composable
expect fun rememberPhoneNumberHint(onNumber: (String) -> Unit, onUnavailable: () -> Unit = {}): () -> Unit

/** True where [rememberPhoneNumberHint] can show a picker (Android). */
expect val phoneNumberHintAvailable: Boolean

/** "Use this phone's number" under a [PhoneNumberField]: fills country and number from the system picker. */
@Composable
fun UseDeviceNumberButton(
    fallbackCountry: com.example.data.model.Country,
    onPicked: (country: com.example.data.model.Country, localNumber: String) -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    if (!phoneNumberHintAvailable) return
    val pick = rememberPhoneNumberHint(
        onNumber = { e164 ->
            com.example.analytics.AnalyticsTracker.phoneHint("used", "whatsapp_step")
            val (country, local) = com.example.data.model.splitE164(e164, fallbackCountry)
            onPicked(country, local)
        },
        onUnavailable = {
            com.example.analytics.AnalyticsTracker.phoneHint("unavailable", "whatsapp_step")
            com.example.ui.util.UiMessages.show(com.example.ui.util.sharedText("phone_hint_unavailable"))
        }
    )
    androidx.compose.material3.TextButton(onClick = pick, modifier = modifier) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Default.SimCard,
            contentDescription = null,
            modifier = androidx.compose.ui.Modifier.size(16.dp)
        )
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(6.dp))
        androidx.compose.material3.Text(appString(Res.string.use_this_phone_s_number), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}
