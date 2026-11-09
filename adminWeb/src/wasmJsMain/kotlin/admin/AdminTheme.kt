package admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.CoolGrayDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.SteelBlue
import com.example.ui.theme.SurfaceLight

/** ProHost's light palette from :shared tokens (the console is light-only for now, as the Android admin follows the system). */
@Composable
fun AdminTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = lightColorScheme(
      primary = SteelBlue, onPrimary = PureWhite,
      background = BackgroundLight, onBackground = CoolGrayDark,
      surface = SurfaceLight, onSurface = CoolGrayDark, onSurfaceVariant = SecondaryTextLight,
    ),
    content = content,
  )
}
