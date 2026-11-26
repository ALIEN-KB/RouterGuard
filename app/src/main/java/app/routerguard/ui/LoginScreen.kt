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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
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
    onConnect: (host: String, user: String, password: String) -> Unit,
    onBrowserMode: (host: String, user: String, password: String) -> Unit,
) {
    var browser by rememberSaveable { mutableStateOf(false) }
    var host by rememberSaveable { mutableStateOf(state.host) }
    var user by rememberSaveable { mutableStateOf(state.user) }
    var pass by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val canConnect = !state.loading && host.isNotBlank() && (browser || (pass.isNotEmpty() && user.isNotBlank()))
    val field = Modifier.fillMaxWidth()
    val round = RoundedCornerShape(14.dp)

    Scaffold { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Router Guard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (browser) "Opens your router's own page inside the app with auto-login, and can record how it works so support can be added."
                else "See who is on your Wi-Fi, and block or unblock devices in one tap.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !browser, onClick = { browser = false }, label = { Text("Huawei HG8546M") })
                FilterChip(selected = browser, onClick = { browser = true }, label = { Text("Other router") })
            }
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = host, onValueChange = { host = it }, label = { Text("Router address") },
                singleLine = true, modifier = field, shape = round,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = user, onValueChange = { user = it }, label = { Text(if (browser) "Username (optional)" else "Username") },
                singleLine = true, modifier = field, shape = round,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pass, onValueChange = { pass = it }, label = { Text(if (browser) "Password (optional, for auto-login)" else "Password") },
                singleLine = true, modifier = field, shape = round,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canConnect) { if (browser) onBrowserMode(host, user, pass) else onConnect(host, user, pass) } }),
            )

            if (state.loginError != null) {
                Spacer(Modifier.height(14.dp))
                Text(state.loginError, color = scheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (browser) onBrowserMode(host, user, pass) else onConnect(host, user, pass) },
                enabled = canConnect,
                modifier = field.height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                } else {
                    Text(if (browser) "Open router page" else "Connect", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Your password is encrypted on this phone and is only ever sent to your router.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
