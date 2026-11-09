package admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarnationOrange
import com.example.ui.theme.CarnationOrangeContainer
import com.example.ui.theme.FreshGreen
import com.example.ui.theme.OxfordBlue
import com.example.ui.theme.OxfordBlueContainer
import com.example.ui.theme.Spacing
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusOnErrorContainer
import com.example.ui.theme.StatusWarningContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** ADMIN_PARITY.md items 18-21: totals, smart search (350 ms debounce), role chips, user cards. Actions (22-28) come next. */
private val ROLES = listOf("SPECIALIST", "PRO_HOST", "ADMIN")

@Composable
fun UsersTab(gateway: AdminGateway) {
  var counts by remember { mutableStateOf<Json?>(null) }
  var query by remember { mutableStateOf("") }
  var role by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var users by remember { mutableStateOf<List<Json>>(emptyList()) }
  var refresh by remember { mutableStateOf(0) }
  var pending by remember { mutableStateOf<Pair<String, Json>?>(null) }
  var editing by remember { mutableStateOf<Json?>(null) }
  var notice by remember { mutableStateOf<String?>(null) }
  var exporting by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) { counts = try { Json.parse(gateway.call("adminCounts")) } catch (_: Throwable) { null } }
  LaunchedEffect(query, refresh) {
    val q = query.trim()
    error = null
    if (q.isEmpty()) { users = emptyList(); loading = false; return@LaunchedEffect }
    loading = true
    delay(350) // debounce, as on Android
    try {
      users = Json.parse(gateway.call("adminSearch", """{"query":${jsonQuote(q)},"kind":"users"}"""))["users"].list()
    } catch (e: Throwable) {
      users = emptyList(); error = "Search failed: ${e.message}"
    }
    loading = false
  }

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
      MetricTile("Total Users", counts?.get("totalUsers")?.int()?.toString() ?: "…", Modifier.weight(1f))
      MetricTile("Pro Hosts", counts?.get("proHosts")?.int()?.toString() ?: "…", Modifier.weight(1f))
      MetricTile("Specialists", counts?.get("specialists")?.int()?.toString() ?: "…", Modifier.weight(1f))
      MetricTile("New (30d)", counts?.get("newUsers30d")?.int()?.let { "+$it" } ?: "…", Modifier.weight(1f))
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("User Directory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Search anyone; export every user", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Button(onClick = {
            exporting = true; notice = null
            scope.launch {
              try { downloadText("prohost_users_${todayStamp()}.csv", exportUsersCsv(gateway), "text/csv") } catch (e: Throwable) { notice = "Export failed — try again. (${e.message})" }
              exporting = false
            }
          }, enabled = !exporting) { Text(if (exporting) "Exporting…" else "Export all (CSV)") }
        }
        OutlinedTextField(query, { query = it }, label = { Text("Name, email, U- code or UID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          FilterChip(selected = role == null, onClick = { role = null }, label = { Text("All roles") })
          ROLES.forEach { r -> FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.replace("_", " ")) }) }
        }
      }
    }
    val shown = users.filter { role == null || it["role"].str() == role }
    when {
      loading -> Text("Searching…", color = MaterialTheme.colorScheme.onSurfaceVariant)
      query.isBlank() -> EmptyState("Find a user", "Type a name, email, U- code or UID. Nothing is loaded until you search.")
      error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
      shown.isEmpty() -> EmptyState("No users found", "No account matches \"${query.trim()}\".")
    }
    notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
    shown.forEach { u -> UserCard(u, onEdit = { editing = u }, onAction = { kind -> pending = kind to u }) }
  }

  pending?.let { (kind, user) ->
    ConfirmDialog(kind, user, onDismiss = { pending = null }) {
      pending = null; notice = null
      scope.launch {
        notice = try { runUserAction(gateway, kind, user); refresh++; doneMessage(kind) } catch (e: Throwable) { "Couldn't complete that: ${e.message}" }
      }
    }
  }
  editing?.let { user ->
    EditDialog(user, onDismiss = { editing = null }) { fields ->
      editing = null; notice = null
      scope.launch {
        notice = try { saveUser(gateway, user, fields); refresh++; "User updated." } catch (e: Throwable) { "Couldn't save: ${e.message}" }
      }
    }
  }
}

private suspend fun exportUsersCsv(gateway: AdminGateway): String {
  val columns = listOf("uid", "displayCode", "fullName", "email", "phone", "role", "country", "city", "packageId", "packageExpiryMillis", "billingStatus", "entitlementSource", "listingCodes", "createdAtMillis")
  val out = StringBuilder(columns.joinToString(",")).append('\n')
  var cursor: String? = null
  do {
    val page = Json.parse(gateway.call("adminExportUsers", if (cursor == null) "{}" else """{"cursor":${jsonQuote(cursor)}}"""))
    page["rows"].list().forEach { row ->
      out.append(columns.joinToString(",") { csvCell(row[it].cell()) }).append('\n')
    }
    cursor = page["nextCursor"].str().ifEmpty { null }
  } while (cursor != null)
  return out.toString()
}

private fun csvCell(text: String): String = if (text.any { it == ',' || it == '"' || it == '\n' }) "\"" + text.replace("\"", "\"\"") + "\"" else text

/** Grant Admin / Suspend / Reactivate / Revoke Pro Host go through the same callables as the Android console. */
private suspend fun runUserAction(gateway: AdminGateway, kind: String, user: Json) {
  val uid = jsonQuote(user["id"].str())
  when (kind) {
    "grant" -> gateway.call("grantAdminRole", """{"targetUid":$uid}""")
    "suspend" -> gateway.call("setAccountSuspended", """{"targetUid":$uid,"suspended":true}""")
    "reactivate" -> gateway.call("setAccountSuspended", """{"targetUid":$uid,"suspended":false}""")
    "revoke" -> gateway.call("revokeProHostRole", """{"targetUid":$uid}""")
    "delete" -> {
      // Profile only; sign-in credentials are not revoked (same as Android).
      gateway.deleteDoc("user_profiles/${user["id"].str()}")
      audit(gateway, "USER_DELETED", "Admin removed user profile #${user["id"].str()} (${user["fullName"].str().ifBlank { "Unknown" }})", "WARN")
    }
  }
}

private fun doneMessage(kind: String) = when (kind) {
  "grant" -> "Admin role granted."
  "suspend" -> "Account suspended."
  "reactivate" -> "Account reactivated."
  "revoke" -> "Pro Host role revoked."
  else -> "User record removed."
}

private suspend fun audit(gateway: AdminGateway, type: String, details: String, severity: String) {
  try { gateway.call("recordClientAuditLog", """{"actionType":${jsonQuote(type)},"details":${jsonQuote(details)},"severity":${jsonQuote(severity)}}""") } catch (_: Throwable) { /* the change itself succeeded */ }
}

private suspend fun saveUser(gateway: AdminGateway, user: Json, f: Map<String, String>) {
  val body = f.entries.joinToString(",") { "${jsonQuote(it.key)}:${jsonQuote(it.value)}" }
  gateway.mergeDoc("user_profiles/${user["id"].str()}", "{$body,\"updatedAt\":${nowMillis().toLong()}}")
  audit(gateway, "USER_UPDATED", "Admin updated user profile for ${f["fullName"]} (${f["email"]})", "INFO")
}

@Composable
private fun ConfirmDialog(kind: String, user: Json, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  val who = "'${user["fullName"].str()}' (${user["email"].str()})"
  val (title, text, button) = when (kind) {
    "grant" -> Triple("Grant Admin Role?", "Are you sure you want to grant full Admin privileges to $who? This gives them unrestricted access to governance, pricing, and user management.", "Confirm Grant")
    "suspend" -> Triple("Suspend Account?", "$who will be signed out and unable to sign back in, create listings, or submit booking requests until reactivated. Their data and history are kept — this is not a deletion.", "Confirm Suspend")
    "reactivate" -> Triple("Reactivate Account?", "$who will regain full access immediately.", "Confirm Reactivate")
    "revoke" -> Triple("Revoke Pro Host Role?", "$who will be downgraded back to Specialist immediately. Every listing they've published will be marked inactive/expired (still visible in Discovery unless the specialist filters for active-subscription only, but shown as expired — not deleted). This does not affect their ability to book workspaces as a Specialist.", "Confirm Revoke")
    else -> Triple("Delete User Record?", "Are you sure you want to permanently remove ${user["fullName"].str()} (${user["email"].str()})'s profile from the platform? Their sign-in credentials are not revoked by this action.", "Confirm Delete")
  }
  AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
    confirmButton = { Button(onClick = onConfirm) { Text(button) } },
    dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun EditDialog(user: Json, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
  var fullName by remember { mutableStateOf(user["fullName"].str()) }
  var email by remember { mutableStateOf(user["email"].str()) }
  var phone by remember { mutableStateOf(user["phone"].str()) }
  var specialty by remember { mutableStateOf(user["specialty"].str()) }
  var country by remember { mutableStateOf(user["country"].str()) }
  var governorate by remember { mutableStateOf(user["governorate"].str()) }
  var city by remember { mutableStateOf(user["city"].str()) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit User: ${user["displayCode"].str().ifBlank { user["id"].str() }}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(fullName, { fullName = it }, label = { Text("Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone (WhatsApp)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(specialty, { specialty = it }, label = { Text("Specialty / Profession") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(country, { country = it }, label = { Text("Country") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(governorate, { governorate = it }, label = { Text("Governorate / Area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(city, { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(
          "Role: ${user["role"].str().replace("_", " ")}  •  ${if (user["isVerified"].bool()) "Phone Verified" else "Phone Unverified"}. Change these from the user row's own actions, not here.",
          style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(onClick = {
        onSave(mapOf("fullName" to fullName.trim(), "email" to email.trim(), "phone" to phone.trim(), "specialty" to specialty.trim(), "country" to country.trim(), "governorate" to governorate.trim(), "city" to city.trim()))
      }) { Text("Save Changes") }
    },
    dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier) {
  Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(Spacing.md)) {
      Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun EmptyState(title: String, description: String) {
  Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun Detail(label: String, value: String, color: Color = Color.Unspecified, bold: Boolean = false) {
  Column {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold)
  }
}

@Composable
private fun Badge(text: String, container: Color, content: Color) {
  Surface(color = container, shape = MaterialTheme.shapes.small) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = content, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
  }
}

@Composable
private fun UserCard(u: Json, onEdit: () -> Unit, onAction: (String) -> Unit) {
  val role = u["role"].str()
  val verified = u["isVerified"].bool()
  val location = u["city"].str().ifBlank { u["governorate"].str().ifBlank { u["country"].str() } }
  Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(u["fullName"].str(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Text(u["email"].str(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
          if (u["isSuspended"].bool()) Badge("SUSPENDED", StatusErrorContainer, StatusOnErrorContainer)
          when (role) {
            "ADMIN" -> Badge("ADMIN", StatusWarningContainer, AmberWarning)
            "PRO_HOST" -> Badge("PRO HOST", CarnationOrangeContainer, CarnationOrange)
            else -> Badge(role.replace("_", " "), OxfordBlueContainer, OxfordBlue)
          }
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Detail("Location:", location)
        Detail("Phone Status:", if (verified) "Verified" else "Unverified", if (verified) FreshGreen else StatusError, bold = true)
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Detail("Phone / WhatsApp:", u["phone"].str().ifBlank { "N/A" })
        Detail("Member Since:", u["createdAtMillis"].num()?.let { dateLabel(it) } ?: "—")
        Detail("Last Sign-In:", u["lastSignInAtMillis"].num()?.let { dateLabel(it) } ?: "—")
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onEdit) { Text("Edit") }
        if (role != "ADMIN") TextButton(onClick = { onAction("grant") }) { Text("Grant Admin") }
        if (role != "ADMIN") TextButton(onClick = { onAction(if (u["isSuspended"].bool()) "reactivate" else "suspend") }) {
          Text(if (u["isSuspended"].bool()) "Reactivate" else "Suspend", color = if (u["isSuspended"].bool()) FreshGreen else StatusError)
        }
        if (role == "PRO_HOST") TextButton(onClick = { onAction("revoke") }) { Text("Revoke Pro Host", color = StatusError) }
        if (role != "ADMIN") TextButton(onClick = { onAction("delete") }) { Text("Delete", color = StatusError) }
      }
    }
  }
}
