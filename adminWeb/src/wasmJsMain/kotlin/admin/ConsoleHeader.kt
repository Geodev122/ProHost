package admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Spacing
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Console header pieces (ADMIN_PARITY items 1-7, D5): the online/offline pill, the admin notification centre (the admin's
 * own in-app notifications — subscription changes, office leads, verification requests — read from
 * `user_profiles/{uid}/notifications`, refreshed every minute) and the tab badge counts from `adminCounts`.
 */
private fun onlineJs(): Boolean = js("navigator.onLine")

@Composable
fun OnlinePill() {
  var online by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) { while (true) { online = onlineJs(); delay(5_000) } }
  Surface(shape = MaterialTheme.shapes.small, color = if (online) StatusSuccess else StatusError) {
    Text(if (online) "Online" else "Offline", color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
  }
}

@Composable
fun NotificationBell(gateway: AdminGateway, onOpenTab: (Int) -> Unit) {
  var items by remember { mutableStateOf<List<Json>>(emptyList()) }
  var open by remember { mutableStateOf(false) }
  var reload by remember { mutableStateOf(0) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(reload) {
    while (true) {
      items = try { Json.parse(gateway.myNotifications()).list() } catch (_: Throwable) { items }
      delay(60_000)
    }
  }
  val unread = items.count { !it["read"].bool() }
  BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
    TextButton(onClick = { open = true }) { Text("Notifications") }
  }
  if (open) {
    AlertDialog(
      onDismissRequest = { open = false },
      title = { Text("Notifications") },
      text = {
        Column(Modifier.widthIn(max = 520.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
          if (items.isEmpty()) Text("Nothing in the last 48 hours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          items.forEachIndexed { i, n ->
            if (i > 0) HorizontalDivider()
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
              Column(Modifier.weight(1f)) {
                Text(n["title"].str(), fontWeight = if (n["read"].bool()) FontWeight.Normal else FontWeight.Bold)
                Text(n["body"].str(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${n["category"].str()} · ${n["createdAt"].num()?.let { dateLabel(it) } ?: ""}", style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              TextButton(onClick = {
                val id = n["id"].str()
                scope.launch {
                  runCatching { gateway.markNotificationRead(id) }
                  reload++
                }
                tabForCategory(n["category"].str())?.let { open = false; onOpenTab(it) }
              }) { Text("Open") }
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
      dismissButton = {
        if (unread > 0) TextButton(onClick = {
          scope.launch { items.filter { !it["read"].bool() }.forEach { runCatching { gateway.markNotificationRead(it["id"].str()) } }; reload++ }
        }) { Text("Mark all read") }
      }
    )
  }
}

/** Which console tab an admin notification belongs to (index into ADMIN_TABS). */
private fun tabForCategory(category: String): Int? = when (category) {
  "ADMIN_SUBSCRIPTION" -> ADMIN_TABS.indexOf("Packages")
  "LISTING_VERIFICATION_REQUEST" -> ADMIN_TABS.indexOf("Listings")
  "ADMIN_REPORT" -> ADMIN_TABS.indexOf("Security")
  "ADMIN_LEAD" -> ADMIN_TABS.indexOf("Users")
  else -> null
}?.takeIf { it >= 0 }
