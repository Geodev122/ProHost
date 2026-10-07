package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BookingRequest
import com.example.data.model.BookingRequestStatus
import com.example.data.model.PriceChangeMode
import com.example.data.model.SpaceListing
import com.example.data.model.UserRole
import com.example.data.model.publicCode
import com.example.ui.theme.Spacing
import com.example.ui.util.PriceChange
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.viewmodel.ProHostViewModel
import java.util.Locale

private enum class PriceStep { LISTING, DIVISION, SLOTS, EDIT, IMPACT }

/** "$1,250" / "$12.50" — no trailing ".00" on whole amounts. */
fun formatUsd(v: Double): String =
    if (v % 1.0 == 0.0) String.format(Locale.US, "$%,.0f", v) else String.format(Locale.US, "$%,.2f", v)

/**
 * Pro Host "Change price" (Manage page, request detail, Renting Progress). Steps: listing → division →
 * slots with their current prices → new price → for every pending request / accepted tenant on that
 * slot, Keep old price · Update now · Next term. The server (changeSlotPrice) applies it and pushes
 * each specialist. Opened from a booking ([booking] set) it starts at the slots step, listing and
 * division taken from the booking and only that booking's slots listed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePriceSheet(
    viewModel: ProHostViewModel,
    onDismiss: () -> Unit,
    initialSpaceId: String? = null,
    booking: BookingRequest? = null
) {
    val context = LocalContext.current
    val allSpaces by viewModel.spaces.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val allBookings by viewModel.bookingRequests.collectAsState()
    val ownSpaces = remember(allSpaces, currentUser) {
        val uid = currentUser?.id
        allSpaces.filter { it.ownerId == uid || (currentUser?.role == UserRole.ADMIN && it.id == booking?.spaceId) }
            .sortedBy { it.title.lowercase() }
    }
    val fromBooking = booking != null

    var spaceId by remember { mutableStateOf(booking?.spaceId ?: initialSpaceId) }
    val space: SpaceListing? = ownSpaces.firstOrNull { it.id == spaceId }
    var scopeId by remember {
        mutableStateOf(booking?.let { b -> b.subdivisionId?.takeIf { it.isNotBlank() } ?: b.spaceId })
    }
    var step by remember { mutableStateOf(if (fromBooking) PriceStep.SLOTS else PriceStep.LISTING) }
    var slot by remember { mutableStateOf<PriceChange.PriceSlot?>(null) }
    var priceText by remember { mutableStateOf("") }
    val decisions = remember { mutableStateMapOf<String, PriceChangeMode>() }
    var saving by remember { mutableStateOf(false) }

    val divisions = remember(space) { space?.let { PriceChange.divisionsOf(it) } ?: emptyList() }
    val division = divisions.firstOrNull { it.scopeId == scopeId }
    val slots = remember(space, division, booking) {
        if (space == null || division == null) emptyList()
        else if (booking != null) PriceChange.slotsForBooking(space, division, booking).ifEmpty { PriceChange.slotsOf(division, space) }
        else PriceChange.slotsOf(division, space)
    }
    val newPrice = priceText.replace(",", ".").toDoubleOrNull()
    val today = remember { PriceChange.todayIso() }
    val affected = remember(space, slot, allBookings) {
        val s = space
        val sl = slot
        if (s == null || sl == null) emptyList() else {
            val pricing = PriceChange.pricingFor(s, sl.ref.scopeId)
            allBookings
                .filter {
                    (it.status == BookingRequestStatus.PENDING || it.status == BookingRequestStatus.ACCEPTED) &&
                        !it.rejectedBySystem && !SpaceCalculationUtils.hasEnded(it, today) &&
                        PriceChange.bookingUses(it, s.id, pricing, sl.ref)
                }
                .sortedBy { if (it.id == booking?.id) 0 else 1 }
        }
    }

    fun submit() {
        val s = space ?: return
        val sl = slot ?: return
        val p = newPrice ?: return
        saving = true
        viewModel.changeSlotPrice(context, s, sl, p, affected.associate { it.id to (decisions[it.id] ?: PriceChangeMode.KEEP) }) { ok ->
            saving = false
            if (ok) onDismiss()
        }
    }

    fun back() {
        step = when (step) {
            PriceStep.LISTING -> { onDismiss(); PriceStep.LISTING }
            PriceStep.DIVISION -> PriceStep.LISTING
            PriceStep.SLOTS -> if (fromBooking) { onDismiss(); PriceStep.SLOTS } else if (divisions.size > 1) PriceStep.DIVISION else PriceStep.LISTING
            PriceStep.EDIT -> PriceStep.SLOTS
            PriceStep.IMPACT -> PriceStep.EDIT
        }
    }

    ProHostBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { back() }, enabled = !saving) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text("Change price", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = when (step) {
                            PriceStep.LISTING -> "Step 1 of 3 · Choose a listing"
                            PriceStep.DIVISION -> "Step 2 of 3 · Choose a division"
                            PriceStep.SLOTS -> if (fromBooking) "Slots of this booking" else "Step 3 of 3 · Choose a slot"
                            PriceStep.EDIT -> "New price"
                            PriceStep.IMPACT -> "Tenants and requests on this slot"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            }
            if (space != null && step != PriceStep.LISTING) {
                Text(
                    text = listOfNotNull(space.title, division?.name?.takeIf { step != PriceStep.DIVISION }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.sm)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(Spacing.md))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                when (step) {
                    PriceStep.LISTING -> {
                        if (ownSpaces.isEmpty()) {
                            Text("You have no listings yet.", style = MaterialTheme.typography.bodyMedium)
                        }
                        ownSpaces.forEach { s ->
                            PickRow(
                                title = s.title,
                                subtitle = "${s.publicCode} · ${if (s.subdivisions.isEmpty()) "Whole space" else "${s.subdivisions.size} divisions"}",
                                highlighted = s.id == spaceId,
                                onClick = {
                                    spaceId = s.id
                                    val divs = PriceChange.divisionsOf(s)
                                    if (divs.size == 1) {
                                        scopeId = divs.first().scopeId
                                        step = PriceStep.SLOTS
                                    } else {
                                        scopeId = null
                                        step = PriceStep.DIVISION
                                    }
                                }
                            )
                        }
                    }
                    PriceStep.DIVISION -> divisions.forEach { d ->
                        PickRow(
                            title = d.name,
                            subtitle = d.typeLabel,
                            highlighted = d.scopeId == scopeId,
                            onClick = { scopeId = d.scopeId; step = PriceStep.SLOTS }
                        )
                    }
                    PriceStep.SLOTS -> {
                        if (space == null || division == null) {
                            Text("This listing or division is no longer available.", style = MaterialTheme.typography.bodyMedium)
                        } else if (slots.isEmpty()) {
                            Text(
                                "No priced slots are configured here yet. Add them by editing the listing.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            slots.groupBy { it.group }.forEach { (group, groupSlots) ->
                                Text(
                                    group.uppercase(Locale.US),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xs)
                                )
                                groupSlots.forEach { s ->
                                    SlotPriceRow(slot = s, onChange = {
                                        slot = s
                                        priceText = ""
                                        decisions.clear()
                                        step = PriceStep.EDIT
                                    })
                                }
                            }
                        }
                    }
                    PriceStep.EDIT -> slot?.let { s ->
                        Text(s.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Current price: ${formatUsd(s.price)}${s.unit}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { v -> priceText = v.filter { it.isDigit() || it == '.' || it == ',' }.take(10) },
                            label = { Text("New price (USD${s.unit})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        val valid = newPrice != null && newPrice > 0 && newPrice <= 500000 &&
                            PriceChange.round2(newPrice) != PriceChange.round2(s.price)
                        if (affected.isNotEmpty()) {
                            Text(
                                "${affected.size} ${if (affected.size == 1) "booking uses" else "bookings use"} this slot — " +
                                    "you'll choose how the change applies to each next.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        ProPrimaryButton(
                            text = if (affected.isEmpty()) "Save new price" else "Continue",
                            onClick = {
                                if (affected.isEmpty()) submit() else {
                                    affected.forEach { b ->
                                        decisions.putIfAbsent(b.id, if (b.isPending) PriceChangeMode.NOW else PriceChangeMode.NEXT_TERM)
                                    }
                                    step = PriceStep.IMPACT
                                }
                            },
                            enabled = valid && !saving,
                            isLoading = saving && affected.isEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PriceStep.IMPACT -> {
                        val s = slot
                        val p = newPrice
                        if (s != null && p != null && space != null) {
                            Text(
                                "${s.label}: ${formatUsd(s.price)} → ${formatUsd(p)}${s.unit}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Each person below is notified. \"Update now\" re-prices every remaining date or month from today; " +
                                    "\"Next term\" from the next billing period (next month, or next Monday for dated bookings).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Apply to all:", style = MaterialTheme.typography.labelMedium)
                                PriceChangeMode.entries.forEach { mode ->
                                    AssistChip(
                                        onClick = { affected.forEach { decisions[it.id] = mode } },
                                        label = { Text(mode.shortLabel(), style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            val pricing = PriceChange.pricingFor(space, s.ref.scopeId)
                            affected.forEach { b ->
                                val mode = decisions[b.id] ?: PriceChangeMode.KEEP
                                val r = PriceChange.reprice(b, pricing, s.ref, s.price, p, mode, today)
                                ImpactRow(
                                    booking = b,
                                    isThisBooking = b.id == booking?.id,
                                    mode = mode,
                                    newTotal = r.newTotal,
                                    effectiveFrom = r.effectiveFrom,
                                    today = today,
                                    onMode = { decisions[b.id] = it }
                                )
                            }
                            Spacer(Modifier.height(Spacing.sm))
                            ProPrimaryButton(
                                text = "Apply new price",
                                icon = Icons.Default.Check,
                                onClick = { submit() },
                                enabled = !saving,
                                isLoading = saving,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun PriceChangeMode.shortLabel(): String = when (this) {
    PriceChangeMode.KEEP -> "Keep"
    PriceChangeMode.NOW -> "Now"
    PriceChangeMode.NEXT_TERM -> "Next term"
}

@Composable
private fun PickRow(title: String, subtitle: String, highlighted: Boolean, onClick: () -> Unit) {
    ProSurfaceCard(
        onClick = onClick,
        borderColor = if (highlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SlotPriceRow(slot: PriceChange.PriceSlot, onChange: () -> Unit) {
    ProSurfaceCard(contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(slot.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "${formatUsd(slot.price)}${slot.unit}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = onChange) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Change")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImpactRow(
    booking: BookingRequest,
    isThisBooking: Boolean,
    mode: PriceChangeMode,
    newTotal: Double,
    effectiveFrom: String,
    today: String,
    onMode: (PriceChangeMode) -> Unit
) {
    ProSurfaceCard(
        borderColor = if (isThisBooking) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        contentPadding = PaddingValues(Spacing.md)
    ) {
        Text(
            "${booking.publicCode} · ${if (booking.isPending) "Pending request" else "Current tenant"}${if (isThisBooking) " · this booking" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(booking.practitionerName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Spacing.xs))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PriceChangeMode.entries.forEachIndexed { index, m ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { onMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = PriceChangeMode.entries.size),
                    label = { Text(m.shortLabel(), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        val old = booking.totalAmountUsd
        val summary = when {
            mode == PriceChangeMode.KEEP -> "Keeps ${formatUsd(old)} — old price."
            newTotal == old -> "Stays ${formatUsd(old)} for now — the new price applies when they renew."
            else -> "Total ${formatUsd(old)} → ${formatUsd(newTotal)}" +
                if (effectiveFrom.isNotBlank() && effectiveFrom > today) " from $effectiveFrom" else " from today"
        }
        Text(summary, style = MaterialTheme.typography.bodySmall)
    }
}

/** "Price updated" line for a booking whose host changed a slot price (host and specialist screens). */
@Composable
fun PriceChangeNote(change: com.example.data.model.BookingPriceChange, modifier: Modifier = Modifier) {
    val text = when {
        change.mode == PriceChangeMode.KEEP ->
            "Host changed ${change.slotLabel} to ${formatUsd(change.newPrice)} — this booking keeps ${formatUsd(change.oldTotal)}."
        change.newTotal == change.oldTotal ->
            "Host changed ${change.slotLabel} to ${formatUsd(change.newPrice)} — applies when this booking renews."
        else -> "Price updated: ${formatUsd(change.oldTotal)} → ${formatUsd(change.newTotal)}" +
            (if (change.effectiveFrom.isNotBlank()) " from ${change.effectiveFrom}" else "") +
            " (${change.slotLabel} ${formatUsd(change.oldPrice)} → ${formatUsd(change.newPrice)})"
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
