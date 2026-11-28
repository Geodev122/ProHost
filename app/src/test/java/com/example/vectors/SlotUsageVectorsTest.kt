package com.example.vectors

import com.example.data.model.BookingRequest
import com.example.data.model.SpaceListing
import com.example.ui.util.PriceChange
import com.example.ui.util.SlotUsage
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
 * Slots a booking still uses stay on: runs tests/vectors/slot-usage.json, written by the server rule's test
 * (functions/src/listings/slotUsage.test.ts). SlotUsage.keepUsedSlots must keep exactly the same slots and closed dates.
 */
class SlotUsageVectorsTest {
    private val doc: JsonObject = listOf("../tests/vectors/slot-usage.json", "tests/vectors/slot-usage.json").map(::File).first { it.exists() }
        .let { Json.parseToJsonElement(it.readText()).jsonObject }

    private fun plain(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonObject -> e.mapValues { plain(it.value) }
        is JsonArray -> e.map(::plain)
        is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.longOrNull ?: e.doubleOrNull
    }

    @Suppress("UNCHECKED_CAST")
    private fun map(e: JsonElement) = plain(e) as Map<String, Any?>

    @Test
    fun `kept slots match the server rule`() {
        val today = doc.getValue("today").jsonPrimitive.content
        val spaceId = doc.getValue("spaceId").jsonPrimitive.content
        val cases = doc.getValue("cases").jsonArray
        assertTrue(cases.size >= 15)
        for (c in cases) {
            val case = c.jsonObject
            val name = case.getValue("name").jsonPrimitive.content
            val before = SpaceListing.fromFirestoreMap(spaceId, map(case.getValue("before")))
            val after = SpaceListing.fromFirestoreMap(spaceId, map(case.getValue("after")))
            val bookings = case.getValue("bookings").jsonArray.map { b ->
                val m = map(b)
                BookingRequest.fromFirestoreMap(m["id"] as String, m)
            }
            val expected = case.getValue("expected").jsonObject
            val result = SlotUsage.keepUsedSlots(before, after, bookings, today)
            val offered = PriceChange.slotsOfPricing(spaceId, result.listing.pricing).map { "${it.ref.kind}:${it.ref.key}" }.sorted()
            assertEquals("$name offered", expected.getValue("offered").jsonArray.map { it.jsonPrimitive.content }, offered)
            assertEquals("$name closed dates", expected.getValue("closedDates").jsonArray.map { it.jsonPrimitive.content },
                result.listing.schedule.closedDates.sorted())
            assertEquals("$name kept", expected.getValue("kept").jsonPrimitive.int, result.kept.size)
            val indefinite = expected.getValue("monthlyIndefinite")
            if (indefinite !is JsonNull) {
                assertEquals("$name monthly window", indefinite.jsonPrimitive.booleanOrNull, result.listing.pricing.monthly?.isIndefinite)
            }
        }
    }
}
