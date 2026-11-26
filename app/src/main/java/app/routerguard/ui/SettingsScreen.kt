package app.routerguard.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.routerguard.MainViewModel
import app.routerguard.UiState

@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel, pad: PaddingValues) {
    val scheme = MaterialTheme.colorScheme
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LazyColumn(
        modifier = Modifier.padding(pad).fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Section("Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Dark", 2 to "Light", 0 to "System").forEach { (mode, label) ->
                        FilterChip(selected = state.theme == mode, onClick = { vm.setTheme(mode) }, label = { Text(label) })
                    }
                }
            }
        }

        if (state.mode != "huawei") {
            item {
                Section("Browser mode") {
                    Text(
                        "Your router is shown through its own web page. Device list, block/unblock, alerts and schedules need a driver for your router model. Open the Router tab, tap Record, block and unblock a device in the page, then share the recording so support can be added.",
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.mode == "huawei") item {
            Section("New-device alerts") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notify me about unknown devices", fontWeight = FontWeight.SemiBold)
                        Text(
                            "The app checks your router in the background and alerts you when a device joins that it has never seen.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.alerts,
                        onCheckedChange = {
                            vm.setAlerts(it)
                            if (it && Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                }
                if (state.alerts) {
                    Spacer(Modifier.height(12.dp))
                    Text("Check every", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60).forEach { m ->
                            FilterChip(selected = state.intervalMin == m, onClick = { vm.setInterval(m) }, label = { Text("$m min") })
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Alerts and schedules run in the background. On Xiaomi/Redmi, set this app's battery saver to \"No restrictions\" and allow autostart, or Android may stop them. While a check runs, the router page can't be open in a browser.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
        }

        if (state.mode == "huawei") item {
            Section("Block schedules") {
                if (state.schedules.isEmpty()) {
                    Text(
                        "No schedules yet. Open a device on the Devices tab, tap it, then tap Schedule.",
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    )
                }
                state.schedules.forEachIndexed { i, s ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name.ifBlank { s.mac }, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${fmtMinutes(s.startMin)} – ${fmtMinutes(s.endMin)}, every day",
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = s.enabled, onCheckedChange = { vm.toggleSchedule(s.mac, it) })
                        IconButton(onClick = { vm.removeSchedule(s.mac) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete schedule", tint = scheme.error)
                        }
                    }
                }
            }
        }

        item {
            Section("Router") {
                Text(state.host, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Signed in as ${state.user}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = vm::signOut, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("Sign out of router")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your password is stored encrypted on this phone and only sent to your router.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
        }

        item {
            Text(
                "Router Guard 1.2",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
