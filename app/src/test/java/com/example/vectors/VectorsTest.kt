package com.example.vectors

import com.example.data.model.AttendeePackage
import com.example.data.model.BookingRequest
import com.example.data.model.Level2Type
import com.example.data.model.RentalFormula
import com.example.data.model.RentalFormulaType
import com.example.data.model.RentalPricingConfig
import com.example.data.model.RentalStrategyType
import com.example.data.model.Subdivision
import com.example.data.model.SubdivisionPricingMode
import com.example.ui.util.SpaceCalculationUtils
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Runs the shared test vectors in the JSON files under tests/vectors (generated from the TypeScript implementation by
 * scripts/migration/export_vectors.js; functions/src/lib/vectors.test.ts runs the same files). Kotlin and TypeScript
 * must agree. A case with `kotlinExpected` is a KNOWN divergence recorded in docs/migration/DECISIONS.md: this test
 * pins today's Kotlin behaviour and is not a licence to change it (owner rule: no behaviour change in the migration).
 */
class VectorsTest {
    private fun load(name: String): JsonObject {
        // Gradle runs unit tests with the module (app/) as the working directory; the vectors live at the repo root.
        val file = listOf("../tests/vectors/$name", "tests/vectors/$name").map(::File).first { it.exists() }
        return Json.parseToJsonElement(file.readText()).jsonObject
    }

    private fun JsonElement.toAny(): Any? = when (this) {
        is JsonNull -> null
        is JsonObject -> mapValues { it.value.toAny() }
        is JsonArray -> map { it.toAny() }
        is JsonPrimitive -> if (isString) content else booleanOrNull ?: doubleOrNull ?: content
    }

    @Suppress("UNCHECKED_CAST")
    private fun JsonElement.toMap(): Map<String, Any?> = toAny() as Map<String, Any?>

    private fun JsonObject.string(key: String): String? = this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

    // ------------------------------------------------------------------ booking terms

    private fun booking(input: JsonObject) = BookingRequest(
        spaceId = "SP", spaceTitle = "T", ownerId = "o", ownerName = "o", practitionerId = "p", practitionerName = "p",
        formula = RentalFormula(type = RentalFormulaType.FULL_MONTH, rateUsd = 0.0, scheduleDescription = ""),
        startDate = input.string("startDate") ?: "",
        durationMonths = input["durationMonths"]?.jsonPrimitive?.int ?: 1,
        selectedCalendarDates = input["selectedCalendarDates"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    )

    @Test
    fun `booking terms match the vectors`() {
        val cases = load("booking-terms.json")["term"]!!.jsonArray
        assertEquals(true, cases.size >= 15)
        for (c in cases) {
            val o = c.jsonObject
            val expected = (o["kotlinExpected"] ?: o["expected"])!!
            val actual = SpaceCalculationUtils.bookingTerm(booking(o["input"]!!.jsonObject))
            if (expected is JsonNull) {
                assertNull("${o.string("name")}", actual)
            } else {
                assertNotNull("${o.string("name")}", actual)
                assertEquals("${o.string("name")} start", expected.jsonObject.string("start"), actual!!.first)
                assertEquals("${o.string("name")} end", expected.jsonObject.string("end"), actual.second)
            }
        }
    }

    @Test
    fun `hasEnded and termsOverlap match the vectors`() {
        val file = load("booking-terms.json")
        for (c in file["hasEnded"]!!.jsonArray) {
            val o = c.jsonObject
            assertEquals(
                o.string("name"), o["expected"]!!.jsonPrimitive.boolean,
                SpaceCalculationUtils.hasEnded(booking(o["input"]!!.jsonObject), o.string("today")!!)
            )
        }
        for (c in file["termsOverlap"]!!.jsonArray) {
            val o = c.jsonObject
            assertEquals(
                o.string("name"), o["expected"]!!.jsonPrimitive.boolean,
                SpaceCalculationUtils.termsOverlap(booking(o["a"]!!.jsonObject), booking(o["b"]!!.jsonObject))
            )
        }
    }

    // ------------------------------------------------------------------ listing prices

    private val unitLabels = mapOf("hour" to "/hr", "shift" to "/shift", "day" to "/day", "month" to "/mo", "person" to "/person")

    @Test
    fun `lowest price per pricing config matches the vectors`() {
        for (c in load("listing-prices.json")["lowestPriceFor"]!!.jsonArray) {
            val o = c.jsonObject
            val actual = SpaceCalculationUtils.lowestPriceFor(RentalPricingConfig.fromFirestoreMap(o["pricing"]!!.toMap()))
            val expected = o["expected"]!!
            if (expected is JsonNull) {
                assertNull(o.string("name"), actual)
            } else {
                assertNotNull(o.string("name"), actual)
                assertEquals("${o.string("name")} amount", expected.jsonObject["amount"]!!.jsonPrimitive.double, actual!!.amount, 0.0001)
                assertEquals("${o.string("name")} unit", unitLabels[expected.jsonObject.string("unit")], actual.unitLabel)
            }
        }
    }

    @Test
    fun `lowest room price matches the vectors`() {
        for (c in load("listing-prices.json")["roomPrice"]!!.jsonArray) {
            val o = c.jsonObject
            val subJson = o["sub"]!!.jsonObject
            val sub = Subdivision(
                name = "room", type = Level2Type.ROOMS,
                pricing = RentalPricingConfig.fromFirestoreMap(subJson["pricing"]!!.toMap()),
                pricingMode = SubdivisionPricingMode.valueOf(subJson.string("pricingMode")!!),
                attendeeTiers = subJson["attendeeTiers"]?.jsonArray?.map { t ->
                    val tj = t.jsonObject
                    AttendeePackage(
                        id = tj.string("id")!!, name = tj.string("name")!!,
                        pricePerAttendeeUsd = tj["pricePerAttendeeUsd"]!!.jsonPrimitive.double,
                        minAttendees = tj["minAttendees"]?.jsonPrimitive?.int ?: 1,
                        isEnabled = tj["isEnabled"]?.jsonPrimitive?.boolean ?: true
                    )
                } ?: emptyList()
            )
            val actual = SpaceCalculationUtils.findLowestPriceForSubdivision(sub)
            val expected = o["expected"]!!.jsonObject
            assertEquals("${o.string("name")} amount", expected["amount"]!!.jsonPrimitive.double, actual.amount, 0.0001)
            assertEquals("${o.string("name")} unit", unitLabels[expected.string("unit")], actual.unitLabel)
        }
    }

    // ------------------------------------------------------------------ legacy pricing

    private fun digest(p: RentalPricingConfig): Map<String, Any?> = when (p.strategyType) {
        RentalStrategyType.HOURLY -> mapOf("strategyType" to "HOURLY", "cellPrices" to p.hourly!!.cellPrices.toSortedMap())
        RentalStrategyType.SHIFT_BASED -> mapOf(
            "strategyType" to "SHIFT_BASED",
            "shifts" to p.shiftBased!!.shifts.map { listOf(it.name.name, it.isUnavailable, it.price, it.startHour.toDouble(), it.endHour.toDouble()) },
            "distribution" to p.shiftBased!!.distribution.toSortedMap()
        )
        RentalStrategyType.DAY_BASED -> mapOf(
            "strategyType" to "DAY_BASED",
            "useFacilityHours" to p.dayBased!!.useFacilityHours,
            "customStartHour" to p.dayBased!!.customStartHour?.toDouble(),
            "customEndHour" to p.dayBased!!.customEndHour?.toDouble(),
            "distribution" to p.dayBased!!.distribution.mapValues { it.value.price }.toSortedMap()
        )
        RentalStrategyType.MONTHLY -> mapOf("strategyType" to "MONTHLY", "rateUsd" to p.monthly!!.rateUsd)
    }

    @Test
    fun `legacy formulas become the same structured pricing as the server`() {
        for (c in load("legacy-pricing.json")["cases"]!!.jsonArray) {
            val o = c.jsonObject
            val formula = o["formula"]!!.takeIf { it !is JsonNull }?.jsonObject?.let { f ->
                RentalFormula(
                    type = RentalFormulaType.valueOf(f.string("type")!!),
                    rateUsd = f["rateUsd"]!!.jsonPrimitive.double,
                    scheduleDescription = "",
                    daysOfWeek = f["daysOfWeek"]!!.jsonArray.map { it.jsonPrimitive.content },
                    startHour = f.string("startHour")!!,
                    endHour = f.string("endHour")!!
                )
            }
            val actual = digest(RentalPricingConfig.fromLegacyFormula(formula))
            assertEquals(o.string("name"), o["expected"]!!.toMap(), actual)
        }
    }
}
