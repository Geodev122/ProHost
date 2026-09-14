package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.theme.Spacing
import java.util.Calendar

/**
 * The single "Renting Formula Allocation" editor (spec Step 3, item 2/3) — one
 * strategy at a time, picked here, configured by the matching editor below. Used
 * identically for a division's own [RentalPricingConfig] and, when a listing has no
 * subdivisions, the listing's own top-level pricing — the same component either way,
 * since [RentalPricingConfig] doesn't care which one it came from.
 */
@Composable
fun RentalPricingConfigEditor(
    config: RentalPricingConfig,
    operatingDays: List<String>,
    openingHour: String,
    closingHour: String,
    onConfigChange: (RentalPricingConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Renting Strategy", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(RentalStrategyType.values()) { type ->
                FilterChip(
                    selected = config.strategyType == type,
                    onClick = {
                        if (config.strategyType != type) {
                            onConfigChange(
                                RentalPricingConfig(
                                    strategyType = type,
                                    monthly = if (type == RentalStrategyType.MONTHLY) config.monthly ?: MonthlyConfig() else null,
                                    hourly = if (type == RentalStrategyType.HOURLY) config.hourly ?: HourlyConfig() else null,
                                    shiftBased = if (type == RentalStrategyType.SHIFT_BASED) config.shiftBased ?: ShiftBasedConfig() else null,
                                    dayBased = if (type == RentalStrategyType.DAY_BASED) config.dayBased ?: DayBasedConfig() else null
                                )
                            )
                        }
                    },
                    label = { Text(type.displayName, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                )
            }
        }

        when (config.strategyType) {
            RentalStrategyType.MONTHLY -> MonthlyStrategyEditor(
                config = config.monthly ?: MonthlyConfig(),
                onConfigChange = { onConfigChange(config.copy(monthly = it)) }
            )
            RentalStrategyType.HOURLY -> HourlyStrategyEditor(
                config = config.hourly ?: HourlyConfig(),
                operatingDays = operatingDays,
                openingHour = openingHour,
                closingHour = closingHour,
                onConfigChange = { onConfigChange(config.copy(hourly = it)) }
            )
            RentalStrategyType.SHIFT_BASED -> ShiftStrategyEditor(
                config = config.shiftBased ?: ShiftBasedConfig(),
                operatingDays = operatingDays,
                onConfigChange = { onConfigChange(config.copy(shiftBased = it)) }
            )
            RentalStrategyType.DAY_BASED -> DayBasedStrategyEditor(
                config = config.dayBased ?: DayBasedConfig(),
                operatingDays = operatingDays,
                openingHour = openingHour,
                closingHour = closingHour,
                onConfigChange = { onConfigChange(config.copy(dayBased = it)) }
            )
        }
    }
}

@Composable
fun MonthlyStrategyEditor(
    config: MonthlyConfig,
    onConfigChange: (MonthlyConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val nowCal = remember { Calendar.getInstance() }
    val currentYear = nowCal.get(Calendar.YEAR)
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = if (config.rateUsd == 0.0) "" else config.rateUsd.toInt().toString(),
            onValueChange = { onConfigChange(config.copy(rateUsd = it.toDoubleOrNull() ?: 0.0)) },
            label = { Text("Rate (USD/mo)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text("Available From", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(monthNames) { m ->
                    val idx = monthNames.indexOf(m) + 1
                    FilterChip(
                        selected = config.fromMonth == idx,
                        onClick = { onConfigChange(config.copy(fromMonth = idx)) },
                        label = { Text(m, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                    )
                }
            }
            OutlinedTextField(
                value = config.fromYear.toString(),
                onValueChange = { onConfigChange(config.copy(fromYear = it.toIntOrNull() ?: currentYear)) },
                modifier = Modifier.width(90.dp),
                singleLine = true
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Indefinite (no end date)", fontSize = MaterialTheme.typography.bodySmall.fontSize)
            Switch(
                checked = config.isIndefinite,
                onCheckedChange = { indefinite ->
                    // Default toMonth/toYear the moment "Indefinite" is turned off,
                    // instead of leaving them null until the host happens to tap a
                    // month chip below — a listing could otherwise publish with
                    // isIndefinite=false and no real end date, which the slot-builder's
                    // own date-range check treats as an already-ended range, so it
                    // stays live forever with permanently zero bookable availability.
                    onConfigChange(
                        if (!indefinite && config.toMonth == null) {
                            config.copy(isIndefinite = false, toMonth = config.fromMonth, toYear = config.fromYear + 1)
                        } else {
                            config.copy(isIndefinite = indefinite)
                        }
                    )
                }
            )
        }

        if (!config.isIndefinite) {
            Text("Available To", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(monthNames) { m ->
                        val idx = monthNames.indexOf(m) + 1
                        FilterChip(
                            selected = config.toMonth == idx,
                            onClick = { onConfigChange(config.copy(toMonth = idx)) },
                            label = { Text(m, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                        )
                    }
                }
                OutlinedTextField(
                    value = (config.toYear ?: currentYear).toString(),
                    onValueChange = { onConfigChange(config.copy(toYear = it.toIntOrNull() ?: currentYear)) },
                    modifier = Modifier.width(90.dp),
                    singleLine = true
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Excluded ranges (${config.excludedRanges.size})", fontSize = MaterialTheme.typography.bodySmall.fontSize)
            TextButton(onClick = {
                onConfigChange(config.copy(excludedRanges = config.excludedRanges + MonthYearRange(nowCal.get(Calendar.MONTH) + 1, currentYear, nowCal.get(Calendar.MONTH) + 1, currentYear)))
            }) { Text("+ Add Excluded Range") }
        }
        config.excludedRanges.forEachIndexed { idx, range ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("${monthNames[range.fromMonth - 1]} ${range.fromYear} – ${monthNames[range.toMonth - 1]} ${range.toYear}", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                IconButton(onClick = { onConfigChange(config.copy(excludedRanges = config.excludedRanges.filterIndexed { i, _ -> i != idx })) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove excluded range", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun parseHourInt(value: String): Int = value.substringBefore(':').trim().toIntOrNull() ?: 0

@Composable
fun HourlyStrategyEditor(
    config: HourlyConfig,
    operatingDays: List<String>,
    openingHour: String,
    closingHour: String,
    onConfigChange: (HourlyConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val openH = parseHourInt(openingHour)
    val closeH = parseHourInt(closingHour).let { if (it <= openH) openH + 1 else it }
    val hours = (openH until closeH).toList()
    var bulkPriceByDay by remember { mutableStateOf(mapOf<String, String>()) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Tap an hour to toggle it on/off for that day at the day's current price. Leaving an hour untouched keeps it out of renting.",
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        operatingDays.forEach { day ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(day, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize, modifier = Modifier.width(40.dp))
                    OutlinedTextField(
                        value = bulkPriceByDay[day] ?: "",
                        onValueChange = { bulkPriceByDay = bulkPriceByDay + (day to it) },
                        label = { Text("USD/hr") },
                        modifier = Modifier.width(110.dp),
                        singleLine = true
                    )
                    TextButton(onClick = {
                        val price = bulkPriceByDay[day]?.toDoubleOrNull() ?: return@TextButton
                        onConfigChange(config.copy(cellPrices = config.cellPrices + hours.associate { "$day|$it" to price }))
                    }) { Text("Fill all hours") }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(hours) { hour ->
                        val key = "$day|$hour"
                        val isOn = config.cellPrices.containsKey(key)
                        FilterChip(
                            selected = isOn,
                            onClick = {
                                val price = bulkPriceByDay[day]?.toDoubleOrNull() ?: config.cellPrices[key] ?: 0.0
                                onConfigChange(
                                    config.copy(
                                        cellPrices = if (isOn) config.cellPrices - key else config.cellPrices + (key to price)
                                    )
                                )
                            },
                            label = { Text("%02d:00".format(hour), fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShiftStrategyEditor(
    config: ShiftBasedConfig,
    operatingDays: List<String>,
    onConfigChange: (ShiftBasedConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("1. Shift Configuration", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelMedium.fontSize, color = MaterialTheme.colorScheme.primary)
        config.shifts.forEach { shift ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(shift.name.displayName, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Unavailable", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                        Switch(
                            checked = shift.isUnavailable,
                            onCheckedChange = { checked ->
                                onConfigChange(config.copy(shifts = config.shifts.map { if (it.name == shift.name) it.copy(isUnavailable = checked) else it }))
                            }
                        )
                    }
                }
                if (!shift.isUnavailable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = shift.startHour.toString(),
                            onValueChange = { v -> onConfigChange(config.copy(shifts = config.shifts.map { if (it.name == shift.name) it.copy(startHour = v.toIntOrNull() ?: it.startHour) else it })) },
                            label = { Text("From (hr)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = shift.endHour.toString(),
                            onValueChange = { v -> onConfigChange(config.copy(shifts = config.shifts.map { if (it.name == shift.name) it.copy(endHour = v.toIntOrNull() ?: it.endHour) else it })) },
                            label = { Text("To (hr)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    // Single flat price per shift (item 7b) — the specialist
                    // configures occurrences (which dates, how many) at booking
                    // time instead of picking one of 3 pre-set commitment tiers.
                    OutlinedTextField(
                        value = if (shift.price == 0.0) "" else shift.price.toInt().toString(),
                        onValueChange = { v -> onConfigChange(config.copy(shifts = config.shifts.map { if (it.name == shift.name) it.copy(price = v.toDoubleOrNull() ?: 0.0) else it })) },
                        label = { Text("Price per shift ($ USD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                HorizontalDivider()
            }
        }

        Text("2. Shifts Distribution", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelMedium.fontSize, color = MaterialTheme.colorScheme.primary)
        Text(
            "Which shifts are offered on which day.",
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val availableShifts = config.shifts.filterNot { it.isUnavailable }
        operatingDays.forEach { day ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(day, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize, modifier = Modifier.width(40.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(availableShifts) { shift ->
                        val offeredToday = config.distribution[day] ?: emptyList()
                        val isOn = offeredToday.contains(shift.name.name)
                        FilterChip(
                            selected = isOn,
                            onClick = {
                                val updated = if (isOn) offeredToday - shift.name.name else offeredToday + shift.name.name
                                onConfigChange(config.copy(distribution = config.distribution + (day to updated)))
                            },
                            label = { Text(shift.name.displayName, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DayBasedStrategyEditor(
    config: DayBasedConfig,
    operatingDays: List<String>,
    openingHour: String,
    closingHour: String,
    onConfigChange: (DayBasedConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Use facility operating hours ($openingHour–$closingHour)", fontSize = MaterialTheme.typography.bodySmall.fontSize)
            Switch(checked = config.useFacilityHours, onCheckedChange = { onConfigChange(config.copy(useFacilityHours = it)) })
        }
        if (!config.useFacilityHours) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = (config.customStartHour ?: parseHourInt(openingHour)).toString(),
                    onValueChange = { v -> onConfigChange(config.copy(customStartHour = v.toIntOrNull())) },
                    label = { Text("From (hr)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = (config.customEndHour ?: parseHourInt(closingHour)).toString(),
                    onValueChange = { v -> onConfigChange(config.copy(customEndHour = v.toIntOrNull())) },
                    label = { Text("To (hr)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }

        Text(
            "Per day: price if rented one time, same day each month, and same day each week. Leave all three blank to mark a day Not Available.",
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        operatingDays.forEach { day ->
            val dp = config.distribution[day] ?: DayPricing()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(day, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = dp.oneTimePrice?.let { if (it == 0.0) "" else it.toInt().toString() } ?: "",
                        onValueChange = { v ->
                            val updated = dp.copy(oneTimePrice = v.toDoubleOrNull())
                            onConfigChange(config.copy(distribution = config.distribution + (day to updated)))
                        },
                        label = { Text("One-time") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dp.sameDayEachMonthPrice?.let { if (it == 0.0) "" else it.toInt().toString() } ?: "",
                        onValueChange = { v ->
                            val updated = dp.copy(sameDayEachMonthPrice = v.toDoubleOrNull())
                            onConfigChange(config.copy(distribution = config.distribution + (day to updated)))
                        },
                        label = { Text("Monthly") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dp.sameDayEachWeekPrice?.let { if (it == 0.0) "" else it.toInt().toString() } ?: "",
                        onValueChange = { v ->
                            val updated = dp.copy(sameDayEachWeekPrice = v.toDoubleOrNull())
                            onConfigChange(config.copy(distribution = config.distribution + (day to updated)))
                        },
                        label = { Text("Weekly") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        }
    }
}
