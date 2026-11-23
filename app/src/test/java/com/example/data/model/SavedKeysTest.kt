package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Same cases as functions/src/users/savedKeys.test.ts. */
class SavedKeysTest {
    @Test fun roomAndWholePlaceKeys() {
        assertEquals("SPC-1#sub-2", SavedKeys.key("SPC-1", "sub-2"))
        assertEquals("SPC-1", SavedKeys.key("SPC-1", null))
        assertEquals("SPC-1", SavedKeys.listingOf("SPC-1#sub-2"))
        assertEquals("sub-2", SavedKeys.roomOf("SPC-1#sub-2"))
        assertNull(SavedKeys.roomOf("SPC-1"))
        assertNull(SavedKeys.roomOf("SPC-1#"))
    }
}
