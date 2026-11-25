package app.routerguard.data

/** A device as reported live by the router. */
data class LiveDevice(
    val mac: String,
    val ip: String,
    val name: String,
    val ssid: String,
    val online: Boolean,
    val wifi: Boolean,
)

/** One entry of the router's Wi-Fi MAC filter (the block list). */
data class FilterEntry(val domain: String, val ssid: String, val mac: String)

/** A device as remembered by the app. */
data class Device(
    val mac: String,
    val name: String = "",
    val label: String = "",
    val ip: String = "",
    val ssid: String = "SSID-1",
    val wifi: Boolean = true,
    val firstSeen: Long = 0L,
    val lastSeen: Long = 0L,
    val online: Boolean = false,
    val blocked: Boolean = false,
) {
    val title: String get() = label.ifBlank { name }.ifBlank { "Unknown device" }
}

class RouterException(message: String) : Exception(message)

/** What one trip to the router returns. */
data class Snapshot(val live: List<LiveDevice>, val filter: List<FilterEntry>, val selfIp: String?)

object Merge {
    /** Combines what the app remembers with what the router says right now. */
    fun merge(stored: List<Device>, snap: Snapshot, now: Long): List<Device> {
        val map = LinkedHashMap<String, Device>()
        stored.forEach { map[it.mac] = it.copy(online = false, blocked = false) }
        for (d in snap.live) {
            val old = map[d.mac]
            map[d.mac] = if (old == null) {
                Device(d.mac, d.name, "", d.ip, d.ssid, d.wifi, now, now, d.online, false)
            } else {
                old.copy(
                    name = d.name.ifBlank { old.name },
                    ip = d.ip.ifBlank { old.ip },
                    ssid = d.ssid,
                    wifi = d.wifi,
                    online = d.online,
                    lastSeen = if (d.online) now else old.lastSeen,
                )
            }
        }
        for (f in snap.filter) {
            val old = map[f.mac] ?: Device(f.mac, ssid = f.ssid, firstSeen = now, lastSeen = now)
            map[f.mac] = old.copy(blocked = true)
        }
        return map.values.toList()
    }
}
