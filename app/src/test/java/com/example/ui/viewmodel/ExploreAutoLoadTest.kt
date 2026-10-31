package com.example.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreAutoLoadTest {

    @Test
    fun `no search means no background loading`() {
        assertFalse(shouldSearchMore(searchActive = false, hasMore = true, matches = 0, loaded = 100))
    }

    @Test
    fun `active search with few matches loads the next page`() {
        assertTrue(shouldSearchMore(searchActive = true, hasMore = true, matches = 0, loaded = 100))
        assertTrue(shouldSearchMore(searchActive = true, hasMore = true, matches = AUTO_LOAD_TARGET_MATCHES - 1, loaded = 200))
    }

    @Test
    fun `stops at enough matches, at the end of the catalog, or at the cap`() {
        assertFalse(shouldSearchMore(true, hasMore = true, matches = AUTO_LOAD_TARGET_MATCHES, loaded = 100))
        assertFalse(shouldSearchMore(true, hasMore = false, matches = 0, loaded = 250))
        assertFalse(shouldSearchMore(true, hasMore = true, matches = 0, loaded = AUTO_LOAD_MAX_LISTINGS))
    }

    @Test
    fun `a match on page three is reached by loading pages until it appears`() {
        // 250 listings, the only match is listing #230: pages arrive 100 at a time.
        var loaded = 100
        fun matches() = if (loaded >= 230) 1 else 0
        while (shouldSearchMore(true, hasMore = loaded < 250, matches = matches(), loaded = loaded)) {
            loaded = minOf(loaded + 100, 250)
        }
        assertTrue(matches() == 1)
    }
}
