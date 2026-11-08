package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** P2.3h: Ids.uuid() replaces UUID.randomUUID().toString(): same canonical v4 form, so every derived id ("SUB-" + take(6).uppercase() ...) keeps its shape. */
class IdsTest {
    private val v4 = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

    @Test
    fun hasTheSameCanonicalVersion4FormAsJavaUtilUuid() {
        repeat(2_000) {
            val ours = Ids.uuid()
            assertTrue(ours, v4.matches(ours))
            assertTrue(UUID.randomUUID().toString(), v4.matches(UUID.randomUUID().toString()))
            assertEquals(36, ours.length)
            assertEquals(ours, ours.lowercase())
            assertEquals(4, UUID.fromString(ours).version()) // java.util.UUID accepts and agrees on the version
        }
    }

    @Test
    fun derivedIdsKeepTheirShape() {
        repeat(500) {
            val sub = "SUB-" + Ids.uuid().take(6).uppercase()
            assertTrue(sub, Regex("SUB-[0-9A-F]{6}").matches(sub))
            val req = "REQ-" + Ids.uuid().replace("-", "").take(10).uppercase()
            assertTrue(req, Regex("REQ-[0-9A-F]{10}").matches(req))
        }
    }

    @Test
    fun valuesAreUnique() {
        val seen = HashSet<String>()
        repeat(100_000) { assertTrue(seen.add(Ids.uuid())) }
    }
}
