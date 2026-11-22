package admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BookingRequest
import com.example.data.model.BookingRequestStatus
import com.example.ui.theme.Spacing
import com.example.ui.util.BookingTerms
import com.example.ui.util.IsoDate

/**
 * Bookings across every host (ADMIN_PARITY item 65, first part: "all requests", "all bookings", "renting progress"), read-only.
 * The newest 300 `booking_requests` (admins may read them all under firestore.rules), parsed with the shared
 * `BookingRequest.fromFirestoreMap` so status and terms read exactly as in the app; the term and its progress come from the
 * shared `BookingTerms`. Accept/reject stay with the host in the app (the server's conflict guard and pushes run there);
 * change price and payment reminders are the next step.
 */
private val FILTERS = listOf(
  "ALL" to "All", "PENDING" to "Pending", "ACCEPTED" to "Accepted", "PROGRESS" to "Renting now",
  "REJECTED" to "Rejected", "CANCELLED" to "Cancelled",
)

private class BookingRow(val booking: BookingRequest, val term: Pair<String, String>?, val ended: Boolean)

private fun progressOf(term: Pair<String, String>, today: String): Float {
  val start = IsoDate.parse(term.first) ?: return 0f
  val end = IsoDate.parse(term.second) ?: return 0f
  val now = IsoDate.parse(today) ?: return 0f
  val total = (end.toEpochDays() - start.toEpochDays()).toFloat()
  if (total <= 0f) return 1f
  return ((now.toEpochDays() - start.toEpochDays()) / total).coerceIn(0f, 1f)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BookingsTab(gateway: AdminGateway) {
  var rows by remember { mutableStateOf<List<BookingRow>>(emptyList()) }
  var error by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(true) }
  var filter by remember { mutableStateOf("ALL") }
  var query by remember { mutableStateOf("") }
  var reload by remember { mutableStateOf(0) }
  val today = remember { IsoDate.todayIso() }

  LaunchedEffect(reload) {
    loading = true; error = null
    try {
      rows = Json.parse(gateway.recentBookings()).list().mapNotNull { j ->
        @Suppress("UNCHECKED_CAST")
        val map = j.toKotlin() as? Map<String, Any?> ?: return@mapNotNull null
        val b = BookingRequest.fromFirestoreMap(j["id"].str(), map)
        BookingRow(b, BookingTerms.bookingTerm(b), BookingTerms.hasEnded(b, today))
      }
    } catch (e: Throwable) {
      error = "Couldn't load bookings: ${e.message}"
    }
    loading = false
  }

  val q = query.trim().lowercase()
  val shown = rows.filter { r ->
    val b = r.booking
    val status = when (filter) {
      "ALL" -> true
      "PROGRESS" -> b.status == BookingRequestStatus.ACCEPTED && !r.ended
      else -> b.status.name == filter
    }
    status && (q.isEmpty() || listOf(b.displayCode, b.spaceTitle, b.ownerName, b.practitionerName, b.subdivisionName.orEmpty())
      .any { it.lowercase().contains(q) })
  }

  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
      OutlinedTextField(query, { query = it }, label = { Text("Search code, listing, host or specialist") }, singleLine = true, modifier = Modifier.weight(1f))
      OutlinedButton(onClick = { reload++ }) { Text("Refresh") }
      OutlinedButton(onClick = {
        val header = "code,status,listing,room,host,specialist,formula,start,end,total_usd,created"
        val lines = shown.map { r ->
          val b = r.booking
          listOf(b.displayCode, b.status.name, b.spaceTitle, b.subdivisionName.orEmpty(), b.ownerName, b.practitionerName, b.formula.type.name,
            r.term?.first.orEmpty(), r.term?.second.orEmpty(), b.totalAmountUsd.toString(), dateLabel(b.createdAt.toDouble()))
            .joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }
        }
        downloadText("prohost-bookings-${todayStamp()}.csv", (listOf(header) + lines).joinToString("\n"), "text/csv")
      }) { Text("Export CSV") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
      FILTERS.forEach { (key, label) ->
        val n = rows.count { r -> if (key == "ALL") true else if (key == "PROGRESS") r.booking.status == BookingRequestStatus.ACCEPTED && !r.ended else r.booking.status.name == key }
        FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text("$label · $n", style = MaterialTheme.typography.labelSmall) })
      }
    }
    when {
      loading -> Text("Loading bookings…", color = MaterialTheme.colorScheme.onSurfaceVariant)
      error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
      shown.isEmpty() -> Text("No bookings match.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(shown, key = { it.booking.id }) { r -> BookingCard(r, today) }
      }
    }
  }
}

@Composable
private fun BookingCard(r: BookingRow, today: String) {
  val b = r.booking
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          listOf(b.displayCode.ifBlank { b.id.take(8) }, b.spaceTitle, b.subdivisionName.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
          fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
        )
        Text(b.status.name + if (b.rejectedBySystem) " (system)" else "", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
      }
      Text("Host ${b.ownerName} · specialist ${b.practitionerName} · ${b.formula.type.name} · $${b.totalAmountUsd}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      val schedule = b.selectedDateTimeRange.ifBlank { b.startDate }
      if (schedule.isNotBlank()) Text(schedule, style = MaterialTheme.typography.bodySmall)
      r.term?.let { term ->
        Text("Term ${term.first} → ${term.second}${if (r.ended) " · ended" else ""} · requested ${dateLabel(b.createdAt.toDouble())}",
          style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (b.status == BookingRequestStatus.ACCEPTED && !r.ended) {
          LinearProgressIndicator(progress = { progressOf(term, today) }, modifier = Modifier.fillMaxWidth())
        }
      }
      if (!b.rejectionReason.isNullOrBlank()) Text("Reason: ${b.rejectionReason}", style = MaterialTheme.typography.labelSmall)
    }
  }
}
