package com.example.ui.components.drawer

import com.example.ui.util.localizedLabel
import com.example.R
import androidx.compose.ui.res.stringResource
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
import coil.compose.AsyncImage
import com.example.data.model.AppUser
import com.example.data.model.publicCode
import com.example.data.model.UserRole
import com.example.ui.theme.*

/**
 * The compact identity card at the top of the Profile page: a one-word 3D role tag, avatar, name and credentials,
 * and one meta line. Verification shows on the avatar and name checks (no separate "phone verified" tag).
 */
@Composable
fun DrawerIdentityCard(
    user: AppUser?,
    onCloseDrawer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
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
                // Top row: Active Plan Tag + Close Drawer Arrow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoleTag3D(role)

                    if (onCloseDrawer != null) {
                        IconButton(
                            onClick = onCloseDrawer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.collapse_side_drawer),
                                tint = pro.onBrandHeader
                            )
                        }
                    }
                }

                // Middle row: Avatar + Name + Credentials
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                                        contentDescription = stringResource(R.string.verified),
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = user?.fullName ?: stringResource(R.string.prohost_member),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = pro.onBrandHeader,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (user?.isVerified == true) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = stringResource(R.string.verified_member),
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

                        Spacer(modifier = Modifier.height(2.dp))

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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = pro.onBrandHeader.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                        Text(
                            text = "ID: ${user?.publicCode ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = pro.onBrandHeader.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private data class RoleLook(val label: String, val top: Color, val bottom: Color, val edge: Color, val text: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/**
 * One-word role tag with a raised, 3D look: a vertical gradient face with a light top rim, sitting on a darker
 * "edge" that shows below it, plus a soft shadow. Steel Blue for Specialist, orange for Host, gold for Admin.
 */
@Composable
private fun RoleTag3D(role: UserRole) {
    val look = when (role) {
        UserRole.ADMIN -> RoleLook("ADMIN", Color(0xFFFFDC7A), Color(0xFFE2A82A), Color(0xFF8A6410), Color(0xFF2A1E04), Icons.Default.Shield)
        UserRole.PRO_HOST -> RoleLook("HOST", Color(0xFFFF8F63), Color(0xFFD2432F), Color(0xFF8C2819), Color.White, Icons.Default.WorkspacePremium)
        else -> RoleLook("SPECIALIST", Color(0xFF6C9FD6), Color(0xFF3A72AE), Color(0xFF1F4268), Color.White, Icons.Default.VerifiedUser)
    }
    val shape = Radius.Sm
    Box(modifier = Modifier.padding(bottom = 3.dp)) {
        // Edge: the darker slab under the face.
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 3.dp)
                .background(look.edge, shape)
        )
        Row(
            modifier = Modifier
                .shadow(4.dp, shape, ambientColor = look.edge, spotColor = look.edge)
                .background(Brush.verticalGradient(listOf(look.top, look.bottom)), shape)
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.1f))), shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(look.icon, contentDescription = null, tint = look.text, modifier = Modifier.size(14.dp))
            Text(
                text = look.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = look.text,
                maxLines = 1
            )
        }
    }
}
