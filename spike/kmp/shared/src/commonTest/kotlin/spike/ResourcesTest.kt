package spike

import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import spike.shared.resources.Res
import spike.shared.resources.listings
import spike.shared.resources.of_format
import spike.shared.resources.trailing_space
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spike item 4 (English side, runs on every target incl. the iOS simulator): plurals, positional args, the   escape. */
class ResourcesTest {
    @Test
    fun englishPlurals() = runTest {
        assertEquals("1 listing", getPluralString(Res.plurals.listings, 1, 1))
        assertEquals("0 listings", getPluralString(Res.plurals.listings, 0, 0))
        assertEquals("2 listings", getPluralString(Res.plurals.listings, 2, 2))
    }

    @Test
    fun positionalArguments() = runTest {
        assertEquals("a of b", getString(Res.string.of_format, "a", "b"))
    }

    @Test
    fun trailingSpaceEscapeIsKept() = runTest {
        assertTrue(getString(Res.string.trailing_space).endsWith(" "), "trailing space must survive")
    }
}
