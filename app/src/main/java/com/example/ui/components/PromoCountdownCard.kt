package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.billing.PromoCampaign
import com.example.ui.theme.proColors

/**
 * "Early launch promo" card on the Premium screen: the code, what it gives, a live countdown to the
 * campaign end, spots left (server-counted redemptions) and one button that applies it at checkout.
 * Brand navy with a single orange accent (the button), so the screen doesn't turn all orange.
 */
@Composable
fun PromoCountdownCard(
    promo: PromoCampaign,
    nowMillis: Long,
    enabled: Boolean,
    onCopy: () -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val on = MaterialTheme.proColors.onBrandHeader
    val left = PromoCampaign.countdown(nowMillis, promo.endsAtMillis)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(MaterialTheme.proColors.brandHeaderStart, MaterialTheme.proColors.brandHeaderEnd)))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.proColors.headerWarning, modifier = Modifier.size(18.dp))
                Text(
                    promo.title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = on.copy(alpha = 0.9f),
                    modifier = Modifier.weight(1f)
                )
                promo.spotsLeft?.let {
                    Text("$it spots left", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.proColors.headerWarning)
                }
            }
            Text(promo.benefit, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = on)

            // The code, copyable.
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = on.copy(alpha = 0.1f),
                border = BorderStroke(1.5.dp, on.copy(alpha = 0.45f))
            ) {
                Row(modifier = Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Redeem, contentDescription = null, tint = on, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        promo.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = on,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy code", tint = on, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Live countdown.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Offer ends in", style = MaterialTheme.typography.labelSmall, color = on.copy(alpha = 0.8f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountdownBox(left.days, "DAYS", Modifier.weight(1f))
                    CountdownBox(left.hours, "HRS", Modifier.weight(1f))
                    CountdownBox(left.minutes, "MIN", Modifier.weight(1f))
                    CountdownBox(left.seconds, "SEC", Modifier.weight(1f))
                }
            }
            promo.maxRedemptions?.let { max ->
                LinearProgressIndicator(
                    progress = { (promo.redeemed.toFloat() / max).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.proColors.headerWarning,
                    trackColor = on.copy(alpha = 0.18f)
                )
            }

            Button(
                onClick = onUse,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Default.Redeem, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (enabled) "Use promo code" else "For new subscribers", fontWeight = FontWeight.Bold)
            }
            Text(
                "Applied in Google Play's checkout. A payment method is needed; after the free month it renews at the " +
                    "regular price unless you cancel in Google Play before then.",
                style = MaterialTheme.typography.labelSmall,
                color = on.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CountdownBox(value: Long, label: String, modifier: Modifier = Modifier) {
    val on = MaterialTheme.proColors.onBrandHeader
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = on.copy(alpha = 0.12f)) {
        Column(modifier = Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString().padStart(2, '0'), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = on)
            Text(label, style = MaterialTheme.typography.labelSmall, color = on.copy(alpha = 0.7f), letterSpacing = 1.sp)
        }
    }
}

/** A small red dot over a control, e.g. "Redeem code" while a promo campaign is live. */
@Composable
fun AttentionDot(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(9.dp).background(MaterialTheme.colorScheme.error, CircleShape))
}
