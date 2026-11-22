package admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BookingRequest
import com.example.data.model.BookingRequestStatus
import com.example.data.model.PriceChangeMode
import com.example.data.model.SpaceListing
import com.example.ui.theme.Spacing
import com.example.ui.util.PriceChange
import kotlinx.coroutines.launch

/**
 * Change price from the console (ADMIN_PARITY item 65, D2): the app's Change price sheet in one dialog — division → slot →
 * new price → per affected booking Keep · Now · Next term, with each booking's new total previewed by the shared
 * `PriceChange.reprice` (the same code the app runs). Only the `changeSlotPrice` callable writes (admins may change any
 * listing); bookings not among the console's loaded 300 keep their price, as the server's default.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChangePriceDialog(gateway: AdminGateway, booking: BookingRequest, loaded: List<BookingRequest>, onDone: (String?) -> Unit) {
  var space by remember { mutableStateOf<SpaceListing?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var scopeId by remember { mutableStateOf<String?>(null) }
  var slot by remember { mutableStateOf<PriceChange.PriceSlot?>(null) }
  var priceText by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  val decisions = remember { mutableStateMapOf<String, PriceChangeMode>() }
  val scope = rememberCoroutineScope()

  LaunchedEffect(booking.spaceId) {
    try {
      val raw = gateway.readDoc("workspace_listings/${booking.spaceId}") ?: run { error = "That listing no longer exists."; return@LaunchedEffect }
      @Suppress("UNCHECKED_CAST")
      val map = Json.parse(raw).toKotlin() as Map<String, Any?>
      val s = SpaceListing.fromFirestoreMap(booking.spaceId, map)
      space = s
      val division = PriceChange.divisionOf(s, booking) ?: PriceChange.divisionsOf(s).firstOrNull()
      scopeId = division?.scopeId
      slot = division?.let { PriceChange.slotsForBooking(s, it, booking).firstOrNull() ?: PriceChange.slotsOf(it, s).firstOrNull() }
    } catch (e: Throwable) {
      error = "Couldn't load the listing: ${e.message}"
    }
  }

  val s = space
  val divisions = s?.let { PriceChange.divisionsOf(it) }.orEmpty()
  val division = divisions.firstOrNull { it.scopeId == scopeId }
  val slots = if (s != null && division != null) PriceChange.slotsOf(division, s) else emptyList()
  val pricing = if (s != null && division != null) PriceChange.pricingFor(s, division.scopeId) else null
  val newPrice = priceText.trim().toDoubleOrNull()?.takeIf { it > 0 && it <= 500_000 }
  val affected = if (s == null || slot == null) emptyList() else loaded.filter {
    it.spaceId == s.id && (it.status == BookingRequestStatus.PENDING || it.status == BookingRequestStatus.ACCEPTED) &&
      PriceChange.bookingUses(it, s.id, pricing, slot!!.ref)
  }

  AlertDialog(
    onDismissRequest = { if (!busy) onDone(null) },
    title = { Text("Change price · ${booking.spaceTitle}") },
    text = {
      Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when {
          error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
          s == null -> Text("Loading the listing…", color = MaterialTheme.colorScheme.onSurfaceVariant)
          else -> {
            if (divisions.size > 1) {
              Text("Room", fontWeight = FontWeight.SemiBold)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                divisions.forEach { d ->
                  FilterChip(selected = d.scopeId == scopeId, onClick = { scopeId = d.scopeId; slot = PriceChange.slotsOf(d, s).firstOrNull(); decisions.clear() },
                    label = { Text(d.name.ifBlank { d.typeLabel }) })
                }
              }
            }
            Text("Slot", fontWeight = FontWeight.SemiBold)
            if (slots.isEmpty()) Text("This room has no priced slots.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
              slots.forEach { sl ->
                FilterChip(selected = sl.ref == slot?.ref, onClick = { slot = sl; decisions.clear() },
                  label = { Text("${sl.group} · ${sl.label} · $${sl.price} ${sl.unit}") })
              }
            }
            OutlinedTextField(priceText, { priceText = it }, label = { Text("New price (USD)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
              supportingText = { slot?.let { Text("Now $${it.price} ${it.unit}") } })
            HorizontalDivider()
            Text(if (affected.isEmpty()) "No pending or accepted booking uses this slot." else "Bookings that use this slot", fontWeight = FontWeight.SemiBold)
            affected.forEach { b ->
              val mode = decisions[b.id] ?: PriceChangeMode.KEEP
              val preview = if (newPrice != null && slot != null) PriceChange.reprice(b, pricing, slot!!.ref, slot!!.price, newPrice, mode) else null
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${b.displayCode.ifBlank { b.id.take(8) }} · ${b.practitionerName} · ${b.status.name}", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                  PriceChangeMode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { decisions[b.id] = m }, label = { Text(m.label) })
                  }
                }
                preview?.let {
                  Text(if (mode == PriceChangeMode.KEEP || it.units == 0) "Total stays $${b.totalAmountUsd}"
                    else "Total $${b.totalAmountUsd} → $${it.newTotal} from ${it.effectiveFrom}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(enabled = !busy && s != null && slot != null && newPrice != null, onClick = {
        val sl = slot ?: return@Button
        val price = newPrice ?: return@Button
        busy = true
        scope.launch {
          val dec = decisions.entries.joinToString(",") { (id, m) -> "${jsonQuote(id)}:${jsonQuote(m.name)}" }
          val body = "{\"spaceId\":${jsonQuote(s!!.id)},\"ref\":{\"scopeId\":${jsonQuote(sl.ref.scopeId)},\"kind\":${jsonQuote(sl.ref.kind.name)},\"key\":${jsonQuote(sl.ref.key)}},\"newPrice\":$price,\"decisions\":{$dec}}"
          val message = try {
            gateway.call("changeSlotPrice", body)
            "Price changed to $$price. Specialists with updated bookings were notified."
          } catch (e: Throwable) {
            "Couldn't change the price: ${e.message}"
          }
          busy = false
          onDone(message)
        }
      }) { Text(if (busy) "Saving…" else "Change price") }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = { onDone(null) }) { Text("Cancel") } }
  )
}
