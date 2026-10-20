package com.example.design

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.billing.PlayCatalog
import com.example.data.billing.PlayOfferText
import com.example.data.model.UserRole
import com.example.ui.theme.proHostScreenBackground
import com.example.hermeticRepository
import com.example.ui.preview.*
import com.example.ui.screens.*
import com.example.ui.theme.ProHostTheme
import com.example.ui.viewmodel.ProHostViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.R
import com.example.data.model.COUNTRIES
import com.example.ui.components.*
import com.example.ui.components.dialogs.DrawerDialogsHandler
import com.example.ui.viewmodel.AuthViewModel
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Design review screenshots: key screens, every sheet / dialog / form (OVERLAYS, captured with their own windows) ×
 * light / dark / font scale 1.3 / RTL / Arabic, for checking design changes
 * (docs/design/UI_AUDIT.md) before and after. Runs only under `recordRoborazziDebug` (workflow "Design
 * Screenshots"); the normal unit-test run skips it, so it never gates CI. A screen that can't render here
 * writes `<name>.error.txt` instead of failing.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class DesignScreenshotTest(private val screen: String, private val variant: String) {

    @get:Rule val compose = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun capture() {
        Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        // -PdesignOnly=a,b renders just those screens (quick checks); empty = everything.
        val only = System.getProperty("design.only").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        Assume.assumeTrue(only.isEmpty() || only.any { screen.startsWith(it) })
        val out = File(OUT_DIR).apply { mkdirs() }
        val name = "${screen}_$variant"
        try {
            val vm = signedInViewModel(if (screen in HOST_SCREENS) UserRole.PRO_HOST else UserRole.SPECIALIST)
            applyVariantToActivity(variant)
            compose.setContent { Variant(variant) { Screen(screen, vm) } }
            compose.waitForIdle()
            Thread.sleep(400) // let async image decoding land before the capture
            compose.waitForIdle()
            TAPS[screen]?.forEach { tap(it) }
            if (screen in OVERLAYS) captureWithWindows("$OUT_DIR/$name.png")
            else compose.onRoot().captureRoboImage(filePath = "$OUT_DIR/$name.png")
        } catch (e: Throwable) {
            File(out, "$name.error.txt").writeText(e.stackTraceToString())
        }
    }

    /** Dialogs and sheets live in their own windows: capture the whole screen, not just the activity's root. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureWithWindows(path: String) = captureScreenRoboImage(filePath = path)

    /** Opens an overlay the way a person does: scrolls to the control with this text and taps it. */
    private fun tap(res: Int) {
        val matcher = hasText(com.example.ui.util.UiText.get(res), substring = true)
        runCatching { compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(matcher) }
        compose.onAllNodes(matcher, useUnmergedTree = true).onFirst().performClick()
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
    }

    /**
     * Dark and Arabic go onto the activity's own resources: dialogs and sheets open in their own windows, which take
     * their configuration (locale, night mode) from the activity, not from the composition.
     */
    private fun applyVariantToActivity(variant: String) {
        if (variant != "dark" && variant != "ar") {
            com.example.ui.util.UiText.init(compose.activity.resources)
            return
        }
        compose.runOnUiThread {
            val res = compose.activity.resources
            val config = Configuration(res.configuration)
            if (variant == "dark") {
                config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            } else {
                config.setLocale(java.util.Locale("ar"))
                config.setLayoutDirection(java.util.Locale("ar"))
            }
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            com.example.ui.util.UiText.init(res, res)
        }
    }

    @Composable
    private fun Variant(variant: String, content: @Composable () -> Unit) {
        val config = Configuration(LocalConfiguration.current)
        if (variant == "dark") {
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        if (variant == "ar") {
            config.setLocale(java.util.Locale("ar"))
            config.setLayoutDirection(java.util.Locale("ar"))
        }
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalConfiguration provides config,
            LocalDensity provides Density(density.density, if (variant == "font130") 1.3f else density.fontScale),
            LocalLayoutDirection provides if (variant == "rtl" || variant == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            content = content
        )
    }

    /** Hermetic view model (never production Firebase) with the mock listings and a signed-in user. */
    private fun signedInViewModel(role: UserRole): ProHostViewModel {
        val repository = hermeticRepository()
        repository.replaceSpacesForTest(listOf(mockSpace1, mockSpace2, mockSpace3))
        runBlocking {
            // The host owns the mock listings (OWNER-01), so host sheets (Change price, verification) have data.
            repository.login(uid = if (role == UserRole.PRO_HOST) "OWNER-01" else "uid-design", email = "design@pro-host.tech", verifiedRole = role)
            repository.toggleSavedSpace(mockSpace1.id)
        }
        repository._hasLoadedBookingsOnce.value = true
        return ProHostViewModel(repository)
    }

    @Composable
    private fun Screen(screen: String, vm: ProHostViewModel) {
        when (screen) {
            "explore_list" -> Preview01_DiscoveryScreen()
            "explore_map" -> Preview03_MapViewScreen()
            "owner_hub" -> Preview06_OwnerHubScreen()
            "renting_progress" -> Preview08_ActiveTenanciesScreen()
            "sign_in" -> Preview11_SignInScreen()
            else -> ProHostTheme {
                when (screen) {
                    "space_details" -> SpaceDetailsScreenContent(
                        space = mockSpace1, viewModel = vm, acceptedBookings = emptyList(),
                        myRequestsForThisSpace = emptyList(), selectedFormula = mockFormula1,
                        currentUserRole = UserRole.SPECIALIST, onSelectFormula = {}, onWhatsAppClick = {},
                        onShareClick = {}, onBack = {}
                    )
                    "renting_requests" -> OwnerRentalRequestsScreenContent(
                        incomingRequests = listOf(mockBooking2, mockBooking1), spaces = listOf(mockSpace1, mockSpace2),
                        viewModel = vm
                    )
                    "my_rentals" -> MyBookingsScreen(viewModel = vm, onNavigateToDiscovery = {}, onSelectSpace = {})
                    "saved" -> MyFavoritesScreen(viewModel = vm, onSelectSpace = {})
                    "premium" -> OwnerSubscriptionsScreen(viewModel = vm)
                    "profile" -> SpecialistProfileScreen(viewModel = vm)
                    "owner_analytics" -> OwnerAnalyticsScreen(viewModel = vm)
                    "premium_plans" -> PremiumPlans()
                    "whatsapp_number" -> KycScreen(currentPhone = "", onSave = { null }, onKycComplete = {})
                    "manage_listing" -> ManageListingScreen(space = mockSpace1, viewModel = vm, onBack = {})
                    else -> Overlay(screen, vm)
                }
            }
        }
    }

    /** Every sheet, dialog and form, opened directly or (TAPS) through the screen that owns it. */
    @Composable
    private fun Overlay(screen: String, vm: ProHostViewModel) {
        val user by vm.currentUser.collectAsState()
        when (screen) {
            "splash" -> SplashScreen(onSplashCompleted = {})
            "login_email" -> LoginAuthScreen(onLoginSuccess = {}, authViewModel = AuthViewModel(vm.repository))
            "registration" -> LoginAuthScreen(onLoginSuccess = {}, resumeAtRegistration = true, authViewModel = AuthViewModel(vm.repository))
            "filters" -> ExploreWithFilters()
            "availability_sheet" -> SpaceDetailsScreenContent(
                space = mockSpace1, viewModel = vm, acceptedBookings = emptyList(),
                myRequestsForThisSpace = emptyList(), selectedFormula = mockFormula1,
                currentUserRole = UserRole.SPECIALIST, onSelectFormula = {}, onWhatsAppClick = {},
                onShareClick = {}, openAvailability = true, onBack = {}
            )
            "request_sent" -> RequestSentSheet(hasWhatsApp = true, onDismiss = {}, onViewRequest = {}, onWhatsApp = {})
            "requirements" -> user?.let { RequirementsSheet(user = it, viewModel = vm, onDismiss = {}, onReady = {}) }
            "requirements_host" -> user?.let { RequirementsSheet(user = it, viewModel = vm, onDismiss = {}, onReady = {}, requireAddress = true) }
            "rebook" -> RebookDialog(space = mockSpace1, initialFormula = mockFormula1, viewModel = vm, onDismiss = {}, onRequestSubmitted = {})
            "edit_booking" -> EditBookingDialog(space = mockSpace1, initialFormula = mockFormula1, replacesBookingId = mockBooking1.id,
                viewModel = vm, onDismiss = {}, onRequestSubmitted = {})
            "cancel_booking" -> CancelAcceptedBookingDialog(spaceTitle = mockSpace1.title, partyLabel = "host", onDismiss = {}, onConfirm = { _, _ -> })
            "country_picker" -> CountryPickerDialog(selected = COUNTRIES.first(), onSelect = {}, onDismiss = {})
            "legal_documents" -> DrawerDialogsHandler(dialogId = "legal_documents", viewModel = vm, onDismiss = {})
            "notifications" -> DrawerDialogsHandler(dialogId = "fcm_alerts", viewModel = vm, onDismiss = {})
            "analytics_consent" -> AnalyticsConsentDialog()
            "delete_account" -> SpecialistProfileScreen(viewModel = vm)
            "redeem_code" -> OwnerSubscriptionsScreen(viewModel = vm)
            "reject_request" -> OwnerRentalRequestsScreenContent(
                incomingRequests = listOf(mockBooking2, mockBooking1), spaces = listOf(mockSpace1, mockSpace2), viewModel = vm
            )
            "request_detail" -> OwnerRequestDetailSheet(request = mockBooking2, onDismiss = {}, onAccept = {}, onReject = {}, onWhatsApp = {}, onChangePrice = {})
            "change_price" -> ChangePriceSheet(viewModel = vm, onDismiss = {}, initialSpaceId = mockSpace1.id)
            "listing_wizard_1" -> CreateListingDialog(currentUser = user, onDismiss = {}, onListingCreated = {})
            "listing_wizard_2", "listing_wizard_3" ->
                CreateListingDialog(currentUser = user, onDismiss = {}, onListingCreated = {}, existingDraft = mockSpace1)
            "listing_verification" -> ListingVerificationDialog(space = mockSpace1, viewModel = vm, onDismiss = {})
            "probe_dialog_input" -> ProbeDialog(lazy = false, raw = false)
            "probe_dialog_raw" -> ProbeDialog(lazy = false, raw = true)
            "probe_dialog_lazy_input" -> ProbeDialog(lazy = true, raw = false)
            "probe_dialog_lazy_raw" -> ProbeDialog(lazy = true, raw = true)
            "probe_dialog_lazy" -> ProbeDialog(lazy = true, raw = null)
        }
    }

    /** Minimal dialogs isolating what keeps a dialog from settling (text field kind × a lazy list). */
    @Composable
    private fun ProbeDialog(lazy: Boolean, raw: Boolean?) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            androidx.compose.material3.Card {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (lazy) {
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 220.dp)) {
                            items(8) { androidx.compose.material3.Text("Row $it", Modifier.padding(8.dp)) }
                        }
                    }
                    when (raw) {
                        true -> androidx.compose.material3.OutlinedTextField(value = "", onValueChange = {}, label = { androidx.compose.material3.Text("Raw") })
                        false -> InputField(value = "", onValueChange = {}, label = "InputField")
                        null -> Unit
                    }
                }
            }
        }
    }

    /** Explore list with the filter sheet open (same data as the explore_list preview). */
    @Composable
    private fun ExploreWithFilters() {
        DiscoveryScreenContent(
            spaces = listOf(mockSpace1, mockSpace2, mockSpace3),
            filterState = com.example.ui.state.DiscoveryFilterState(),
            savedSpaceIds = listOf(mockSpace1.id),
            isMapView = false,
            showFilterSheet = true,
            onSearchQueryChange = {},
            onToggleMapView = {},
            onSetFilterSheetVisible = {},
            onSelectCategories = {},
            onSelectDivisionTypes = {},
            onSelectStrategies = {},
            onPriceRangeChange = {},
            onToggleVerifiedOnly = {},
            onToggleSavedSpace = {},
            onResetFilters = {},
            onSelectSpace = { _, _ -> },
            onQuickWhatsApp = { _, _ -> }
        )
    }

    /** Both plan tiles + the explainer with Play-shaped offers (Play itself isn't reachable in a unit test). */
    @Composable
    private fun PremiumPlans() {
        val monthly = PlayOfferText.OfferSummary(
            offerId = "free-4-months", headline = "Free", headlinePeriod = "month", original = "$8.99",
            discountPercent = 100, introSpan = null, trialSpan = "4 months", recurring = "$8.99", recurringPeriod = "month",
            introPeriods = listOf("P1M" to 4)
        )
        val yearly = PlayOfferText.OfferSummary(
            offerId = "launch-50-first-year", headline = "$39.99", headlinePeriod = "year", original = "$79.99",
            discountPercent = 50, introSpan = "first year", trialSpan = null, recurring = "$79.99", recurringPeriod = "year",
            introPeriods = listOf("P1Y" to 1), headlinePerMonth = "≈ $3.33 / month"
        )
        Column(
            Modifier.fillMaxSize().proHostScreenBackground().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlanTile(PlayCatalog.PlanKind.MONTHLY, monthly, selected = false, isCurrent = false, savingsPercent = null,
                    modifier = Modifier.weight(1f), onClick = {})
                PlanTile(PlayCatalog.PlanKind.YEARLY, yearly, selected = true, isCurrent = false, savingsPercent = 26,
                    modifier = Modifier.weight(1f), onClick = {})
            }
            PlanExplainer(yearly, hasOffer = true)
        }
    }

    companion object {
        const val OUT_DIR = "build/design-shots"

        val SCREENS = listOf(
            "explore_list", "explore_map", "space_details", "saved", "my_rentals", "profile",
            "premium", "premium_plans", "whatsapp_number", "owner_hub", "manage_listing", "renting_requests", "renting_progress", "owner_analytics", "sign_in"
        )
        /** Sheets, dialogs and forms (captured with their windows). */
        val OVERLAYS = listOf(
            "splash", "login_email", "registration", "filters", "availability_sheet", "request_sent", "requirements",
            "requirements_host", "rebook", "edit_booking", "cancel_booking", "country_picker", "legal_documents", "notifications",
            "analytics_consent", "delete_account", "redeem_code", "reject_request", "request_detail", "change_price",
            "listing_wizard_1", "listing_wizard_2", "listing_wizard_3", "listing_verification",
            "probe_dialog_input", "probe_dialog_raw", "probe_dialog_lazy_input", "probe_dialog_lazy_raw", "probe_dialog_lazy"
        )
        /** Overlays opened by tapping a control on their screen (string resources of the controls, in order). */
        val TAPS = mapOf(
            "delete_account" to listOf(R.string.delete_account),
            "redeem_code" to listOf(R.string.redeem_code),
            "reject_request" to listOf(R.string.reject),
            "listing_wizard_2" to listOf(R.string.next),
            "listing_wizard_3" to listOf(R.string.next, R.string.next)
        )
        val HOST_SCREENS = setOf(
            "renting_requests", "owner_analytics", "manage_listing", "requirements_host", "redeem_code", "reject_request",
            "request_detail", "change_price", "listing_wizard_1", "listing_wizard_2", "listing_wizard_3", "listing_verification"
        )
        val VARIANTS = listOf("light", "dark", "font130", "rtl", "ar")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> = (SCREENS + OVERLAYS).flatMap { s -> VARIANTS.map { v -> arrayOf<Any>(s, v) } }
    }
}
