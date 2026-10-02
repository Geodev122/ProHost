package com.example.ui.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProHostViewModel

// ============================================================================
// MOCK DATA FOR ALL 11 FEATURE PREVIEWS
// ============================================================================

val mockFormula1 = RentalFormula(
    id = "FRM-001",
    type = RentalFormulaType.SHIFT,
    rateUsd = 350.0,
    scheduleDescription = "Morning Shift (08:00 - 14:00)",
    daysOfWeek = listOf("Mon", "Wed", "Fri"),
    startHour = "08:00",
    endHour = "14:00",
    totalWeeklyHours = 18,
    shiftName = "Morning Shift"
)

val mockFormula2 = RentalFormula(
    id = "FRM-002",
    type = RentalFormulaType.DAY_PER_WEEK,
    rateUsd = 600.0,
    scheduleDescription = "Full Day Access (2 Days/Wk)",
    daysOfWeek = listOf("Tue", "Thu"),
    startHour = "08:00",
    endHour = "18:00",
    totalWeeklyHours = 20
)

val mockFormula3 = RentalFormula(
    id = "FRM-003",
    type = RentalFormulaType.HOURLY,
    rateUsd = 40.0,
    scheduleDescription = "Hourly Slot Access",
    daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
    startHour = "08:00",
    endHour = "20:00",
    minHours = 2
)

val mockSpace1 = SpaceListing(
    id = "SP-001",
    title = "Achrafieh Executive Medical Suite",
    description = "Fully furnished medical polyclinic with state-of-the-art diagnostic equipment",
    spaceType = SpaceType.POLYCLINIC,
    governorate = Governorate.BEIRUT,
    district = "Achrafieh",
    streetAddress = "Sursock Street, Suite 204",
    floorInfo = "2nd Floor",
    lat = 33.8886,
    lng = 35.5142,
    isShared = true,
    complementarySpecialties = listOf("Cardiology", "Dermatology", "Pediatrics"),
    residentPractitioners = listOf("Dr. Sami Haddad", "Dr. Youssef Khoury"),
    essentialFacilities = listOf("High-Speed Wi-Fi", "Receptionist Lounge", "Sterilization Suite", "Ultrasound Machine"),
    equipment = listOf(
        EquipmentItem("EQ-1", "Hydraulic Exam Table", EquipmentCategory.WORKSPACES, 1, "Electric position control"),
        EquipmentItem("EQ-2", "Echocardiogram", EquipmentCategory.WORKSPACES, 1, "4K Color Doppler")
    ),
    rentalFormulas = listOf(mockFormula1, mockFormula2, mockFormula3),
    rules = PremisesRules(
        smokingAllowed = false,
        foodAllowed = true,
        visitorPolicy = "Clients & visitors welcomed in reception lounge"
    ),
    ownerId = "OWNER-01",
    ownerName = "Achrafieh Commercial Properties",
    ownerPhone = "+9613123456",
    ownerEmail = "host.achrafieh@prohost.lb",
    baseMonthlyRateUsd = 850.0,
    status = ListingStatus.ACTIVE,
    isVerified = true,
    ownershipProofUrl = "https://example.com/deed.pdf",
    imageUrls = listOf("https://images.unsplash.com/photo-1629909613654-28e377c37b09")
)

val mockSpace2 = SpaceListing(
    id = "SP-002",
    title = "Badaro Dental & Surgical Hub",
    description = "Modern dental clinic with autonomous sterilization and digital X-ray",
    spaceType = SpaceType.CENTER,
    governorate = Governorate.BEIRUT,
    district = "Badaro",
    streetAddress = "Badaro Main Street, Center 12",
    floorInfo = "1st Floor",
    lat = 33.8750,
    lng = 35.5200,
    isShared = true,
    complementarySpecialties = listOf("Dentistry", "Orthodontics"),
    residentPractitioners = listOf("Dr. Tariq Mansour"),
    essentialFacilities = listOf("Dental Chair", "X-Ray Autoclave", "Waiting Lounge"),
    equipment = listOf(EquipmentItem("EQ-3", "Dental Chair", EquipmentCategory.WORKSPACES, 1, "Full hydraulic")),
    rentalFormulas = listOf(mockFormula2),
    rules = PremisesRules(),
    ownerId = "OWNER-01",
    ownerName = "Achrafieh Commercial Properties",
    ownerPhone = "+9613123456",
    ownerEmail = "host.achrafieh@prohost.lb",
    baseMonthlyRateUsd = 950.0,
    status = ListingStatus.ACTIVE,
    isVerified = true,
    imageUrls = listOf("https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d")
)

val mockSpace3 = SpaceListing(
    id = "SP-003",
    title = "Verdun Wellness & Polyclinic Suites",
    description = "Quiet private office for consultation and wellness practice",
    spaceType = SpaceType.PRIVATE_OFFICE,
    governorate = Governorate.BEIRUT,
    district = "Verdun",
    streetAddress = "Verdun Street, Suite 501",
    floorInfo = "5th Floor",
    lat = 33.8850,
    lng = 35.4850,
    isShared = false,
    complementarySpecialties = listOf("Psychology", "Nutrition"),
    residentPractitioners = listOf("Dr. Maya Ziad"),
    essentialFacilities = listOf("Private Restroom", "High-Speed Internet", "Janitorial Service"),
    equipment = emptyList(),
    rentalFormulas = listOf(mockFormula1),
    rules = PremisesRules(),
    ownerId = "OWNER-02",
    ownerName = "Verdun Real Estate",
    ownerPhone = "+9611223344",
    ownerEmail = "info@verdunre.lb",
    baseMonthlyRateUsd = 700.0,
    status = ListingStatus.ACTIVE,
    isVerified = true,
    imageUrls = listOf("https://images.unsplash.com/photo-1586773860418-d37222d8fce3")
)

val mockSpaceDraft = SpaceListing(
    id = "SP-004",
    title = "Hazmieh Modern Orthopedic Suite",
    description = "Draft workspace listing waiting for publishing details",
    spaceType = SpaceType.POLYCLINIC,
    governorate = Governorate.MOUNT_LEBANON,
    district = "Hazmieh",
    streetAddress = "Damascus Highway",
    floorInfo = "Ground Floor",
    lat = 33.8500,
    lng = 35.5400,
    isShared = true,
    complementarySpecialties = listOf("Orthopedics"),
    residentPractitioners = emptyList(),
    essentialFacilities = listOf("24/7 Generator", "Elevator"),
    equipment = emptyList(),
    rentalFormulas = listOf(mockFormula1),
    rules = PremisesRules(),
    ownerId = "OWNER-01",
    ownerName = "Achrafieh Commercial Properties",
    ownerPhone = "+9613123456",
    ownerEmail = "host.achrafieh@prohost.lb",
    baseMonthlyRateUsd = 600.0,
    status = ListingStatus.DRAFT,
    isVerified = false
)

val mockBooking1 = BookingRequest(
    id = "BK-101",
    spaceId = mockSpace1.id,
    spaceTitle = mockSpace1.title,
    spaceDistrict = mockSpace1.district,
    governorate = mockSpace1.governorate,
    ownerId = "OWNER-01",
    ownerName = "Achrafieh Commercial Properties",
    ownerPhone = "+9613123456",
    practitionerId = "DR-001",
    practitionerName = "Dr. Layla Karam",
    practitionerEmail = "dr.layla@medical.lb",
    practitionerPhone = "+96170112233",
    practitionerSpecialty = "Dermatologist",
    formula = mockFormula1,
    startDate = "2026-10-01",
    selectedDateTimeRange = "Mon, Wed, Fri (08:00 - 14:00)",
    durationMonths = 3,
    totalAmountUsd = 1050.0,
    status = BookingRequestStatus.ACCEPTED,
    paymentAcknowledgedByHost = false
)

val mockBooking2 = BookingRequest(
    id = "BK-102",
    spaceId = mockSpace2.id,
    spaceTitle = mockSpace2.title,
    spaceDistrict = mockSpace2.district,
    governorate = mockSpace2.governorate,
    ownerId = "OWNER-01",
    ownerName = "Achrafieh Commercial Properties",
    ownerPhone = "+9613123456",
    practitionerId = "DR-002",
    practitionerName = "Dr. Tariq Mansour",
    practitionerEmail = "dr.tariq@dental.lb",
    practitionerPhone = "+9613998877",
    practitionerSpecialty = "Dentist",
    formula = mockFormula2,
    startDate = "2026-10-15",
    selectedDateTimeRange = "Tue, Thu (08:00 - 18:00)",
    durationMonths = 1,
    totalAmountUsd = 600.0,
    status = BookingRequestStatus.PENDING,
    paymentAcknowledgedByHost = false
)

val mockPackagePriority = PackagePlan(
    id = "pkg_priority",
    name = "Priority Host Plan",
    description = "Up to 5 Verified Listings with Priority Search Ranking",
    badgeName = "Most Popular",
    priceUsd = 29.99,
    isFeatured = true
)

// ============================================================================
// 11 PREVIEW COMPOSABLES FOR ALL REQUESTED SCREENS
// ============================================================================

/** 1. Discovery — search bar, space type chips, 3 verified listing cards */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun Preview01_DiscoveryScreen() {
    ProHostTheme {
        DiscoveryScreenContent(
            spaces = listOf(mockSpace1, mockSpace2, mockSpace3),
            searchQuery = "Achrafieh Medical",
            selectedGovernorate = Governorate.BEIRUT,
            categoryOptions = listOf(
                SchemaItem("POLYCLINIC", "Polyclinic", "Multi-specialty polyclinic", "SPACE_TYPE"),
                SchemaItem("CENTER", "Medical Center", "Full center facility", "SPACE_TYPE"),
                SchemaItem("PRIVATE_OFFICE", "Private Office", "Private consultation office", "SPACE_TYPE"),
                SchemaItem("COWORKING", "Co-working Hub", "Shared practice hub", "SPACE_TYPE")
            ),
            selectedCategoryId = "POLYCLINIC",
            selectedStrategyType = null,
            onlyVerified = true,
            onlySaved = false,
            savedSpaceIds = listOf(mockSpace1.id),
            isMapView = false,
            showFilterSheet = false,
            onSearchQueryChange = {},
            onToggleMapView = {},
            onSetFilterSheetVisible = {},
            onSelectGovernorate = {},
            onSelectCategory = {},
            onSelectStrategyType = {},
            onToggleVerifiedOnly = {},
            onToggleSavedOnly = {},
            onToggleSavedSpace = {},
            onResetFilters = {},
            onSelectSpace = { _, _ -> },
            onQuickWhatsApp = {}
        )
    }
}

/** 2. Space Details — hero photo, slot picker, hourly/daily/monthly toggle, amenity chips, CTA */
@Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
fun Preview02_SpaceDetailsScreen() {
    ProHostTheme {
        val vm = remember { ProHostViewModel() }
        SpaceDetailsScreenContent(
            space = mockSpace1,
            viewModel = vm,
            acceptedBookings = listOf(mockBooking1),
            myRequestsForThisSpace = listOf(mockBooking1),
            selectedFormula = mockFormula1,
            currentUserRole = UserRole.SPECIALIST,
            onSelectFormula = {},
            onWhatsAppClick = {},
            onShareClick = {},
            isSaved = true,
            onToggleSave = {},
            onBack = {}
        )
    }
}

/** 3. Map View — full-screen map with 4 color-coded pins + bottom carousel */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun Preview03_MapViewScreen() {
    ProHostTheme {
        DiscoveryScreenContent(
            spaces = listOf(mockSpace1, mockSpace2, mockSpace3, mockSpaceDraft),
            searchQuery = "Beirut Workspaces",
            selectedGovernorate = Governorate.BEIRUT,
            categoryOptions = listOf(
                SchemaItem("POLYCLINIC", "Polyclinic", "Multi-specialty polyclinic", "SPACE_TYPE"),
                SchemaItem("CENTER", "Medical Center", "Full center facility", "SPACE_TYPE")
            ),
            selectedCategoryId = null,
            selectedStrategyType = null,
            onlyVerified = false,
            onlySaved = false,
            savedSpaceIds = emptyList(),
            isMapView = true,
            showFilterSheet = false,
            onSearchQueryChange = {},
            onToggleMapView = {},
            onSetFilterSheetVisible = {},
            onSelectGovernorate = {},
            onSelectCategory = {},
            onSelectStrategyType = {},
            onToggleVerifiedOnly = {},
            onToggleSavedOnly = {},
            onToggleSavedSpace = {},
            onResetFilters = {},
            onSelectSpace = { _, _ -> },
            onQuickWhatsApp = {}
        )
    }
}

/** 4. My Bookings — digital key pass card with progress bar, active/pending tabs */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun Preview04_MyBookingsScreen() {
    ProHostTheme {
        val vm = remember { ProHostViewModel() }
        MyBookingsScreen(
            viewModel = vm,
            onNavigateToDiscovery = {},
            onSelectSpace = {}
        )
    }
}

/** 5. My Favorites — grouped by category with heart icons */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun Preview05_MyFavoritesScreen() {
    ProHostTheme {
        val vm = remember { ProHostViewModel() }
        MyFavoritesScreen(
            viewModel = vm,
            onSelectSpace = {}
        )
    }
}

/** 6. Owner Hub — stat tiles, active/draft listing cards, + New button */
@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
fun Preview06_OwnerHubScreen() {
    ProHostTheme {
        OwnerHubScreenContent(
            ownerSpaces = listOf(mockSpace1, mockSpace2, mockSpaceDraft),
            allBookingRequests = listOf(mockBooking1, mockBooking2),
            currentPackage = mockPackagePriority,
            ownerPackageExpiryMillis = System.currentTimeMillis() + 15 * 86400000L,
            onSelectSpace = {},
            onManageSpace = {},
            onOpenRenewal = {},
            onOpenCreateListing = {},
            onOpenPackageSelection = {}
        )
    }
}

/** 7. Rental Requests — verified tenant cards with Accept/Decline */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun Preview07_RentalRequestsScreen() {
    ProHostTheme {
        val vm = remember { ProHostViewModel() }
        OwnerRentalRequestsScreenContent(
            incomingRequests = listOf(mockBooking2, mockBooking1),
            spaces = listOf(mockSpace1, mockSpace2),
            viewModel = vm
        )
    }
}

/** 8. Active Tenancies — payment warning badge, WhatsApp button, progress bars */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun Preview08_ActiveTenanciesScreen() {
    ProHostTheme {
        OwnerRentingProgressScreenContent(
            ownerSpaces = listOf(mockSpace1, mockSpace2),
            activeBookings = listOf(mockBooking1),
            currentPackage = mockPackagePriority,
            ownerPackageExpiryMillis = System.currentTimeMillis() + 5 * 86400000L,
            onWhatsAppPractitioner = {},
            onSendPaymentReminder = {}
        )
    }
}

/** 9. Analytics — weekly occupancy bar chart, subdivision breakdown */
@Preview(showBackground = true, widthDp = 390, heightDp = 950)
@Composable
fun Preview09_AnalyticsScreen() {
    ProHostTheme {
        val vm = remember { ProHostViewModel() }
        OwnerAnalyticsScreen(viewModel = vm)
    }
}

/** 10. Subscriptions — Priority vs Featured plan cards with Google Play billing CTA */
@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
fun Preview10_SubscriptionsScreen() {
    ProHostTheme {
        val vm = remember { ProHostViewModel() }
        OwnerSubscriptionsScreen(viewModel = vm)
    }
}

/** 11. Sign-In — Google/email social buttons + phone OTP input */
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun Preview11_SignInScreen() {
    ProHostTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(premiumBackgroundBrush())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Welcome to ProHost",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Sign in or create your account",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            ModernCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(20.dp)
            ) {
                OutlinedTextField(
                    value = "dr.layla@medical.lb",
                    onValueChange = {},
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                ProPrimaryButton(
                    text = "Continue with Email",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Google",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Continue with Google")
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign in with Phone OTP instead")
                }
            }
        }
    }
}
