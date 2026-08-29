package com.example.ui.components.drawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppUser
import com.example.ui.components.ProSpaceBrandLogo
import com.example.ui.components.ProSpaceCedarBadge
import com.example.ui.theme.*

@Composable
fun ProfessionalDrawerContent(
    currentUser: AppUser?,
    activeTabId: String,
    onTabSelected: (String) -> Unit,
    onDrawerAction: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Oxford Blue & Carnation Orange Practitioner Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = OxfordBlue,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Surface(
                    color = CarnationOrange,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentUser?.fullName?.take(1) ?: "P",
                            color = PureWhite,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = currentUser?.fullName ?: "Practitioner Member",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = PureWhite
                )
                Text(
                    text = currentUser?.specialty ?: "Licensed Professional",
                    style = MaterialTheme.typography.bodySmall,
                    color = LightGray,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = CoolGray.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = FreshGreen, modifier = Modifier.size(13.dp))
                        Text(
                            text = "Syndicate: ${currentUser?.syndicateNumber ?: "OEA-LB-VERIFIED"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                }
            }
        }

        Text(
            text = "PRIMARY CORES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CoolGray,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("Explore Listings", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "search_map",
            onClick = { onTabSelected("search_map") },
            icon = { Icon(Icons.Default.Search, contentDescription = null, tint = if (activeTabId == "search_map") CarnationOrange else OxfordBlue) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                selectedTextColor = OxfordBlue,
                unselectedTextColor = CoolGray
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("My Bookings & Reservations", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "pro_rentals",
            onClick = { onTabSelected("pro_rentals") },
            icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = if (activeTabId == "pro_rentals") CarnationOrange else OxfordBlue) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                selectedTextColor = OxfordBlue,
                unselectedTextColor = CoolGray
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Professional Profile", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "pro_profile",
            onClick = { onTabSelected("pro_profile") },
            icon = { Icon(Icons.Default.Person, contentDescription = null, tint = if (activeTabId == "pro_profile") CarnationOrange else OxfordBlue) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                selectedTextColor = OxfordBlue,
                unselectedTextColor = CoolGray
            )
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = LightGray)

        Text(
            text = "RENTER TABS (DRAWER ONLY)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CarnationOrange,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("Pending Rent Requests", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("pro_pending") },
            icon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = OxfordBlue) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Payment Due Reminders", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("pro_dues") },
            icon = { Icon(Icons.Default.NotificationImportant, contentDescription = null, tint = CarnationOrange) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Real-time Push Alerts", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("fcm_alerts") },
            icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = VibrantBlue) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Syndicate Verification", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("pro_syndicate") },
            icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = FreshGreen) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Lebanese Rent Laws", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("pro_laws") },
            icon = { Icon(Icons.Default.Gavel, contentDescription = null, tint = OxfordBlue) }
        )

        ProSpaceDrawerFooter()
    }
}

@Composable
fun OwnerDrawerContent(
    currentUser: AppUser?,
    activeTabId: String,
    onTabSelected: (String) -> Unit,
    onDrawerAction: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Oxford Blue & Fresh Green Host Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = OxfordBlue,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Surface(
                    color = FreshGreen,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentUser?.fullName?.take(1) ?: "H",
                            color = PureWhite,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = currentUser?.fullName ?: "Workspace Host",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = PureWhite
                )
                Text(
                    text = currentUser?.specialty ?: "Commercial Host Node",
                    style = MaterialTheme.typography.bodySmall,
                    color = LightGray,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = CoolGray.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = BrightOrange, modifier = Modifier.size(13.dp))
                        Text(
                            text = "Premium Host Node",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                }
            }
        }

        Text(
            text = "HOST CONTROLS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CoolGray,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("My Workspace Listings", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "manage_listings",
            onClick = { onTabSelected("manage_listings") },
            icon = { Icon(Icons.Default.HomeWork, contentDescription = null, tint = if (activeTabId == "manage_listings") FreshGreen else OxfordBlue) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                selectedTextColor = OxfordBlue,
                unselectedTextColor = CoolGray
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Renting Requests", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "owner_requests",
            onClick = { onTabSelected("owner_requests") },
            icon = { Icon(Icons.Default.Inbox, contentDescription = null, tint = if (activeTabId == "owner_requests") FreshGreen else OxfordBlue) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                selectedTextColor = OxfordBlue,
                unselectedTextColor = CoolGray
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Renting Progress", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "owner_progress",
            onClick = { onTabSelected("owner_progress") },
            icon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = if (activeTabId == "owner_progress") FreshGreen else OxfordBlue) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                selectedTextColor = OxfordBlue,
                unselectedTextColor = CoolGray
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Financial Stats & Yields", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "stats",
            onClick = { onTabSelected("stats") },
            icon = { Icon(Icons.Default.Analytics, contentDescription = null, tint = if (activeTabId == "stats") FreshGreen else OxfordBlue) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                selectedTextColor = OxfordBlue,
                unselectedTextColor = CoolGray
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Host Profile Settings", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "owner_profile",
            onClick = { onTabSelected("owner_profile") },
            icon = { Icon(Icons.Default.Person, contentDescription = null, tint = if (activeTabId == "owner_profile") FreshGreen else OxfordBlue) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                selectedTextColor = OxfordBlue,
                unselectedTextColor = CoolGray
            )
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = LightGray)

        Text(
            text = "HOST POWER-UPS (DRAWER ONLY)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = FreshGreen,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("Social Reels Campaign", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("owner_reels") },
            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = InstagramPink) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Whish Settlements Log", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("owner_whish") },
            icon = { Icon(Icons.Default.Receipt, contentDescription = null, tint = CarnationOrange) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("House Guidelines", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("owner_guidelines") },
            icon = { Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = OxfordBlue) }
        )

        ProSpaceDrawerFooter()
    }
}

@Composable
fun AdminDrawerContent(
    currentUser: AppUser?,
    activeTabId: String,
    onTabSelected: (String) -> Unit,
    onDrawerAction: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Oxford Blue & Vibrant Blue Super Admin Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = OxfordBlue,
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Surface(
                    color = VibrantBlue,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentUser?.fullName?.take(1) ?: "A",
                            color = PureWhite,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = currentUser?.fullName ?: "Geo El-Najjar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = PureWhite
                )
                Text(
                    text = "Platform Super Admin Node",
                    style = MaterialTheme.typography.bodySmall,
                    color = LightGray,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = CoolGray.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = BrightOrange, modifier = Modifier.size(13.dp))
                        Text(
                            text = "Admin Access: Root Node",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                }
            }
        }

        Text(
            text = "CENTRAL SECURITY CORES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = CoolGray,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("System Admin Console", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "admin_console",
            onClick = { onTabSelected("admin_console") },
            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = if (activeTabId == "admin_console") CarnationOrange else OxfordBlue) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                selectedTextColor = OxfordBlue,
                unselectedTextColor = CoolGray
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Security ID Card", fontWeight = FontWeight.Bold) },
            selected = activeTabId == "admin_profile",
            onClick = { onTabSelected("admin_profile") },
            icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = if (activeTabId == "admin_profile") CarnationOrange else OxfordBlue) },
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = OxfordBlue.copy(alpha = 0.08f),
                selectedTextColor = OxfordBlue,
                unselectedTextColor = CoolGray
            )
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = LightGray)

        Text(
            text = "SYSTEM AUDIT & PRICING (DRAWER ONLY)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = OxfordBlue,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        NavigationDrawerItem(
            label = { Text("System Audit Logs", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("admin_audit") },
            icon = { Icon(Icons.Default.Terminal, contentDescription = null, tint = OxfordBlue) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Governorate Nodes Status", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("admin_gov") },
            icon = { Icon(Icons.Default.Dns, contentDescription = null, tint = VibrantBlue) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Financial Baseline Slider", fontWeight = FontWeight.SemiBold) },
            selected = false,
            onClick = { onDrawerAction("admin_forecast") },
            icon = { Icon(Icons.Default.BarChart, contentDescription = null, tint = FreshGreen) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        NavigationDrawerItem(
            label = { Text("Firebase & System Debugger", fontWeight = FontWeight.Bold) },
            selected = false,
            onClick = { onDrawerAction("system_debugger") },
            icon = { Icon(Icons.Default.BugReport, contentDescription = null, tint = AmberWarning) }
        )

        ProSpaceDrawerFooter()
    }
}

@Composable
fun ProSpaceDrawerFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = LightGray)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProSpaceBrandLogo(
                size = 28.dp,
                roundedCorner = 7.dp,
                elevation = 1.dp,
                showBorder = true
            )
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "ProSpace Lebanon",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = OxfordBlue
                    )
                    ProSpaceCedarBadge(text = "v2.4", isCompact = true)
                }
                Text(
                    text = "Syndicate & OEA Verified Workspace Grid",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = CoolGray
                )
            }
        }
    }
}

@Composable
fun LawBulletinCard(number: String, title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightGray.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, LightGrayCardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = OxfordBlue,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(number, color = PureWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = OxfordBlue)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(content, style = MaterialTheme.typography.bodySmall, color = CoolGray)
        }
    }
}
