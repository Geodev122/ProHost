package admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

/** P1.0 pipeline proof: the Users tab header shows the live totals from the `adminCounts` callable (the full tab is ported in P1.1). */
@Composable
fun UsersTab(gateway: AdminGateway) {
  var text by remember { mutableStateOf("Loading totals…") }
  LaunchedEffect(Unit) {
    text = try { "adminCounts: " + gateway.call("adminCounts") } catch (e: Throwable) { "Could not load totals: ${e.message}" }
  }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Users", style = MaterialTheme.typography.titleMedium)
    Text(text, style = MaterialTheme.typography.bodyMedium)
  }
}
