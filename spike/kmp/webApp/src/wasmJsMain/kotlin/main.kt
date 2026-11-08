import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import spike.SpikeApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Smoke signal for the browser test (scripts/migration/web_smoke.js): the page title reports whether Firebase JS initialised
    // through the Kotlin AdminGateway, with a dummy config (no network, no Firebase project needed).
    document.title = try {
        val gateway: AdminGateway = FirebaseWebGateway()
        "firebase:ok app=${gateway.appName} signedIn=${gateway.isSignedIn} callable=${gateway.callableExists("assignInitialRole")}"
    } catch (e: Throwable) {
        "firebase:error ${e.message}"
    }
    ComposeViewport(viewportContainerId = "root") { SpikeApp() }
}
