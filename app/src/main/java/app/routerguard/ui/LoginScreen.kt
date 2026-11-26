package app.routerguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import app.routerguard.data.Catalog
import app.routerguard.data.DriverRegistry
import app.routerguard.data.RouterModel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.routerguard.UiState

@Composable
fun LoginScreen(
    state: UiState,
    onConnect: (host: String, user: String, password: String, driverId: String) -> Unit,
    onBrowserMode: (host: String, user: String, password: String) -> Unit,
) {
    val context = LocalContext.current
    val registry = remember { DriverRegistry(context) }
    var pickedId by rememberSaveable { mutableStateOf<String?>(null) }   // model id, "other", or null
    var host by rememberSaveable { mutableStateOf(state.host) }
    var user by rememberSaveable { mutableStateOf(state.user) }
    var pass by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val model = Catalog.models.firstOrNull { it.id == pickedId }
    val hasDriver = model != null && registry.available(model.driverId)
    val guided = pickedId != null && !hasDriver
    val canGo = !state.loading && host.isNotBlank() && pass.isNotEmpty() && user.isNotBlank()
    val field = Modifier.fillMaxWidth()
    val round = RoundedCornerShape(14.dp)
    val go = {
        if (guided) onBrowserMode(host, user, pass) else onConnect(host, user, pass, model?.driverId ?: Catalog.HUAWEI)
    }

    Scaffold { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.size(68.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Lock, null, tint = scheme.onPrimary, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.height(14.dp))
            Text("Router Guard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick your router. If the app has a driver for it you're connected in seconds; if not, it will guide you through teaching it.",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Catalog.models.forEach { m ->
                    ModelCard(m, registry.available(m.driverId), selected = pickedId == m.id) {
                        pickedId = m.id
                        host = m.defaultHost; user = m.defaultUser
                    }
                }
                Card(
                    onClick = { pickedId = "other" },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (pickedId == "other") scheme.primaryContainer else scheme.surfaceContainer,
                    ),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("My router isn't listed", fontWeight = FontWeight.SemiBold)
                        Text("Guided setup will learn it from your own login", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }

            if (pickedId != null) {
                Spacer(Modifier.height(20.dp))
                if (guided) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("No driver for this router yet", fontWeight = FontWeight.SemiBold, color = WarnAmber)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Enter your router's login below. The app will open the router's own page, sign in for you, and walk you through a short recording so a driver can be made for it. Until then you can still use the router page from inside the app.",
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = host, onValueChange = { host = it }, label = { Text("Router address") },
                    singleLine = true, modifier = field, shape = round,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = user, onValueChange = { user = it }, label = { Text("Username") },
                    singleLine = true, modifier = field, shape = round,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text("Password") },
                    singleLine = true, modifier = field, shape = round,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canGo) go() }),
                )
                if (state.loginError != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(state.loginError, color = scheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(18.dp))
                Button(onClick = { go() }, enabled = canGo, modifier = field.height(54.dp), shape = RoundedCornerShape(16.dp)) {
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                    } else {
                        Text(if (guided) "Start guided setup" else "Connect", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Your password is encrypted on this phone and is only ever sent to your router.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ModelCard(m: RouterModel, available: Boolean, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${m.brand} ${m.model}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text("Seen on: ${m.seenOn}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            val c = if (available) OnlineGreen else WarnAmber
            Box(Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(if (available) "Driver ready" else "No driver yet", color = c, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
