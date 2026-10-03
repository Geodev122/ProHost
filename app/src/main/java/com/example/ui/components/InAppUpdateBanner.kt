package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.proColors
import com.example.util.UpdateState
import com.example.ui.theme.Spacing

@Composable
fun InAppUpdateBanner(
    updateState: UpdateState,
    downloadProgress: Float,
    onCompleteUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pro = MaterialTheme.proColors
    val isReady = updateState == UpdateState.DOWNLOADED
    val bannerColor = if (isReady) pro.success else MaterialTheme.colorScheme.primary
    val onBanner = if (isReady) pro.onSuccess else MaterialTheme.colorScheme.onPrimary
    AnimatedVisibility(
        visible = updateState == UpdateState.DOWNLOADED || updateState == UpdateState.DOWNLOADING,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = bannerColor,
                contentColor = onBanner
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(onBanner.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (updateState == UpdateState.DOWNLOADED) Icons.Default.DownloadDone else Icons.Default.Downloading,
                            contentDescription = null,
                            tint = onBanner,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (updateState == UpdateState.DOWNLOADED) "ProHost Update Ready" else "Downloading ProHost Update...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = onBanner
                        )
                        Text(
                            text = if (updateState == UpdateState.DOWNLOADED) {
                                "Restart the app to apply the latest features and security patches."
                            } else {
                                "Download in progress: ${(downloadProgress * 100).toInt()}%"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = onBanner.copy(alpha = 0.85f)
                        )
                    }

                    if (updateState == UpdateState.DOWNLOADED) {
                        Button(
                            onClick = onCompleteUpdate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = onBanner,
                                contentColor = bannerColor
                            ),
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Restart",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                if (updateState == UpdateState.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                        color = onBanner,
                        trackColor = onBanner.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
