package com.example.ui.components

import com.example.ui.util.localizedLabel
import com.example.R
import androidx.compose.ui.res.stringResource
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
    modifier: Modifier = Modifier,
    // Per-attendee rooms: configure only WHEN the room is offered; price comes from
    // attendee tiers (see AttendeePricing), so price inputs are hidden, Monthly isn't
    // offered, and offered slots carry the availability marker price.
    availabilityOnly: Boolean = false
) {
    // Per-attendee: which shifts are offered is decided by the day distribution, and the
    // shift editor has no price field — so every shift carries the availability marker.
    // (Shifts used to stay at 0.0 when Shift-Based was picked after switching to per-attendee,
    // so the room could never pass hasRealPrice() and its Save button stayed disabled.)
    val emitConfig: (RentalPricingConfig) -> Unit = { c ->
        onConfigChange(
            if (!availabilityOnly) c else c.copy(
                shiftBased = c.shiftBased?.let { sb ->
                    sb.copy(shifts = sb.shifts.map { it.copy(price = com.example.ui.util.AttendeePricing.AVAILABILITY_MARKER_PRICE) })
                }
            )
        )
    }
    val strategyChoices = if (availabilityOnly) {
        RentalStrategyType.values().filter { it != RentalStrategyType.MONTHLY }
    } else {
        RentalStrategyType.values().toList()
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            if (availabilityOnly) stringResource(R.string.offered_times_hourly_shifts_or_days) else stringResource(R.string.renting_strategy),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(strategyChoices) { type ->
                FilterChip(
                    selected = config.strategyType == type,
                    onClick = {
                        if (config.strategyType != type) {
                            emitConfig(
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
                    label = { Text(localizedLabel(type.displayName), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        when (config.strategyType) {
            RentalStrategyType.MONTHLY -> MonthlyStrategyEditor(
                config = config.monthly ?: MonthlyConfig(),
                onConfigChange = { emitConfig(config.copy(monthly = it)) }
            )
            RentalStrategyType.HOURLY -> HourlyStrategyEditor(
                config = config.hourly ?: HourlyConfig(),
                operatingDays = operatingDays,
                openingHour = openingHour,
                closingHour = closingHour,
                onConfigChange = { emitConfig(config.copy(hourly = it)) },
                availabilityOnly = availabilityOnly
            )
            RentalStrategyType.SHIFT_BASED -> ShiftStrategyEditor(
                config = config.shiftBased ?: ShiftBasedConfig(),
                operatingDays = operatingDays,
                onConfigChange = { emitConfig(config.copy(shiftBased = it)) },
                availabilityOnly = availabilityOnly
            )
            RentalStrategyType.DAY_BASED -> DayBasedStrategyEditor(
                config = config.dayBased ?: DayBasedConfig(),
                operatingDays = operatingDays,
                openingHour = openingHour,
                closingHour = closingHour,
                onConfigChange = { emitConfig(config.copy(dayBased = it)) },
                availabilityOnly = availabilityOnly
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        InputField(
            value = if (config.rateUsd == 0.0) "" else config.rateUsd.toInt().toString(),
            onValueChange = { onConfigChange(config.copy(rateUsd = it.toDoubleOrNull() ?: 0.0)) },
            label = stringResource(R.string.rate_usd_mo),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(stringResource(R.string.available_from), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(monthNames) { m ->
                    val idx = monthNames.indexOf(m) + 1
                    FilterChip(
                        selected = config.fromMonth == idx,
                        onClick = { onConfigChange(config.copy(fromMonth = idx)) },
                        label = { Text(m, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            InputField(
                value = config.fromYear.toString(),
                onValueChange = { onConfigChange(config.copy(fromYear = it.toIntOrNull() ?: currentYear)) },
                label = "",
                modifier = Modifier.width(90.dp),
                singleLine = true
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.indefinite_no_end_date), style = MaterialTheme.typography.bodySmall)
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
            Text(stringResource(R.string.available_to), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(monthNames) { m ->
                        val idx = monthNames.indexOf(m) + 1
                        FilterChip(
                            selected = config.toMonth == idx,
                            onClick = { onConfigChange(config.copy(toMonth = idx)) },
                            label = { Text(m, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                InputField(
                    value = (config.toYear ?: currentYear).toString(),
                    onValueChange = { onConfigChange(config.copy(toYear = it.toIntOrNull() ?: currentYear)) },
                    label = "",
                    modifier = Modifier.width(90.dp),
                    singleLine = true
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.excluded_ranges_x, config.excludedRanges.size), style = MaterialTheme.typography.bodySmall)
            TextButton(shape = MaterialTheme.shapes.medium, onClick = {
                onConfigChange(
                    config.copy(
                        excludedRanges = config.excludedRanges + MonthYearRange(
                            nowCal.get(Calendar.MONTH) + 1, currentYear, nowCal.get(Calendar.MONTH) + 1, currentYear
                        )
                    )
                )
            }) { Text(stringResource(R.string.add_excluded_range)) }
        }
        config.excludedRanges.forEachIndexed { idx, range ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${monthNames[range.fromMonth - 1]} ${range.fromYear} – ${monthNames[range.toMonth - 1]} ${range.toYear}",
                    style = MaterialTheme.typography.labelSmall
                )
                IconButton(onClick = { onConfigChange(config.copy(excludedRanges = config.excludedRanges.filterIndexed { i, _ -> i != idx })) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_excluded_range), tint = MaterialTheme.colorScheme.error)
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
    modifier: Modifier = Modifier,
    availabilityOnly: Boolean = false
) {
    val marker = com.example.ui.util.AttendeePricing.AVAILABILITY_MARKER_PRICE
    val openH = parseHourInt(openingHour)
    val closeH = parseHourInt(closingHour).let { if (it <= openH) openH + 1 else it }
    val hours = (openH until closeH).toList()
    var bulkPriceByDay by remember { mutableStateOf(mapOf<String, String>()) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            if (availabilityOnly) stringResource(R.string.tap_an_hour_to_offer_it_on_that_day_untouc)
            else stringResource(R.string.tap_an_hour_to_toggle_it_on_off_for_that_d),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        operatingDays.forEach { day ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(localizedLabel(day), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                    if (!availabilityOnly) InputField(
     value = bulkPriceByDay[day] ?: "",
     onValueChange = { bulkPriceByDay = bulkPriceByDay + (day to it) },
     label = stringResource(R.string.usd_hr),
     modifier = Modifier.width(110.dp),
     singleLine = true
 )
                    TextButton(shape = MaterialTheme.shapes.medium, onClick = {
                        val price = if (availabilityOnly) marker else bulkPriceByDay[day]?.toDoubleOrNull() ?: return@TextButton
                        onConfigChange(config.copy(cellPrices = config.cellPrices + hours.associate { "$day|$it" to price }))
                    }) { Text(stringResource(R.string.fill_all_hours)) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(hours) { hour ->
                        val key = "$day|$hour"
                        val isOn = config.cellPrices.containsKey(key)
                        FilterChip(
                            selected = isOn,
                            onClick = {
                                val price = if (availabilityOnly) marker
                                    else bulkPriceByDay[day]?.toDoubleOrNull() ?: config.cellPrices[key] ?: 0.0
                                onConfigChange(
                                    config.copy(
                                        cellPrices = if (isOn) config.cellPrices - key else config.cellPrices + (key to price)
                                    )
                                )
                            },
                            label = { Text(stringResource(R.string.t_02d_00).format(hour), style = MaterialTheme.typography.labelSmall) }
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
    modifier: Modifier = Modifier,
    availabilityOnly: Boolean = false
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(stringResource(R.string.t_1_shift_configuration), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        config.shifts.forEach { shift ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(localizedLabel(shift.name.displayName), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.unavailable), style = MaterialTheme.typography.labelSmall)
                        Switch(
                            checked = shift.isUnavailable,
                            onCheckedChange = { checked ->
                                onConfigChange(config.copy(shifts = config.shifts.map { if (it.name == shift.name) it.copy(isUnavailable = checked) else it }))
                            }
                        )
                    }
                }
                if (!shift.isUnavailable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        InputField(
                            value = shift.startHour.toString(),
                            onValueChange = { v ->
                                onConfigChange(
                                    config.copy(
                                        shifts = config.shifts.map {
                                            if (it.name == shift.name) it.copy(startHour = v.toIntOrNull() ?: it.startHour) else it
                                        }
                                    )
                                )
                            },
                            label = stringResource(R.string.from_hr),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        InputField(
                            value = shift.endHour.toString(),
                            onValueChange = { v ->
                                onConfigChange(
                                    config.copy(
                                        shifts = config.shifts.map {
                                            if (it.name == shift.name) it.copy(endHour = v.toIntOrNull() ?: it.endHour) else it
                                        }
                                    )
                                )
                            },
                            label = stringResource(R.string.to_hr),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    // Single flat price per shift (item 7b) — the specialist
                    // configures occurrences (which dates, how many) at booking
                    // time instead of picking one of 3 pre-set commitment tiers.
                    if (!availabilityOnly) InputField(
     value = if (shift.price == 0.0) "" else shift.price.toInt().toString(),
     onValueChange = { v ->
                            onConfigChange(
                                config.copy(
                                    shifts = config.shifts.map {
                                        if (it.name == shift.name) it.copy(price = v.toDoubleOrNull() ?: 0.0) else it
                                    }
                                )
                            )
                        },
     label = stringResource(R.string.price_per_shift_usd),
     modifier = Modifier.fillMaxWidth(),
     singleLine = true
 )
                }
                HorizontalDivider()
            }
        }

        Text(stringResource(R.string.t_2_shifts_distribution), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.which_shifts_are_offered_on_which_day),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val availableShifts = config.shifts.filterNot { it.isUnavailable }
        operatingDays.forEach { day ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(localizedLabel(day), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(availableShifts) { shift ->
                        val offeredToday = config.distribution[day] ?: emptyList()
                        val isOn = offeredToday.contains(shift.name.name)
                        FilterChip(
                            selected = isOn,
                            onClick = {
                                val updated = if (isOn) offeredToday - shift.name.name else offeredToday + shift.name.name
                                onConfigChange(config.copy(distribution = config.distribution + (day to updated)))
                            },
                            label = { Text(localizedLabel(shift.name.displayName), style = MaterialTheme.typography.labelSmall) }
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
    modifier: Modifier = Modifier,
    availabilityOnly: Boolean = false
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.use_facility_operating_hours_x_x, openingHour, closingHour), style = MaterialTheme.typography.bodySmall)
            Switch(checked = config.useFacilityHours, onCheckedChange = { onConfigChange(config.copy(useFacilityHours = it)) })
        }
        if (!config.useFacilityHours) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                InputField(
                    value = (config.customStartHour ?: parseHourInt(openingHour)).toString(),
                    onValueChange = { v -> onConfigChange(config.copy(customStartHour = v.toIntOrNull())) },
                    label = stringResource(R.string.from_hr),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                InputField(
                    value = (config.customEndHour ?: parseHourInt(closingHour)).toString(),
                    onValueChange = { v -> onConfigChange(config.copy(customEndHour = v.toIntOrNull())) },
                    label = stringResource(R.string.to_hr),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }

        if (availabilityOnly) {
            Text(
                stringResource(R.string.switch_on_each_day_this_room_is_offered),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            operatingDays.forEach { day ->
                val offered = (config.distribution[day]?.price ?: 0.0) > 0.0
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(localizedLabel(day), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = offered,
                        onCheckedChange = { on ->
                            val price = if (on) com.example.ui.util.AttendeePricing.AVAILABILITY_MARKER_PRICE else 0.0
                            onConfigChange(config.copy(distribution = config.distribution + (day to DayPricing(price = price))))
                        }
                    )
                }
            }
            return@Column
        }
        Text(
            stringResource(R.string.per_day_a_single_price_leave_blank_to_mark) +
                stringResource(R.string.specialist_picks_real_occurrences_one_time) +
                stringResource(R.string.a_chosen_date_at_booking_time),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        operatingDays.forEach { day ->
            val dp = config.distribution[day] ?: DayPricing()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(localizedLabel(day), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                // Single flat price per day (mirrors ShiftStrategyEditor's item 7b
                // rework, same request applied to Day-Based) — occurrence count and
                // recurrence (one-time vs. weekly-recurring) are configured by the
                // specialist at booking time, not pre-set here.
                InputField(
                    value = if (dp.price == 0.0) "" else dp.price.toInt().toString(),
                    onValueChange = { v ->
                        val updated = dp.copy(price = v.toDoubleOrNull() ?: 0.0)
                        onConfigChange(config.copy(distribution = config.distribution + (day to updated)))
                    },
                    label = stringResource(R.string.price_per_day_usd),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}
