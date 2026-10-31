package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListingDeepLinkTest {

    @Test
    fun `listing link with a room opens that room`() {
        assertEquals(
            ListingDeepLink.Target("SPC-123", "SUB-a1"),
            ListingDeepLink.parse("https", "pro-host.tech", "/listing/SPC-123", "SUB-a1")
        )
    }

    @Test
    fun `share link without room and with a trailing slash`() {
        assertEquals(ListingDeepLink.Target("SPC-123", null), ListingDeepLink.parse("https", "pro-host.tech", "/listing/SPC-123/", null))
        assertEquals(ListingDeepLink.Target("SPC-123", null), ListingDeepLink.parse("https", "pro-host.tech", "/listing/SPC-123", "  "))
    }

    @Test
    fun `other hosts, paths and malformed ids are ignored`() {
        assertNull(ListingDeepLink.parse("https", "evil.example", "/listing/SPC-1", null))
        assertNull(ListingDeepLink.parse("http", "pro-host.tech", "/listing/SPC-1", null))
        assertNull(ListingDeepLink.parse("https", "pro-host.tech", "/get", null))
        assertNull(ListingDeepLink.parse("https", "pro-host.tech", "/listing/", null))
        assertNull(ListingDeepLink.parse("https", "pro-host.tech", "/listing/a/b", null))
        assertEquals(null, ListingDeepLink.parse("https", "pro-host.tech", "/listing/SPC-1", "x y")?.roomId)
    }
}
