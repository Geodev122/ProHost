package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SchemaItem
import com.example.ui.theme.Spacing
import java.util.Locale

/**
 * Shared per-category PAYG quantity cart — one row per admin-defined Space Category
 * (a quantity stepper starting at 0), a running line-item list, and a total. Reused
 * by the Renew popup (SubscriptionRenewalDialog, for a PAYG owner) and the
 * Subscriptions screen's "Buy PAYG" flow — replacing two separate single-category
 * pickers with one real cart, per the user's own spec: "the list of admin created
 * Types with Unit price, a quantity to activate... the bottom cart updates with
 * element and quantity unit price and total price."
 *
 * State (the [quantities] map) is hoisted to the caller, matching this codebase's
 * usual pattern for anything that needs to survive/commit across a dialog's buttons.
 */
@Composable
fun PaygCartSection(
    categories: List<SchemaItem>,
    quantities: Map<String, Int>,
    onQuantityChange: (categoryId: String, newQuantity: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        categories.forEach { category ->
            val priceUsd = category.priceUsd
            val quantity = quantities[category.id] ?: 0
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (priceUsd != null) {
                            "$${String.format(Locale.US, "%.2f", priceUsd)} / listing"
                        } else {
                            "Not priced yet"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { if (quantity > 0) onQuantityChange(category.id, quantity - 1) },
                        enabled = quantity > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease quantity", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "$quantity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 20.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(
                        onClick = { if (priceUsd != null) onQuantityChange(category.id, quantity + 1) },
                        enabled = priceUsd != null,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase quantity", modifier = Modifier.size(18.dp))
                    }
                }
            }
            HorizontalDivider()
        }

        val cartLines = categories.filter { (quantities[it.id] ?: 0) > 0 }
        if (cartLines.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    cartLines.forEach { category ->
                        val qty = quantities[category.id] ?: 0
                        val price = category.priceUsd ?: 0.0
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${category.name} x$qty",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "$${String.format(Locale.US, "%.2f", price * qty)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "$${String.format(Locale.US, "%.2f", paygCartTotal(categories, quantities))}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** Real total for the current cart state — sum of each priced category's qty x price. */
fun paygCartTotal(categories: List<SchemaItem>, quantities: Map<String, Int>): Double =
    categories.sumOf { (quantities[it.id] ?: 0) * (it.priceUsd ?: 0.0) }

/** Non-zero (categoryId, quantity) pairs — the shape payPaygCartViaWhish expects. */
fun paygCartItems(quantities: Map<String, Int>): List<Pair<String, Int>> =
    quantities.filter { it.value > 0 }.map { it.key to it.value }
