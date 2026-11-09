package admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * ADMIN_PARITY items 8-17 (Packages): billing health + self-test, payments needing attention with Retry / Activate for an account and the
 * reassignment confirmation, Force Upgrade → ProHost (lookup, verification, confirm), Sync with Google Play, governance tag, all-users CSV.
 * The CSV export is the same as the Users tab's. Google Play remains the only billing authority: nothing here sets prices or plans.
 */
private const val PRODUCT_ID = "package_pro_mrr"
private const val LIFETIME_EXPIRY_MILLIS = 4_102_444_800_000.0

private fun planLabel(id: String?): String {
  val v = id?.trim()?.lowercase().orEmpty()
  return when {
    v == "admin_forced" || v == "admin_unlimited_grant" -> "Pro Host (granted)"
    v.contains("year") -> "ProHost Premium · Yearly"
    v.contains("mon") -> "ProHost Premium · Monthly"
    else -> "ProHost Premium"
  }
}

private fun roleLabel(role: String) = when (role) { "PRO_HOST" -> "Pro Host"; "ADMIN" -> "Admin"; "SPECIALIST" -> "Specialist"; else -> role }

private fun expiryLabel(ms: Double?): String = when {
  ms == null -> "no expiry set"
  ms >= LIFETIME_EXPIRY_MILLIS -> "never expires"
  else -> "until ${dateLabel(ms)}"
}

private fun stamp(j: Json): String = j.num()?.takeIf { it > 0 }?.let { stampShort(it) } ?: "never"

@Composable
fun PackagesTab(gateway: AdminGateway) {
  var health by remember { mutableStateOf<Json?>(null) }
  var pending by remember { mutableStateOf<List<Json>>(emptyList()) }
  var checking by remember { mutableStateOf(false) }
  var activatingId by remember { mutableStateOf<String?>(null) }
  var assigning by remember { mutableStateOf<Json?>(null) }
  var candidates by remember { mutableStateOf<List<Json>>(emptyList()) }
  var assignQuery by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf<Reassign?>(null) }
  var notice by remember { mutableStateOf<String?>(null) }
  var tag by remember { mutableStateOf("") }
  val scope = rememberCoroutineScope()

  // Force Upgrade card
  var grantEmail by remember { mutableStateOf("") }
  var target by remember { mutableStateOf<Json?>(null) }
  var grantError by remember { mutableStateOf<String?>(null) }
  var lookingUp by remember { mutableStateOf(false) }
  var granting by remember { mutableStateOf(false) }
  var granted by remember { mutableStateOf(false) }
  var confirmGrant by remember { mutableStateOf(false) }

  suspend fun refresh() {
    checking = true
    try { health = Json.parse(gateway.call("billingHealthCheck")) } catch (e: Throwable) { notice = "Couldn't load billing status. (${e.message})" }
    try { pending = Json.parse(gateway.call("adminBillingPending"))["rows"].list() } catch (e: Throwable) { notice = "Couldn't load billing status. (${e.message})" }
    checking = false
  }
  LaunchedEffect(Unit) {
    refresh()
    tag = try { gateway.readDoc("system_metadata/pricing")?.let { Json.parse(it)["governanceTag"].str() } } catch (_: Throwable) { null } ?: "HOST-PACKAGING-TIERS-V2-ACTIVE"
  }

  fun activate(payment: Json, targetUid: String?, targetName: String?, confirmReassign: Boolean) {
    activatingId = payment["id"].str()
    scope.launch {
      try {
        val body = """{"kind":${jsonQuote(payment["kind"].str())},"id":${jsonQuote(payment["id"].str())},"targetUid":${targetUid?.let { jsonQuote(it) } ?: "null"},"confirmReassign":$confirmReassign}"""
        val answer = Json.parse(gateway.call("adminActivatePurchase", body))
        when (answer["status"].str()) {
          "granted" -> { assigning = null; candidates = emptyList(); notice = "Activated and acknowledged — the user now has Pro Host."; refresh() }
          "needs_confirm" -> {
            val owner = answer["taggedFor"]
            confirm = Reassign(payment, targetUid ?: payment["user"]["uid"].str(), targetName ?: payment["user"]["name"].str(),
              listOf(owner["name"].str(), owner["code"].str()).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "another account" })
          }
          "granted_not_acknowledged" -> { notice = "Granted, but Google Play didn't confirm the acknowledgement — it will retry automatically."; refresh() }
          "retired_plan" -> { notice = "Retired plan — no longer offered; the row was closed."; refresh() }
          "play_error" -> notice = "Google Play: ${answer["message"].str().take(160).ifBlank { "error" }}"
          else -> { notice = "Not activated: ${answer["status"].str().ifBlank { "unknown" }}"; refresh() }
        }
      } catch (e: Throwable) { notice = "Activation failed — try again. (${e.message})" }
      activatingId = null
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
    notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

    // --- Billing health (items 8, 9)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Billing health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Google Play verification and purchases needing attention", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val h = health
        if (h == null) Text(if (checking) "Checking Google Play…" else "Not checked yet.", style = MaterialTheme.typography.bodySmall)
        else {
          val api = h["playApi"].str().ifBlank { "transient" }
          val lastRtdn = h["lastRtdnAt"].num()
          when {
            api == "ok" -> Banner(
              if (lastRtdn == null) "Play API OK — no Play notifications received yet" else "Google Play connected",
              "${h["message"].str()} Last Play notification (RTDN): ${stamp(h["lastRtdnAt"])}.", warning = lastRtdn == null,
            )
            else -> {
              Banner(if (api == "config") "Google Play access is not set up" else "Google Play unreachable", h["message"].str(), error = true)
              if (api == "config") SelectionContainer {
                Text(
                  "Play Console › Users and permissions › Invite: " + (h["serviceAccount"].str().ifBlank { "the Cloud Functions service account" }) +
                    " with View financial data + Manage orders and subscriptions. Then enable the Google Play Android Developer API in Google Cloud and tap Check again.",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
          Text("Server self-test (topic → server): ${stamp(h["lastSelfTestAt"])}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("Play catalog ($PRODUCT_ID):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
          val lines = h["catalog"].list().flatMap { r ->
            val plan = listOf(r["basePlanId"].str(), r["state"].str(), r["period"].str().let { p -> if (p.isBlank()) "" else r["interval"].str().let { i -> if (i.isBlank()) p else "$p ($i)" } })
              .filter { it.isNotBlank() }.joinToString(" · ")
            listOf(plan) + r["offers"].list().map { "   ↳ offer ${it.str()}" }
          }
          if (lines.isEmpty()) Text(h["catalogError"].str().let { if (it.isBlank()) "No base plans returned." else "Couldn't read it: $it" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
          lines.forEach { Text("• $it", style = MaterialTheme.typography.labelSmall, color = if ("ACTIVE" in it) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error) }
          if (lastRtdn == null) SelectionContainer {
            Text(
              "No notification from Google Play yet. 1) Play Console › Monetize with Play › Monetization setup › Real-time developer notifications → topic projects/prohost-f766f/topics/play-billing-rtdn. " +
                "2) Google Cloud › Pub/Sub › play-billing-rtdn › Permissions › add google-play-developer-notifications@system.gserviceaccount.com as Pub/Sub Publisher. 3) Send test notification in Play Console, then Check again.",
              style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          TextButton(enabled = !checking, onClick = { scope.launch { refresh() } }) { Text(if (checking) "Checking…" else "Check again") }
          TextButton(enabled = !checking, onClick = {
            scope.launch {
              checking = true
              try { gateway.call("billingRtdnSelfTest"); pause(8_000) } catch (e: Throwable) { notice = "Couldn't send the test notification. (${e.message})"; checking = false; return@launch }
              checking = false; refresh()
            }
          }) { Text("Send server self-test") }
        }
      }
    }

    // --- Payments needing attention (items 10-13)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Payments needing attention (${pending.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Paid in Google Play but not active yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (pending.isEmpty()) {
          Text("Nothing waiting", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Text("Paid purchases that still need activation show up here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        pending.forEach { p ->
          val busy = activatingId == p["id"].str()
          val user = p["user"]
          val needsAdmin = p["needsAdmin"].bool()
          val hoursLeft = p["hoursLeft"].num()?.toInt()
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("${user["name"].str()}${user["code"].str().takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""} — ${p["productId"].str()}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
              "Since ${stamp(p["createdAt"])} · ${p["attempts"].int()} attempt(s)" + (hoursLeft?.let { " · ~$it h before Google Play refunds it" } ?: "") + (p["orderId"].str().takeIf { it.isNotBlank() }?.let { " · order $it" } ?: ""),
              style = MaterialTheme.typography.bodySmall, color = if ((hoursLeft ?: 99) < 24) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(p["hint"].str(), style = MaterialTheme.typography.bodySmall)
            if (p["lastError"].str().isNotBlank()) Text(p["lastError"].str().take(200), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
              if (p["kind"].str() == "pending" && !needsAdmin) Button(enabled = !busy, onClick = { activate(p, null, null, false) }) { Text(if (busy) "Working…" else "Retry now") }
              OutlinedButton(enabled = !busy, onClick = { assigning = p; candidates = emptyList(); assignQuery = "" }) {
                Text(if (!user["uid"].isNull && needsAdmin) "Activate for…" else "Activate for another account")
              }
              if (p["kind"].str() == "pending" && needsAdmin && !user["uid"].isNull) Button(enabled = !busy, onClick = { activate(p, null, null, false) }) { Text("Activate for ${user["name"].str().take(14)}") }
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
      }
    }

    // --- Force Upgrade → ProHost (item 14)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Force Upgrade → ProHost", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("VIP access, customer-service recovery or internal testing. Permanent until revoked in Users.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          OutlinedTextField(grantEmail, { grantEmail = it }, label = { Text("Email or account code") }, singleLine = true, enabled = !lookingUp && !granting, modifier = Modifier.weight(1f))
          Button(enabled = grantEmail.isNotBlank() && !lookingUp && !granting, onClick = {
            lookingUp = true; grantError = null; granted = false; target = null
            scope.launch {
              try { target = Json.parse(gateway.call("lookupUserForGrant", """{"email":${jsonQuote(grantEmail.trim())}}""")) } catch (e: Throwable) { grantError = "Lookup failed: ${e.message}" }
              lookingUp = false
            }
          }) { Text(if (lookingUp) "…" else "Find user") }
        }
        grantError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        val t = target
        if (t != null) {
          val role = t["role"].str().ifBlank { "SPECIALIST" }
          val already = role == "PRO_HOST" || role == "ADMIN"
          val canUpgrade = t["hasProfile"].bool() && !t["isDisabled"].bool() && !already && !granting
          Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
              Text("Verify this is the right person", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(t["fullName"].str().ifBlank { "(no name on profile)" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(t["email"].str(), style = MaterialTheme.typography.bodyMedium)
              SelectionContainer { Text("Account: ${t["displayCode"].str().ifBlank { t["uid"].str() }}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
              val pkg = t["ownerPackageId"].str().ifBlank { null }
              Text(
                "Role: ${roleLabel(role)} · Access: ${pkg?.let { planLabel(it) } ?: "None"}" + (if (pkg != null) " (${expiryLabel(t["ownerPackageExpiryMillis"].num())})" else "") + " · Active listings: ${t["activeListingCount"].int()}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (!t["hasProfile"].bool()) Text("This user hasn't completed registration, so they can't be upgraded yet.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          if (t["isDisabled"].bool()) Text("This account is disabled in Firebase Auth.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          if (t["isSuspended"].bool()) Text("This account is suspended. The upgrade will apply, but their listings stay hidden until the suspension is lifted.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
          if (already && !granted) Text("This account already has ${roleLabel(role)} access.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Button(enabled = canUpgrade, modifier = Modifier.fillMaxWidth(), onClick = { confirmGrant = true }) { Text(if (granting) "Upgrading…" else "Force Upgrade → ProHost", fontWeight = FontWeight.Bold) }
          if (granted) Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium) {
            Text("Upgraded to Pro Host (never expires). Their app updates immediately.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
          }
          TextButton(onClick = { target = null; grantEmail = ""; granted = false; grantError = null }) { Text("Clear / find another user") }
        }
      }
    }

    // --- Google Play billing: sync + governance tag (items 15, 16)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Google Play Billing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Read-only — managed in Play Console", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          "Subscription $PRODUCT_ID with a monthly and a yearly base plan (exact ids and states: Billing health below). Prices, offers, renewals and refunds come only from Google Play; roles follow automatically.",
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = {
          scope.launch {
            notice = try {
              val r = Json.parse(gateway.call("runBillingSync"))
              "Billing sync: ${r["checked"].int()} checked, ${r["changed"].int()} changed, ${r["failed"].int()} failed" + (if (r["stoppedOnConfigError"].bool()) " — Play API access denied" else "")
            } catch (e: Throwable) { "Billing sync failed: ${e.message}" }
          }
        }) { Text("Sync with Google Play now") }
        HorizontalDivider()
        OutlinedTextField(tag, { tag = it }, label = { Text("Admin Governance Control Tag") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
          trailingIcon = {
            TextButton(onClick = {
              scope.launch {
                notice = try { gateway.call("updatePricing", """{"governanceTag":${jsonQuote(tag.take(200))}}"""); "Governance tag saved" } catch (e: Throwable) { "Couldn't save the tag: ${e.message}" }
              }
            }) { Text("Save") }
          })
      }
    }

    // --- Export (item 17)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("One-Click System Exports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Every user, built on the server", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var exporting by remember { mutableStateOf(false) }
        Button(enabled = !exporting, modifier = Modifier.fillMaxWidth(), onClick = {
          exporting = true
          scope.launch {
            try { downloadText("prohost_users_${todayStamp()}.csv", exportUsersCsv(gateway), "text/csv") } catch (e: Throwable) { notice = "Export failed — try again. (${e.message})" }
            exporting = false
          }
        }) { Text(if (exporting) "Exporting…" else "All users (CSV)") }
      }
    }
  }

  // Account picker (item 12)
  assigning?.let { payment ->
    if (confirm == null) AlertDialog(
      onDismissRequest = { assigning = null; candidates = emptyList() }, title = { Text("Activate for which account?") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          Text("Search by name, email, U- code or UID.", style = MaterialTheme.typography.bodySmall)
          OutlinedTextField(assignQuery, { assignQuery = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = {
            TextButton(onClick = {
              if (assignQuery.isNotBlank()) scope.launch {
                try { candidates = Json.parse(gateway.call("adminSearch", """{"query":${jsonQuote(assignQuery.trim())},"kind":"users"}"""))["users"].list() } catch (e: Throwable) { notice = "Search failed — try again. (${e.message})" }
              }
            }) { Text("Find") }
          })
          candidates.forEach { u ->
            TextButton(enabled = activatingId == null, onClick = { activate(payment, u["id"].str(), u["fullName"].str().ifBlank { u["email"].str() }, false) }) {
              Text("${u["fullName"].str().ifBlank { u["email"].str() }} · ${u["displayCode"].str().ifBlank { u["id"].str() }} · ${u["email"].str()}")
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { assigning = null; candidates = emptyList() }) { Text("Close") } },
    )
  }
  // Reassignment confirmation (item 13)
  confirm?.let { c ->
    AlertDialog(
      onDismissRequest = { confirm = null; assigning = null; candidates = emptyList() }, title = { Text("Move this purchase?") },
      text = { Text("Google Play records this purchase for ${c.taggedForName}. Activate it for ${c.targetName} instead? Renewals will follow ${c.targetName}. Only do this after confirming with the buyer.") },
      confirmButton = { TextButton(onClick = { confirm = null; activate(c.payment, c.targetUid, c.targetName, true) }) { Text("Activate for ${c.targetName.take(20)}") } },
      dismissButton = { TextButton(onClick = { confirm = null; assigning = null; candidates = emptyList() }) { Text("Cancel") } },
    )
  }
  if (confirmGrant && target != null) {
    val t = target!!
    AlertDialog(
      onDismissRequest = { confirmGrant = false }, title = { Text("Force upgrade to Pro Host?") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          Text("${t["fullName"].str().ifBlank { "(no name)" }} · ${t["email"].str()}")
          Text("Account: ${t["displayCode"].str().ifBlank { t["uid"].str() }}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
          Text("Role: ${roleLabel(t["role"].str().ifBlank { "SPECIALIST" })} → Pro Host, with no Google Play subscription and no expiry. Revoke it from the Users tab.")
        }
      },
      confirmButton = {
        Button(onClick = {
          confirmGrant = false; granting = true; grantError = null
          scope.launch {
            try {
              gateway.call("forceProHostUpgrade", """{"targetUid":${jsonQuote(t["uid"].str())}}""")
              // Re-read from the server so the card shows the persisted role.
              target = try { Json.parse(gateway.call("lookupUserForGrant", """{"email":${jsonQuote(t["email"].str())}}""")) } catch (_: Throwable) { t }
              granted = true
              notice = "${t["fullName"].str().ifBlank { t["email"].str() }} is now a Pro Host"
            } catch (e: Throwable) { grantError = "Upgrade failed: ${e.message}" }
            granting = false
          }
        }) { Text("Force Upgrade") }
      },
      dismissButton = { TextButton(onClick = { confirmGrant = false }) { Text("Cancel") } },
    )
  }
}

private class Reassign(val payment: Json, val targetUid: String, val targetName: String, val taggedForName: String)

@Composable
private fun Banner(title: String, message: String, warning: Boolean = false, error: Boolean = false) {
  val container = when { error -> MaterialTheme.colorScheme.errorContainer; warning -> androidx.compose.ui.graphics.Color(0xFFFFF4DB); else -> androidx.compose.ui.graphics.Color(0xFFE3F4EA) }
  Surface(color = container, shape = MaterialTheme.shapes.medium) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text(message, style = MaterialTheme.typography.bodySmall)
    }
  }
}
