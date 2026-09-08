package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserRole
import com.example.ui.theme.*
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.util.Locale

/**
 * Common ProHost Reusable UI Components
 * Ensures cohesive design language, typography, consistent padding, and accessibility across all screens.
 */

enum class ProBadgeType {
    VERIFIED_MEMBER,
    ACTIVE_30D,
    EXPIRED,
    PENDING,
    ACCEPTED_LOCKED,
    DECLINED,
    UNAVAILABLE,
    SUPER_ADMIN,
    TIER_A,
    TIER_B,
    TIER_C,
    CUSTOM_INFO,
    CUSTOM_SUCCESS,
    CUSTOM_WARNING,
    CUSTOM_ERROR
}

private data class BadgeConfig(
    val bg: Color,
    val text: Color,
    val icon: ImageVector,
    val defaultLabel: String
)

/**
 * Standardized status and category badge with semantic colors and icons.
 */
@Composable
fun ProStatusBadge(
    type: ProBadgeType,
    customText: String? = null,
    modifier: Modifier = Modifier
) {
    StatusBadge(
        status = when (type) {
            ProBadgeType.VERIFIED_MEMBER -> StatusBadgeType.VERIFIED
            ProBadgeType.ACTIVE_30D -> StatusBadgeType.AVAILABLE
            ProBadgeType.EXPIRED -> StatusBadgeType.CANCELLED
            ProBadgeType.PENDING -> StatusBadgeType.PENDING
            ProBadgeType.ACCEPTED_LOCKED -> StatusBadgeType.OCCUPIED
            ProBadgeType.DECLINED -> StatusBadgeType.CANCELLED
            ProBadgeType.UNAVAILABLE -> StatusBadgeType.MAINTENANCE
            ProBadgeType.SUPER_ADMIN -> StatusBadgeType.ADMIN
            ProBadgeType.TIER_A -> StatusBadgeType.GOLD
            ProBadgeType.TIER_B -> StatusBadgeType.INFO
            ProBadgeType.TIER_C -> StatusBadgeType.INFO
            ProBadgeType.CUSTOM_INFO -> StatusBadgeType.INFO
            ProBadgeType.CUSTOM_SUCCESS -> StatusBadgeType.AVAILABLE
            ProBadgeType.CUSTOM_WARNING -> StatusBadgeType.PENDING
            ProBadgeType.CUSTOM_ERROR -> StatusBadgeType.CANCELLED
        },
        label = customText,
        modifier = modifier
    )
}

// =========================================================================
// 1. REUSABLE STATUS BADGE COMPONENT
// =========================================================================

enum class StatusBadgeType {
    AVAILABLE,
    OCCUPIED,
    VERIFIED,
    PENDING,
    MAINTENANCE,
    CANCELLED,
    ADMIN,
    GOLD,
    INFO
}

/**
 * Standard StatusBadge Component
 * Features consistent pill shape (8.dp radius), compact padding, high-contrast semantic typography, and icons.
 */
@Composable
fun StatusBadge(
    status: StatusBadgeType,
    label: String? = null,
    modifier: Modifier = Modifier,
    customIcon: ImageVector? = null,
    shape: CornerBasedShape = MaterialTheme.shapes.small
) {
    val (bg, text, defaultIcon, defaultText) = when (status) {
        StatusBadgeType.AVAILABLE -> Quad(
            FreshGreen,
            PureWhite,
            Icons.Default.CheckCircle,
            "Available"
        )
        StatusBadgeType.OCCUPIED -> Quad(
            OxfordBlue,
            PureWhite,
            Icons.Default.Lock,
            "Occupied / Booked"
        )
        StatusBadgeType.VERIFIED -> Quad(
            FreshGreen,
            PureWhite,
            Icons.Default.Verified,
            "License / ID Verified"
        )
        StatusBadgeType.PENDING -> Quad(
            BrightOrange,
            PureWhite,
            Icons.Default.HourglassTop,
            "Pending Approval"
        )
        StatusBadgeType.MAINTENANCE -> Quad(
            CoolGray,
            PureWhite,
            Icons.Default.Build,
            "Maintenance Slot"
        )
        StatusBadgeType.CANCELLED -> Quad(
            CrimsonRed,
            PureWhite,
            Icons.Default.Cancel,
            "Cancelled / Unavailable"
        )
        StatusBadgeType.ADMIN -> Quad(
            OxfordBlue,
            PureWhite,
            Icons.Default.Shield,
            "Super Admin"
        )
        StatusBadgeType.GOLD -> Quad(
            BrightOrange,
            PureWhite,
            Icons.Default.WorkspacePremium,
            "Premium Tier"
        )
        StatusBadgeType.INFO -> Quad(
            VibrantBlue,
            PureWhite,
            Icons.Default.Info,
            "Information"
        )
    }

    Surface(
        color = bg,
        shape = shape,
        shadowElevation = 0.5.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = customIcon ?: defaultIcon,
                contentDescription = null,
                tint = text,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label ?: defaultText,
                color = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


/**
 * Standardized Section Header with title, subtitle, and optional trailing widget or button.
 */
@Composable
fun ProSectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (icon != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(Spacing.sm))
            trailingContent()
        }
    }
}

/**
 * Standardized KPI / Metric Tile for Dashboards, Analytics, and Financials.
 */
@Composable
fun ProMetricTile(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = iconTint.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Standardized Surface Card with theme-compatible border and elevation.
 */
@Composable
fun ProSurfaceCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Clean USD Currency Display Tag ($ USD only).
 */
@Composable
fun ProCurrencyTag(
    usdAmount: Double = 0.0,
    rateUsd: Double = usdAmount,
    isPerMonth: Boolean = true,
    exchangeRateLbp: Long = 89500L, // Kept for backwards signature compatibility
    modifier: Modifier = Modifier
) {
    val finalUsd = if (usdAmount > 0.0) usdAmount else rateUsd

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$${finalUsd.toInt()} USD${if (isPerMonth) "/mo" else ""}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Standard Member / Professional Avatar with initials and verification badge.
 */
@Composable
fun ProMemberAvatar(
    name: String,
    specialty: String? = null,
    isVerified: Boolean = true,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val initials = name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifEmpty { "PS" }

    if (specialty != null) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.size(size)) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initials,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = (size.value * 0.38f).sp
                        )
                    }
                }
                if (isVerified) {
                    Surface(
                        color = StatusSuccess,
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .size(size * 0.4f)
                            .align(Alignment.BottomEnd)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Verified",
                                tint = Color.White,
                                modifier = Modifier.size(size * 0.25f)
                            )
                        }
                    }
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isVerified) {
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = specialty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Box(modifier = modifier.size(size)) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (size.value * 0.38f).sp
                    )
                }
            }
            if (isVerified) {
                Surface(
                    color = StatusSuccess,
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .size(size * 0.4f)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Verified",
                            tint = Color.White,
                            modifier = Modifier.size(size * 0.25f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility alias for ProMemberAvatar
 */
@Composable
fun ProDoctorAvatar(
    name: String,
    specialty: String? = null,
    isVerified: Boolean = true,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    ProMemberAvatar(name, specialty, isVerified, size, modifier)
}

/**
 * Standardized Empty State layout for missing search results, lists, or logs.
 */
@Composable
fun ProEmptyState(
    title: String,
    description: String,
    icon: ImageVector = Icons.Default.SearchOff,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionButtonText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(
                onClick = onActionClick,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(actionButtonText, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// =========================================================================
// REUSABLE PROHOST BUTTONS
// =========================================================================

enum class CustomButtonVariant {
    PRIMARY,
    SECONDARY,
    OUTLINED,
    TONAL,
    TEXT,
    SUCCESS,
    DANGER,
    WHATSAPP
}

/**
 * Standard CustomButton Component
 * Supports multiple ProHost styles, loading states, leading/trailing icons, and adheres to 48dp touch targets.
 * Features consistent M3 typography (labelLarge, Bold) and standardized 12.dp curvature.
 */
@Composable
fun CustomButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CustomButtonVariant = CustomButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    customContainerColor: Color? = null,
    customContentColor: Color? = null,
    shape: CornerBasedShape = MaterialTheme.shapes.medium,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
) {
    val containerColor = customContainerColor ?: when (variant) {
        CustomButtonVariant.PRIMARY -> CarnationOrange
        CustomButtonVariant.SECONDARY -> CoolGray
        CustomButtonVariant.OUTLINED -> Color.Transparent
        CustomButtonVariant.TONAL -> LightGray
        CustomButtonVariant.TEXT -> Color.Transparent
        CustomButtonVariant.SUCCESS -> FreshGreen
        CustomButtonVariant.DANGER -> CrimsonRed
        CustomButtonVariant.WHATSAPP -> WhatsAppGreen
    }

    val contentColor = customContentColor ?: when (variant) {
        CustomButtonVariant.PRIMARY -> PureWhite
        CustomButtonVariant.SECONDARY -> LightGray
        CustomButtonVariant.OUTLINED -> OxfordBlue
        CustomButtonVariant.TONAL -> CoolGray
        CustomButtonVariant.TEXT -> OxfordBlue
        CustomButtonVariant.SUCCESS -> PureWhite
        CustomButtonVariant.DANGER -> PureWhite
        CustomButtonVariant.WHATSAPP -> PureWhite
    }

    when (variant) {
        CustomButtonVariant.OUTLINED -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.defaultMinSize(minHeight = 48.dp),
                enabled = enabled && !isLoading,
                shape = shape,
                border = BorderStroke(1.dp, if (enabled) (customContainerColor ?: OxfordBlue) else LightGray),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = contentColor,
                    disabledContentColor = contentColor.copy(alpha = 0.5f)
                ),
                contentPadding = contentPadding
            ) {
                ButtonInnerContent(text, icon, trailingIcon, isLoading, contentColor)
            }
        }
        CustomButtonVariant.TEXT -> {
            TextButton(
                onClick = onClick,
                modifier = modifier.defaultMinSize(minHeight = 48.dp),
                enabled = enabled && !isLoading,
                shape = shape,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = contentColor,
                    disabledContentColor = contentColor.copy(alpha = 0.5f)
                ),
                contentPadding = contentPadding
            ) {
                ButtonInnerContent(text, icon, trailingIcon, isLoading, contentColor)
            }
        }
        else -> {
            Button(
                onClick = onClick,
                modifier = modifier.defaultMinSize(minHeight = 48.dp),
                enabled = enabled && !isLoading,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                    disabledContainerColor = containerColor.copy(alpha = 0.5f),
                    disabledContentColor = contentColor.copy(alpha = 0.7f)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (variant == CustomButtonVariant.PRIMARY) 2.dp else 0.dp,
                    pressedElevation = 4.dp
                ),
                contentPadding = contentPadding
            ) {
                ButtonInnerContent(text, icon, trailingIcon, isLoading, contentColor)
            }
        }
    }
}

@Composable
private fun ButtonInnerContent(
    text: String,
    icon: ImageVector?,
    trailingIcon: ImageVector?,
    isLoading: Boolean,
    contentColor: Color
) {
    if (isLoading) {
        CircularProgressIndicator(
            color = contentColor,
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
    } else if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold
    )
    if (!isLoading && trailingIcon != null) {
        Spacer(modifier = Modifier.width(Spacing.sm))
        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Standard ProHost Primary Button (Filled with Carnation Orange #F25F4C, rounded 12dp, min 48dp height).
 */
@Composable
fun ProPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    containerColor: Color = CarnationOrange,
    contentColor: Color = PureWhite,
    shape: CornerBasedShape = MaterialTheme.shapes.medium
) {
    CustomButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = CustomButtonVariant.PRIMARY,
        icon = icon,
        enabled = enabled,
        isLoading = isLoading,
        customContainerColor = containerColor,
        customContentColor = contentColor,
        shape = shape
    )
}

/**
 * Standard Outlined Button (Transparent with Oxford Blue border, 12dp radius).
 */
@Composable
fun ProOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    borderColor: Color = OxfordBlue,
    contentColor: Color = OxfordBlue,
    shape: CornerBasedShape = MaterialTheme.shapes.medium
) {
    CustomButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = CustomButtonVariant.OUTLINED,
        icon = icon,
        enabled = enabled,
        customContainerColor = borderColor,
        customContentColor = contentColor,
        shape = shape
    )
}

// =========================================================================
// REUSABLE PROHOST INPUTS & TEXT FIELDS
// =========================================================================

/**
 * Standard InputField Component
 * Features consistent 12.dp corner radius, ProHost light background, label, placeholder, leading/trailing icons,
 * supportive helper text, and error handling with M3 typography.
 */
@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    helperText: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    shape: CornerBasedShape = MaterialTheme.shapes.medium
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
            placeholder = if (placeholder != null) { { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) } } else null,
            prefix = if (prefix != null) { { Text(prefix, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) } } else null,
            leadingIcon = if (leadingIcon != null) {
                {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else null,
            trailingIcon = trailingIcon,
            isError = isError,
            singleLine = singleLine,
            maxLines = maxLines,
            readOnly = readOnly,
            enabled = enabled,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            shape = shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                errorBorderColor = MaterialTheme.colorScheme.error,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                errorLabelColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (isError && !errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else if (!helperText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/**
 * Standard ProHost Outlined Text Field with refined borders, rounded corners, and helper text (alias for InputField).
 */
@Composable
fun ProOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    helperText: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    shape: CornerBasedShape = MaterialTheme.shapes.medium
) {
    InputField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        errorMessage = errorMessage,
        helperText = helperText,
        singleLine = singleLine,
        maxLines = maxLines,
        readOnly = readOnly,
        enabled = enabled,
        shape = shape
    )
}

// =========================================================================
// REUSABLE PROHOST CARDS & CONTAINERS (WITH MODERN SHADOWS)
// =========================================================================

/**
 * Modern Elevated Card Container with clean 16dp radius and subtle border/shadow.
 */
@Composable
fun ModernCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    elevation: Dp = 2.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation,
            pressedElevation = elevation + 2.dp
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Standard WorkspaceCard Component
 * Modern card layout with shadows, rounded corners (16.dp), consistent padding, badges, pricing, schedule, and host identity.
 */
@Composable
fun WorkspaceCard(
    title: String,
    specialization: String,
    location: String,
    rateUsd: Double,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    scheduleSummary: String? = null,
    doctorName: String? = null,
    practiceType: String? = null,
    isVerified: Boolean = true,
    bookedDoctorCount: Int = 0,
    facilities: List<String> = emptyList(),
    elevation: Dp = 3.dp,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    onClick: () -> Unit,
    onWhatsAppClick: (() -> Unit)? = null
) {
    ModernCard(
        modifier = modifier,
        onClick = onClick,
        shape = shape,
        contentPadding = PaddingValues(16.dp),
        elevation = elevation
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        // Header: Price (the specialty tag that used to sit here was removed — a
        // workspace listing isn't tied to one specialty, and the tag read as a
        // filter/category label that didn't match how Explore actually works)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProCurrencyTag(usdAmount = rateUsd)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        // Location with pin
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = location,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Schedule & Occupancy Subtitle
        if (scheduleSummary != null || bookedDoctorCount > 0) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (scheduleSummary != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = scheduleSummary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (bookedDoctorCount > 0) {
                    Surface(
                        color = StatusInfoContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = StatusOnInfoContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = "$bookedDoctorCount Active Member(s)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusOnInfoContainer
                            )
                        }
                    }
                }
            }
        }

        // Highlighted Facilities Chips
        if (facilities.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                facilities.take(3).forEach { facility ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = facility,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Footer: Owner details & Quick WhatsApp inquiry
        if (doctorName != null || onWhatsAppClick != null) {
            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (doctorName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProMemberAvatar(name = doctorName, isVerified = isVerified, size = 34.dp)
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                text = doctorName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (practiceType != null) {
                                Text(
                                    text = practiceType,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (onWhatsAppClick != null) {
                    CustomButton(
                        text = "WhatsApp",
                        onClick = onWhatsAppClick,
                        variant = CustomButtonVariant.WHATSAPP,
                        icon = Icons.AutoMirrored.Filled.Chat,
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Standard ProHost Card Container (alias for ModernCard).
 */
@Composable
fun ProCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ModernCard(
        modifier = modifier,
        containerColor = containerColor,
        borderColor = borderColor,
        shape = shape,
        contentPadding = contentPadding,
        onClick = onClick,
        content = content
    )
}


/**
 * Standard ProHost Information Alert / Banner.
 */
@Composable
fun ProInfoBanner(
    text: String,
    icon: ImageVector = Icons.Default.Info,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// =========================================================================
// REUSABLE CHIPS & SEGMENTED CONTROLS
// =========================================================================

/**
 * Standard ProHost Filter Chip.
 */
@Composable
fun ProChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        leadingIcon = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else null,
        shape = MaterialTheme.shapes.medium,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier
    )
}

/**
 * Standard Segmented Control Switcher (e.g. for switching active filters, roles, views).
 */
@Composable
fun ProSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onIndexSelected(index) }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard Crisp Divider.
 */
@Composable
fun ProDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    thickness: Dp = 1.dp
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}

/**
 * Real-time Firebase Sync & Network Resilience Status Banner
 */
@Composable
fun NetworkSyncResilienceBanner(
    isOffline: Boolean,
    statusMessage: String?,
    pendingOfflineCount: Int = 0,
    onRetrySync: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isOffline) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (isOffline) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOffline) StatusWarning else FreshGreen)
                )
                Column {
                    Text(
                        text = if (isOffline) "Resilient Offline Mode" else "Firebase Real-time Sync",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOffline) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = statusMessage ?: "Connected to Lebanese ProHost Cloud Node",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOffline) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isOffline || pendingOfflineCount > 0) {
                FilledTonalButton(
                    onClick = onRetrySync,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(if (pendingOfflineCount > 0) "Retry ($pendingOfflineCount)" else "Sync", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Standardized ProHost Brand Logo Composable
 * Renders the official pin-shaped mark (workspace silhouette on a two-tone base),
 * which carries its own background/gradient - no surrounding box or border needed.
 */
@Composable
fun ProHostBrandLogo(
    size: Dp = 38.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = com.example.R.drawable.ic_prohost_logo_brand),
        contentDescription = "ProHost Logo",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size)
    )
}

/**
 * Authentic Lebanese Cedar Network Badge
 */
@Composable
fun ProHostCedarBadge(
    text: String = "Lebanon Verified",
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        color = LebaneseCedarContainer.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, LebaneseCedarGreen.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isCompact) 5.dp else 7.dp,
                vertical = if (isCompact) 2.dp else 3.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = LebaneseCedarGreen,
                modifier = Modifier.size(if (isCompact) 10.dp else 12.dp)
            )
            Text(
                text = text,
                color = LebaneseCedarGreen,
                fontSize = if (isCompact) 9.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
    }
}

/**
 * Standardized High-Impact ProHost Brand Top App Bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProHostTopAppBar(
    currentRole: UserRole,
    unreadAlertCount: Int,
    onMenuClick: () -> Unit,
    onAlertsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Hamburger Menu + Logo + Brand Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.testTag("hamburger_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Side Navigation Drawer",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                ProHostBrandLogo(size = 34.dp)

                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "ProHost",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ProHostCedarBadge(text = "LB", isCompact = true)
                    }

                    Text(
                        text = when (currentRole) {
                            UserRole.ADMIN -> "Super Admin Node"
                            UserRole.PRO_HOST -> "Host & Owner Hub"
                            UserRole.SPECIALIST -> "Practitioner Circle"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Right: Push Notification Bell
            IconButton(
                onClick = onAlertsClick,
                modifier = Modifier.testTag("top_bar_notifications_button")
            ) {
                BadgedBox(
                    badge = {
                        if (unreadAlertCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = unreadAlertCount.toString(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Real-time Push Alerts",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Top bar for a Pro Host drawer destination opened full-screen (no bottom nav —
 * these screens live outside the unified Specialist/Pro Host bottom nav). Just
 * the menu icon (to reopen the drawer, exactly like the regular top bar) and the
 * screen's own heading — no brand lockup, no alerts bell, since this isn't the
 * app's home surface.
 */
@Composable
fun ProHostFullScreenTopAppBar(
    title: String,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.testTag("hamburger_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Side Navigation Drawer",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}



