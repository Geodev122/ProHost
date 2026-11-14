package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BookedSlot
import com.example.data.model.BookingRequest
import com.example.data.model.RentalPricingConfig
import com.example.data.model.RentalStrategyType
import com.example.data.model.SpaceOperatingSchedule
import com.example.ui.theme.Radius
import com.example.ui.theme.Spacing
import com.example.ui.theme.proColors
import com.example.ui.util.DateSlots
import com.example.ui.util.IsoDate
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.util.localizedLabel
import com.example.util.NumberFormats
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.LocalDate

/**
 * Booking on real dates (owner decision 14 Nov 2026, approved mockups docs/migration/mockups/dated-booking.html): the one
 * picker the availability sheet and Re-book / Edit share. The professional picks calendar dates first, then the free times
 * of each date ([DateSlotPicker]); a monthly room asks for a start date and a number of months ([MonthlyPeriodPicker]).
 * What a date offers comes only from [DateSlots] (shared with the server), never from this file.
 */

/** The app's language for date texts (the per-app language, not the device's). */
@Composable
private fun appLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

private fun LocalDate.toJava(): java.time.LocalDate = java.time.LocalDate.of(year, monthNumber, dayOfMonth)

/** "Tue 17 Nov" in the app's language. */
fun datedLabel(iso: String, locale: Locale): String =
    IsoDate.parse(iso)?.toJava()?.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)) ?: iso

/** "14 Dec 2026" in the app's language. */
fun datedLongLabel(iso: String, locale: Locale): String =
    IsoDate.parse(iso)?.toJava()?.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)) ?: iso

private fun hourLabel(hour: Int): String = hour.toString().padStart(2, '0') + ":00"

/** Slots of [selection] as the dated slots a request carries, priced from what each date offers today. */
fun datedSlotsOf(
    selection: Map<String, Set<String>>, today: String, pricing: RentalPricingConfig, schedule: SpaceOperatingSchedule,
    closedDates: Collection<String>, bookings: List<BookingRequest>,
): List<BookedSlot> = selection.toSortedMap().flatMap { (date, keys) ->
    DateSlots.dayView(date, today, pricing, schedule, closedDates, bookings).slots
        .filter { it.key in keys && !it.taken }
        .map { BookedSlot(date, it.key, it.startHour, it.endHour, it.shiftName, it.priceUsd) }
}

private enum class CellKind { OFF, FREE, FULL, CLOSED }

/** One month of day cells, Sunday first. [kindOf] decides how a date looks and whether it can be tapped. */
@Composable
private fun MonthCalendar(
    month: LocalDate,
    onMonthChange: (LocalDate) -> Unit,
    canGoBack: Boolean,
    selected: Set<String>,
    rangeStart: String? = null,
    rangeEndExclusive: String? = null,
    today: String,
    kindOf: (String) -> CellKind,
    onTap: (String) -> Unit,
) {
    val locale = appLocale()
    val first = month.toJava()
    val daysInMonth = first.lengthOfMonth()
    val leading = first.dayOfWeek.value % 7 // Sunday = 0
    Surface(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonthChange(IsoDate.plusMonths(month, -1)) }, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
                }
                Text(
                    first.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onMonthChange(IsoDate.plusMonths(month, 1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                // Sunday first, as in the approved mockup.
                listOf(7, 1, 2, 3, 4, 5, 6).forEach { dow ->
                    Text(
                        java.time.DayOfWeek.of(dow).getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f)
                    )
                }
            }
            val cells = leading + daysInMonth
            for (week in 0 until (cells + 6) / 7) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val day = week * 7 + col - leading + 1
                        if (day < 1 || day > daysInMonth) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val iso = LocalDate(month.year, month.monthNumber, day).toString()
                            val kind = kindOf(iso)
                            val isSelected = iso in selected
                            val inRange = rangeStart != null && rangeEndExclusive != null && iso > rangeStart && iso < rangeEndExclusive
                            DayCell(
                                day = day, kind = kind, isSelected = isSelected, inRange = inRange, isToday = iso == today,
                                onClick = { onTap(iso) }, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, kind: CellKind, isSelected: Boolean, inRange: Boolean, isToday: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val enabled = kind == CellKind.FREE || isSelected
    val container = when {
        isSelected -> scheme.primary
        inRange -> scheme.primaryContainer
        kind == CellKind.CLOSED -> scheme.surfaceVariant
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val content = when {
        isSelected -> scheme.onPrimary
        inRange -> scheme.onPrimaryContainer
        kind == CellKind.FREE -> scheme.onSurface
        else -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(container)
            .then(if (isToday && !isSelected) Modifier.background(scheme.primary.copy(alpha = 0.08f)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = content,
                textDecoration = if (kind == CellKind.FULL && !isSelected) TextDecoration.LineThrough else null
            )
            // A dot marks a date that still has free times.
            Box(
                Modifier.size(4.dp).clip(CircleShape)
                    .background(if (kind == CellKind.FREE && !isSelected && !inRange) MaterialTheme.proColors.success else androidx.compose.ui.graphics.Color.Transparent)
            )
        }
    }
}

@Composable
private fun CalendarLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(MaterialTheme.proColors.success, stringResource(R.string.calendar_legend_free))
        LegendDot(MaterialTheme.colorScheme.surfaceVariant, stringResource(R.string.calendar_legend_closed))
        Text(
            stringResource(R.string.calendar_legend_full), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough
        )
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Hourly, shift and day rooms: a month calendar, then one card per chosen date with that date's times.
 * [selection] maps a date ("yyyy-MM-dd") to the slot keys picked on it; a date with an empty set is chosen but has no time yet.
 * [nowHour] hides today's times that have already started. Slot prices are hidden for per-attendee rooms ([showPrices] false).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateSlotPicker(
    pricing: RentalPricingConfig,
    schedule: SpaceOperatingSchedule,
    closedDates: Collection<String>,
    bookings: List<BookingRequest>,
    selection: Map<String, Set<String>>,
    onSelectionChange: (Map<String, Set<String>>) -> Unit,
    showPrices: Boolean = true,
    today: String = IsoDate.todayIso(),
    nowHour: Int = java.time.LocalTime.now().hour,
    modifier: Modifier = Modifier,
) {
    val locale = appLocale()
    val todayDate = remember(today) { IsoDate.parse(today) ?: IsoDate.todayDate() }
    var month by remember(today) { mutableStateOf(IsoDate.firstOfMonth(todayDate)) }

    fun view(date: String): DateSlots.DayView {
        val v = DateSlots.dayView(date, today, pricing, schedule, closedDates, bookings)
        // Today: times that have already started are no longer offered.
        return if (date == today) v.copy(slots = v.slots.filter { it.startHour > nowHour }) else v
    }
    val views = remember(month, today, nowHour, pricing, schedule, closedDates, bookings) {
        val days = month.toJava().lengthOfMonth()
        (1..days).associate { d -> LocalDate(month.year, month.monthNumber, d).toString().let { it to view(it) } }
    }
    fun kind(date: String): CellKind {
        val v = views[date] ?: view(date)
        return when {
            v.state == DateSlots.DayState.CLOSED -> CellKind.CLOSED
            v.state != DateSlots.DayState.OPEN || v.slots.isEmpty() -> CellKind.OFF
            v.slots.all { it.taken } -> CellKind.FULL
            else -> CellKind.FREE
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        MonthCalendar(
            month = month,
            onMonthChange = { month = it },
            canGoBack = month > IsoDate.firstOfMonth(todayDate),
            selected = selection.keys,
            today = today,
            kindOf = ::kind,
            onTap = { date ->
                if (date in selection) {
                    onSelectionChange(selection - date)
                } else {
                    // A day room has one slot per date: tapping the date is the choice.
                    val free = view(date).slots.filter { !it.taken }
                    val auto = if (pricing.strategyType == RentalStrategyType.DAY_BASED) free.map { it.key }.toSet() else emptySet()
                    if (selection.size < DateSlots.MAX_DATES_PER_REQUEST) onSelectionChange(selection + (date to auto))
                }
            }
        )
        CalendarLegend()
        if (views.values.none { v -> v.state == DateSlots.DayState.OPEN && v.slots.any { !it.taken } }) {
            Text(stringResource(R.string.no_free_dates_this_month), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (selection.isEmpty()) {
            Text(stringResource(R.string.tap_a_date_to_see_its_times), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val unit = SpaceCalculationUtils.strategyUnitLabel(pricing.strategyType)
        selection.keys.sorted().forEach { date ->
            val slots = view(date).slots
            val picked = selection[date].orEmpty()
            Surface(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(datedLabel(date, locale), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onSelectionChange(selection - date) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_date), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    fun toggle(key: String) = onSelectionChange(selection + (date to (if (key in picked) picked - key else picked + key)))
                    if (pricing.strategyType == RentalStrategyType.HOURLY) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            slots.forEach { slot ->
                                FilterChip(
                                    selected = slot.key in picked,
                                    enabled = !slot.taken,
                                    onClick = { toggle(slot.key) },
                                    label = {
                                        Text(
                                            if (showPrices) "${hourLabel(slot.startHour)} · ${NumberFormats.usdAuto(slot.priceUsd)}" else hourLabel(slot.startHour),
                                            textDecoration = if (slot.taken) TextDecoration.LineThrough else null
                                        )
                                    }
                                )
                            }
                        }
                    } else {
                        slots.forEach { slot ->
                            val isPicked = slot.key in picked
                            val name = slot.shiftName?.let { n -> com.example.data.model.ShiftName.entries.firstOrNull { it.name == n }?.displayName?.let { localizedLabel(it) } }
                            val hours = stringResource(R.string.x_to_x_time, hourLabel(slot.startHour), hourLabel(slot.endHour))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (isPicked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                    .clickable(enabled = !slot.taken, role = Role.Checkbox) { toggle(slot.key) }
                            ) {
                                Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (name != null) "$name · $hours" else stringResource(R.string.whole_day_x_to_x, hourLabel(slot.startHour), hourLabel(slot.endHour)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (slot.taken) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        when {
                                            slot.taken -> stringResource(R.string.slot_taken)
                                            showPrices -> NumberFormats.usdAuto(slot.priceUsd) + unit
                                            else -> ""
                                        },
                                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                                        color = if (slot.taken) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    if (slots.isEmpty() && date == today) {
                        Text(stringResource(R.string.this_time_has_passed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Monthly rooms: any start date, whole months. Dates whose first month would hit a booked or excluded period can't be
 * picked, and the months stepper stops where availability ends ([DateSlots.maxMonthlyMonths]).
 */
@Composable
fun MonthlyPeriodPicker(
    pricing: RentalPricingConfig,
    bookings: List<BookingRequest>,
    start: String?,
    months: Int,
    onChange: (start: String?, months: Int) -> Unit,
    today: String = IsoDate.todayIso(),
    modifier: Modifier = Modifier,
) {
    val locale = appLocale()
    val todayDate = remember(today) { IsoDate.parse(today) ?: IsoDate.todayDate() }
    var month by remember(today) { mutableStateOf(IsoDate.firstOfMonth(todayDate)) }
    val startable = remember(month, today, pricing, bookings) {
        val days = month.toJava().lengthOfMonth()
        (1..days).map { LocalDate(month.year, month.monthNumber, it).toString() }
            .filter { DateSlots.monthlyProblem(it, 1, today, pricing, bookings) == null }.toSet()
    }
    val maxMonths = remember(start, today, pricing, bookings) { start?.let { DateSlots.maxMonthlyMonths(it, today, pricing, bookings) } ?: 0 }
    val term = start?.let { DateSlots.monthlyTerm(it, months) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(stringResource(R.string.when_do_you_start), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        MonthCalendar(
            month = month,
            onMonthChange = { month = it },
            canGoBack = month > IsoDate.firstOfMonth(todayDate),
            selected = setOfNotNull(start),
            rangeStart = term?.first,
            rangeEndExclusive = term?.second,
            today = today,
            kindOf = { if (it in startable) CellKind.FREE else CellKind.OFF },
            onTap = { date ->
                if (date == start) onChange(null, 1)
                else onChange(date, months.coerceIn(1, DateSlots.maxMonthlyMonths(date, today, pricing, bookings).coerceAtLeast(1)))
            }
        )
        if (start != null && term != null) {
            Surface(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.how_long), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onChange(start, months - 1) }, enabled = months > 1) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.fewer_months))
                    }
                    Text(pluralStringResource(R.plurals.n_months, months, months), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onChange(start, months + 1) }, enabled = months < maxMonths) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.more_months))
                    }
                }
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        // The last day is the day before the exclusive end.
                        stringResource(R.string.period_x_to_x, datedLongLabel(term.first, locale), datedLongLabel(IsoDate.addDays(term.second, -1) ?: term.second, locale)),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        stringResource(R.string.x_per_month_times_x, NumberFormats.usdAuto(pricing.monthly?.rateUsd ?: 0.0), months),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
