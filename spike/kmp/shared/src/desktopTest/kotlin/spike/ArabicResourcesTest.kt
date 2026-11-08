package spike

import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import spike.shared.resources.Res
import spike.shared.resources.listings
import spike.shared.resources.of_format
import spike.shared.resources.trailing_space
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spike item 4 (Arabic side). The plural-rule and format code in composeResources is common Kotlin, so a result on the JVM host
 * with the default locale set to Arabic is valid for iOS too; the iOS simulator cannot switch language inside a unit test.
 * Arabic CLDR categories: zero 0, one 1, two 2, few n%100 in 3..10, many n%100 in 11..99, other 100-102 ...
 */
class ArabicResourcesTest {
    private lateinit var saved: Locale

    @BeforeTest
    fun useArabic() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar"))
    }

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    @Test
    fun arabicPluralCategories() = runTest {
        val expected = mapOf(
            0 to "لا مساحات", 1 to "مساحة واحدة", 2 to "مساحتان", 3 to "3 مساحات", 10 to "10 مساحات",
            11 to "11 مساحة", 99 to "99 مساحة", 100 to "100 مساحة", 103 to "103 مساحات"
        )
        for ((n, text) in expected) assertEquals(text, getPluralString(Res.plurals.listings, n, n), "ar plural for $n")
    }

    @Test
    fun arabicPositionalArgumentsAreReordered() = runTest {
        assertEquals("b من a", getString(Res.string.of_format, "a", "b"))
    }

    @Test
    fun arabicTrailingSpaceEscapeIsKept() = runTest {
        assertTrue(getString(Res.string.trailing_space).endsWith(" "), "ar trailing space must survive")
    }
}
