package app.routerguard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.routerguard.data.AppSettings
import app.routerguard.data.CredentialStore
import app.routerguard.data.Credentials
import app.routerguard.data.Device
import app.routerguard.data.DeviceStore
import app.routerguard.data.Merge
import app.routerguard.data.RouterClient
import app.routerguard.data.RouterException
import app.routerguard.data.Schedule
import app.routerguard.data.Scheduler
import app.routerguard.data.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class UiState(
    val configured: Boolean,
    val host: String,
    val user: String,
    val devices: List<Device> = emptyList(),
    val loading: Boolean = false,
    val busyMac: String? = null,
    val selfIp: String? = null,
    val lastRefresh: Long = 0L,
    val error: String? = null,
    val loginError: String? = null,
    val message: String? = null,
    val theme: Int = 1,
    val alerts: Boolean = false,
    val intervalMin: Int = 30,
    val schedules: List<Schedule> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val store = DeviceStore(context)
    private val creds = CredentialStore(context)
    private val settings = AppSettings(context)

    private val _state: MutableStateFlow<UiState>
    val state: StateFlow<UiState> get() = _state

    init {
        val saved = creds.load()
        _state = MutableStateFlow(
            UiState(
                configured = saved != null,
                host = saved?.host ?: creds.lastHost(),
                user = saved?.user ?: creds.lastUser(),
                devices = if (saved != null) sorted(store.load()) else emptyList(),
                theme = settings.theme,
                alerts = settings.alerts,
                intervalMin = settings.intervalMin,
                schedules = settings.schedules,
            )
        )
        Scheduler.sync(context)
        if (saved != null) refresh()
    }

    private val busy get() = _state.value.loading || _state.value.busyMac != null

    fun refresh() {
        val c = creds.load() ?: return
        if (busy) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                apply(withContext(Dispatchers.IO) { talk(c) { snapshot() } })
            } catch (e: Exception) {
                fail(e, c.host)
            }
        }
    }

    fun refreshIfStale() {
        val s = _state.value
        if (s.configured && !busy && System.currentTimeMillis() - s.lastRefresh > 45_000) refresh()
    }

    fun connect(host: String, user: String, password: String) {
        if (busy) return
        val c = Credentials(host.trim(), user.trim(), password)
        _state.update { it.copy(loading = true, loginError = null) }
        viewModelScope.launch {
            try {
                val snap = withContext(Dispatchers.IO) { talk(c) { snapshot() } }
                creds.save(c)
                _state.update { it.copy(configured = true, host = c.host, user = c.user) }
                apply(snap)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loginError = friendly(e, c.host)) }
            }
        }
    }

    fun setBlocked(d: Device, shouldBlock: Boolean) {
        val c = creds.load() ?: return
        if (busy) return
        _state.update { it.copy(busyMac = d.mac, error = null) }
        viewModelScope.launch {
            try {
                val snap = withContext(Dispatchers.IO) {
                    talk(c) {
                        if (shouldBlock) block(d.mac, d.ssid) else unblock(d.mac)
                        snapshot()
                    }
                }
                apply(snap, (if (shouldBlock) "Blocked " else "Unblocked ") + d.title)
            } catch (e: Exception) {
                fail(e, c.host)
            }
        }
    }

    fun setLabel(mac: String, label: String) {
        val updated = _state.value.devices.map { if (it.mac == mac) it.copy(label = label) else it }
        store.save(updated)
        _state.update { it.copy(devices = sorted(updated)) }
    }

    fun signOut() {
        creds.clear()
        _state.update {
            it.copy(configured = false, devices = emptyList(), error = null, loginError = null, loading = false)
        }
    }

    fun setTheme(mode: Int) {
        settings.theme = mode
        _state.update { it.copy(theme = mode) }
    }

    fun setAlerts(on: Boolean) {
        settings.alerts = on
        _state.update { it.copy(alerts = on) }
        Scheduler.sync(context)
    }

    fun setInterval(minutes: Int) {
        settings.intervalMin = minutes
        _state.update { it.copy(intervalMin = minutes) }
        Scheduler.sync(context)
    }

    fun saveSchedule(s: Schedule) {
        val list = settings.schedules.filter { it.mac != s.mac } + s
        settings.schedules = list
        _state.update { it.copy(schedules = list, message = "Schedule saved for ${s.name}. Checked every 15 minutes.") }
        Scheduler.sync(context)
    }

    fun removeSchedule(mac: String) {
        val list = settings.schedules.filter { it.mac != mac }
        settings.schedules = list
        _state.update { it.copy(schedules = list) }
        Scheduler.sync(context)
    }

    fun toggleSchedule(mac: String, enabled: Boolean) {
        val list = settings.schedules.map { if (it.mac == mac) it.copy(enabled = enabled) else it }
        settings.schedules = list
        _state.update { it.copy(schedules = list) }
        Scheduler.sync(context)
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    // ---- helpers ----

    private fun <T> talk(c: Credentials, action: RouterClient.() -> T): T {
        val network = RouterClient.wifiNetwork(context)
            ?: throw RouterException("You're not connected to Wi-Fi. Join your home Wi-Fi and try again.")
        return RouterClient.session(RouterClient(c.host, c.user, c.password, network), action)
    }

    private fun apply(snap: Snapshot, message: String? = null) {
        val now = System.currentTimeMillis()
        val merged = Merge.merge(store.load(), snap, now)
        store.save(merged)
        _state.update {
            it.copy(
                devices = sorted(merged),
                selfIp = snap.selfIp ?: it.selfIp,
                lastRefresh = now,
                loading = false,
                busyMac = null,
                error = null,
                message = message ?: it.message,
            )
        }
    }

    private fun fail(e: Exception, host: String) {
        _state.update { it.copy(loading = false, busyMac = null, error = friendly(e, host)) }
    }

    private fun friendly(e: Throwable, host: String): String = when (e) {
        is RouterException -> e.message ?: "Router error."
        is SocketTimeoutException, is ConnectException, is UnknownHostException, is NoRouteToHostException ->
            "Can't reach the router at $host. Check that you're on your home Wi-Fi. If you are, open Android Settings > Apps > Router Guard > Data usage and allow Wi-Fi (Xiaomi/Redmi phones can block new apps)."
        is IOException -> "Network problem: ${e.message}"
        else -> e.message ?: "Something went wrong."
    }

    private fun sorted(list: List<Device>): List<Device> =
        list.sortedWith(
            compareByDescending<Device> { it.online }
                .thenByDescending { it.blocked }
                .thenByDescending { it.lastSeen }
        )
}
