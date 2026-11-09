package admin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  // Smoke signal for scripts/migration/web_smoke.js: Firebase JS initialised through the Kotlin gateway.
  val gateway: AdminGateway = FirebaseWebGateway()
  document.title = "ProHost Admin"
  ComposeViewport(viewportContainerId = "root") { AdminApp(gateway) }
}
