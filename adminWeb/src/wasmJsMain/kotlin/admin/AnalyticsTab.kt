package admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Radius
import com.example.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * ADMIN_PARITY items 40-44 (Analytics): range presets + from/to day fields (end inclusive), country filter, refresh; Pro Host upgrades bar
 * chart (auto bucket <= 45 d day, <= 200 d week, else month; UTC, zero-filled, peak and axis labels); "Fill from audit log" backfill;
 * booking mix and per-attendee stats; listing performance by country / city / space type with division types. Figures are server-side (`getAdminAnalytics`).
 * Differences to record in ADMIN_PARITY.md: the from/to inputs are `yyyy-MM-dd` text fields (no calendar popup); weeks start on Sunday (en-US).
 */
private const val DAY_MS = 86_400_000.0
private const val CITY_PREVIEW_COUNT = 10

private enum class Preset(val label: String, val days: Int?) { D7("7d", 7), D30("30d", 30), D90("90d", 90), M12("12m", 365), ALL("All time", null) }

private enum class Bucket { DAY, WEEK, MONTH }

@Composable
fun AnalyticsTab(gateway: AdminGateway) {
  var from by remember { mutableStateOf<Double?>(nowMillis() - 30 * DAY_MS) }
  var to by remember { mutableStateOf<Double?>(nowMillis()) }
  var country by remember { mutableStateOf<String?>(null) }
  var data by remember { mutableStateOf<Json?>(null) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var notice by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableStateOf(0) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(from, to, country, reload) {
    loading = true; error = null
    val body = buildString {
      append("{")
      val parts = buildList {
        from?.let { add("\"fromMillis\":${it.toLong()}") }
        to?.let { add("\"toMillis\":${it.toLong()}") }
        country?.let { add("\"country\":${jsonQuote(it)}") }
      }
      append(parts.joinToString(","))
      append("}")
    }
    try { data = Json.parse(gateway.call("getAdminAnalytics", body)) } catch (e: Throwable) { error = "Couldn't load analytics: ${e.message}" }
    loading = false
  }

  val d = data
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
    FiltersCard(from, to, country, d?.get("countries")?.list().orEmpty().map { it.str() }, loading,
      onRange = { f, t -> from = f; to = t }, onCountry = { country = it }, onRefresh = { reload++ })
    notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    when {
      error != null && d == null -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          Text("Couldn't load analytics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
          OutlinedButton(onClick = { reload++ }) { Text("Try again") }
        }
      }
      d == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      else -> {
        UpgradesCard(d, from, to, onBackfill = {
          scope.launch {
            notice = try {
              val r = Json.parse(gateway.call("backfillProHostUpgradeDates"))
              reload++
              "Filled ${r["updated"].int()} upgrade date(s); ${r["stillMissing"].int()} had no record"
            } catch (e: Throwable) { "Backfill failed: ${e.message}" }
          }
        })
        BookingMixCard(d)
        ListingsCard(d, country, hasRange = from != null || to != null, onPickCountry = { country = it })
      }
    }
  }
}

private fun presetFor(from: Double?, to: Double?): Preset? {
  if (from == null && to == null) return Preset.ALL
  if (from == null || to == null) return null
  val days = ((to - from + DAY_MS / 2) / DAY_MS).toInt()
  return Preset.entries.firstOrNull { it.days == days && nowMillis() - to < DAY_MS }
}

@Composable
private fun FiltersCard(from: Double?, to: Double?, country: String?, countries: List<String>, loading: Boolean, onRange: (Double?, Double?) -> Unit, onCountry: (String?) -> Unit, onRefresh: () -> Unit) {
  var menu by remember { mutableStateOf(false) }
  var fromText by remember(from) { mutableStateOf(from?.let { dayStampUtc(it) } ?: "") }
  var toText by remember(to) { mutableStateOf(to?.let { dayStampUtc(it) } ?: "") }
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
          Text("Platform Analytics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text("Pro Host upgrades and published listings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp) else TextButton(onClick = onRefresh) { Text("Refresh") }
      }
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Preset.entries.forEach { p ->
          FilterChip(selected = presetFor(from, to) == p, onClick = {
            if (p.days == null) onRange(null, null) else { val now = nowMillis(); onRange(now - p.days * DAY_MS, now) }
          }, label = { Text(p.label) })
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(fromText, { fromText = it; if (it.isBlank()) onRange(null, to) else parseDayUtc(it.trim())?.let { ms -> onRange(ms, to) } }, label = { Text("From (yyyy-MM-dd)") }, singleLine = true, modifier = Modifier.weight(1f))
        // The end day is inclusive: +24h-1ms, as the Android picker row does.
        OutlinedTextField(toText, { toText = it; if (it.isBlank()) onRange(from, null) else parseDayUtc(it.trim())?.let { ms -> onRange(from, ms + DAY_MS - 1) } }, label = { Text("To (yyyy-MM-dd)") }, singleLine = true, modifier = Modifier.weight(1f))
      }
      Box {
        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(country ?: "All countries") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(text = { Text("All countries") }, onClick = { menu = false; onCountry(null) })
          countries.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { menu = false; onCountry(c) }) }
        }
      }
    }
  }
}

/** Groups daily counts into day/week/month buckets across the whole range, zero-filled (UTC). */
private fun bucketUpgrades(daily: List<Pair<Double, Int>>, from: Double?, to: Double?): Pair<Bucket, List<Pair<Double, Int>>> {
  if (daily.isEmpty()) return Bucket.DAY to emptyList()
  val start = from ?: daily.first().first
  val end = to ?: maxOf(daily.last().first, nowMillis())
  val spanDays = (end - start) / DAY_MS
  val bucket = when { spanDays <= 45 -> Bucket.DAY; spanDays <= 200 -> Bucket.WEEK; else -> Bucket.MONTH }
  fun dayStart(ms: Double) = kotlin.math.floor(ms / DAY_MS) * DAY_MS
  fun bucketStart(ms: Double): Double = when (bucket) {
    Bucket.DAY -> dayStart(ms)
    Bucket.WEEK -> { val d = dayStart(ms); val dow = (kotlin.math.floor(d / DAY_MS).toLong() + 4) % 7 /* 0 = Sunday */; d - dow * DAY_MS }
    Bucket.MONTH -> monthStartUtc(ms)
  }
  fun next(ms: Double): Double = when (bucket) { Bucket.DAY -> ms + DAY_MS; Bucket.WEEK -> ms + 7 * DAY_MS; Bucket.MONTH -> nextMonthUtc(ms) }
  val counts = HashMap<Double, Int>()
  daily.forEach { (day, n) -> val k = bucketStart(day); counts[k] = (counts[k] ?: 0) + n }
  val result = ArrayList<Pair<Double, Int>>()
  var cursor = bucketStart(start)
  val last = bucketStart(end)
  while (cursor <= last && result.size < 400) { result += cursor to (counts[cursor] ?: 0); cursor = next(cursor) }
  return bucket to result
}

@Composable
private fun UpgradesCard(data: Json, from: Double?, to: Double?, onBackfill: () -> Unit) {
  val daily = data["upgrades"].list().map { (it["dayMillis"].num() ?: 0.0) to it["count"].int() }
  val buckets = remember(daily, from, to) { bucketUpgrades(daily, from, to) }
  val total = data["upgradesTotal"].int()
  val undated = data["proHostsWithoutDate"].int()
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
      Text("Pro Host Upgrades", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text("$total upgrade${if (total == 1) "" else "s"} in this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (total == 0) Text("No Pro Host upgrades in this period.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      else BarChart(buckets.second, buckets.first)
      if (undated > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("$undated current Pro Host(s) upgraded before dates were recorded.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
          TextButton(onClick = onBackfill) { Text("Fill from audit log") }
        }
      }
    }
  }
}

@Composable
private fun BarChart(buckets: List<Pair<Double, Int>>, bucket: Bucket) {
  val maxCount = (buckets.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
  val barColor = MaterialTheme.colorScheme.primary
  val gridColor = MaterialTheme.colorScheme.outlineVariant
  Column {
    Text("Peak: $maxCount per ${bucket.name.lowercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
      drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
      drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f)
      if (buckets.isEmpty()) return@Canvas
      val slot = size.width / buckets.size
      val barWidth = (slot * 0.7f).coerceAtLeast(1f)
      buckets.forEachIndexed { i, (_, count) ->
        if (count == 0) return@forEachIndexed
        val h = size.height * (count.toFloat() / maxCount)
        drawRoundRect(barColor, Offset(i * slot + (slot - barWidth) / 2, size.height - h), Size(barWidth, h), CornerRadius(3f, 3f))
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      val monthly = bucket == Bucket.MONTH
      Text(buckets.firstOrNull()?.let { axisLabel(it.first, monthly) }.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(buckets.lastOrNull()?.let { axisLabel(it.first, monthly) }.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun BookingMixCard(data: Json) {
  val mix = data["bookingMix"].list()
  val totalRequests = mix.sumOf { it["requests"].int() }
  val a = data["attendeeStats"]
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
      Text("Booking Mix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text("$totalRequests booking request${if (totalRequests == 1) "" else "s"} by renting strategy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (mix.isEmpty()) Text("No booking requests in this period.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      else {
        val max = mix.maxOf { it["requests"].int() }
        mix.forEach { row ->
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            BarRow(row["label"].str(), row["requests"].int(), max)
            Text("${row["accepted"].int()} accepted · $${groupedWhole(row["acceptedRevenueUsd"].num() ?: 0.0)} accepted value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      if (a["requests"].int() > 0) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text("Per-attendee bookings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val avg = a["avgGroupSize"].num() ?: 0.0
        Text("${a["requests"].int()} requested · ${a["accepted"].int()} accepted · ${a["acceptedAttendees"].int()} attendees accepted · average group ${kotlin.math.round(avg * 10) / 10.0}", style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun ListingsCard(data: Json, country: String?, hasRange: Boolean, onPickCountry: (String?) -> Unit) {
  var showAll by remember { mutableStateOf(false) }
  var expanded by remember { mutableStateOf<String?>(null) }
  val total = data["listingsTotal"].int()
  val byCountry = data["byCountry"].entries().map { it.first to it.second.int() }
  val cities = data["byCity"].entries().map { it.first to it.second.int() }.sortedByDescending { it.second }
  val types = data["bySpaceType"].entries().map { it.first to it.second.int() }.sortedByDescending { it.second }
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
      Text("Listing Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text("$total published listing${if (total == 1) "" else "s"}${country?.let { " in $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (hasRange && data["undatedListings"].int() > 0) Text("${data["undatedListings"].int()} older listing(s) have no creation date and appear only under All time.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (total == 0) Text("No published listings match these filters.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      else {
        if (country == null && byCountry.isNotEmpty()) {
          Text("By country · tap to filter", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          RankedBars(byCountry, onClick = { onPickCountry(it) })
        }
        Text(if (country == null) "By city" else "Cities in $country", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        RankedBars(if (showAll) cities else cities.take(CITY_PREVIEW_COUNT))
        if (cities.size > CITY_PREVIEW_COUNT) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show top $CITY_PREVIEW_COUNT" else "Show all ${cities.size} cities") }
        Text("By space type · tap for division types", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val max = types.maxOfOrNull { it.second } ?: 1
        types.forEach { (type, count) ->
          val open = expanded == type
          Column {
            BarRow(type, count, max, onClick = { expanded = if (open) null else type }, suffix = if (open) " ▲" else " ▼")
            if (open) {
              Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
                Text("Division types in $type", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RankedBars(data["divisionsBySpaceType"][type].entries().map { it.first to it.second.int() })
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RankedBars(counts: List<Pair<String, Int>>, onClick: ((String) -> Unit)? = null) {
  val max = counts.maxOfOrNull { it.second } ?: 1
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
    counts.sortedByDescending { it.second }.forEach { (label, count) -> BarRow(label, count, max, onClick = onClick?.let { cb -> { cb(label) } }) }
  }
}

@Composable
private fun BarRow(label: String, count: Int, max: Int, onClick: (() -> Unit)? = null, suffix: String = "") {
  val fraction = (count.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f)
  Column(Modifier.fillMaxWidth().clip(Radius.Xs).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 2.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
      Text("$count$suffix", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
    Box(Modifier.fillMaxWidth().height(6.dp).clip(Radius.Xs).background(MaterialTheme.colorScheme.surfaceVariant)) {
      if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(Radius.Xs).background(MaterialTheme.colorScheme.primary))
    }
  }
}
