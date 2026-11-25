@file:OptIn(ExperimentalMaterial3Api::class)

package app.routerguard.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.routerguard.UiState
import app.routerguard.data.Device
import java.text.DateFormat
import java.util.Date

private enum class Filter(val label: String) { All("All"), Online("Online"), Blocked("Blocked"), Offline("Offline") }

@Composable
fun MainScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onToggleBlock: (Device, Boolean) -> Unit,
    onRename: (mac: String, label: String) -> Unit,
    onSignOut: () -> Unit,
    onMessageShown: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var expandedMac by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Device?>(null) }
    var confirmSelf by remember { mutableStateOf<Device?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    val online = state.devices.count { it.online }
    val blocked = state.devices.count { it.blocked }
    val offline = state.devices.count { !it.online && !it.blocked }
    val q = query.trim()
    val shown = state.devices.filter { d ->
        val inFilter = when (filter) {
            Filter.All -> true
            Filter.Online -> d.online
            Filter.Blocked -> d.blocked
            Filter.Offline -> !d.online && !d.blocked
        }
        inFilter && (q.isEmpty() || listOf(d.title, d.name, d.label, d.mac, d.ip).any { it.contains(q, ignoreCase = true) })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Router Guard", fontWeight = FontWeight.Bold)
                        Text(
                            if (state.loading) "Updating…" else if (state.lastRefresh > 0) "Updated ${ago(state.lastRefresh)}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Sign out of router") },
                                onClick = { menuOpen = false; onSignOut() },
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.error != null) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = scheme.errorContainer), shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(state.error, color = scheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = onRefresh) { Text("Retry") }
                            }
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("$online", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer)
                                Text("devices online now", color = scheme.onPrimaryContainer)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$blocked blocked", style = MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                                Text("$offline offline", color = scheme.onPrimaryContainer)
                                Text("${state.devices.size} remembered", style = MaterialTheme.typography.bodySmall, color = scheme.onPrimaryContainer)
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search name, MAC or IP") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Filter.entries.forEach { f ->
                            val n = when (f) {
                                Filter.All -> state.devices.size
                                Filter.Online -> online
                                Filter.Blocked -> blocked
                                Filter.Offline -> offline
                            }
                            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} $n") })
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            if (state.loading) "Looking for devices…" else "No devices to show.",
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                items(shown, key = { it.mac }) { d ->
                    val isSelf = d.online && state.selfIp != null && d.ip == state.selfIp
                    DeviceCard(
                        d = d,
                        isSelf = isSelf,
                        busy = state.busyMac == d.mac,
                        locked = state.busyMac != null || state.loading,
                        expanded = expandedMac == d.mac,
                        onClick = { expandedMac = if (expandedMac == d.mac) null else d.mac },
                        onToggle = {
                            if (!d.blocked && isSelf) confirmSelf = d else onToggleBlock(d, !d.blocked)
                        },
                        onRename = { renaming = d },
                    )
                }
            }
        }
    }

    renaming?.let { d ->
        var text by remember(d.mac) { mutableStateOf(d.label.ifBlank { d.name }) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename device") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = { TextButton(onClick = { onRename(d.mac, text.trim()); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    confirmSelf?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmSelf = null },
            title = { Text("Block this phone?") },
            text = { Text("This is the phone you are using. Blocking it will cut its Wi-Fi, and you would need another device or the router page to unblock it.") },
            confirmButton = {
                TextButton(onClick = { onToggleBlock(d, true); confirmSelf = null }) { Text("Block anyway", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSelf = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DeviceCard(
    d: Device,
    isSelf: Boolean,
    busy: Boolean,
    locked: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onRename: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val statusColor = when {
        d.blocked -> scheme.error
        d.online -> OnlineGreen
        else -> scheme.outline
    }
    val subtitle = buildString {
        append(
            when {
                d.blocked -> "Blocked"
                d.online -> "Online"
                else -> if (d.lastSeen > 0) "Seen ${ago(d.lastSeen)}" else "Offline"
            }
        )
        if (d.online && d.ip.isNotBlank()) append(" · ${d.ip}")
        if (!d.wifi) append(" · wired")
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        d.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                        color = statusColor, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            d.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isSelf) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "This phone", style = MaterialTheme.typography.labelSmall, color = scheme.onSecondaryContainer,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(scheme.secondaryContainer).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                        Spacer(Modifier.width(6.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                when {
                    busy -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    d.blocked -> FilledTonalButton(onClick = onToggle, enabled = !locked) { Text("Unblock") }
                    d.wifi -> OutlinedButton(
                        onClick = onToggle, enabled = !locked,
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                    ) { Text("Block") }
                }
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Detail("MAC address", d.mac.uppercase())
                Detail("IP address", d.ip.ifBlank { "—" })
                Detail("Name from device", d.name.ifBlank { "—" })
                Detail("First seen", fullDate(d.firstSeen))
                Detail("Last online", if (d.online) "Now" else fullDate(d.lastSeen))
                TextButton(onClick = onRename) { Text("Rename") }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun fullDate(t: Long): String =
    if (t <= 0) "—" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))

private fun ago(t: Long): String {
    val s = (System.currentTimeMillis() - t) / 1000
    return when {
        s < 90 -> "just now"
        s < 5400 -> "${s / 60} min ago"
        s < 172800 -> "${s / 3600} h ago"
        else -> "${s / 86400} days ago"
    }
}
