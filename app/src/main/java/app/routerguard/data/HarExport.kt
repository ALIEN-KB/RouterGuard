package app.routerguard.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Turns recorded traffic into a HAR file with passwords removed and (optionally) MAC addresses masked. */
object HarExport {
    private val sensitiveName = Regex("(?i)pass|pwd|psk|secret|passphrase|wpakey|credential|auth")
    private val secretValue = Regex(
        """(?i)((?:wpa\w*key|presharedkey|passphrase|psk|password|passwd|pwd)\w*["']?\s*[:=,(]\s*)(["'])[^"']+\2"""
    )
    private val formSecret = Regex("""(?i)(\w*(?:pass|pwd|psk)\w*=)[^&\s"'<]+""")
    private val jsonSecret = Regex("""("[^"]*(?:pass|pwd|psk|secret)[^"]*"\s*:\s*)"[^"]*"""", RegexOption.IGNORE_CASE)
    private val macRe = Regex("""(?i)[0-9a-f]{2}(?:(?::|\\x3a|%3a|-)[0-9a-f]{2}){5}""")
    private val macSeparators = Regex("""(?i)\\x3a|%3a|[:-]""")

    /** Pages that usually carry the Wi-Fi password or account settings: their content is left out. */
    private val riskyPage = Regex("(?i)wlanbasic|wlansecurity|wlancfg|wpa|psk|passw|account|usercfg|backup|\\.bin")

    private class MacMasker {
        private val seen = LinkedHashMap<String, Int>()
        fun mask(s: String): String = macRe.replace(s) { m ->
            val v = m.value
            val sep = when {
                v.contains("\\x3a", ignoreCase = true) -> "\\x3a"
                v.contains("%3a", ignoreCase = true) -> v.substring(2, 5)
                v[2] == '-' -> "-"
                else -> ":"
            }
            val key = v.replace(macSeparators, "").lowercase()
            val n = seen.getOrPut(key) { seen.size + 1 }
            listOf("02", "00", "00", "00", "00", String.format(Locale.US, "%02x", n and 0xFF)).joinToString(sep)
        }
    }

    fun redactRequest(body: String, mime: String): String {
        if (body.isEmpty() || body.startsWith("<binary")) return body
        return if (mime.contains("x-www-form-urlencoded") || (mime.isEmpty() && body.contains('='))) {
            body.split('&').joinToString("&") { pair ->
                val name = pair.substringBefore('=')
                val decoded = runCatching { java.net.URLDecoder.decode(name, "UTF-8") }.getOrDefault(name)
                if (pair.contains('=') && sensitiveName.containsMatchIn(decoded)) "$name=<redacted>" else pair
            }
        } else {
            jsonSecret.replace(body) { "${it.groupValues[1]}\"<redacted>\"" }
        }
    }

    fun redactResponse(url: String, body: String): String {
        if (body.isEmpty() || body.startsWith("<omitted") || body.startsWith("<binary")) return body
        if (riskyPage.containsMatchIn(url.substringBefore('?'))) return "<omitted: page may contain Wi-Fi or account passwords>"
        return formSecret.replace(
            secretValue.replace(body) { "${it.groupValues[1]}${it.groupValues[2]}<redacted>${it.groupValues[2]}" }
        ) { "${it.groupValues[1]}<redacted>" }
    }

    fun build(entries: List<HarEntry>, maskMacs: Boolean): String {
        val masker = MacMasker()
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        fun m(s: String) = if (maskMacs) masker.mask(s) else s

        val sb = StringBuilder()
        sb.append("{\"log\":{\"version\":\"1.2\",\"creator\":{\"name\":\"Router Guard\",\"version\":\"1.2\"},\"entries\":[")
        entries.forEachIndexed { i, e ->
            if (i > 0) sb.append(',')
            val reqBody = m(redactRequest(e.requestBody, e.requestMime))
            val respBody = m(redactResponse(e.url, e.responseBody))
            sb.append("{\"startedDateTime\":").append(js(fmt.format(Date(e.startedMs))))
            sb.append(",\"time\":").append(e.durationMs)
            sb.append(",\"request\":{\"method\":").append(js(e.method))
            sb.append(",\"url\":").append(js(m(e.url)))
            sb.append(",\"headers\":[")
            e.requestHeaders.forEachIndexed { j, h ->
                if (j > 0) sb.append(',')
                sb.append("{\"name\":").append(js(h.first)).append(",\"value\":").append(js(m(h.second))).append('}')
            }
            sb.append(']')
            if (reqBody.isNotEmpty()) {
                sb.append(",\"postData\":{\"mimeType\":").append(js(e.requestMime)).append(",\"text\":").append(js(reqBody)).append('}')
            }
            sb.append("},\"response\":{\"status\":").append(e.status)
            sb.append(",\"content\":{\"size\":").append(e.responseSize)
            sb.append(",\"mimeType\":").append(js(e.responseMime))
            sb.append(",\"text\":").append(js(respBody)).append("}}}")
        }
        sb.append("]}}")
        return sb.toString()
    }

    private fun js(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append(String.format(Locale.US, "\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
