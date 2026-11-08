package spike.android

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import spike.SpikeTheme
import spike.SplashScreen
import java.io.File

/**
 * Spike item 2: render the migrated SplashScreen under the same conditions as app/src/test/.../DesignScreenshotTest
 * (Robolectric NATIVE graphics, 390x844 dp at xxhdpi = 1170x2532, light and dark, wait for idle + 400 ms) and write
 * splash_light.png / splash_dark.png so the workflow can diff them with scripts/migration/diff_shots.py against the golden set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class SplashParityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun splashLight() = capture("splash_light", dark = false)

    @Test fun splashDark() = capture("splash_dark", dark = true)

    /**
     * The spinner is an endless animation, so one frame can never be compared to the golden's frame unless both were taken at the same
     * animation time. This renders the light splash at 30 ms steps on a manual clock (0..2.7 s); the workflow then looks for any frame whose
     * spinner box equals the golden's. A match proves the rendering (geometry, stroke, colours, track) is identical and only the phase differed.
     */
    @Test fun splashLightPhaseScan() {
        System.setProperty("roborazzi.test.record", "true")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) { SpikeTheme(false) { SplashScreen(onSplashCompleted = {}) } }
        }
        File("build/spike-shots/phase").mkdirs()
        Thread.sleep(400) // async image decode
        for (i in 0 until 90) {
            compose.mainClock.advanceTimeBy(30)
            compose.onRoot().captureRoboImage(filePath = "build/spike-shots/phase/splash_light_p%03d.png".format(i))
        }
    }

    private fun capture(name: String, dark: Boolean) {
        System.setProperty("roborazzi.test.record", "true")
        compose.setContent {
            val config = Configuration(LocalConfiguration.current)
            if (dark) config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(3f, 1f)) {
                SpikeTheme(dark) { SplashScreen(onSplashCompleted = {}) }
            }
        }
        compose.waitForIdle()
        Thread.sleep(400) // let async image decoding land, as the original harness does
        compose.waitForIdle()
        File("build/spike-shots").mkdirs()
        compose.onRoot().captureRoboImage(filePath = "build/spike-shots/$name.png")
    }
}
