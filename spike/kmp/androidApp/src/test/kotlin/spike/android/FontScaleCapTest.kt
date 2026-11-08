package spike.android

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Dialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import spike.FontScaleCap

/**
 * Spike item 5: the app caps text at 1.15 (CLAUDE.md design rule). This checks the pure-Compose replacement (FontScaleCap)
 * reaches content, a Dialog window and a ModalBottomSheet when the user's font scale is 1.5. The baseline reading shows the
 * uncapped scale really is 1.5, so a missing cap would be detected.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class FontScaleCapTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun capReachesContentDialogAndSheet() {
        val seen = linkedMapOf<String, Float>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1.5f)) {
                seen["baseline (uncapped)"] = LocalDensity.current.fontScale
                FontScaleCap {
                    seen["content"] = LocalDensity.current.fontScale
                    Dialog(onDismissRequest = {}) {
                        seen["dialog"] = LocalDensity.current.fontScale
                        Text("dialog")
                    }
                    ModalBottomSheet(onDismissRequest = {}) {
                        seen["sheet"] = LocalDensity.current.fontScale
                        Text("sheet")
                    }
                }
            }
        }
        compose.waitForIdle()
        println("FONT-SCALE-SEEN $seen")
        assertEquals(1.5f, seen.getValue("baseline (uncapped)"), 0.0001f)
        for (key in listOf("content", "dialog", "sheet")) {
            assertTrue("$key font scale ${seen[key]} must be <= 1.15", (seen[key] ?: 99f) <= 1.1501f)
        }
        assertTrue("dialog and sheet were composed", "dialog" in seen && "sheet" in seen)
    }
}
