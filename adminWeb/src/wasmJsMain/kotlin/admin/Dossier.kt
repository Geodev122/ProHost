package admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.Spacing

private fun day(j: Json): String = j.num()?.takeIf { it > 0 }?.let { dateLabel(it) } ?: "—"

/**
 * ADMIN_PARITY item 22: everything about one account from `adminUserDossier` — identity (U- code + Firebase UID), providers, package,
 * a Pro Host's listings with D- room codes, Play subscription history with order ids (no amounts), rental history, actions.
 * ("Payments not active yet" for this account lives with the Packages tab, item 29+.)
 */
@Composable
fun DossierDialog(dossier: Json, onDismiss: () -> Unit, onEdit: (Json) -> Unit, onAction: (String, Json) -> Unit) {
  val profile = dossier["profile"]
  val auth = dossier["auth"]
  val role = profile["role"].str()
  val listings = dossier["listings"].list()
  val subs = dossier["subscriptions"].list()
  val rentals = dossier["rentals"].list()
  val code = profile["displayCode"].str().ifBlank { profile["id"].str() }
  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 640.dp)) {
      Column(Modifier.heightIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(profile["fullName"].str().ifBlank { "Unnamed account" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("$code · ${role.replace("_", " ")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Field("Display ID", code)
            Field("Firebase UID", profile["id"].str())
            Field("Email", auth["email"].str().ifBlank { profile["email"].str() }.ifBlank { "—" } + if (auth["emailVerified"].bool() || profile["emailVerified"].bool()) " (verified)" else "")
            Field("Phone", auth["phoneNumber"].str().ifBlank { profile["phone"].str() }.ifBlank { "—" } + if (auth["phoneNumber"].str().isNotBlank()) " (verified)" else "")
            Field("Sign-in", auth["providers"].list().joinToString { it.str() }.ifBlank { "—" })
            Field("Location", listOf(profile["city"].str(), profile["country"].str()).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "—" })
            Field("Member since", day(profile["createdAtMillis"]))
            Field("Last sign-in", day(profile["lastSignInAtMillis"]))
            if (profile["isSuspended"].bool()) Field("Status", "SUSPENDED")
            if (auth["missing"].bool()) Field("Auth account", "missing (profile only)")
          }
        }
        if (role == "PRO_HOST" || !profile["ownerPackageId"].isNull) {
          HorizontalDivider()
          Section("Package")
          Field("Plan", profile["ownerPackageId"].str().ifBlank { "—" })
          Field("Source", profile["entitlementSource"].str().ifBlank { "—" })
          Field("Billing status", profile["billingStatus"].str().ifBlank { "—" })
          Field("Access until", day(profile["ownerPackageExpiryMillis"]))
        }
        if (listings.isNotEmpty() || role == "PRO_HOST") {
          HorizontalDivider()
          Section("Listings (${listings.size}) · bookings received: ${dossier["hostBookingsCount"].int()}")
          listings.forEach { l ->
            Text(
              "${l["displayCode"].str().ifBlank { l["id"].str() }} · ${l["title"].str().ifBlank { "Untitled" }} · ${l["status"].str()}" + if (l["isVerified"].bool()) " · verified" else "",
              style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
            l["rooms"].list().forEach { r ->
              Text("   ${r["displayCode"].str().ifBlank { r["id"].str() }} · ${r["name"].str().ifBlank { "Room" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
        HorizontalDivider()
        Section("Subscription history (${subs.size})")
        if (subs.isEmpty()) Text("No Google Play subscriptions.", style = MaterialTheme.typography.bodySmall)
        subs.forEach { s ->
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("${s["basePlanId"].str().ifBlank { "Premium" }} · ${s["status"].str()}" + if (s["autoRenewing"].bool()) " · auto-renews" else "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("${day(s["startDate"])} → ${day(s["expiryDate"])} · order ${s["orderId"].str().ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        Text("Amounts are in Play Console (Order management), looked up by order id.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (rentals.isNotEmpty() || role == "SPECIALIST") {
          HorizontalDivider()
          Section("Rental history (${rentals.size})")
          rentals.forEach { b ->
            val total = b["totalAmountUsd"].num() ?: 0.0
            Text(
              "${b["displayCode"].str().ifBlank { b["id"].str() }} · ${b["spaceTitle"].str().ifBlank { "Listing" }} · ${b["startDate"].str()} · ${b["status"].str()} · $${money(total)}",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
        HorizontalDivider()
        Section("Actions")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          OutlinedButton(onClick = { onEdit(profile) }) { Text("Edit") }
          if (role != "ADMIN") OutlinedButton(onClick = { onAction(if (profile["isSuspended"].bool()) "reactivate" else "suspend", profile) }) { Text(if (profile["isSuspended"].bool()) "Reactivate" else "Suspend") }
          if (role == "PRO_HOST") Button(onClick = { onAction("revoke", profile) }) { Text("Revoke Pro Host") }
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
      }
    }
  }
}

/** Two decimals, like `%.2f` on Android (Locale.US). */
private fun money(v: Double): String {
  val cents = kotlin.math.round(v * 100).toLong()
  val whole = cents / 100
  val frac = (cents % 100).toString().padStart(2, '0')
  return "$whole.$frac"
}

@Composable
private fun Section(title: String) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }

@Composable
private fun Field(label: String, value: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
    Text("$label:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
  }
}
