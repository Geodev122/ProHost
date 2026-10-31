package com.example.ui.util

import com.example.data.model.AttendeePackage
import com.example.data.model.Level2Type
import com.example.data.model.RentalPricingConfig
import com.example.data.model.RentalStrategyType
import com.example.data.model.ShiftBasedConfig
import com.example.data.model.Subdivision
import com.example.data.model.SubdivisionPricingMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A per-attendee conference room offered by shifts used to be unsaveable (shift prices stayed 0). */
class AttendeePricingShiftTest {
    private val shiftsPickedAfterSwitching = RentalPricingConfig(
        strategyType = RentalStrategyType.SHIFT_BASED,
        // Fresh ShiftBasedConfig(): every shift priced 0.0 — what the strategy chip used to create.
        shiftBased = ShiftBasedConfig().let { it.copy(distribution = mapOf("Mon" to listOf(it.shifts.first().name.name))) }
    )
    private val room = Subdivision(
        name = "Conference A",
        type = Level2Type.CONFERENCE_ROOM,
        pricing = shiftsPickedAfterSwitching,
        pricingMode = SubdivisionPricingMode.PER_ATTENDEE,
        attendeeTiers = listOf(AttendeePackage(id = "T1", name = "Standard", pricePerAttendeeUsd = 8.0, minAttendees = 1, maxAttendees = 20))
    )

    @Test
    fun `unmarked shifts never pass, marked shifts do`() {
        assertFalse(AttendeePricing.isConfigured(room))
        assertTrue(AttendeePricing.isConfigured(room.copy(pricing = AttendeePricing.markShifts(room.pricing))))
    }

    @Test
    fun `marking needs an offered shift on some day`() {
        val noDays = room.copy(pricing = AttendeePricing.markShifts(room.pricing.copy(shiftBased = ShiftBasedConfig())))
        assertFalse(AttendeePricing.isConfigured(noDays))
    }
}
