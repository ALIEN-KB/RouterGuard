package app.routerguard.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLEncoder

/** Plain HTTP client with a cookie jar and manual redirects, which is what router pages need. */
class HttpSession(private val base: String, private val open: (URL) -> URLConnection) {
    private val cookies = LinkedHashMap<String, String>()

    fun request(
        method: String,
        path: String,
        form: Map<String, String>? = null,
        body: String? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
        cookie: String? = null,
        ajax: Boolean = false,
        referer: String? = null,
    ): String {
        var url = URL(base + path)
        var m = method.uppercase()
        var payload: ByteArray? = when {
            form != null -> form.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }.toByteArray()
            body != null -> body.toByteArray()
            else -> null
        }
        var type = contentType ?: if (form != null) "application/x-www-form-urlencoded" else null
        var hops = 0
        while (true) {
            val c = open(url) as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 8_000
                c.readTimeout = 20_000
                c.requestMethod = m
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Origin", base)
                c.setRequestProperty("Referer", referer ?: "$base/")
                val jar = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                val sendCookie = if (hops == 0 && cookie != null) cookie else jar
                if (sendCookie.isNotEmpty()) c.setRequestProperty("Cookie", sendCookie)
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (ajax) c.setRequestProperty("X-Requested-With", "XMLHttpRequest")
                if (m == "POST") {
                    c.doOutput = true
                    if (type != null) c.setRequestProperty("Content-Type", type)
                    c.outputStream.use { it.write(payload ?: ByteArray(0)) }
                }
                val code = c.responseCode
                c.headerFields.entries.filter { it.key.equals("Set-Cookie", ignoreCase = true) }.flatMap { it.value }.forEach { raw ->
                    val pair = raw.substringBefore(';')
                    val name = pair.substringBefore('=').trim()
                    if (name.isNotEmpty()) cookies[name] = pair.substringAfter('=', "")
                }
                if ((code in 301..303 || code == 307 || code == 308) && hops < 5) {
                    val loc = c.getHeaderField("Location")
                    if (loc != null) {
                        c.inputStream?.close()
                        url = URL(url, loc)
                        m = "GET"; payload = null; type = null
                        hops++
                        continue
                    }
                }
                val stream = if (code >= 400) c.errorStream else c.inputStream
                return stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            } finally {
                c.disconnect()
            }
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36"
    }
}
