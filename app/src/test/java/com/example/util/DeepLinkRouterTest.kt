package com.example.util

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs tests/vectors/deep-links.json: the same cases the iOS host must pass, so both platforms route links identically. */
class DeepLinkRouterTest {
    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    @Test
    fun `every vector routes as recorded`() {
        val file = listOf("../tests/vectors/deep-links.json", "tests/vectors/deep-links.json").map(::File).first { it.exists() }
        val cases = Json.parseToJsonElement(file.readText()).jsonObject.getValue("cases").jsonArray
        assertTrue(cases.size >= 18)
        for (case in cases) {
            val obj = case.jsonObject
            val name = obj.str("name")
            val link = obj.getValue("link").jsonObject
            val expected = obj.getValue("expected").jsonObject
            val route = DeepLinkRouter.route(
                DeepLinkRouter.Link(link.str("scheme"), link.str("host"), link.str("path"), link.str("room"), link.str("code"))
            )
            assertEquals("$name: source", expected.str("source"), route.source)
            assertEquals("$name: analyticsTarget", expected.str("analyticsTarget"), route.analyticsTarget)
            assertEquals("$name: tab", expected.str("tab"), route.tab)
            assertEquals("$name: spaceId", expected.str("spaceId"), route.listing?.spaceId)
            assertEquals("$name: roomId", expected.str("roomId"), route.listing?.roomId)
            assertEquals("$name: emailVerified", expected.getValue("emailVerified").jsonPrimitive.boolean, route.emailVerified)
            assertEquals("$name: isRedeem", expected.getValue("isRedeem").jsonPrimitive.boolean, route.isRedeem)
            assertEquals("$name: promoCode", expected.str("promoCode"), route.promoCode)
        }
    }
}
