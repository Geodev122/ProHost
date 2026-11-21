package com.example.ui.components

import com.example.ui.util.UiMessages
import com.example.ui.util.localizedLabel
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
import com.example.shared.resources.Res
import com.example.shared.resources.a_chosen_date_at_booking_time
import com.example.shared.resources.add_excluded_range
import com.example.shared.resources.available_from
import com.example.shared.resources.available_to
import com.example.shared.resources.excluded_ranges_x
import com.example.shared.resources.fill_all_hours
import com.example.shared.resources.from_hr
import com.example.shared.resources.indefinite_no_end_date
import com.example.shared.resources.per_day_a_single_price_leave_blank_to_mark
import com.example.shared.resources.price_per_day_usd
import com.example.shared.resources.price_per_shift_usd
import com.example.shared.resources.rate_usd_mo
import com.example.shared.resources.remove_excluded_range
import com.example.shared.resources.specialist_picks_real_occurrences_one_time
import com.example.shared.resources.strategy_day
import com.example.shared.resources.strategy_day_hint
import com.example.shared.resources.strategy_hourly
import com.example.shared.resources.strategy_hourly_hint
import com.example.shared.resources.strategy_monthly
import com.example.shared.resources.strategy_monthly_hint
import com.example.shared.resources.strategy_shift
import com.example.shared.resources.strategy_shift_hint
import com.example.shared.resources.switch_on_each_day_this_room_is_offered
import com.example.shared.resources.t_02d_00
import com.example.shared.resources.t_1_shift_configuration
import com.example.shared.resources.t_2_shifts_distribution
import com.example.shared.resources.tap_an_hour_to_offer_it_on_that_day_untouc
import com.example.shared.resources.tap_an_hour_to_toggle_it_on_off_for_that_d
import com.example.shared.resources.to_hr
import com.example.shared.resources.unavailable
import com.example.shared.resources.usd_hr
import com.example.shared.resources.use_facility_operating_hours_x_x
import com.example.shared.resources.which_shifts_are_offered_on_which_day
import com.example.ui.util.appString
import com.example.ui.util.sharedText

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
    availabilityOnly: Boolean = false,
    // A division with live bookings keeps its rental type (hourly bookings cannot become monthly ones); prices and slots stay editable.
    strategyLocked: Boolean = false,
    // Shown between the strategy choice and its price table (the division form asks "fixed price or per person" there).
    afterStrategy: (@Composable ColumnScope.() -> Unit)? = null
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
        val choices = strategyChoices.map { type ->
            when (type) {
                RentalStrategyType.HOURLY -> Choice(type, Icons.Default.Schedule, appString(Res.string.strategy_hourly), appString(Res.string.strategy_hourly_hint))
                RentalStrategyType.SHIFT_BASED -> Choice(type, Icons.Default.AccessTime, appString(Res.string.strategy_shift), appString(Res.string.strategy_shift_hint))
                RentalStrategyType.DAY_BASED -> Choice(type, Icons.Default.CalendarToday, appString(Res.string.strategy_day), appString(Res.string.strategy_day_hint))
                RentalStrategyType.MONTHLY -> Choice(type, Icons.Default.CalendarMonth, appString(Res.string.strategy_monthly), appString(Res.string.strategy_monthly_hint))
            }
        }
        ChoiceGrid(
            choices = choices,
            selected = config.strategyType,
            onSelect = { type ->
                if (config.strategyType != type && strategyLocked) {
                    UiMessages.show(sharedText("rental_type_locked"), long = true)
                } else if (config.strategyType != type) {
                    emitConfig(
                        RentalPricingConfig(
                            strategyType = type,
                            monthly = if (type == RentalStrategyType.MONTHLY) config.monthly ?: MonthlyConfig() else null,
                            hourly = if (type == RentalStrategyType.HOURLY) config.hourly ?: HourlyConfig() else null,
                            shiftBased = if (type == RentalStrategyType.SHIFT_BASED) config.shiftBased ?: ShiftBasedConfig() else null,
                            dayBased = if (type == RentalStrategyType.DAY_BASED) config.dayBased ?: DayBasedConfig() else null,
                            bookingHorizonMonths = config.bookingHorizonMonths
                        )
                    )
                }
            }
        )
        afterStrategy?.invoke(this)

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
    val today = remember { com.example.ui.util.IsoDate.todayDate() }
    val currentYear = today.year
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        val rateText = rememberPriceText(config.rateUsd)
        InputField(
            value = rateText.value,
            onValueChange = { val clean = cleanPrice(it); rateText.value = clean; onConfigChange(config.copy(rateUsd = clean.toDoubleOrNull() ?: 0.0)) },
            label = appString(Res.string.rate_usd_mo),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(appString(Res.string.available_from), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
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
            Text(appString(Res.string.indefinite_no_end_date), style = MaterialTheme.typography.bodySmall)
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
            Text(appString(Res.string.available_to), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
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
            Text(appString(Res.string.excluded_ranges_x, config.excludedRanges.size), style = MaterialTheme.typography.bodySmall)
            TextButton(shape = MaterialTheme.shapes.medium, onClick = {
                onConfigChange(
                    config.copy(
                        excludedRanges = config.excludedRanges + MonthYearRange(
                            today.monthNumber, currentYear, today.monthNumber, currentYear
                        )
                    )
                )
            }) { Text(appString(Res.string.add_excluded_range)) }
        }
        config.excludedRanges.forEachIndexed { idx, range ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${monthNames[range.fromMonth - 1]} ${range.fromYear} – ${monthNames[range.toMonth - 1]} ${range.toYear}",
                    style = MaterialTheme.typography.labelSmall
                )
                IconButton(onClick = { onConfigChange(config.copy(excludedRanges = config.excludedRanges.filterIndexed { i, _ -> i != idx })) }) {
                    Icon(Icons.Default.Delete, contentDescription = appString(Res.string.remove_excluded_range), tint = MaterialTheme.colorScheme.error)
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
            if (availabilityOnly) appString(Res.string.tap_an_hour_to_offer_it_on_that_day_untouc)
            else appString(Res.string.tap_an_hour_to_toggle_it_on_off_for_that_d),
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
     label = appString(Res.string.usd_hr),
     modifier = Modifier.width(110.dp),
     singleLine = true
 )
                    TextButton(shape = MaterialTheme.shapes.medium, onClick = {
                        val price = if (availabilityOnly) marker else bulkPriceByDay[day]?.toDoubleOrNull() ?: return@TextButton
                        onConfigChange(config.copy(cellPrices = config.cellPrices + hours.associate { "$day|$it" to price }))
                    }) { Text(appString(Res.string.fill_all_hours)) }
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
                            label = { Text(com.example.util.formatTwoDigits(appString(Res.string.t_02d_00), hour), style = MaterialTheme.typography.labelSmall) }
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
        Text(appString(Res.string.t_1_shift_configuration), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        config.shifts.forEach { shift ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(localizedLabel(shift.name.displayName), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(appString(Res.string.unavailable), style = MaterialTheme.typography.labelSmall)
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
                            label = appString(Res.string.from_hr),
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
                            label = appString(Res.string.to_hr),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    // Single flat price per shift (item 7b) — the specialist
                    // configures occurrences (which dates, how many) at booking
                    // time instead of picking one of 3 pre-set commitment tiers.
                    val shiftPriceText = rememberPriceText(shift.price, shift.name)
                    if (!availabilityOnly) InputField(
     value = shiftPriceText.value,
     onValueChange = { raw ->
                            val v = cleanPrice(raw)
                            shiftPriceText.value = v
                            onConfigChange(
                                config.copy(
                                    shifts = config.shifts.map {
                                        if (it.name == shift.name) it.copy(price = v.toDoubleOrNull() ?: 0.0) else it
                                    }
                                )
                            )
                        },
     label = appString(Res.string.price_per_shift_usd),
     modifier = Modifier.fillMaxWidth(),
     singleLine = true
 )
                }
                HorizontalDivider()
            }
        }

        Text(appString(Res.string.t_2_shifts_distribution), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            appString(Res.string.which_shifts_are_offered_on_which_day),
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
            Text(appString(Res.string.use_facility_operating_hours_x_x, openingHour, closingHour), style = MaterialTheme.typography.bodySmall)
            Switch(checked = config.useFacilityHours, onCheckedChange = { onConfigChange(config.copy(useFacilityHours = it)) })
        }
        if (!config.useFacilityHours) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                InputField(
                    value = (config.customStartHour ?: parseHourInt(openingHour)).toString(),
                    onValueChange = { v -> onConfigChange(config.copy(customStartHour = v.toIntOrNull())) },
                    label = appString(Res.string.from_hr),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                InputField(
                    value = (config.customEndHour ?: parseHourInt(closingHour)).toString(),
                    onValueChange = { v -> onConfigChange(config.copy(customEndHour = v.toIntOrNull())) },
                    label = appString(Res.string.to_hr),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }

        if (availabilityOnly) {
            Text(
                appString(Res.string.switch_on_each_day_this_room_is_offered),
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
            appString(Res.string.per_day_a_single_price_leave_blank_to_mark) +
                appString(Res.string.specialist_picks_real_occurrences_one_time) +
                appString(Res.string.a_chosen_date_at_booking_time),
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
                val dayPriceText = rememberPriceText(dp.price, day)
                InputField(
                    value = dayPriceText.value,
                    onValueChange = { raw ->
                        val v = cleanPrice(raw)
                        dayPriceText.value = v
                        val updated = dp.copy(price = v.toDoubleOrNull() ?: 0.0)
                        onConfigChange(config.copy(distribution = config.distribution + (day to updated)))
                    },
                    label = appString(Res.string.price_per_day_usd),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}


/**
 * Text of a price field. Kept as text so decimals can be typed ("12.5"): deriving it from the number on every
 * change dropped the decimal point. Follows the number when it changes from outside.
 */
@Composable
private fun rememberPriceText(value: Double, key: Any? = null): MutableState<String> {
    val state = remember(key) { mutableStateOf(if (value == 0.0) "" else com.example.util.NumberFormats.plainAmount(value)) }
    if ((state.value.toDoubleOrNull() ?: 0.0) != value) {
        state.value = if (value == 0.0) "" else com.example.util.NumberFormats.plainAmount(value)
    }
    return state
}

/** Digits and one decimal point, at most two decimals. */
private fun cleanPrice(raw: String): String {
    val filtered = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
    val dot = filtered.indexOf('.')
    return if (dot < 0) filtered else filtered.substring(0, dot + 1) + filtered.substring(dot + 1).replace(".", "").take(2)
}
