package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.SpaceListing
import com.example.ui.theme.StatusOnSuccessContainer
import com.example.ui.theme.StatusOnWarningContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.WhishBrandRed
import com.example.ui.viewmodel.ProHostViewModel
import com.example.ui.theme.Spacing
import java.util.Locale

enum class WhishPaymentMethod(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val currencyBadge: String
) {
    WHISH_USD_WALLET("Whish USD Digital Wallet", "Instant settlement from USD balance", Icons.Default.AccountBalanceWallet, "USD"),
    WHISH_LBP_ACCOUNT("Whish LBP Account", "Converted at 89,500 LBP reference rate", Icons.Default.CurrencyExchange, "LBP"),
    WHISH_CREDIT_CARD("Vaulted Visa / MasterCard", "Secure PCI-DSS encrypted card token", Icons.Default.CreditCard, "USD"),
    WHISH_OTC_AGENT("Whish OTC Cash Agent", "Cash payment at any Lebanese exchange branch", Icons.Default.Store, "CASH")
}

/**
 * Collects payer details and hands off to ProHostViewModel.paySubscriptionViaWhish,
 * which calls the initiateWhishPayment Cloud Function, opens the real Whish checkout
 * in the browser, and polls for server-confirmed settlement. This is the Pro Host
 * package/PAYG subscription flow only now — booking rent settlement (Specialist <->
 * Pro Host) happens entirely outside the app; a host records the deal by uploading
 * the signed leasing agreement when accepting a request instead (see
 * ProHostRepository.acceptBookingRequest).
 *
 * This used to also own the whole checkout lifecycle itself: it called Whish's API
 * directly with a client-side HMAC signature computed from a secret shipped in the
 * APK, pointed the success/failure callback URLs at a malformed address
 * ("https://ceo@hopebearer-award.com/success" — not a real endpoint, so those
 * callbacks could never have reached anything), and had a "Manual Reference" mode
 * that let the payer type any string and instantly self-report success with zero
 * verification. None of that exists here anymore — the dialog's only job now is to
 * collect who's paying and kick off the real, server-verified flow.
 */
@Composable
fun WhishPayModal(
    space: SpaceListing,
    currentFeeUsd: Double,
    viewModel: ProHostViewModel? = null,
    onDismiss: () -> Unit,
    onConfirmPayment: (payerName: String, payerPhone: String) -> Unit
) {
    val context = LocalContext.current

    var selectedPaymentMethod by remember { mutableStateOf(WhishPaymentMethod.WHISH_USD_WALLET) }
    var payerName by remember { mutableStateOf(space.ownerName) }
    var payerPhone by remember { mutableStateOf(space.ownerPhone) }
    var isLaunching by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isLaunching) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = WhishBrandRed, // Official Whish Red
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = "Whish Pay Subscription",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                Text(
                    text = "30-Day Listing Entitlement",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = space.title,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Monthly Subscription Fee",
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", currentFeeUsd)}",
                                fontSize = MaterialTheme.typography.displayLarge.fontSize,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = " USD",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 6.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = "The exact amount charged is confirmed by the server — this is an estimate for display.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Select Whish Payment Instrument:",
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WhishPaymentMethod.values().forEach { method ->
                        val isSelected = selectedPaymentMethod == method
                        Surface(
                            onClick = { selectedPaymentMethod = method },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) WhishBrandRed else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = if (isSelected) WhishBrandRed else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            method.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = method.title,
                                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = method.subtitle,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = if (method.currencyBadge == "LBP") StatusWarning.copy(alpha = 0.2f) else StatusSuccess.copy(alpha = 0.2f),
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = method.currencyBadge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (method.currencyBadge == "LBP") StatusOnWarningContainer else StatusOnSuccessContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                InputField(
                    value = payerName,
                    onValueChange = { payerName = it },
                    label = "Payer Full Name",
                    leadingIcon = Icons.Default.Person,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                InputField(
                    value = payerPhone,
                    onValueChange = { payerPhone = it },
                    label = if (selectedPaymentMethod == WhishPaymentMethod.WHISH_LBP_ACCOUNT) "Whish LBP Mobile (89,500 Rate)" else "Whish Registered Mobile",
                    leadingIcon = Icons.Default.Phone,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = WhishBrandRed, modifier = Modifier.size(18.dp))
                        Text(
                            text = "You'll complete payment on Whish's secure checkout page. We only confirm settlement after Whish itself verifies it — this app never signs or self-reports payments.",
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProOutlinedButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isLaunching
                    )

                    ProPrimaryButton(
                        text = "Authorize Settlement",
                        onClick = {
                            isLaunching = true
                            viewModel?.paySubscriptionViaWhish(space.id, payerName, payerPhone, context)
                            onConfirmPayment(payerName, payerPhone)
                            onDismiss()
                        },
                        icon = Icons.Default.Lock,
                        containerColor = WhishBrandRed,
                        contentColor = Color.White,
                        modifier = Modifier.weight(1.5f),
                        isLoading = isLaunching,
                        enabled = !isLaunching && payerName.isNotBlank() && payerPhone.isNotBlank() && viewModel != null
                    )
                }
            }
        }
    }
}
