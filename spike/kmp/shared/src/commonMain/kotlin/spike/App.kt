package spike

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import spike.shared.resources.Res
import spike.shared.resources.app_name
import spike.shared.resources.listings
import spike.shared.resources.trailing_space

private val SteelBlue = Color(0xFF2B5A8C)

/** Same idea as the app's FontScaleCap: text follows the phone up to 1.15, never beyond (CLAUDE.md design rule). */
@Composable
fun FontScaleCap(max: Float = 1.15f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, minOf(d.fontScale, max)), content = content)
}

/** The app's primary colour in light (#2B5A8C) and dark (#93B8E0) - Color.kt / Theme.kt. */
@Composable
fun SpikeTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = Color(0xFF93B8E0)) else lightColorScheme(primary = SteelBlue),
        content = content
    )
}

@Composable
fun SpikeApp() {
    MaterialTheme(colorScheme = lightColorScheme(primary = SteelBlue)) {
        FontScaleCap {
            var dialog by remember { mutableStateOf(false) }
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(pluralStringResource(Res.plurals.listings, 3, 3))
                Text(stringResource(Res.string.trailing_space) + "|")
                TextButton(onClick = { dialog = true }) { Text("Dialog") }
            }
            if (dialog) {
                AlertDialog(
                    onDismissRequest = { dialog = false },
                    confirmButton = { TextButton(onClick = { dialog = false }) { Text("OK") } },
                    text = { Text(pluralStringResource(Res.plurals.listings, 11, 11)) }
                )
            }
        }
    }
}
