package admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** The seven tabs of the Android admin console (ADMIN_PARITY.md items 1-7); each is ticked there when its content matches. */
val ADMIN_TABS = listOf("Users", "Listings", "Packages", "Analytics", "Security", "Schema", "Demo")

@Composable
fun AdminApp(gateway: AdminGateway) {
  AdminTheme {
    var session by remember { mutableStateOf<AdminSession>(AdminSession.Loading) }
    LaunchedEffect(Unit) { session = try { gateway.restore() } catch (_: Throwable) { AdminSession.SignedOut } }
    val scope = rememberCoroutineScope()
    when (val s = session) {
      AdminSession.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
      AdminSession.SignedOut -> SignInPage(gateway) { session = it }
      is AdminSession.NotAdmin -> NoAccessPage(s.email) { scope.launch { gateway.signOut(); session = AdminSession.SignedOut } }
      is AdminSession.Admin -> ConsoleShell(gateway, s.email) { scope.launch { gateway.signOut(); session = AdminSession.SignedOut } }
    }
  }
}

@Composable
private fun SignInPage(gateway: AdminGateway, onSession: (AdminSession) -> Unit) {
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("ProHost Admin", style = MaterialTheme.typography.headlineMedium)
      Text("Sign in with your admin email and password.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
      OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
      error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
      Button(
        onClick = {
          busy = true; error = null
          scope.launch {
            try { onSession(gateway.signIn(email, password)) } catch (e: AdminAuthException) { error = e.message } finally { busy = false }
          }
        },
        enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
      ) { Text(if (busy) "Signing in…" else "Sign in") }
    }
  }
}

@Composable
private fun NoAccessPage(email: String, onSignOut: () -> Unit) {
  Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
    Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("No access", style = MaterialTheme.typography.headlineMedium)
      Text("$email is not an admin account.", style = MaterialTheme.typography.bodyLarge)
      Button(onClick = onSignOut) { Text("Sign out") }
    }
  }
}

@Composable
private fun ConsoleShell(gateway: AdminGateway, email: String, onSignOut: () -> Unit) {
  var tab by remember { mutableStateOf(0) }
  Scaffold { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text("ProHost Admin", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          TextButton(onClick = onSignOut) { Text("Sign out") }
        }
      }
      ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
        ADMIN_TABS.forEachIndexed { i, name -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(name) }) }
      }
      Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth()) { // 900 dp content width as on the Android console (tablet layout)
          if (tab == 0) UsersTab(gateway) else if (tab == 1) ListingsTab(gateway) else Text("${ADMIN_TABS[tab]} — not ported yet (ADMIN_PARITY.md).", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}
