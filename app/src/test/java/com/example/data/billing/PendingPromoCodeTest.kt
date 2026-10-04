package com.example.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingPromoCodeTest {

    @Test
    fun `codes are upper-cased and stripped to letters and digits`() {
        assertEquals("EARLY50ABC", PendingPromoCode.sanitize(" early-50 abc "))
    }

    @Test
    fun `blank or symbol-only input is no code`() {
        assertNull(PendingPromoCode.sanitize(null))
        assertNull(PendingPromoCode.sanitize("   "))
        assertNull(PendingPromoCode.sanitize("<>!-_ "))
        assertEquals("SCRIPT", PendingPromoCode.sanitize("<script>"))
    }

    @Test
    fun `a linked code is consumed once`() {
        PendingPromoCode.offer("abc123")
        assertEquals("ABC123", PendingPromoCode.consume())
        assertNull(PendingPromoCode.consume())
    }
}
