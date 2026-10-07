package com.example.data

import com.example.FakeFunctionsClient
import com.example.data.firestore.FirestoreService
import com.example.data.model.PriceChangeMode
import com.example.data.repository.ProHostRepository
import com.example.ui.util.PriceChange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The repository hands Change price to the server callable unchanged (hermetic: fake functions). */
class ChangeSlotPriceRepositoryTest {
    private val functions = FakeFunctionsClient()
    private val repo = ProHostRepository(FirestoreService.localOnly(), functions)

    @Test
    fun `sends the slot, price and per-booking decisions`() = runBlocking {
        val ref = PriceChange.SlotRef("SUB-1", PriceChange.SlotKind.SHIFT, "MORNING")
        val result = repo.changeSlotPrice(
            "SP", ref, 25.0,
            mapOf("b1" to PriceChangeMode.NOW, "b2" to PriceChangeMode.NEXT_TERM, "b3" to PriceChangeMode.KEEP)
        ).getOrThrow()
        val sent = functions.lastChangeSlotPrice!!
        assertEquals("SP", sent["spaceId"])
        assertEquals("SUB-1", sent["scopeId"])
        assertEquals("SHIFT", sent["kind"])
        assertEquals("MORNING", sent["key"])
        assertEquals(25.0, sent["newPrice"])
        assertEquals(mapOf("b1" to "NOW", "b2" to "NEXT_TERM", "b3" to "KEEP"), sent["decisions"])
        assertEquals(3, result.affected)
        assertEquals(1, result.now)
        assertEquals(1, result.nextTerm)
        assertEquals(1, result.keep)
    }

    @Test
    fun `a server refusal comes back as a failure`() = runBlocking {
        functions.changeSlotPriceError = IllegalStateException("permission-denied")
        val result = repo.changeSlotPrice("SP", PriceChange.SlotRef("SP", PriceChange.SlotKind.MONTHLY, ""), 600.0, emptyMap())
        assertTrue(result.isFailure)
    }
}
