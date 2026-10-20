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
import com.github.takahirom.roborazzi.captureRoboImage
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
 * Design review screenshots: key screens × light / dark / font scale 1.3 / RTL, for checking design changes
 * (docs/design/UI_AUDIT.md) before and after. Runs only under `recordRoborazziDebug` (workflow "Design
 * Screenshots"); the normal unit-test run skips it, so it never gates CI. A screen that can't render here
 * writes `<name>.error.txt` instead of failing.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi", sdk = [34])
class DesignScreenshotTest(private val screen: String, private val variant: String) {

    @get:Rule val compose = createComposeRule()

    @Test
    fun capture() {
        Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        val out = File(OUT_DIR).apply { mkdirs() }
        val name = "${screen}_$variant"
        try {
            val vm = signedInViewModel(if (screen in HOST_SCREENS) UserRole.PRO_HOST else UserRole.SPECIALIST)
            compose.setContent { Variant(variant) { Screen(screen, vm) } }
            compose.waitForIdle()
            Thread.sleep(400) // let async image decoding land before the capture
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(filePath = "$OUT_DIR/$name.png")
        } catch (e: Throwable) {
            File(out, "$name.error.txt").writeText(e.stackTraceToString())
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
        val baseContext = androidx.compose.ui.platform.LocalContext.current
        val registryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
        val context = if (variant == "ar") baseContext.createConfigurationContext(config) else baseContext
        com.example.ui.util.UiText.init(context.resources)
        val density = LocalDensity.current
        CompositionLocalProvider(
            androidx.compose.ui.platform.LocalContext provides context,
            // The Arabic context isn't the activity: keep its result registry for screens with launchers.
            androidx.activity.compose.LocalActivityResultRegistryOwner provides registryOwner!!,
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
            repository.login(uid = "uid-design", email = "design@pro-host.tech", verifiedRole = role)
            repository.toggleSavedSpace(mockSpace1.id)
        }
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
                }
            }
        }
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
        val HOST_SCREENS = setOf("renting_requests", "owner_analytics", "manage_listing")
        val VARIANTS = listOf("light", "dark", "font130", "rtl", "ar")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun params(): List<Array<Any>> = SCREENS.flatMap { s -> VARIANTS.map { v -> arrayOf<Any>(s, v) } }
    }
}
