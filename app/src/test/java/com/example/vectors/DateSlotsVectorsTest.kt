package com.example.vectors

import com.example.data.model.BookingRequest
import com.example.data.model.RentalPricingConfig
import com.example.data.model.SpaceOperatingSchedule
import com.example.ui.util.DateSlots
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Booking on real dates: runs tests/vectors/date-slots.json, written by the server engine's test
 * (functions/src/bookings/dateSlots.test.ts). The Kotlin engine must give the same day views, monthly terms and messages.
 */
class DateSlotsVectorsTest {
    private val doc: JsonObject = listOf("../tests/vectors/date-slots.json", "tests/vectors/date-slots.json").map(::File).first { it.exists() }
        .let { Json.parseToJsonElement(it.readText()).jsonObject }

    /** JSON → the Map/List/primitive shapes Firestore hands the model mappers. */
    private fun plain(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonObject -> e.mapValues { plain(it.value) }
        is JsonArray -> e.map(::plain)
        is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.longOrNull ?: e.doubleOrNull
    }

    @Suppress("UNCHECKED_CAST")
    private fun map(e: JsonElement) = plain(e) as Map<String, Any?>
    private fun bookings(key: String) = doc.getValue(key).jsonArray.mapIndexed { i, b -> BookingRequest.fromFirestoreMap("b$i", map(b)) }
    private val today = doc.getValue("today").jsonPrimitive.content
    private val schedule = SpaceOperatingSchedule.fromFirestoreMap(map(doc.getValue("schedule")))!!

    @Test
    fun `every date offers what the server says`() {
        val rooms = doc.getValue("rooms").jsonObject.mapValues { RentalPricingConfig.fromFirestoreMap(map(it.value)) }
        val closed = doc.getValue("closedDates").jsonArray.map { it.jsonPrimitive.content }
        val held = bookings("bookings")
        val cases = doc.getValue("dayCases").jsonArray
        assertTrue(cases.size >= 30)
        for (c in cases) {
            val case = c.jsonObject
            val room = case.getValue("room").jsonPrimitive.content
            val date = case.getValue("date").jsonPrimitive.content
            val expected = case.getValue("expected").jsonObject
            val view = DateSlots.dayView(date, today, rooms.getValue(room), schedule, closed, held)
            assertEquals("$room $date state", expected.getValue("state").jsonPrimitive.content, view.state.name.lowercase())
            val slots = expected.getValue("slots").jsonArray.map { it.jsonObject }
            assertEquals("$room $date slot count", slots.size, view.slots.size)
            slots.zip(view.slots).forEach { (e, s) ->
                val at = "$room $date ${s.key}"
                assertEquals(at, e.getValue("key").jsonPrimitive.content, s.key)
                assertEquals(at, e.getValue("startHour").jsonPrimitive.int, s.startHour)
                assertEquals(at, e.getValue("endHour").jsonPrimitive.int, s.endHour)
                assertEquals(at, (e["shiftName"] as? JsonPrimitive)?.takeIf { it.isString }?.content, s.shiftName)
                assertEquals(at, e.getValue("priceUsd").jsonPrimitive.doubleOrNull!!, s.priceUsd, 0.0001)
                assertEquals(at, e.getValue("taken").jsonPrimitive.booleanOrNull, s.taken)
            }
        }
    }

    @Test
    fun `monthly terms and problems match the server`() {
        val pricing = RentalPricingConfig.fromFirestoreMap(map(doc.getValue("monthly")))
        val held = bookings("monthlyBookings")
        for (c in doc.getValue("monthlyCases").jsonArray) {
            val case = c.jsonObject
            val start = case.getValue("start").jsonPrimitive.content
            val months = case.getValue("months").jsonPrimitive.int
            val term = case.getValue("term").jsonObject
            assertEquals("$start +$months term", term.getValue("start").jsonPrimitive.content to term.getValue("end").jsonPrimitive.content, DateSlots.monthlyTerm(start, months))
            assertEquals("$start +$months problem", (case["problem"] as? JsonPrimitive)?.takeIf { it.isString }?.content, DateSlots.monthlyProblem(start, months, today, pricing, held))
            assertEquals("$start max months", case.getValue("maxMonths").jsonPrimitive.int, DateSlots.maxMonthlyMonths(start, today, pricing, held))
        }
    }

    @Test
    fun `new fields are written only when set and read back`() {
        val plainBooking = BookingRequest.fromFirestoreMap("x", mapOf("spaceId" to "s"))
        assertTrue("dateModel" !in plainBooking.toFirestoreMap() && "bookedSlots" !in plainBooking.toFirestoreMap())
        val dated = plainBooking.copy(dateModel = 2, bookedSlots = listOf(com.example.data.model.BookedSlot("2026-11-17", "10", 10, 11, null, 12.0)))
        assertEquals(dated.bookedSlots, BookingRequest.fromFirestoreMap("x", dated.toFirestoreMap()).bookedSlots)
        assertEquals(2, BookingRequest.fromFirestoreMap("x", dated.toFirestoreMap()).dateModel)
        assertTrue("closedDates" !in SpaceOperatingSchedule().toFirestoreMap())
        val closed = SpaceOperatingSchedule(closedDates = listOf("2026-12-25"))
        assertEquals(closed.closedDates, SpaceOperatingSchedule.fromFirestoreMap(closed.toFirestoreMap())!!.closedDates)
        val pricing = RentalPricingConfig.fromFirestoreMap(map(doc.getValue("monthly")))
        assertTrue("bookingHorizonMonths" !in pricing.toFirestoreMap())
        assertEquals(2, RentalPricingConfig.fromFirestoreMap(pricing.copy(bookingHorizonMonths = 2).toFirestoreMap()).bookingHorizonMonths)
    }
}
