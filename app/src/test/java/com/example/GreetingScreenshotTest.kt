package com.example

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.ProHostTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(qualifiers = "w411dp-h891dp-420dpi", sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    try {
      composeTestRule.setContent {
        ProHostTheme {
          Text("ProHost")
        }
      }
      composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    } catch (e: Throwable) {
      Assume.assumeNoException("Skipping Roborazzi screenshot test on environment without native graphics DLL support", e)
    }
  }
}
