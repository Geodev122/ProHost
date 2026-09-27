package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.auth.AdminAnalytics
import com.example.ui.components.DateRangePickerRow
import com.example.ui.components.ProSectionHeader
import com.example.ui.components.ProSurfaceCard
import com.example.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val CITY_PREVIEW_COUNT = 10

private enum class RangePreset(val label: String, val days: Int?) {
    D7("7d", 7), D30("30d", 30), D90("90d", 90), M12("12m", 365), ALL("All time", null)
}

private enum class Bucket { DAY, WEEK, MONTH }

/**
 * Admin platform analytics: Pro Host upgrades over time, and published-listing counts
 * by country, city, space type and division type for a chosen date range and country.
 * Figures are computed server-side (getAdminAnalytics).
 */
@Composable
fun AdminAnalyticsScreen(adminViewModel: AdminViewModel = viewModel()) {
    val state by adminViewModel.analytics.collectAsState()
    LaunchedEffect(Unit) {
        if (state.data == null && !state.isLoading) adminViewModel.loadAnalytics()
    }
    val data = state.data

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FiltersCard(
                fromMillis = state.fromMillis,
                toMillis = state.toMillis,
                country = state.country,
                countries = data?.countries.orEmpty(),
                isLoading = state.isLoading,
                onRange = adminViewModel::setAnalyticsRange,
                onCountry = adminViewModel::setAnalyticsCountry,
                onRefresh = adminViewModel::loadAnalytics
            )
        }

        when {
            state.error != null && data == null -> item {
                ProSurfaceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Couldn't load analytics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(state.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = adminViewModel::loadAnalytics) { Text("Try again") }
                    }
                }
            }
            data == null -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                item {
                    UpgradesCard(
                        data = data,
                        fromMillis = state.fromMillis,
                        toMillis = state.toMillis,
                        onBackfill = adminViewModel::backfillProHostUpgradeDates
                    )
                }
                item {
                    ListingsCard(
                        data = data,
                        country = state.country,
                        hasRange = state.fromMillis != null || state.toMillis != null,
                        onPickCountry = adminViewModel::setAnalyticsCountry
                    )
                }
            }
        }
    }
}

@Composable
private fun FiltersCard(
    fromMillis: Long?,
    toMillis: Long?,
    country: String?,
    countries: List<String>,
    isLoading: Boolean,
    onRange: (Long?, Long?) -> Unit,
    onCountry: (String?) -> Unit,
    onRefresh: () -> Unit
) {
    var countryMenuOpen by remember { mutableStateOf(false) }
    ProSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProSectionHeader(
                title = "Platform Analytics",
                subtitle = "Pro Host upgrades and published listings",
                icon = Icons.Default.Insights,
                trailingContent = {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh analytics") }
                    }
                }
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RangePreset.entries.forEach { preset ->
                    val selected = presetFor(fromMillis, toMillis) == preset
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (preset.days == null) onRange(null, null)
                            else {
                                val now = System.currentTimeMillis()
                                onRange(now - preset.days * DAY_MS, now)
                            }
                        },
                        label = { Text(preset.label) }
                    )
                }
            }
            DateRangePickerRow(
                fromMillis = fromMillis,
                toMillis = toMillis,
                onFromChange = { onRange(it, toMillis) },
                onToChange = { onRange(fromMillis, it) }
            )
            Box {
                OutlinedButton(onClick = { countryMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(country ?: "All countries", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = countryMenuOpen, onDismissRequest = { countryMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("All countries") }, onClick = { countryMenuOpen = false; onCountry(null) })
                    countries.forEach { c ->
                        DropdownMenuItem(text = { Text(c) }, onClick = { countryMenuOpen = false; onCountry(c) })
                    }
                }
            }
        }
    }
}

private fun presetFor(fromMillis: Long?, toMillis: Long?): RangePreset? {
    if (fromMillis == null && toMillis == null) return RangePreset.ALL
    if (fromMillis == null || toMillis == null) return null
    val days = ((toMillis - fromMillis + DAY_MS / 2) / DAY_MS).toInt()
    return RangePreset.entries.firstOrNull { it.days == days && System.currentTimeMillis() - toMillis < DAY_MS }
}

@Composable
private fun UpgradesCard(data: AdminAnalytics, fromMillis: Long?, toMillis: Long?, onBackfill: () -> Unit) {
    val buckets = remember(data.upgrades, fromMillis, toMillis) { bucketUpgrades(data.upgrades, fromMillis, toMillis) }
    ProSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProSectionHeader(
                title = "Pro Host Upgrades",
                subtitle = "${data.upgradesTotal} upgrade${if (data.upgradesTotal == 1) "" else "s"} in this period",
                icon = Icons.AutoMirrored.Filled.TrendingUp
            )
            if (data.upgradesTotal == 0) {
                Text(
                    "No Pro Host upgrades in this period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                BarChart(buckets = buckets.second, bucket = buckets.first)
            }
            if (data.proHostsWithoutDate > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${data.proHostsWithoutDate} current Pro Host(s) upgraded before dates were recorded.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onBackfill) { Text("Fill from audit log") }
                }
            }
        }
    }
}

/** Groups daily counts into day/week/month buckets across the whole range, zero-filled. */
private fun bucketUpgrades(daily: List<Pair<Long, Int>>, fromMillis: Long?, toMillis: Long?): Pair<Bucket, List<Pair<Long, Int>>> {
    if (daily.isEmpty()) return Bucket.DAY to emptyList()
    val start = fromMillis ?: daily.first().first
    val end = toMillis ?: maxOf(daily.last().first, System.currentTimeMillis())
    val spanDays = (end - start) / DAY_MS
    val bucket = when {
        spanDays <= 45 -> Bucket.DAY
        spanDays <= 200 -> Bucket.WEEK
        else -> Bucket.MONTH
    }
    fun bucketStart(millis: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            when (bucket) {
                Bucket.WEEK -> set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                Bucket.MONTH -> set(Calendar.DAY_OF_MONTH, 1)
                Bucket.DAY -> Unit
            }
        }
        return cal.timeInMillis
    }
    fun next(millis: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
        when (bucket) {
            Bucket.DAY -> cal.add(Calendar.DAY_OF_MONTH, 1)
            Bucket.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            Bucket.MONTH -> cal.add(Calendar.MONTH, 1)
        }
        return cal.timeInMillis
    }
    val counts = HashMap<Long, Int>()
    daily.forEach { (day, n) -> counts.merge(bucketStart(day), n) { a, b -> a + b } }
    val result = ArrayList<Pair<Long, Int>>()
    var cursor = bucketStart(start)
    val last = bucketStart(end)
    // Bounded: at most ~400 daily, ~30 weekly or a few hundred monthly buckets.
    while (cursor <= last && result.size < 400) {
        result += cursor to (counts[cursor] ?: 0)
        cursor = next(cursor)
    }
    return bucket to result
}

@Composable
private fun BarChart(buckets: List<Pair<Long, Int>>, bucket: Bucket) {
    val maxCount = (buckets.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val fmt = remember(bucket) {
        SimpleDateFormat(if (bucket == Bucket.MONTH) "MMM yyyy" else "MMM d", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    Column {
        Text("Peak: $maxCount per ${bucket.name.lowercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f)
            if (buckets.isEmpty()) return@Canvas
            val slot = size.width / buckets.size
            val barWidth = (slot * 0.7f).coerceAtLeast(1f)
            buckets.forEachIndexed { i, (_, count) ->
                if (count == 0) return@forEachIndexed
                val h = size.height * (count.toFloat() / maxCount)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(3f, 3f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
            val labelStyle = MaterialTheme.typography.labelSmall
            Text(buckets.firstOrNull()?.let { fmt.format(Date(it.first)) }.orEmpty(), style = labelStyle, color = axisColor)
            Text(buckets.lastOrNull()?.let { fmt.format(Date(it.first)) }.orEmpty(), style = labelStyle, color = axisColor)
        }
    }
}

@Composable
private fun ListingsCard(data: AdminAnalytics, country: String?, hasRange: Boolean, onPickCountry: (String?) -> Unit) {
    var showAllCities by remember { mutableStateOf(false) }
    var expandedType by remember { mutableStateOf<String?>(null) }
    ProSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProSectionHeader(
                title = "Listing Performance",
                subtitle = "${data.listingsTotal} published listing${if (data.listingsTotal == 1) "" else "s"}" +
                    (country?.let { " in $it" } ?: ""),
                icon = Icons.Default.Insights
            )
            if (hasRange && data.undatedListings > 0) {
                Text(
                    "${data.undatedListings} older listing(s) have no creation date and appear only under All time.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (data.listingsTotal == 0) {
                Text(
                    "No published listings match these filters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {

                if (country == null && data.byCountry.isNotEmpty()) {
                    SectionTitle("By country · tap to filter", Icons.Default.Public)
                    RankedBars(data.byCountry, onClick = onPickCountry)
                }

                SectionTitle(if (country == null) "By city" else "Cities in $country", Icons.Default.LocationCity)
                val cities = data.byCity.entries.sortedByDescending { it.value }
                RankedBars(
                    (if (showAllCities) cities else cities.take(CITY_PREVIEW_COUNT)).associate { it.key to it.value }
                )
                if (cities.size > CITY_PREVIEW_COUNT) {
                    TextButton(onClick = { showAllCities = !showAllCities }) {
                        Text(if (showAllCities) "Show top $CITY_PREVIEW_COUNT" else "Show all ${cities.size} cities")
                    }
                }

                SectionTitle("By space type · tap for division types", Icons.Default.Category)
                val max = data.bySpaceType.values.maxOrNull() ?: 1
                data.bySpaceType.entries.sortedByDescending { it.value }.forEach { (type, count) ->
                    val expanded = expandedType == type
                    Column {
                        BarRow(
                            label = type,
                            count = count,
                            max = max,
                            trailingIcon = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            onClick = { expandedType = if (expanded) null else type }
                        )
                        if (expanded) {
                            val divisions = data.divisionsBySpaceType[type].orEmpty()
                            Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
                                Text("Division types in $type", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                RankedBars(divisions)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RankedBars(counts: Map<String, Int>, onClick: ((String) -> Unit)? = null) {
    val max = counts.values.maxOrNull() ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        counts.entries.sortedByDescending { it.value }.forEach { (label, count) ->
            BarRow(label = label, count = count, max = max, onClick = onClick?.let { cb -> { cb(label) } })
        }
    }
}

@Composable
private fun BarRow(
    label: String,
    count: Int,
    max: Int,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val fraction = (count.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$count", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
