package app.routerguard.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Base64
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val UA =
    "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36"
private const val FILTER_DOMAIN = "InternetGatewayDevice.X_HW_Security.WLANMacFilter"
private const val TOKEN_PAGE = "/html/bbsp/userdevinfo/userdevinfosmart.asp"

/**
 * Talks to the Huawei HG8546M web interface exactly the way its own web page does.
 * Use [session] so the router's single admin session is always released afterwards.
 */
class RouterClient(
    host: String,
    private val user: String,
    private val password: String,
    network: Network?,
) {
    private val base = "http://" + host.trim().removePrefix("http://").trimEnd('/')
    private val cookies = LinkedHashMap<String, String>()
    private var selfIpCache: String? = null

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .apply { if (network != null) socketFactory(network.socketFactory) }
        .addInterceptor { chain ->
            val b = chain.request().newBuilder()
            if (cookies.isNotEmpty() && chain.request().header("Cookie") == null) {
                b.header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            val response = chain.proceed(b.build())
            response.headers("Set-Cookie").forEach { raw ->
                val pair = raw.substringBefore(';')
                val name = pair.substringBefore('=').trim()
                if (name.isNotEmpty()) cookies[name] = pair.substringAfter('=', "")
            }
            response
        }
        .build()

    private fun call(
        path: String,
        form: Map<String, String>? = null,
        ajax: Boolean = false,
        referer: String? = null,
        cookie: String? = null,
    ): String {
        val rb = Request.Builder()
            .url(base + path)
            .header("Origin", base)
            .header("Referer", referer ?: "$base/")
            .header("User-Agent", UA)
        if (ajax) rb.header("X-Requested-With", "XMLHttpRequest")
        if (cookie != null) rb.header("Cookie", cookie)
        if (form != null) {
            val body = FormBody.Builder()
            form.forEach { (k, v) -> body.add(k, v) }
            rb.post(body.build())
        }
        http.newCall(rb.build()).execute().use { return it.body?.string() ?: "" }
    }

    fun login() {
        val page = call("/")
        val token = Parsing.loginToken(page)
            ?: throw RouterException("This doesn't look like the router's login page. Check the address.")
        call(
            "/login.cgi",
            mapOf(
                "UserName" to user,
                "PassWord" to Base64.encodeToString(password.toByteArray(), Base64.NO_WRAP),
                "x.X_HW_Token" to token,
            ),
            // The router's own login page sets this cookie with JavaScript before submitting.
            cookie = "Cookie=body:Language:english:id=-1",
        )
        if (!call("/html/bbsp/common/GetLanUserDevInfo.asp", emptyMap(), ajax = true).contains("USERDevice")) {
            throw RouterException(
                "Login failed. Check the username and password, and close the router page if it is " +
                    "open in a browser (the router allows only one admin at a time)."
            )
        }
    }

    fun logout() {
        runCatching { call("/logout.cgi?RequestFile=html/logout.html", emptyMap()) }
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    fun devices(): List<LiveDevice> =
        Parsing.devices(call("/html/bbsp/common/GetLanUserDevInfo.asp", emptyMap(), ajax = true))

    fun filterEntries(): List<FilterEntry> =
        Parsing.filterEntries(call("/html/bbsp/wlanmacfilter/wlanmacfilter.asp"))

    /** The action token is single-use, so a fresh one is read before every change. */
    private fun freshToken(): String {
        val html = call("$TOKEN_PAGE?type=wifidev")
        Parsing.selfIp(html)?.let { selfIpCache = it }
        return Parsing.actionToken(html)
            ?: throw RouterException("The router session expired. Pull down to try again.")
    }

    /** The IP address of the phone running this app, as the router sees it. */
    fun selfIp(): String? {
        if (selfIpCache == null) freshToken()
        return selfIpCache
    }

    private fun change(action: String, form: MutableMap<String, String>) {
        form["x.X_HW_Token"] = freshToken()
        val url = "/html/bbsp/userdevinfo/$action.cgi?x=$FILTER_DOMAIN&RequestFile=$TOKEN_PAGE"
        val reply = Parsing.unescape(call(url, form, ajax = true, referer = "$base$TOKEN_PAGE?type=wifidev"))
        if (!Regex("\"result\"\\s*:\\s*0").containsMatchIn(reply)) {
            throw RouterException("The router rejected the change: ${reply.trim().take(120)}")
        }
    }

    fun block(mac: String, ssid: String) {
        change(
            "addajax",
            mutableMapOf("x.SSIDName" to ssid, "x.SourceMACAddress" to mac, "x.Enable" to "1"),
        )
    }

    fun unblock(mac: String) {
        filterEntries().filter { it.mac == mac.lowercase() }.forEach { change("delajax", mutableMapOf(it.domain to "")) }
    }

    fun snapshot(): Snapshot = Snapshot(devices(), filterEntries(), selfIp())

    companion object {
        /** Runs [action] logged in, and always logs out afterwards. */
        fun <T> session(client: RouterClient, action: RouterClient.() -> T): T {
            client.login()
            try {
                return client.action()
            } finally {
                client.logout()
            }
        }

        /** Binds traffic to Wi-Fi so it still works while mobile data is on. */
        @Suppress("DEPRECATION")
        fun wifiNetwork(context: Context): Network? {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            return cm.allNetworks.firstOrNull {
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        }
    }
}
