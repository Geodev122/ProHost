package spike

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import spike.shared.resources.Res
import spike.shared.resources.app_name
import spike.shared.resources.splash

/**
 * Spike copy of app/src/main/java/com/example/ui/screens/SplashScreen.kt: the logic, sizes and colours are unchanged;
 * only the resource access differs (R.drawable.splash / R.string.prohost -> composeResources). First real unit of the
 * migration: rendered on Compose 1.12 and diffed against the golden splash_* renders taken on Compose 1.7.
 */
@Composable
fun SplashScreen(
    onSplashCompleted: () -> Unit,
    onError: (() -> Unit)? = null
) {
    var contentAlpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(400)
        ) { value, _ ->
            contentAlpha = value
        }
        delay(300)
        onSplashCompleted()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(Res.drawable.splash),
            contentDescription = stringResource(Res.string.app_name),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .size(28.dp)
                .alpha(contentAlpha),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.35f),
            strokeWidth = 3.dp
        )
    }
}
