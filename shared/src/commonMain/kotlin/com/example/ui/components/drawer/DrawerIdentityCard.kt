package com.example.ui.components.drawer

import com.example.ui.util.localizedLabel
import com.example.ui.theme.Radius
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.data.model.AppUser
import com.example.data.model.publicCode
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.shared.resources.Res
import com.example.shared.resources.collapse_side_drawer
import com.example.shared.resources.find_space
import com.example.shared.resources.view_rentals
import com.example.shared.resources.id_x
import com.example.shared.resources.prohost_member
import com.example.shared.resources.verified
import com.example.shared.resources.verified_member
import com.example.ui.util.appString

/**
 * The compact identity card at the top of the Profile page: a one-word 3D role tag, avatar, name and credentials,
 * and one meta line. Verification shows on the avatar and name checks (no separate "phone verified" tag).
 */
@Composable
fun DrawerIdentityCard(
    user: AppUser?,
    onCloseDrawer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    // Specialist Profile (owner, 22 Nov 2026): the latest update and the two main actions live on this card.
    activityLine: String? = null,
    onFindSpace: (() -> Unit)? = null,
    onViewRentals: (() -> Unit)? = null
) {
    val role = user?.role ?: UserRole.SPECIALIST
    val pro = MaterialTheme.proColors
    // Brand field + role accent all come from the theme, so the card re-tones with
    // light/dark. Orange (secondary) is the role-agnostic accent; the role itself is
    // told apart by the pill label/icon.
    val primaryAccent = MaterialTheme.colorScheme.secondary
    val heroGradient = Brush.linearGradient(listOf(pro.brandHeaderStart, pro.brandHeaderEnd))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, MaterialTheme.shapes.extraLarge),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroGradient)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                // No role tag (owner, 22 Nov 2026); the close arrow only where the card sits in a drawer.
                if (onCloseDrawer != null) Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    run {
                        IconButton(
                            onClick = onCloseDrawer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = appString(Res.string.collapse_side_drawer),
                                tint = pro.onBrandHeader
                            )
                        }
                    }
                }

                // Middle row: Avatar + Name + Credentials
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Box(modifier = Modifier.size(56.dp)) {
                        val picUrl = user?.profilePictureUrl
                        if (picUrl != null) {
                            AsyncImage(
                                model = picUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                color = primaryAccent,
                                shape = CircleShape,
                                modifier = Modifier.fillMaxSize(),
                                border = BorderStroke(2.5.dp, pro.onBrandHeader.copy(alpha = 0.9f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (user?.fullName ?: "").split(" ")
                                            .filter { it.isNotBlank() }
                                            .take(2)
                                            .mapNotNull { it.firstOrNull()?.uppercase() }
                                            .joinToString("")
                                            .ifEmpty { "PS" },
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                            }
                        }
                        if (user?.isVerified == true) {
                            Surface(
                                color = pro.headerSuccess,
                                shape = CircleShape,
                                border = BorderStroke(2.dp, pro.brandHeaderStart),
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.BottomEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = appString(Res.string.verified),
                                        tint = pro.brandHeaderStart,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Text(
                                text = user?.fullName ?: appString(Res.string.prohost_member),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = pro.onBrandHeader,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (user?.isVerified == true) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = appString(Res.string.verified_member),
                                    tint = if (role == UserRole.ADMIN) pro.headerWarning else pro.headerSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = user?.specialty?.ifBlank { localizedLabel(role.displayName) } ?: localizedLabel(role.displayName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = pro.onBrandHeader.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(Spacing.xs))

                        Text(
                            text = user?.email ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = pro.onBrandHeader.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = pro.onBrandHeader.copy(alpha = 0.15f))

                // Bottom Meta: Location + Member ID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = pro.onBrandHeader.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                        Text(
                            text = user?.city?.ifBlank { user.governorate.ifBlank { user.country } } ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = pro.onBrandHeader.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = pro.onBrandHeader.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                        Text(
                            text = appString(Res.string.id_x, user?.publicCode ?: "—"),
                            style = MaterialTheme.typography.labelSmall,
                            color = pro.onBrandHeader.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (activityLine != null) {
                    Surface(
                        color = pro.onBrandHeader.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = pro.onBrandHeader, modifier = Modifier.size(16.dp))
                            Text(
                                text = activityLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = pro.onBrandHeader,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (onFindSpace != null || onViewRentals != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (onFindSpace != null) {
                            com.example.ui.components.Button(
                                onClick = onFindSpace,
                                colors = com.example.ui.components.proPrimaryButtonColors(),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(appString(Res.string.find_space), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (onViewRentals != null) {
                            com.example.ui.components.OutlinedButton(
                                onClick = onViewRentals,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, pro.onBrandHeader.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = pro.onBrandHeader),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(appString(Res.string.view_rentals), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
