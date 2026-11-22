package admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Spacing
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

/**
 * Diagnostics (ADMIN_PARITY item 64, decision 4): the web counterpart of the Android debug-only "System & Firebase debugger".
 * The browser can't inspect the phone's local state, so the checks are the ones that matter from here: this admin session,
 * the callables, Firestore reads under the admin rules, Google Play billing and its notification heartbeat, and the review
 * queues. Health score = (passed + ½ warnings) / checks, as on Android. Read-only: nothing is written.
 */
private enum class Status { PASSED, WARNING, FAILED }

private class Check(val category: String, val name: String, val status: Status, val details: String, val ms: Long)

private suspend fun timed(category: String, name: String, block: suspend () -> Pair<Status, String>): Check {
  val start = nowMillis()
  val (status, details) = try { block() } catch (e: Throwable) { Status.FAILED to (e.message ?: "failed") }
  return Check(category, name, status, details, (nowMillis() - start).toLong())
}

private fun ageLabel(millis: Double?): String {
  if (millis == null) return "never"
  val hours = (nowMillis() - millis) / 3_600_000.0
  return if (hours < 48) "${hours.toInt()} h ago" else "${(hours / 24).toInt()} days ago (${dateLabel(millis)})"
}

private suspend fun runChecks(gateway: AdminGateway, email: String): List<Check> {
  // One Play probe for the three billing checks.
  var health: Json? = null
  suspend fun billing(): Json = health ?: Json.parse(gateway.call("billingHealthCheck")).also { health = it }
  return listOf(
  timed("Authentication & Identity", "Admin session") {
    Status.PASSED to "Signed in as $email with the ADMIN role claim (rules and callables enforce it again server-side)."
  },
  timed("Cloud Functions", "Admin callables answer (adminCounts)") {
    val c = Json.parse(gateway.call("adminCounts"))
    Status.PASSED to c.entries().joinToString(" · ") { (k, v) -> "$k ${v.cell()}" }.ifBlank { "answered" }
  },
  timed("Firestore", "Audit log readable") {
    val n = Json.parse(gateway.auditLogs()).list().size
    (if (n > 0) Status.PASSED else Status.WARNING) to (if (n > 0) "$n recent entries." else "No audit entries returned.")
  },
  timed("Firestore", "Verification review queue") {
    val n = Json.parse(gateway.reviewQueue()).list().size
    Status.PASSED to (if (n == 0) "Nothing waiting." else "$n listing(s) waiting for review (Listings tab).")
  },
  timed("Trust & Safety", "Reports queue") {
    val open = Json.parse(gateway.reports()).list().count { it["status"].str("open") == "open" }
    (if (open == 0) Status.PASSED else Status.WARNING) to (if (open == 0) "No open reports." else "$open open report(s): review within 24 hours (Security tab).")
  },
  timed("Google Play billing", "Play Developer API access") {
    val h = billing()
    val ok = h["playApi"].str() == "ok"
    (if (ok) Status.PASSED else Status.FAILED) to h["message"].str(if (ok) "OK" else "Play API not reachable")
  },
  timed("Google Play billing", "Real-time notifications heartbeat") {
    val h = billing()
    val last = h["lastRtdnAt"].num()
    val days = last?.let { (nowMillis() - it) / 86_400_000.0 }
    val status = when { last == null -> Status.WARNING; days!! > 7 -> Status.WARNING; else -> Status.PASSED }
    status to "Last message from Play: ${ageLabel(last)} · server self-test: ${ageLabel(h["lastSelfTestAt"].num())}."
  },
  timed("Google Play billing", "Purchases needing attention") {
    val h = billing()
    val pending = h["pendingCount"].int(); val unlinked = h["unlinkedCount"].int()
    (if (pending + unlinked == 0) Status.PASSED else Status.WARNING) to
      "$pending parked · $unlinked unlinked (Packages tab › Payments needing attention)."
  },
  )
}

@Composable
fun DiagnosticsTab(gateway: AdminGateway, email: String) {
  var checks by remember { mutableStateOf<List<Check>>(emptyList()) }
  var running by remember { mutableStateOf(true) }
  var run by remember { mutableStateOf(0) }
  var ranAt by remember { mutableStateOf(0.0) }

  LaunchedEffect(run) {
    running = true
    checks = runChecks(gateway, email)
    ranAt = nowMillis()
    running = false
  }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
    val passed = checks.count { it.status == Status.PASSED }
    val warnings = checks.count { it.status == Status.WARNING }
    val failed = checks.count { it.status == Status.FAILED }
    val score = if (checks.isEmpty()) 100 else (((passed + warnings * 0.5) / checks.size) * 100).toInt()
    Card(Modifier.fillMaxWidth()) {
      Row(Modifier.padding(Spacing.lg).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("System health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            if (running) "Running checks…" else "$score% · $passed passed · $warnings warnings · $failed failed · ran ${dateLabel(ranAt)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Button(onClick = { run++ }, enabled = !running) { Text("Run again") }
      }
    }
    checks.groupBy { it.category }.forEach { (category, items) ->
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          Text(category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          items.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider()
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
              val color = when (c.status) { Status.PASSED -> StatusSuccess; Status.WARNING -> StatusWarning; Status.FAILED -> StatusError }
              Surface(color = color, shape = MaterialTheme.shapes.small) {
                Text(c.status.name, color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
              }
              Column(Modifier.weight(1f)) {
                Text(c.name, fontWeight = FontWeight.SemiBold)
                Text(c.details, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
              }
              Text("${c.ms} ms", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }
  }
}
