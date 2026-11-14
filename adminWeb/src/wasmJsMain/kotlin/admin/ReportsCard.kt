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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Spacing
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import kotlinx.coroutines.launch

/**
 * Report/block review queue (Apple Guideline 1.2; approved mockup docs/migration/mockups/report-block.html, step 4). New on the web console:
 * the Android admin never had it. Reports are written by the `reportContent` callable; every decision goes through `adminResolveReport`
 * (audit log). "Hide listing" and "Suspend" call the existing callables first, so their own rules and audit entries apply.
 */
private val REASON_LABEL = mapOf(
  "MISLEADING" to "Misleading or false information",
  "SCAM" to "Scam or fraud",
  "OFFENSIVE" to "Offensive or inappropriate content",
  "UNSAFE" to "Unsafe space",
  "HARASSMENT" to "Harassment or abusive behaviour",
  "OTHER" to "Something else",
)

private class PendingAction(val report: Json, val action: String, val title: String, val text: String)

@Composable
fun ReportsCard(gateway: AdminGateway) {
  var reports by remember { mutableStateOf<List<Json>>(emptyList()) }
  var error by remember { mutableStateOf<String?>(null) }
  var notice by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableStateOf(0) }
  var busy by remember { mutableStateOf(false) }
  var pending by remember { mutableStateOf<PendingAction?>(null) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(reload) {
    error = null
    try { reports = Json.parse(gateway.reports()).list() } catch (e: Throwable) { error = "Couldn't load reports: ${e.message}" }
  }

  fun run(p: PendingAction) {
    busy = true; notice = null
    scope.launch {
      notice = try {
        val r = p.report
        when (p.action) {
          "hide_listing" -> gateway.call("setListingSubscriptionActive", """{"spaceId":${jsonQuote(r["listingId"].str())},"active":false}""")
          "suspend_user" -> gateway.call("setAccountSuspended", """{"targetUid":${jsonQuote(r["targetUid"].str())},"suspended":true}""")
        }
        gateway.call("adminResolveReport", """{"reportId":${jsonQuote(r["id"].str())},"action":${jsonQuote(p.action)}}""")
        "Done: ${p.title}"
      } catch (e: Throwable) { "${p.title} failed: ${e.message}" }
      busy = false; reload++
    }
  }

  val open = reports.count { it["status"].str() == "NEW" }
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
          Text("Reports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text("$open waiting for review · act within 24 hours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { reload++ }) { Text("Refresh") }
      }
      error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
      if (reports.isEmpty() && error == null) Text("No reports yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      reports.forEach { r ->
        HorizontalDivider()
        ReportRow(r, busy) { action ->
          val about = if (r["targetType"].str() == "listing") "listing ${r["listingCode"].str().ifBlank { r["listingId"].str() }}" else "user ${r["targetCode"].str().ifBlank { r["targetUid"].str() }}"
          pending = when (action) {
            "dismiss" -> PendingAction(r, action, "Dismiss report", "Close this report about $about with no action?")
            "hide_listing" -> PendingAction(r, action, "Hide listing", "Deactivate the reported $about? It disappears from Explore until reactivated in the Listings tab.")
            "suspend_user" -> PendingAction(r, action, "Suspend user", "Suspend the account behind this report ($about)? They can't sign in, and their listings are hidden.")
            else -> PendingAction(r, action, "Reopen report", "Put this report back in the queue?")
          }
        }
      }
    }
  }

  pending?.let { p ->
    AlertDialog(
      onDismissRequest = { pending = null }, title = { Text(p.title + "?") }, text = { Text(p.text) },
      confirmButton = { Button(onClick = { pending = null; run(p) }) { Text(p.title) } },
      dismissButton = { OutlinedButton(onClick = { pending = null }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun ReportRow(r: Json, busy: Boolean, onAction: (String) -> Unit) {
  val status = r["status"].str()
  val isNew = status == "NEW"
  val created = r["createdAtMillis"].num() ?: 0.0
  val due = r["dueAtMillis"].num() ?: 0.0
  val hoursLeft = ((due - nowMillis()) / 3_600_000.0).toInt()
  val isListing = r["targetType"].str() == "listing"
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Surface(color = if (isNew) StatusErrorContainer else StatusSuccessContainer, shape = MaterialTheme.shapes.extraSmall) {
        Text(
          when {
            !isNew -> if (status == "DISMISSED") "Dismissed" else "Action taken"
            hoursLeft >= 0 -> "New · $hoursLeft h left"
            else -> "New · overdue"
          },
          style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isNew) StatusError else StatusSuccess,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }
      Text(dateTimeLocal(created), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
      if (isListing) "Listing ${r["listingCode"].str()} · ${r["listingTitle"].str()}" else "Person ${r["targetCode"].str()}" + r["bookingId"].str().let { if (it.isBlank()) "" else " · from request $it" },
      style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
    )
    Text(REASON_LABEL[r["reason"].str()] ?: r["reason"].str(), style = MaterialTheme.typography.bodyMedium)
    r["details"].str().takeIf { it.isNotBlank() }?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text("Reported by ${r["reporterCode"].str().ifBlank { r["reporterUid"].str() }}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
      if (isNew) {
        TextButton(enabled = !busy, onClick = { onAction("dismiss") }) { Text("Dismiss") }
        if (r["listingId"].str().isNotBlank()) TextButton(enabled = !busy, onClick = { onAction("hide_listing") }) { Text("Hide listing") }
        if (r["targetUid"].str().isNotBlank()) TextButton(enabled = !busy, onClick = { onAction("suspend_user") }) { Text(if (isListing) "Suspend host" else "Suspend user") }
      } else {
        TextButton(enabled = !busy, onClick = { onAction("reopen") }) { Text("Reopen") }
      }
    }
  }
}
