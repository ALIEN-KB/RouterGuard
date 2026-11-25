package app.routerguard.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.routerguard.HealthViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HealthScreen(pad: PaddingValues, vm: HealthViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    val scheme = MaterialTheme.colorScheme

    // Live signal meter while this tab is open.
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshWifi()
            delay(2500)
        }
    }

    LazyColumn(
        modifier = Modifier.padding(pad).fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (s.error != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = scheme.errorContainer), shape = RoundedCornerShape(16.dp)) {
                    Text(s.error!!, color = scheme.onErrorContainer, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ---- Wi-Fi link ----
        item {
            HealthCard("Wi-Fi link", "Live, from this phone") {
                val w = s.wifi
                if (w == null) {
                    Text("Not connected to Wi-Fi.", color = scheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${w.rssi}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text(" dBm", Modifier.padding(bottom = 6.dp), color = scheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Pill(w.band)
                        Spacer(Modifier.width(6.dp))
                        Pill(w.quality, qualityColor(w.quality))
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { w.level },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = qualityColor(w.quality),
                        trackColor = scheme.surfaceContainerHighest,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Stat("Link speed", "${w.linkMbps} Mbps", Modifier.weight(1f))
                        if (w.rxMbps > 0) Stat("Receive", "${w.rxMbps} Mbps", Modifier.weight(1f))
                        if (w.txMbps > 0) Stat("Send", "${w.txMbps} Mbps", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Link speed is what your phone and router negotiate, not your internet speed. Your router only has 2.4 GHz Wi-Fi, so it tops out far below the internet plan.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- Speed test ----
        item {
            HealthCard("Internet speed", "Uses up to ~100 MB of data") {
                Row(Modifier.fillMaxWidth()) {
                    Stat("Download", s.downMbps?.let { fmt(it) + " Mbps" } ?: "—", Modifier.weight(1f), big = true)
                    Stat("Upload", s.upMbps?.let { fmt(it) + " Mbps" } ?: "—", Modifier.weight(1f), big = true)
                }
                Spacer(Modifier.height(14.dp))
                val running = s.speedStage != null
                Button(onClick = vm::runSpeed, enabled = !running, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
                    if (running) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text(if (s.speedStage == "download") "Testing download…" else "Testing upload…")
                    } else {
                        Text(if (s.downMbps == null) "Start speed test" else "Run again")
                    }
                }
            }
        }

        // ---- Latency ----
        item {
            HealthCard("Latency and stability", "10 quick connections to each target") {
                if (s.latency.isEmpty()) {
                    Text("Shows delay, jitter and packet loss. Low jitter and 0% loss matter most for calls and gaming.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else {
                    s.latency.forEachIndexed { i, r ->
                        if (i > 0) Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (r.avgMs == null) "No response" else "${r.minMs}–${r.maxMs} ms · jitter ${r.jitterMs} ms · loss ${r.lossPct}%",
                                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (r.avgMs == null) "—" else "${r.avgMs} ms",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                color = if (r.avgMs == null) scheme.error else msColor(r.avgMs, r.lossPct),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = vm::runLatency, enabled = !s.latencyRunning, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
                    if (s.latencyRunning) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text("Testing…")
                    } else {
                        Text(if (s.latency.isEmpty()) "Test latency" else "Run again")
                    }
                }
            }
        }

        // ---- DNS ----
        item {
            HealthCard("DNS speed", "How fast each DNS server answers") {
                if (s.dns.isEmpty()) {
                    Text("Compares your router's DNS (your ISP) with Cloudflare, Google and Quad9.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else {
                    val best = s.dns.filter { it.medianMs != null }.minByOrNull { it.medianMs!! }
                    s.dns.forEachIndexed { i, r ->
                        if (i > 0) Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.label, fontWeight = FontWeight.SemiBold)
                                    if (r == best) {
                                        Spacer(Modifier.width(8.dp))
                                        Pill("Fastest", OnlineGreen)
                                    }
                                }
                                Text(
                                    if (r.failed > 0) "${r.server} · ${r.failed} of 5 failed" else r.server,
                                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (r.medianMs == null) "—" else "${r.medianMs} ms",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                color = if (r.medianMs == null) scheme.error else msColor(r.medianMs, 0, 40, 120),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "To use a faster one on this phone: Settings > Network > Private DNS, for example one.one.one.one or dns.google.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = vm::runDns, enabled = !s.dnsRunning, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
                    if (s.dnsRunning) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text("Testing…")
                    } else {
                        Text(if (s.dns.isEmpty()) "Compare DNS servers" else "Run again")
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Pill(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
}

private fun fmt(v: Double): String = if (v >= 100) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)

@Composable
private fun qualityColor(q: String): Color = when (q) {
    "Excellent", "Good" -> OnlineGreen
    "Fair" -> WarnAmber
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun msColor(ms: Int, lossPct: Int, good: Int = 30, ok: Int = 80): Color = when {
    lossPct >= 10 || ms > ok -> MaterialTheme.colorScheme.error
    lossPct > 0 || ms > good -> WarnAmber
    else -> OnlineGreen
}
