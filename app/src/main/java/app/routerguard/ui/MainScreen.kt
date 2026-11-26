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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.routerguard.MainViewModel
import app.routerguard.UiState
import app.routerguard.data.CredentialStore
import app.routerguard.data.Device
import app.routerguard.data.Schedule

private enum class Filter(val label: String) { All("All"), Online("Online"), Blocked("Blocked"), Offline("Offline") }

private enum class Tab(val title: String) { Devices("Devices"), Router("Router"), Health("Health"), Settings("Settings") }

@Composable
fun AppRoot(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val tabs = remember(state.mode) {
        if (state.mode != "browser") Tab.entries.toList() else Tab.entries.filter { it != Tab.Devices }
    }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = tabs.getOrElse(tabIndex) { tabs.first() }
    val routerController = remember { RouterController(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { routerController.release() } }
    val snackbar = remember { SnackbarHostState() }
    val scheme = MaterialTheme.colorScheme
    val creds = remember(state.host, state.user) { CredentialStore(context).load() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    Scaffold(
        containerColor = scheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.background),
                title = {
                    Column {
                        Text(if (tab == Tab.Devices) "Router Guard" else if (tab == Tab.Router) "Router page" else tab.title, fontWeight = FontWeight.Bold)
                        if (tab == Tab.Devices) {
                            Text(
                                if (state.loading) "Updating…" else if (state.lastRefresh > 0) "Updated ${ago(state.lastRefresh)}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (tab == Tab.Devices) {
                        IconButton(onClick = vm::refresh, enabled = !state.loading) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = scheme.surfaceContainer) {
                tabs.forEachIndexed { i, t ->
                    val icon = when (t) {
                        Tab.Devices -> Icons.Default.Home
                        Tab.Router -> Icons.Default.Build
                        Tab.Health -> Icons.Default.Favorite
                        Tab.Settings -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tabIndex = i },
                        icon = { Icon(icon, contentDescription = t.title) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize()) {
            val guided = state.driverMissing || state.mode == "browser"
            androidx.compose.animation.Crossfade(targetState = tab, label = "tabs") { t ->
                when (t) {
                    Tab.Devices ->
                        if (state.driverMissing) DriverMissing(state.driverNotice ?: "", pad) { tabIndex = tabs.indexOf(Tab.Router) }
                        else DevicesTab(state, vm, pad)
                    Tab.Health -> HealthScreen(pad)
                    Tab.Settings -> SettingsScreen(state, vm, pad)
                    Tab.Router -> Column(Modifier.fillMaxSize().padding(pad)) {
                        if (guided) SetupGuide()
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            RouterWebScreen(
                                controller = routerController,
                                host = state.host,
                                user = creds?.user ?: state.user,
                                password = creds?.password ?: "",
                                visible = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicesTab(state: UiState, vm: MainViewModel, pad: PaddingValues) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var expandedMac by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Device?>(null) }
    var scheduling by remember { mutableStateOf<Device?>(null) }
    var confirmSelf by remember { mutableStateOf<Device?>(null) }
    val scheme = MaterialTheme.colorScheme

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

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = vm::refresh,
        modifier = Modifier.padding(pad).fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.error != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = scheme.errorContainer), shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(state.error, color = scheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = vm::refresh) { Text("Retry") }
                        }
                    }
                }
            }
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerHigh)))
                        .padding(22.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("$online", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer)
                            Text("devices online now", color = scheme.onPrimaryContainer)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$blocked blocked", style = MaterialTheme.typography.titleMedium, color = scheme.error, fontWeight = FontWeight.SemiBold)
                            Text("$offline offline", color = scheme.onPrimaryContainer)
                            Text("${state.devices.size} remembered", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
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
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(shown, key = { it.mac }) { d ->
                val isSelf = d.online && state.selfIp != null && d.ip == state.selfIp
                DeviceCard(
                    d = d,
                    isSelf = isSelf,
                    schedule = state.schedules.firstOrNull { it.mac == d.mac },
                    busy = state.busyMac == d.mac,
                    locked = state.busyMac != null || state.loading,
                    expanded = expandedMac == d.mac,
                    onClick = { expandedMac = if (expandedMac == d.mac) null else d.mac },
                    onToggle = { if (!d.blocked && isSelf) confirmSelf = d else vm.setBlocked(d, !d.blocked) },
                    onRename = { renaming = d },
                    onSchedule = { scheduling = d },
                )
            }
        }
    }

    renaming?.let { d ->
        var text by remember(d.mac) { mutableStateOf(d.label.ifBlank { d.name }) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename device") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { vm.setLabel(d.mac, text.trim()); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    scheduling?.let { d ->
        ScheduleDialog(
            d = d,
            existing = state.schedules.firstOrNull { it.mac == d.mac },
            onSave = { vm.saveSchedule(it); scheduling = null },
            onRemove = { vm.removeSchedule(d.mac); scheduling = null },
            onDismiss = { scheduling = null },
        )
    }

    confirmSelf?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmSelf = null },
            title = { Text("Block this phone?") },
            text = { Text("This is the phone you are using. Blocking it will cut its Wi-Fi, and you would need another device or the router page to unblock it.") },
            confirmButton = {
                TextButton(onClick = { vm.setBlocked(d, true); confirmSelf = null }) { Text("Block anyway", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSelf = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ScheduleDialog(d: Device, existing: Schedule?, onSave: (Schedule) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val start = rememberTimePickerState((existing?.startMin ?: (23 * 60)) / 60, (existing?.startMin ?: 0) % 60, false)
    val end = rememberTimePickerState((existing?.endMin ?: (7 * 60)) / 60, (existing?.endMin ?: 0) % 60, false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block schedule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${d.title} will be blocked automatically every day:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Text("From", style = MaterialTheme.typography.labelLarge)
                TimeInput(state = start)
                Spacer(Modifier.height(8.dp))
                Text("Until", style = MaterialTheme.typography.labelLarge)
                TimeInput(state = end)
                Spacer(Modifier.height(8.dp))
                Text(
                    "The app checks every 15 minutes, so changes can be up to 15 minutes late. Keep this phone on Wi-Fi and the app's battery set to unrestricted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Schedule(d.mac, d.title, start.hour * 60 + start.minute, end.hour * 60 + end.minute, true))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun DeviceCard(
    d: Device,
    isSelf: Boolean,
    schedule: Schedule?,
    busy: Boolean,
    locked: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onSchedule: () -> Unit,
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
        if (schedule != null && schedule.enabled) append(" · ${fmtMinutes(schedule.startMin)}–${fmtMinutes(schedule.endMin)}")
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                    ) { Text("Block") }
                }
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = scheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Detail("MAC address", d.mac.uppercase())
                Detail("IP address", d.ip.ifBlank { "—" })
                Detail("Name from device", d.name.ifBlank { "—" })
                Detail("First seen", fullDate(d.firstSeen))
                Detail("Last online", if (d.online) "Now" else fullDate(d.lastSeen))
                Row {
                    TextButton(onClick = onRename) { Text("Rename") }
                    if (d.wifi) TextButton(onClick = onSchedule) { Text(if (schedule != null) "Edit schedule" else "Schedule") }
                }
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

@Composable
private fun DriverMissing(notice: String, pad: PaddingValues, onSetup: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(pad).padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(WarnAmber.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Build, null, tint = WarnAmber, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Driver needed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(notice, color = scheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onSetup, shape = RoundedCornerShape(16.dp)) { Text("Open guided setup") }
    }
}

/** Short, always-visible checklist for teaching the app a router it has no driver for. */
@Composable
private fun SetupGuide() {
    val scheme = MaterialTheme.colorScheme
    val recording by app.routerguard.data.RecorderHub.recording.collectAsState()
    val count by app.routerguard.data.RecorderHub.count.collectAsState()
    var open by rememberSaveable { mutableStateOf(true) }
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        onClick = { open = !open },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Guided setup", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (recording) "Recording: $count requests" else "Tap to ${if (open) "hide" else "show"}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            if (open) {
                Spacer(Modifier.height(6.dp))
                listOf(
                    "1. The page below signs in for you. If it doesn't, tap Auto-login.",
                    "2. Tap Record.",
                    "3. Open the list of connected devices, block one device you don't mind, then unblock it.",
                    "4. Tap Stop recording, review, and share the file. Passwords are removed.",
                ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
            }
        }
    }
}
