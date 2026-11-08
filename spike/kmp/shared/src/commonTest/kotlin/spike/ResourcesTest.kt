package spike

import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.DensityQualifier
import org.jetbrains.compose.resources.LanguageQualifier
import org.jetbrains.compose.resources.RegionQualifier
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.ThemeQualifier
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import spike.shared.resources.Res
import spike.shared.resources.listings
import spike.shared.resources.of_format
import spike.shared.resources.trailing_space
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spike item 4: Arabic plural categories, positional format arguments and   survive composeResources. */
class ResourcesTest {
    private fun env(lang: String) = ResourceEnvironment(LanguageQualifier(lang), RegionQualifier(""), ThemeQualifier.LIGHT, DensityQualifier.MDPI)

    @Test
    fun arabicPluralCategories() = runTest {
        val ar = env("ar")
        val expected = mapOf(
            0 to "لا مساحات", 1 to "مساحة واحدة", 2 to "مساحتان", 3 to "3 مساحات", 10 to "10 مساحات",
            11 to "11 مساحة", 99 to "99 مساحة", 100 to "100 مساحة", 103 to "103 مساحات"
        )
        for ((n, text) in expected) assertEquals(text, getPluralString(ar, Res.plurals.listings, n, n), "ar plural for $n")
    }

    @Test
    fun englishPlurals() = runTest {
        val en = env("en")
        assertEquals("1 listing", getPluralString(en, Res.plurals.listings, 1, 1))
        assertEquals("0 listings", getPluralString(en, Res.plurals.listings, 0, 0))
        assertEquals("2 listings", getPluralString(en, Res.plurals.listings, 2, 2))
    }

    @Test
    fun positionalArgumentsAreReordered() = runTest {
        assertEquals("a of b", getString(env("en"), Res.string.of_format, "a", "b"))
        assertEquals("b من a", getString(env("ar"), Res.string.of_format, "a", "b"))
    }

    @Test
    fun trailingSpaceEscapeIsKept() = runTest {
        assertTrue(getString(env("en"), Res.string.trailing_space).endsWith(" "), "en trailing space")
        assertTrue(getString(env("ar"), Res.string.trailing_space).endsWith(" "), "ar trailing space")
    }
}
