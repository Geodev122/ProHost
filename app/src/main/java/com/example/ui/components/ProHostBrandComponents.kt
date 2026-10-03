package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.navigation.AppNavTab
import com.example.ui.theme.DrawerShape
import com.example.ui.theme.SheetShape
import com.example.ui.theme.Spacing
import com.example.ui.theme.proColors

/**
 * ProHost shared brand component library for the Specialist and Pro Host surfaces.
 *
 * Rules every component here follows:
 *  - No color literals and no fixed brand constants: every color is read from
 *    [MaterialTheme.colorScheme] or [MaterialTheme.proColors], both resolved per
 *    light/dark theme in ProHostTheme, so a theme switch re-colors them with no
 *    component-side branching.
 *  - Corners come from [MaterialTheme.shapes] (medium 12dp / large 16dp) or the
 *    shared [SheetShape]/[DrawerShape]; spacing comes from [Spacing] (4/8/12/16/24/32).
 *  - Orange (`secondary`) marks the primary action / highlight; Special Blue
 *    (`primary`) marks navigation, focus and informational emphasis.
 */

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

/**
 * Drop-in replacement for Material's `AlertDialog` with the ProHost look: 16dp corners,
 * theme surface, primary-tinted icon, secondary-variant body text. Same parameter
 * names as `AlertDialog`, so call sites only swap the function name.
 */
@Composable
fun ProHostDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    iconContentColor: Color = MaterialTheme.colorScheme.primary,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = 3.dp,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}

// ---------------------------------------------------------------------------
// Bottom sheets
// ---------------------------------------------------------------------------

/**
 * Drop-in replacement for `ModalBottomSheet`: shared 24dp top corners, theme surface,
 * a drag handle and a scrim derived from the theme's `scrim`. With [accentTint] the sheet
 * takes a faint Special Blue wash (used by the booking flow so the trigger bar and the
 * sheet it opens read as one design); the wash and handle come from `primary`, so they
 * re-tone automatically in dark mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProHostBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    accentTint: Boolean = false,
    shape: Shape = SheetShape,
    scrimColor: Color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.40f),
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface
    val container = if (accentTint) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f).compositeOver(surface)
    } else surface
    val handle = if (accentTint) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = container,
        contentColor = contentColorFor(surface),
        tonalElevation = 4.dp,
        scrimColor = scrimColor,
        contentWindowInsets = contentWindowInsets,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = Spacing.md)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(handle)
            )
        },
        content = content
    )
}

// ---------------------------------------------------------------------------
// Alert banners / status strips
// ---------------------------------------------------------------------------

enum class ProHostAlertSeverity { INFO, SUCCESS, WARNING, ERROR }

/**
 * Inline alert banner (rounded card) or, with [fullBleed], an edge-to-edge status strip.
 * Severity maps to the theme-resolved container/on-container pairs, each contrast-checked
 * (>= 4.5:1) in light and dark.
 */
@Composable
fun ProHostAlertBanner(
    message: String,
    modifier: Modifier = Modifier,
    severity: ProHostAlertSeverity = ProHostAlertSeverity.INFO,
    title: String? = null,
    icon: ImageVector? = null,
    fullBleed: Boolean = false,
    action: (@Composable () -> Unit)? = null
) {
    val pro = MaterialTheme.proColors
    val scheme = MaterialTheme.colorScheme
    val (container, content, accent, defaultIcon) = when (severity) {
        ProHostAlertSeverity.INFO -> AlertTone(pro.infoContainer, pro.onInfoContainer, pro.info, Icons.Default.Info)
        ProHostAlertSeverity.SUCCESS -> AlertTone(pro.successContainer, pro.onSuccessContainer, pro.success, Icons.Default.CheckCircle)
        ProHostAlertSeverity.WARNING -> AlertTone(pro.warningContainer, pro.onWarningContainer, pro.warning, Icons.Default.WarningAmber)
        ProHostAlertSeverity.ERROR -> AlertTone(scheme.errorContainer, scheme.onErrorContainer, scheme.error, Icons.Default.ErrorOutline)
    }
    Surface(
        color = container,
        contentColor = content,
        shape = if (fullBleed) RectangleShape else MaterialTheme.shapes.medium,
        border = if (fullBleed) null else BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                imageVector = icon ?: defaultIcon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (!title.isNullOrBlank()) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, color = content)
                }
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = content)
            }
            action?.invoke()
        }
    }
}

private data class AlertTone(val container: Color, val content: Color, val accent: Color, val icon: ImageVector)

/** Animated wrapper used by app-shell strips (offline, update) so they expand/collapse uniformly. */
@Composable
fun ProHostStatusStrip(
    visible: Boolean,
    message: String,
    modifier: Modifier = Modifier,
    severity: ProHostAlertSeverity = ProHostAlertSeverity.INFO,
    icon: ImageVector? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        ProHostAlertBanner(message = message, severity = severity, icon = icon, fullBleed = true)
    }
}

// ---------------------------------------------------------------------------
// Badges
// ---------------------------------------------------------------------------

/** Numeric notification badge (error-colored, theme-aware) for use inside `BadgedBox`. */
@Composable
fun ProHostCountBadge(count: Int, modifier: Modifier = Modifier) {
    Badge(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Plain "something new" dot badge for use inside `BadgedBox`. */
@Composable
fun ProHostDotBadge(modifier: Modifier = Modifier) {
    Badge(modifier = modifier, containerColor = MaterialTheme.colorScheme.error)
}

// ---------------------------------------------------------------------------
// Bottom navigation
// ---------------------------------------------------------------------------

/**
 * Role bottom navigation bar. [highlightWithSecondary] switches the active-tab accent
 * from Special Blue (Specialist) to Orange (Pro Host); both come from the theme, so the
 * accent stays legible in dark mode.
 */
@Composable
fun ProHostBottomNavBar(
    tabs: List<AppNavTab>,
    activeTabId: String,
    onTabSelected: (String) -> Unit,
    highlightWithSecondary: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (highlightWithSecondary) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        // Role-colored strip with a soft bloom below it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, accent.copy(alpha = 0.6f), accent, accent.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent)))
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            // No fixed height: NavigationBar adds the system nav-bar inset inside its own bounds.
            modifier = Modifier.testTag("bottom_navigation_bar")
        ) {
            tabs.forEach { tab ->
                val isSelected = activeTabId == tab.id
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(tab.id) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.title
                        )
                    },
                    label = {
                        Text(
                            tab.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accent,
                        selectedTextColor = accent,
                        indicatorColor = accent.copy(alpha = 0.14f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_item_${tab.id}")
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Drawer
// ---------------------------------------------------------------------------

/** Drawer panel container: scales with the screen (cap 320dp), rounded on the content edge. */
@Composable
fun ProHostDrawerSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalDrawerSheet(
        modifier = modifier
            .fillMaxWidth(0.86f)
            .widthIn(max = 320.dp),
        drawerShape = DrawerShape,
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerTonalElevation = 2.dp,
        content = content
    )
}

/**
 * Drawer identity header on the brand gradient. [leading] is the avatar slot,
 * [pill] the optional role chip rendered under the name.
 */
@Composable
fun ProHostDrawerHeader(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    pill: (@Composable () -> Unit)? = null
) {
    val pro = MaterialTheme.proColors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(pro.brandHeaderStart, pro.brandHeaderEnd)))
            .padding(horizontal = Spacing.xl, vertical = Spacing.xl)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier = Modifier.fillMaxWidth()
        ) {
            leading()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = pro.onBrandHeader,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                pill?.invoke()
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = pro.onBrandHeader.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Collapse Drawer",
                    tint = pro.onBrandHeader
                )
            }
        }
    }
}

/** Initials avatar for the drawer header (secondary accent, theme-aware on-color). */
@Composable
fun ProHostDrawerAvatar(initials: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondary
        )
    }
}

/** Compact role chip for the drawer header: filled when [emphasized], translucent otherwise. */
@Composable
fun ProHostRolePill(text: String, emphasized: Boolean, modifier: Modifier = Modifier) {
    val pro = MaterialTheme.proColors
    val container = if (emphasized) MaterialTheme.colorScheme.secondary else pro.onBrandHeader.copy(alpha = 0.18f)
    val content = if (emphasized) MaterialTheme.colorScheme.onSecondary else pro.onBrandHeader
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.extraSmall, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs / 2)
        )
    }
}

/** Section label inside drawers ("PRO HOST", "MORE"...). */
@Composable
fun ProHostDrawerSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = Spacing.sm, bottom = Spacing.sm)
    )
}

/** Divider with the drawer's standard vertical rhythm. */
@Composable
fun ProHostDrawerDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = Spacing.lg),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

/**
 * Unified drawer row: 12dp pill, Special Blue selected state, [badgeDot] for new-item
 * indication, and an optional [selectedIconTint] (e.g. secondary for Pro Host items).
 */
@Composable
fun ProHostDrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeDot: Boolean = false,
    selectedIconTint: Color = MaterialTheme.colorScheme.primary,
    emphasized: Boolean = false
) {
    NavigationDrawerItem(
        label = {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (emphasized || selected) FontWeight.Bold else FontWeight.SemiBold
            )
        },
        selected = selected,
        onClick = onClick,
        icon = {
            BadgedBox(badge = { if (badgeDot) ProHostDotBadge() }) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) selectedIconTint else MaterialTheme.colorScheme.primary
                )
            }
        },
        shape = MaterialTheme.shapes.medium,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
    )
}

/** Pinned "Explore" call-to-action at the top of every drawer, on the brand gradient. */
@Composable
fun ProHostDrawerHighlight(
    title: String,
    subtitle: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.TravelExplore
) {
    val pro = MaterialTheme.proColors
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(pro.brandHeaderStart, pro.brandHeaderEnd)))
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(pro.onBrandHeader.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = pro.onBrandHeader)
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = pro.onBrandHeader, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = pro.onBrandHeader.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = pro.onBrandHeader)
        }
    }
}
