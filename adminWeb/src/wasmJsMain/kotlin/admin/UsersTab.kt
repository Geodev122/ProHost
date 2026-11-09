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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

  LaunchedEffect(Unit) { counts = try { Json.parse(gateway.call("adminCounts")) } catch (_: Throwable) { null } }
  LaunchedEffect(query) {
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
        Text("Search anyone; export every user", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    shown.forEach { UserCard(it) }
  }
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
private fun UserCard(u: Json) {
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
      Spacer(Modifier.width(0.dp))
    }
  }
}
