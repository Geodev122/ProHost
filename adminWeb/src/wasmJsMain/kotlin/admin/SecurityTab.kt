package admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

/**
 * ADMIN_PARITY items 53-55 (Security): live audit log (newest 500 first), CSV export with a date range, versioned legal-document upload.
 * Known quirk carried over on purpose (DECISIONS.md): the CSV contains only the loaded (<= 500) rows. The log is read once per visit
 * with a Refresh button instead of a live listener (a stated, small difference).
 */
private class LegalSlot(val docId: String, val title: String, val mime: String, val ext: String, val blocking: Boolean)

private val LEGAL_SLOTS = listOf(
  LegalSlot("privacy_policy", "Privacy Policy", "text/html", "html", true),
  LegalSlot("terms_of_use", "Terms of Use", "text/html", "html", true),
  LegalSlot("revocation_policy", "Revocation Policy", "text/html", "html", true),
  // Never a dead end: the app falls back to generating this PDF from the built-in template when no admin version exists yet.
  LegalSlot("rerental_authorization_template", "Re-Rental Authorization Template (PDF)", "application/pdf", "pdf", false),
)

@Composable
fun SecurityTab(gateway: AdminGateway, adminEmail: String) {
  var logs by remember { mutableStateOf<List<Json>>(emptyList()) }
  var logsError by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableStateOf(0) }
  var fromText by remember { mutableStateOf("") }
  var toText by remember { mutableStateOf("") }
  var legal by remember { mutableStateOf<Map<String, Json?>>(emptyMap()) }
  var uploading by remember { mutableStateOf<String?>(null) }
  var notice by remember { mutableStateOf<String?>(null) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(reload) {
    logsError = null
    try { logs = Json.parse(gateway.auditLogs()).list() } catch (e: Throwable) { logsError = "Couldn't load the audit log: ${e.message}" }
    legal = LEGAL_SLOTS.associate { slot -> slot.docId to (try { gateway.readDoc("legal_documents/${slot.docId}")?.let { Json.parse(it) } } catch (_: Throwable) { null }) }
  }

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
    ReportsCard(gateway)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Security & Audit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Total Registered Logs: ${logs.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          OutlinedTextField(fromText, { fromText = it }, label = { Text("From (yyyy-MM-dd, any)") }, singleLine = true, modifier = Modifier.weight(1f))
          OutlinedTextField(toText, { toText = it }, label = { Text("To (yyyy-MM-dd, any)") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          Button(onClick = {
            val from = parseDayUtc(fromText.trim())
            val to = parseDayUtc(toText.trim())?.let { it + 86_400_000.0 - 1 } // inclusive end of day
            downloadText("prohost_audit_logs.csv", auditCsv(logs, from, to), "text/csv")
          }) { Text("Export Audit Logs (CSV)") }
          OutlinedButton(onClick = { reload++ }) { Text("Refresh") }
        }
        logsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Legal Documents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Upload to publish new version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        LEGAL_SLOTS.forEach { slot ->
          val current = legal[slot.docId]
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(slot.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
              Text(
                when {
                  current != null -> "v${current["version"].int()} — published ${current["uploadedAtMillis"].num()?.let { dateLabel(it) } ?: "—"}"
                  slot.blocking -> "Not yet published"
                  else -> "Not yet published — app falls back to a generated PDF"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (current != null || !slot.blocking) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
              )
            }
            OutlinedButton(enabled = uploading == null, onClick = {
              scope.launch {
                val file = try { pickFile(slot.mime).await<JsAny?>() } catch (_: Throwable) { null } ?: return@launch
                uploading = slot.docId; notice = null
                notice = try { publishLegal(gateway, slot, file, adminEmail).also { reload++ } } catch (e: Throwable) { "Upload failed — check your connection and try again (${e.message})" }
                uploading = null
              }
            }) { Text(if (uploading == slot.docId) "Uploading…" else if (current != null) "Upload New Version" else "Upload") }
          }
        }
      }
    }

    if (logs.isEmpty() && logsError == null) {
      Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No audit entries yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Security and billing actions appear here as they happen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    logs.forEach { log -> LogCard(log) }
  }
}

/** Same contract as `RepositoryCsv.auditLogs`: header lines, then quoted rows (the whole loaded list, date-filtered). */
private fun auditCsv(logs: List<Json>, from: Double?, to: Double?): String {
  val rows = logs.filter { l ->
    val t = l["timestamp"].num() ?: 0.0
    (from == null || t >= from) && (to == null || t <= to)
  }
  val sb = StringBuilder()
  sb.append("=== PROHOST SYSTEM AUDIT LOGS EXPORT (CSV) ===\n")
  sb.append("Export Date,${dateTimeLocal(nowMillis())}\n")
  sb.append("Range,${from?.let { dateTimeLocal(it) } ?: "Beginning"} to ${to?.let { dateTimeLocal(it) } ?: "Now"}\n")
  sb.append("Total Entries,${rows.size}\n\n")
  sb.append("Timestamp,Action Type,Severity,Actor Email,Details\n")
  rows.forEach { l ->
    val details = l["details"].str().replace("\"", "\"\"")
    sb.append("\"${dateTimeLocal(l["timestamp"].num() ?: 0.0)}\",\"${l["actionType"].str()}\",\"${l["severity"].str()}\",\"${l["actorEmail"].str()}\",\"$details\"\n")
  }
  return sb.toString()
}

/** Versioned publish: Storage `legal_documents/{docId}/v{n}.{ext}` (immutable), then the `current` pointer doc and the permanent `versions/{n}` doc, then the audit entry. */
private suspend fun publishLegal(gateway: AdminGateway, slot: LegalSlot, file: JsAny, adminEmail: String): String {
  val current = gateway.readDoc("legal_documents/${slot.docId}")?.let { Json.parse(it)["version"].int() } ?: 0
  val next = current + 1
  val url = gateway.uploadFile("legal_documents/${slot.docId}/v$next.${slot.ext}", file, slot.mime)
  val name = fileNameOf(file).ifBlank { null }
  val body = toJsonText(mapOf("version" to next, "url" to url, "fileName" to name, "uploadedAtMillis" to nowMillis().toLong(), "uploadedByEmail" to adminEmail, "contentType" to slot.mime))
  gateway.mergeDoc("legal_documents/${slot.docId}", body)
  gateway.mergeDoc("legal_documents/${slot.docId}/versions/$next", body)
  try {
    gateway.call("recordClientAuditLog", """{"actionType":"LEGAL_DOCUMENT_PUBLISHED","details":${jsonQuote("Admin published ${slot.docId} v$next" + (name?.let { " (\"$it\")" } ?: ""))},"severity":"SECURE"}""")
  } catch (_: Throwable) { /* the publish itself succeeded */ }
  return "Published ${slot.docId} v$next"
}

@Composable
private fun LogCard(log: Json) {
  val severity = log["severity"].str()
  val high = severity.contains("WARN", true) || severity.contains("CRIT", true) || severity.contains("HIGH", true)
  Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Surface(color = if (high) StatusErrorContainer else StatusSuccessContainer, shape = MaterialTheme.shapes.extraSmall) {
          Text(severity, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (high) StatusError else StatusSuccess, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Text(dateTimeLocal(log["timestamp"].num() ?: 0.0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text(log["actionType"].str(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text(log["details"].str(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text("Actor: ${log["actorEmail"].str()} • IP: ${log["ipAddress"].str()} • ID: ${log["id"].str()}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
    }
  }
}
