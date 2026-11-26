package app.routerguard.data

/** Everything the app needs from a router, whatever brand it is. */
interface RouterDriver {
    fun login()
    fun logout()
    fun snapshot(): Snapshot
    fun block(mac: String, ssid: String)
    fun unblock(mac: String)
}
