package com.example.ui.components.drawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.data.model.PackagePlan
import com.example.data.model.UserRole
import com.example.ui.theme.*

/**
 * The rich role-tailored identity card shown at the top of every role's side drawer.
 */
@Composable
fun DrawerIdentityCard(
    user: AppUser?,
    currentPackage: PackagePlan? = null,
    onCloseDrawer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val role = user?.role ?: UserRole.SPECIALIST
    val primaryAccent = when (role) {
        UserRole.ADMIN -> AmberWarning
        UserRole.PRO_HOST -> CarnationOrange
        UserRole.SPECIALIST -> OxfordBlue
    }
    val heroGradient = when (role) {
        UserRole.ADMIN -> Brush.linearGradient(listOf(OxfordBlueDark, OxfordBlue, CoolGrayDark))
        UserRole.PRO_HOST -> Brush.linearGradient(listOf(OxfordBlue, CarnationOrangeDark.copy(alpha = 0.85f), OxfordBlueDark))
        UserRole.SPECIALIST -> Brush.linearGradient(listOf(OxfordBlueDark, OxfordBlue, VibrantBlue.copy(alpha = 0.7f)))
    }

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
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Top row: Active Plan Tag + Close Drawer Arrow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (role == UserRole.PRO_HOST && user != null) {
                            Surface(
                                color = CarnationOrange.copy(alpha = 0.35f),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, CarnationOrange.copy(alpha = 0.8f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = CarnationOrangeLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = currentPackage?.badgeName?.ifBlank { currentPackage.name } ?: "Active Plan",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = when (role) {
                                    UserRole.ADMIN -> AmberWarning.copy(alpha = 0.25f)
                                    else -> VibrantBlue.copy(alpha = 0.25f)
                                },
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = when (role) {
                                            UserRole.ADMIN -> Icons.Default.Shield
                                            else -> Icons.Default.VerifiedUser
                                        },
                                        contentDescription = null,
                                        tint = when (role) {
                                            UserRole.ADMIN -> AmberWarning
                                            else -> Color.White
                                        },
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = when (role) {
                                            UserRole.ADMIN -> "Super Administrator Node"
                                            else -> "Practitioner / Specialist"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (onCloseDrawer != null) {
                        IconButton(
                            onClick = onCloseDrawer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Collapse Side Drawer",
                                tint = Color.White
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
                    Box(modifier = Modifier.size(64.dp)) {
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
                                border = BorderStroke(2.5.dp, Color.White.copy(alpha = 0.9f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (user?.fullName ?: "").split(" ")
                                            .filter { it.isNotBlank() }
                                            .take(2)
                                            .mapNotNull { it.firstOrNull()?.uppercase() }
                                            .joinToString("")
                                            .ifEmpty { "PS" },
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                }
                            }
                        }
                        if (user?.isVerified == true) {
                            Surface(
                                color = FreshGreen,
                                shape = CircleShape,
                                border = BorderStroke(2.dp, OxfordBlueDark),
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.BottomEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Verified",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                        if (user?.idDocumentUrl != null) {
                            Surface(
                                color = AmberWarning,
                                shape = CircleShape,
                                border = BorderStroke(2.dp, OxfordBlueDark),
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Badge,
                                        contentDescription = "ID Verified",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
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
                                text = user?.fullName ?: "ProHost Member",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (user?.isVerified == true) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified Member",
                                    tint = if (role == UserRole.ADMIN) AmberWarning else FreshGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = user?.specialty?.ifBlank { role.displayName } ?: role.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = user?.email ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

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
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                        Text(
                            text = user?.city?.ifBlank { user.governorate.ifBlank { user.country } } ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
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
                        Icon(Icons.Default.Badge, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                        Text(
                            text = "ID: ${user?.id?.take(10) ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (user?.isVerified == true) "🛡️ Phone Verified" else "Phone Unverified",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
