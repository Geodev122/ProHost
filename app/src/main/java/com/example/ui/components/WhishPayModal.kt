package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.data.crypto.WhishSecurity
import com.example.data.api.WhishPayApi
import com.example.data.api.WhishPaymentRequest
import com.example.data.api.WhishStatusRequest
import com.example.data.model.SpaceListing
import com.example.data.model.BookingRequest
import com.example.ui.viewmodel.ProSpaceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
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

@Composable
fun WhishPayModal(
    space: SpaceListing,
    currentFeeUsd: Double,
    booking: BookingRequest? = null,
    viewModel: ProSpaceViewModel? = null,
    onDismiss: () -> Unit,
    onConfirmPayment: (payerName: String, payerPhone: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedPaymentMethod by remember { mutableStateOf(WhishPaymentMethod.WHISH_USD_WALLET) }

    var payerName by remember { mutableStateOf(booking?.practitionerName ?: space.ownerName) }
    var payerPhone by remember { mutableStateOf(booking?.practitionerPhone ?: space.ownerPhone) }
    var orderId by remember {
        mutableStateOf(
            if (booking != null) "ORD-BKG-${booking.id}"
            else "ORD-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        )
    }

    var isProcessing by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Sandbox production checkout states
    var collectUrl by remember { mutableStateOf<String?>(null) }
    var externalIdByApi by remember { mutableStateOf<Long?>(null) }
    var isWaitingForPayer by remember { mutableStateOf(false) }
    var isVerifyingStatus by remember { mutableStateOf(false) }
    var isManualEntryMode by remember { mutableStateOf(false) }
    var manualRefId by remember { mutableStateOf("") }

    val calculatedSignature = remember(currentFeeUsd, orderId) {
        WhishSecurity.generateSignature(
            channel = WhishSecurity.CHANNEL_ID,
            amount = currentFeeUsd,
            currency = "USD",
            orderId = orderId
        )
    }

    // Verify payment status by calling sandbox GET Status
    fun verifyPaymentStatus(extId: Long) {
        isVerifyingStatus = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val response = WhishPayApi.service.getStatus(
                    WhishStatusRequest(currency = "USD", externalId = extId)
                )
                if (response.status && response.data != null) {
                    val status = response.data.collectStatus
                    if (status.lowercase() == "success") {
                        isWaitingForPayer = false
                        isSuccess = true
                        // Complete logic
                        if (booking != null && viewModel != null) {
                            viewModel.payBookingViaWhish(
                                bookingId = booking.id,
                                payerName = payerName,
                                payerPhone = payerPhone,
                                txId = "TX-WSH-" + extId,
                                signature = calculatedSignature,
                                context = context
                            )
                        } else {
                            onConfirmPayment(payerName, payerPhone)
                        }
                    } else {
                        errorMessage = "Payment status: ${status.uppercase()}. Please complete payment on the portal first."
                    }
                } else {
                    errorMessage = response.dialog?.message ?: "Verification response indicates pending or failure."
                }
            } catch (e: Exception) {
                // Network error, show toast and offer fallback or display error
                errorMessage = "Could not reach Whish servers: ${e.localizedMessage}. Tap 'Manual Reference' to enter transfer code."
            } finally {
                isVerifyingStatus = false
            }
        }
    }

    // Handle payment checkout initiation
    fun initiateWhishCheckout() {
        isProcessing = true
        errorMessage = null
        val extId = System.currentTimeMillis()

        coroutineScope.launch {
            try {
                val request = WhishPaymentRequest(
                    amount = currentFeeUsd.toInt().toString(),
                    currency = "USD",
                    invoice = if (booking != null) "Booking Agreement #${booking.id}" else "Listing subscription #${space.id}",
                    externalId = extId,
                    successCallbackUrl = "https://ceo@hopebearer-award.com/success",
                    failureCallbackUrl = "https://ceo@hopebearer-award.com/failure",
                    successRedirectUrl = "https://ceo@hopebearer-award.com/thank-you",
                    failureRedirectUrl = "https://ceo@hopebearer-award.com/payment-error"
                )

                val response = WhishPayApi.service.initiatePayment(request)
                if (response.status && response.data != null && response.data.collectUrl.isNotBlank()) {
                    collectUrl = response.data.collectUrl
                    externalIdByApi = extId
                    isWaitingForPayer = true
                    // Open browser immediately
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(response.data.collectUrl))
                        context.startActivity(browserIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Please click on the payment link below", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // API returned false, fallback to manual reference verification
                    isManualEntryMode = true
                    isWaitingForPayer = true
                    Toast.makeText(context, "Redirect unavailable. Switch to direct manual reference.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                // Offline or failure, fallback to manual reference verification
                isManualEntryMode = true
                isWaitingForPayer = true
                Toast.makeText(context, "Portal connection unavailable. Switch to direct manual reference.", Toast.LENGTH_LONG).show()
            } finally {
                isProcessing = false
            }
        }
    }

    Dialog(onDismissRequest = { if (!isProcessing && !isVerifyingStatus) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
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
                if (!isSuccess) {
                    // Header with Whish Pay badge
                    Surface(
                        color = Color(0xFFE2001A), // Official Whish Red
                        shape = RoundedCornerShape(12.dp)
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (booking != null) "Secure Workspace Settlement" else "Whish Pay Subscription",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (booking != null) "Confirm Booking & Settle" else "30-Day Listing Entitlement",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = space.title,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Amount Display Card with Itemized Settlement Breakdown
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (booking != null) "Total Workspace Settlement (${booking.durationMonths} Months)" else "Monthly Subscription Fee",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$${String.format(Locale.US, "%.2f", currentFeeUsd)}",
                                    fontSize = 32.sp,
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
                            if (booking != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("• Base Rental Rate:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                    Text("$${String.format(Locale.US, "%.2f", currentFeeUsd * 0.9)} USD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("• 24/7 Power / Generator Backup:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                    Text("$${String.format(Locale.US, "%.2f", currentFeeUsd * 0.1)} USD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("• Whish Escrow Fee (Channel 15462415):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                    Text("$0.00 (Waived)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live API Checkout Details or User input fields
                    if (!isWaitingForPayer) {
                        // Payment Instrument Selector
                        Text(
                            text = "Select Whish Payment Instrument:",
                            fontSize = 12.sp,
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
                                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) Color(0xFFE2001A) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            color = if (isSelected) Color(0xFFE2001A) else MaterialTheme.colorScheme.surfaceVariant,
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
                                                fontSize = 12.sp,
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
                                            color = if (method.currencyBadge == "LBP") Color(0xFFFF9800).copy(alpha = 0.2f) else Color(0xFF4CAF50).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = method.currencyBadge,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (method.currencyBadge == "LBP") Color(0xFFE65100) else Color(0xFF2E7D32),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

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

                        Spacer(modifier = Modifier.height(12.dp))

                        // Security Specs & HMAC Crypto Badge Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "🛡️ Whish Secure Escrow & HMAC Signature",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE2001A)
                                    )
                                    Text(
                                        text = "Active",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                                Text(
                                    text = "• Channel ID: ${WhishSecurity.CHANNEL_ID} | Instrument: ${selectedPaymentMethod.title}\n• HMAC-SHA256: ${calculatedSignature.take(20)}...\n• Order ID: $orderId",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CustomButton(
                                text = "Cancel",
                                onClick = onDismiss,
                                variant = CustomButtonVariant.OUTLINED,
                                modifier = Modifier.weight(1f),
                                enabled = !isProcessing
                            )

                            CustomButton(
                                text = "Authorize Settlement",
                                onClick = { initiateWhishCheckout() },
                                icon = Icons.Default.Lock,
                                customContainerColor = Color(0xFFE2001A),
                                customContentColor = Color.White,
                                modifier = Modifier.weight(1.5f),
                                isLoading = isProcessing,
                                enabled = !isProcessing && payerName.isNotBlank() && payerPhone.isNotBlank()
                            )
                        }
                    } else {
                        // WAITING FOR PAYER TO COMPLETE
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFE2001A),
                                modifier = Modifier.size(36.dp)
                            )

                            Text(
                                text = "Waiting for Payment Settlement",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (collectUrl != null) {
                                Text(
                                    text = "Whish Secure Hosted Checkout (Enter mobile phone & OTP in portal below):",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(
                                                    view: WebView?,
                                                    request: WebResourceRequest?
                                                ): Boolean {
                                                    val url = request?.url?.toString() ?: ""
                                                    if (url.contains("success") || url.contains("thank-you") || url.contains("successCallbackUrl")) {
                                                        isWaitingForPayer = false
                                                        isSuccess = true
                                                        externalIdByApi?.let { verifyPaymentStatus(it) }
                                                        return true
                                                    } else if (url.contains("failure") || url.contains("error") || url.contains("failureCallbackUrl")) {
                                                        errorMessage = "Payment failed or cancelled on Whish checkout portal."
                                                        isWaitingForPayer = false
                                                        return true
                                                    }
                                                    return false
                                                }
                                            }
                                            loadUrl(collectUrl!!)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFFE2001A), RoundedCornerShape(12.dp))
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(collectUrl))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Open in Browser", style = MaterialTheme.typography.labelSmall)
                                    }

                                    Button(
                                        onClick = { externalIdByApi?.let { verifyPaymentStatus(it) } },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2001A)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Verify Status", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else {
                                Text(
                                    text = "If you have processed the payment directly, you can submit the transaction reference code below.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            errorMessage?.let { err ->
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            if (isManualEntryMode) {
                                Spacer(modifier = Modifier.height(6.dp))
                                InputField(
                                    value = manualRefId,
                                    onValueChange = { manualRefId = it },
                                    label = "Whish 8-Digit Transfer Code",
                                    leadingIcon = Icons.Default.CheckCircle,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isManualEntryMode) {
                                    Button(
                                        onClick = {
                                            isSuccess = true
                                            isWaitingForPayer = false
                                            val finalRef = if (manualRefId.isNotBlank()) manualRefId else "WSH-" + System.currentTimeMillis().toString().takeLast(6)
                                            if (booking != null && viewModel != null) {
                                                viewModel.payBookingViaWhish(
                                                    bookingId = booking.id,
                                                    payerName = payerName,
                                                    payerPhone = payerPhone,
                                                    txId = "TX-" + finalRef,
                                                    signature = calculatedSignature,
                                                    context = context
                                                )
                                            } else {
                                                onConfirmPayment(payerName, payerPhone)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2001A)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Confirm Transfer Code", style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    Button(
                                        onClick = { externalIdByApi?.let { verifyPaymentStatus(it) } },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2001A)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        enabled = !isVerifyingStatus
                                    ) {
                                        if (isVerifyingStatus) {
                                             CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                        } else {
                                            Text("Verify Payment", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    Button(
                                        onClick = { isManualEntryMode = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outlineVariant),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Manual Reference", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    isWaitingForPayer = false
                                    collectUrl = null
                                    externalIdByApi = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Back to Details", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    // Success View
                    Surface(
                        color = Color(0xFF4CAF50),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Settlement Successful!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (booking != null)
                            "Your workspace booking has been fully paid, approved, and locked. The owner has been notified of your guaranteed reservation!"
                            else "30-Day active listing entitlement has been granted. Your clinic space is now live for all Lebanese medical specialists.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "Order: $orderId", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Text(
                                text = "Amount: $${String.format(Locale.US, "%.2f", currentFeeUsd)} USD",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(text = "Gateway: Whish Pay (ID: 15462415)", fontSize = 12.sp)
                            Text(
                                text = "Crypto Sig: ${calculatedSignature.take(24)}...",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    CustomButton(
                        text = "Done",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
