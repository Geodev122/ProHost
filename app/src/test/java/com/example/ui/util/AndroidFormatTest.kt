package com.example.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** [formatAndroidTemplate] (used by the web console's shared strings) must equal Android's `String.format` for our patterns. */
class AndroidFormatTest {
    private fun same(template: String, vararg args: Any?) =
        assertEquals(template, String.format(Locale.US, template, *args), formatAndroidTemplate(template, args))

    @Test fun positional() {
        same("%1\$s of %2\$s", "a", "b")
        same("%2\$s before %1\$s", "a", "b")
        same("%1\$d listings, %1\$d left", 3)
        same("%1\$s, %2\$d, %3\$s and %4\$s", "x", 7, "y", "z")
    }

    @Test fun sequentialAndPadding() {
        same("%s and %s", "one", "two")
        same("%d items", 12)
        same("%02d:%02d", 9, 5)
    }

    @Test fun percentAndPlainText() {
        same("Save %1\$s%%", "20")
        same("100%% sure, %s", "yes")
        same("No placeholders here", "unused")
    }

    @Test fun noArgumentsReturnsRawText() {
        assertEquals("Save 20%%", formatAndroidTemplate("Save 20%%", emptyArray()))
        assertEquals("%1\$s left", formatAndroidTemplate("%1\$s left", emptyArray()))
    }
}
