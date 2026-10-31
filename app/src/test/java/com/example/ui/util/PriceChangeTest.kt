package com.example.ui.util

import com.example.data.model.BookingRequest
import com.example.data.model.DayBasedConfig
import com.example.data.model.DayPricing
import com.example.data.model.HourlyConfig
import com.example.data.model.MonthlyConfig
import com.example.data.model.PriceChangeMode
import com.example.data.model.RentalFormula
import com.example.data.model.RentalFormulaType
import com.example.data.model.RentalPricingConfig
import com.example.data.model.RentalStrategyType
import com.example.data.model.ShiftBasedConfig
import com.example.data.model.ShiftDefinition
import com.example.data.model.ShiftName
import com.example.ui.util.PriceChange.Reprice
import com.example.ui.util.PriceChange.SlotKind
import com.example.ui.util.PriceChange.SlotRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as functions/src/bookings/priceChange.test.ts — both sides must agree. */
class PriceChangeTest {
    private val today = "2026-10-07" // a Wednesday

    private val shiftPricing = RentalPricingConfig(
        strategyType = RentalStrategyType.SHIFT_BASED,
        shiftBased = ShiftBasedConfig(
            shifts = listOf(
                ShiftDefinition(ShiftName.MORNING, 8, 12, price = 20.0),
                ShiftDefinition(ShiftName.EVENING, 16, 20, price = 30.0)
            ),
            distribution = mapOf("Mon" to listOf("MORNING"), "Wed" to listOf("MORNING", "EVENING"), "Fri" to listOf("EVENING"))
        )
    )
    private val monthlyPricing = RentalPricingConfig(RentalStrategyType.MONTHLY, monthly = MonthlyConfig(rateUsd = 500.0))
    private val dayPricing = RentalPricingConfig(
        RentalStrategyType.DAY_BASED,
        dayBased = DayBasedConfig(distribution = mapOf("Mon" to DayPricing(50.0), "Tue" to DayPricing(60.0)))
    )
    private val hourlyPricing = RentalPricingConfig(RentalStrategyType.HOURLY, hourly = HourlyConfig(mapOf("Mon|9" to 10.0, "Mon|10" to 10.0)))

    private fun booking(
        type: RentalFormulaType, start: String = "08:00", end: String = "12:00",
        sub: String? = null, dates: List<String> = emptyList(), days: List<String> = emptyList(),
        startDate: String = "", months: Int = 1, total: Double = 0.0,
        tierId: String? = null, attendees: Int = 0
    ) = BookingRequest(
        spaceId = "SP", spaceTitle = "T", ownerId = "o", ownerName = "o", practitionerId = "p", practitionerName = "p",
        formula = RentalFormula(type = type, rateUsd = 0.0, scheduleDescription = "", daysOfWeek = days, startHour = start, endHour = end),
        startDate = startDate, durationMonths = months, totalAmountUsd = total, subdivisionId = sub,
        selectedCalendarDates = dates, selectedDays = days, selectedAttendeePackageId = tierId, attendeeCount = attendees
    )

    @Test
    fun `weekdays and next billing periods`() {
        assertEquals("Wed", PriceChange.weekdayOf(today))
        assertEquals("2026-10-12", PriceChange.nextTermStart(SlotRef("s", SlotKind.SHIFT, "MORNING"), today))
        assertEquals("2026-10-19", PriceChange.nextTermStart(SlotRef("s", SlotKind.SHIFT, "MORNING"), "2026-10-12"))
        assertEquals("2026-11-01", PriceChange.nextTermStart(SlotRef("s", SlotKind.MONTHLY, ""), today))
        assertEquals("2027-01-01", PriceChange.nextTermStart(SlotRef("s", SlotKind.MONTHLY, ""), "2026-12-15"))
    }

    @Test
    fun `slots list every offered price`() {
        val shifts = PriceChange.slotsOfPricing("SUB-1", shiftPricing)
        assertEquals(listOf("MORNING", "EVENING"), shifts.map { it.ref.key })
        assertEquals(20.0, shifts.first().price, 0.0)
        assertEquals(listOf("Mon|9", "Mon|10"), PriceChange.slotsOfPricing("SP", hourlyPricing).map { it.ref.key })
        assertEquals(listOf("Mon", "Tue"), PriceChange.slotsOfPricing("SP", dayPricing).map { it.ref.key })
        assertEquals(1, PriceChange.slotsOfPricing("SP", monthlyPricing).size)
    }

    @Test
    fun `bookings match a slot by room, kind, hours and days`() {
        val morning = SlotRef("SUB-1", SlotKind.SHIFT, "MORNING")
        val b = booking(RentalFormulaType.SHIFT, sub = "SUB-1", dates = listOf("2026-10-12", "2026-10-14"))
        assertTrue(PriceChange.bookingUses(b, "SP", shiftPricing, morning))
        assertFalse(PriceChange.bookingUses(b.copy(subdivisionId = "SUB-2"), "SP", shiftPricing, morning))
        assertFalse(PriceChange.bookingUses(b, "SP", shiftPricing, morning.copy(key = "EVENING")))
        assertTrue(PriceChange.bookingUses(booking(RentalFormulaType.FULL_MONTH), "SP", monthlyPricing, SlotRef("SP", SlotKind.MONTHLY, "")))
        assertTrue(PriceChange.bookingUses(booking(RentalFormulaType.HOURLY, "09:00", "11:00", days = listOf("Mon")), "SP", hourlyPricing, SlotRef("SP", SlotKind.HOURLY, "Mon|10")))
        assertFalse(PriceChange.bookingUses(booking(RentalFormulaType.HOURLY, "09:00", "10:00", days = listOf("Mon")), "SP", hourlyPricing, SlotRef("SP", SlotKind.HOURLY, "Mon|10")))
        assertTrue(PriceChange.bookingUses(booking(RentalFormulaType.DAY_PER_WEEK, dates = listOf("2026-10-13")), "SP", dayPricing, SlotRef("SP", SlotKind.DAY, "Tue")))
        assertFalse(PriceChange.bookingUses(booking(RentalFormulaType.DAY_PER_WEEK, dates = listOf("2026-10-13")), "SP", dayPricing, SlotRef("SP", SlotKind.DAY, "Mon")))
        assertTrue(PriceChange.bookingUses(booking(RentalFormulaType.SHIFT, sub = "R", tierId = "t1"), "SP", null, SlotRef("R", SlotKind.TIER, "t1")))
    }

    @Test
    fun `KEEP never changes the total`() {
        val b = booking(RentalFormulaType.FULL_MONTH, startDate = "2026-09-01", months = 6, total = 3000.0)
        assertEquals(Reprice(3000.0, "", 0), PriceChange.reprice(b, monthlyPricing, SlotRef("SP", SlotKind.MONTHLY, ""), 500.0, 600.0, PriceChangeMode.KEEP, today))
    }

    @Test
    fun `monthly - NOW re-prices from this month, NEXT_TERM from next month`() {
        val ref = SlotRef("SP", SlotKind.MONTHLY, "")
        val b = booking(RentalFormulaType.FULL_MONTH, startDate = "2026-09-01", months = 6, total = 3000.0)
        assertEquals(5, PriceChange.unitsFrom(b, monthlyPricing, ref, today))
        assertEquals(Reprice(3500.0, today, 5), PriceChange.reprice(b, monthlyPricing, ref, 500.0, 600.0, PriceChangeMode.NOW, today))
        assertEquals(Reprice(3400.0, "2026-11-01", 4), PriceChange.reprice(b, monthlyPricing, ref, 500.0, 600.0, PriceChangeMode.NEXT_TERM, today))
        val later = b.copy(startDate = "2027-01-01", durationMonths = 3, totalAmountUsd = 1500.0)
        assertEquals(Reprice(1350.0, "2027-01-01", 3), PriceChange.reprice(later, monthlyPricing, ref, 500.0, 450.0, PriceChangeMode.NOW, today))
        val ending = b.copy(startDate = "2026-10-01", durationMonths = 1, totalAmountUsd = 500.0)
        assertEquals(Reprice(500.0, "", 0), PriceChange.reprice(ending, monthlyPricing, ref, 500.0, 600.0, PriceChangeMode.NEXT_TERM, today))
    }

    @Test
    fun `dated shifts - NOW counts remaining dates, NEXT_TERM from next Monday`() {
        val ref = SlotRef("SUB-1", SlotKind.SHIFT, "MORNING")
        val b = booking(
            RentalFormulaType.SHIFT, sub = "SUB-1", total = 100.0,
            dates = listOf("2026-10-05", "2026-10-07", "2026-10-09", "2026-10-12", "2026-10-14")
        )
        assertEquals(Reprice(115.0, today, 3), PriceChange.reprice(b, shiftPricing, ref, 20.0, 25.0, PriceChangeMode.NOW, today))
        assertEquals(Reprice(110.0, "2026-10-12", 2), PriceChange.reprice(b, shiftPricing, ref, 20.0, 25.0, PriceChangeMode.NEXT_TERM, today))
    }

    @Test
    fun `day-based dates and price decreases never go below zero`() {
        val ref = SlotRef("SP", SlotKind.DAY, "Tue")
        val b = booking(RentalFormulaType.DAY_PER_WEEK, dates = listOf("2026-10-13", "2026-10-20"), total = 120.0)
        assertEquals(Reprice(80.0, "2026-10-13", 2), PriceChange.reprice(b, dayPricing, ref, 60.0, 40.0, PriceChangeMode.NOW, today))
        assertEquals(0.0, PriceChange.reprice(b.copy(totalAmountUsd = 10.0), dayPricing, ref, 60.0, 0.5, PriceChangeMode.NOW, today).newTotal, 0.0)
    }

    @Test
    fun `undated units - NOW re-prices, NEXT_TERM waits for renewal`() {
        val cell = SlotRef("SP", SlotKind.HOURLY, "Mon|9")
        val h = booking(RentalFormulaType.HOURLY, "09:00", "11:00", days = listOf("Mon"), startDate = "2026-10-01", total = 20.0)
        assertEquals(Reprice(22.5, today, 1), PriceChange.reprice(h, hourlyPricing, cell, 10.0, 12.5, PriceChangeMode.NOW, today))
        assertEquals(Reprice(20.0, "", 0), PriceChange.reprice(h, hourlyPricing, cell, 10.0, 12.5, PriceChangeMode.NEXT_TERM, today))
        val tier = SlotRef("R", SlotKind.TIER, "t1")
        val t = booking(RentalFormulaType.SHIFT, sub = "R", tierId = "t1", attendees = 4, startDate = "2026-10-20", total = 40.0)
        assertEquals(Reprice(48.0, "2026-10-20", 4), PriceChange.reprice(t, null, tier, 10.0, 12.0, PriceChangeMode.NOW, today))
        assertEquals(Reprice(40.0, "", 0), PriceChange.reprice(t, null, tier, 10.0, 12.0, PriceChangeMode.NEXT_TERM, today))
    }
}
