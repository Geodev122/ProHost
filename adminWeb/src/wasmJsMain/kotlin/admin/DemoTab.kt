package admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.FreshGreen
import com.example.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * ADMIN_PARITY items 57-63 (Demo Control): demo metrics, purge (with counts), display codes, search index, email-verified backfill, legacy-field migration.
 * "Generate demo content" is NOT offered on the web: on Android it is debug-build only (it writes fake users to the live project); recorded in DECISIONS.md.
 */
@Composable
fun DemoTab(gateway: AdminGateway) {
  var counts by remember { mutableStateOf<Json?>(null) }
  var notice by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var purgeOpen by remember { mutableStateOf(false) }
  var reload by remember { mutableStateOf(0) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(reload) { counts = try { Json.parse(gateway.call("adminCounts")) } catch (_: Throwable) { null } }
  val listings = counts?.get("demoListings")?.int() ?: 0
  val bookings = counts?.get("demoBookings")?.int() ?: 0
  val users = counts?.get("demoUsers")?.int() ?: 0

  fun run(label: String, block: suspend () -> String) {
    busy = true; notice = null
    scope.launch {
      notice = try { block() } catch (e: Throwable) { "$label failed: ${e.message}" }
      busy = false; reload++
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Demo Content Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
          "Generate or purge legitimate demo listings, fake rental requests, and demo specialist/host accounts for testing, client showcases, and UI verification before going live in production.",
          style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Active Demo Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          Metric("Demo Listings", listings, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
          Metric("Fake Requests", bookings, FreshGreen, Modifier.weight(1f))
          Metric("Demo Users", users, AmberWarning, Modifier.weight(1f))
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(onClick = { purgeOpen = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Purge Demo Content") }
        // Short public codes (U-/L-/D-/B-) are assigned automatically to new documents; this fills in everything created before codes existed.
        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
          run("Code backfill") { val c = Json.parse(gateway.call("backfillDisplayCodes")); "Codes assigned — users: ${c["users"].int()}, listings: ${c["listings"].int()}, bookings: ${c["bookings"].int()}" }
        }) { Text("Assign Display Codes to Existing Records") }
        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { run("Search index") { "Search index done: ${gateway.call("backfillSearchNames")}" } }) { Text("Index Names for Admin Search") }
        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { run("Email verification backfill") { "Email verification backfill done: ${gateway.call("backfillEmailVerified")}" } }) { Text("Mark Email/Google Sign-ups Email-Verified") }
        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { run("Legacy field migration") { "Legacy field migration done: ${gateway.call("migrateLegacyListingFields")}" } }) { Text("Migrate Legacy Listing Fields") }
      }
    }
  }

  if (purgeOpen) AlertDialog(
    onDismissRequest = { purgeOpen = false }, title = { Text("Purge All Demo Content?") },
    text = { Text("This will permanently delete all $listings demo listings, $bookings fake rental requests, and $users demo user accounts from Firestore and local memory before production.") },
    confirmButton = {
      Button(onClick = {
        purgeOpen = false
        run("Purge") { val r = Json.parse(gateway.call("purgeDemoContent")); "Purged all demo content (${r["total"].int()} items removed)" }
      }) { Text("Purge Everything", fontWeight = FontWeight.Bold) }
    },
    dismissButton = { OutlinedButton(onClick = { purgeOpen = false }) { Text("Cancel") } },
  )
}

@Composable
private fun Metric(label: String, value: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
  Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
    Column(Modifier.padding(Spacing.md)) {
      Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text("$value", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
    }
  }
}
