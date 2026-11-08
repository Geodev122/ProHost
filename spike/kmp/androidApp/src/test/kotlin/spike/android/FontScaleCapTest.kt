package spike.android

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import spike.FontScaleCap

/**
 * Spike item 5: the app caps text at 1.15 (CLAUDE.md design rule). Two readings of the font scale seen by content, a Dialog window and
 * a ModalBottomSheet when the user's font scale is 1.5:
 *  - UNCAPPED: no FontScaleCap. If a dialog/sheet reads less than 1.5 here, Compose windows do not follow a parent LocalDensity override,
 *    so a pure-Compose cap cannot be what protects them (the real app caps the Configuration in attachBaseContext, which every window inherits).
 *  - CAPPED: inside FontScaleCap. Must never exceed 1.15.
 * Both maps are printed (FONT-SCALE-SEEN) for the report; only "content capped <= 1.15", "capped never above 1.15" and "baseline reads 1.5" are asserted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class FontScaleCapTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Probe(seen: MutableMap<String, Float>, prefix: String) {
        seen["$prefix content"] = LocalDensity.current.fontScale
        Dialog(onDismissRequest = {}) {
            seen["$prefix dialog"] = LocalDensity.current.fontScale
            Text("dialog")
        }
        ModalBottomSheet(onDismissRequest = {}) {
            seen["$prefix sheet"] = LocalDensity.current.fontScale
            Text("sheet")
        }
    }

    @Test
    fun capReachesContentDialogAndSheet() {
        RuntimeEnvironment.setFontScale(1.5f) // the user's system setting: every window's Configuration says 1.5
        val seen = linkedMapOf<String, Float>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1.5f)) {
                seen["baseline"] = LocalDensity.current.fontScale
                Probe(seen, "UNCAPPED")
                FontScaleCap { Probe(seen, "CAPPED") }
            }
        }
        compose.waitForIdle()
        println("FONT-SCALE-SEEN $seen")
        assertEquals(1.5f, seen.getValue("baseline"), 0.0001f)
        for (key in listOf("CAPPED content", "CAPPED dialog", "CAPPED sheet")) {
            assertTrue("$key font scale ${seen[key]} must be present", key in seen)
            assertTrue("$key font scale ${seen[key]} must be <= 1.15", seen.getValue(key) <= 1.1501f)
        }
    }
}
