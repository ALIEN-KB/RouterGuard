package app.routerguard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.routerguard.data.CredentialStore
import app.routerguard.data.Diagnostics
import app.routerguard.data.DnsResult
import app.routerguard.data.LatencyResult
import app.routerguard.data.RouterClient
import app.routerguard.data.WifiSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HealthState(
    val wifi: WifiSnapshot? = null,
    val latency: List<LatencyResult> = emptyList(),
    val latencyRunning: Boolean = false,
    val dns: List<DnsResult> = emptyList(),
    val dnsRunning: Boolean = false,
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    val speedStage: String? = null, // "download", "upload" or null when idle
    val error: String? = null,
)

class HealthViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val host = CredentialStore(context).lastHost()
    private val _state = MutableStateFlow(HealthState())
    val state: StateFlow<HealthState> get() = _state

    private val noWifi = "Not connected to Wi-Fi. Join your home Wi-Fi to run this test."

    fun refreshWifi() {
        _state.update { it.copy(wifi = Diagnostics.wifi(context)) }
    }

    fun runLatency() {
        val net = RouterClient.wifiNetwork(context)
        if (net == null) { _state.update { it.copy(error = noWifi) }; return }
        if (_state.value.latencyRunning) return
        _state.update { it.copy(latencyRunning = true, error = null) }
        viewModelScope.launch {
            val results = coroutineScope {
                listOf(
                    async { Diagnostics.latency("Your router", host, 80, net) },
                    async { Diagnostics.latency("Cloudflare", "1.1.1.1", 443, net) },
                    async { Diagnostics.latency("Google", "8.8.8.8", 443, net) },
                ).awaitAll()
            }
            _state.update { it.copy(latency = results, latencyRunning = false) }
        }
    }

    fun runDns() {
        val net = RouterClient.wifiNetwork(context)
        if (net == null) { _state.update { it.copy(error = noWifi) }; return }
        if (_state.value.dnsRunning) return
        _state.update { it.copy(dnsRunning = true, error = null) }
        viewModelScope.launch {
            val results = coroutineScope {
                listOf(
                    async { Diagnostics.dns("Router (your ISP)", host, net) },
                    async { Diagnostics.dns("Cloudflare", "1.1.1.1", net) },
                    async { Diagnostics.dns("Google", "8.8.8.8", net) },
                    async { Diagnostics.dns("Quad9", "9.9.9.9", net) },
                ).awaitAll()
            }
            _state.update { it.copy(dns = results, dnsRunning = false) }
        }
    }

    fun runSpeed() {
        val net = RouterClient.wifiNetwork(context)
        if (net == null) { _state.update { it.copy(error = noWifi) }; return }
        if (_state.value.speedStage != null) return
        _state.update { it.copy(speedStage = "download", downMbps = null, upMbps = null, error = null) }
        viewModelScope.launch {
            val down = Diagnostics.download(net)
            _state.update { it.copy(downMbps = down, speedStage = "upload") }
            val up = Diagnostics.upload(net)
            _state.update {
                it.copy(
                    upMbps = up, speedStage = null,
                    error = if (down == 0.0 && up == 0.0) "Speed test couldn't reach the internet." else null,
                )
            }
        }
    }
}
