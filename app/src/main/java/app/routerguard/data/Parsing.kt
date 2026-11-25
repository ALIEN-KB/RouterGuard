package app.routerguard.data

/** Pure functions that read the router's (old-style, JavaScript-in-HTML) pages. */
object Parsing {
    private val hexEscape = Regex("""\\x([0-9a-fA-F]{2})""")
    private val deviceRe = Regex("new USERDevice\\(((?:\"[^\"]*\",?)+)\\)")
    private val stringRe = Regex("\"([^\"]*)\"")
    private val filterRe = Regex("new stMacFilter\\(\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\"\\)")
    private val tokenRe = Regex("var token = '([0-9a-f]+)'")
    private val selfIpRe = Regex("var ConnectDevIp = '([^']*)'")
    private val loginTokenRe = Regex("GetRandCnt\\(\\)\\s*\\{\\s*return '([0-9a-f]+)'")
    private val portRe = Regex("SSID(\\d+)")

    /** The router writes characters as \xNN inside its JavaScript strings. */
    fun unescape(s: String): String =
        hexEscape.replace(s) { it.groupValues[1].toInt(16).toChar().toString() }

    fun devices(js: String): List<LiveDevice> = deviceRe.findAll(js).mapNotNull { m ->
        val f = stringRe.findAll(m.groupValues[1]).map { unescape(it.groupValues[1]) }.toList()
        if (f.size < 10) return@mapNotNull null
        val index = portRe.matchEntire(f[3])?.groupValues?.get(1)
        LiveDevice(
            mac = f[2].lowercase(),
            ip = f[1],
            name = f[9].takeIf { it != "--" } ?: "",
            // "SSID0" is only a placeholder for offline devices; SSID-1 is the usual Wi-Fi name.
            ssid = if (index != null && index != "0") "SSID-$index" else "SSID-1",
            online = f[6] == "Online",
            wifi = f[7] == "WIFI",
        )
    }.toList()

    fun filterEntries(html: String): List<FilterEntry> = filterRe.findAll(html).map {
        FilterEntry(unescape(it.groupValues[1]), unescape(it.groupValues[2]), unescape(it.groupValues[3]).lowercase())
    }.toList()

    fun actionToken(html: String): String? = tokenRe.find(html)?.groupValues?.get(1)

    fun selfIp(html: String): String? =
        selfIpRe.find(html)?.groupValues?.get(1)?.let { unescape(it) }?.takeIf { it.isNotBlank() }

    fun loginToken(html: String): String? = loginTokenRe.find(html)?.groupValues?.get(1)
}
