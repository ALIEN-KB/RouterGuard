package app.routerguard.data

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import javax.net.SocketFactory

/** One request/response pair, like a line in a browser's HAR file. */
data class HarEntry(
    val startedMs: Long,
    val durationMs: Long,
    val method: String,
    val url: String,
    val requestHeaders: List<Pair<String, String>>,
    val requestMime: String,
    val requestBody: String,
    val status: Int,
    val responseMime: String,
    val responseBody: String,
    val responseSize: Int,
)

/**
 * A tiny local HTTP proxy. The in-app browser sends all its traffic through it, so every
 * request and response of the router's web page can be recorded exactly, including form
 * data. Plain-HTTP only: HTTPS pages are tunnelled through untouched (and not recorded).
 * Every request is forwarded over the given socket factory (the phone's Wi-Fi network).
 */
class RecordingProxy(
    private val sockets: SocketFactory,
    private val recording: () -> Boolean,
    private val onEntry: (HarEntry) -> Unit,
) {
    private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "rg-proxy").apply { isDaemon = true } }
    @Volatile private var running = true

    val port: Int get() = server.localPort

    fun start() {
        pool.execute {
            while (running) {
                try {
                    val c = server.accept()
                    pool.execute { handle(c) }
                } catch (_: IOException) {
                    break
                }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { server.close() }
        pool.shutdownNow()
    }

    private class Failure(message: String) : Exception(message)

    private fun handle(client: Socket) {
        client.use {
            client.soTimeout = 30_000
            val cin = BufferedInputStream(client.getInputStream())
            val cout = client.getOutputStream()
            try {
                val head = readHead(cin) ?: return
                val lines = head.split("\r\n")
                val first = lines[0].split(" ", limit = 3)
                if (first.size < 2) return
                val method = first[0]
                val target = first[1]
                val headers = parseHeaders(lines.drop(1))
                if (method.equals("CONNECT", ignoreCase = true)) {
                    tunnel(cin, cout, target)
                    return
                }
                val started = System.currentTimeMillis()
                val url = URL(if (target.startsWith("http", true)) target else "http://" + header(headers, "host") + target)
                val body = readBody(cin, headers, isResponse = false, method = method, status = 0)
                forward(method, url, headers, body, cout, started)
            } catch (e: Exception) {
                runCatching {
                    val msg = "Router Guard proxy error: ${e.message}".toByteArray()
                    cout.write("HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain\r\nContent-Length: ${msg.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    cout.write(msg)
                    cout.flush()
                }
            }
        }
    }

    private fun forward(
        method: String, url: URL, headers: List<Pair<String, String>>, body: ByteArray,
        cout: OutputStream, started: Long,
    ) {
        val port = if (url.port == -1) 80 else url.port
        val path = (url.path.ifEmpty { "/" }) + (url.query?.let { "?$it" } ?: "")
        sockets.createSocket().use { up ->
            up.connect(InetSocketAddress(url.host, port), 10_000)
            up.soTimeout = 30_000
            val out = up.getOutputStream()
            val sb = StringBuilder("$method $path HTTP/1.1\r\n")
            for ((k, v) in headers) {
                when (k.lowercase()) {
                    "proxy-connection", "connection", "keep-alive", "accept-encoding",
                    "content-length", "transfer-encoding", "proxy-authorization" -> {}
                    else -> sb.append(k).append(": ").append(v).append("\r\n")
                }
            }
            sb.append("Accept-Encoding: identity\r\nConnection: close\r\n")
            if (body.isNotEmpty() || method.uppercase() in setOf("POST", "PUT", "PATCH")) {
                sb.append("Content-Length: ").append(body.size).append("\r\n")
            }
            sb.append("\r\n")
            out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            out.write(body)
            out.flush()

            val rin = BufferedInputStream(up.getInputStream())
            val rhead = readHead(rin) ?: throw Failure("The router closed the connection")
            val rlines = rhead.split("\r\n")
            val status = rlines[0].split(" ", limit = 3).getOrNull(1)?.toIntOrNull() ?: throw Failure("Bad response from router")
            val reason = rlines[0].split(" ", limit = 3).getOrNull(2) ?: ""
            val rheaders = parseHeaders(rlines.drop(1))
            val rbody = readBody(rin, rheaders, isResponse = true, method = method, status = status)

            // Hand the page to the browser (always as one complete, non-chunked message).
            val resp = StringBuilder("HTTP/1.1 $status $reason\r\n")
            for ((k, v) in rheaders) {
                when (k.lowercase()) {
                    "transfer-encoding", "connection", "content-length", "keep-alive", "proxy-connection" -> {}
                    else -> resp.append(k).append(": ").append(v).append("\r\n")
                }
            }
            resp.append("Content-Length: ").append(rbody.size).append("\r\nConnection: close\r\n\r\n")
            cout.write(resp.toString().toByteArray(Charsets.ISO_8859_1))
            cout.write(rbody)
            cout.flush()

            if (recording()) record(method, url.toString(), headers, body, status, rheaders, rbody, started)
        }
    }

    private fun record(
        method: String, url: String, headers: List<Pair<String, String>>, body: ByteArray,
        status: Int, rheaders: List<Pair<String, String>>, rbody: ByteArray, started: Long,
    ) {
        val rmime = header(rheaders, "content-type").substringBefore(';').trim().lowercase()
        if (rmime.startsWith("image/") || rmime.startsWith("font/") || rmime == "text/css" ||
            rmime.contains("font") || url.substringBefore('?').substringAfterLast('/').equals("favicon.ico", true)
        ) return
        val qmime = header(headers, "content-type").substringBefore(';').trim().lowercase()
        val reqText = if (isText(qmime) || (qmime.isEmpty() && body.isNotEmpty())) String(body, Charsets.UTF_8).take(20_000) else
            if (body.isEmpty()) "" else "<binary ${body.size} bytes>"
        val decoded = if (header(rheaders, "content-encoding").contains("gzip", true)) {
            runCatching { GZIPInputStream(rbody.inputStream()).readBytes() }.getOrDefault(rbody)
        } else rbody
        val respText = when {
            decoded.isEmpty() -> ""
            isText(rmime) && decoded.size <= 250_000 -> String(decoded, Charsets.UTF_8)
            isText(rmime) -> "<omitted: ${decoded.size} bytes>"
            else -> "<binary ${decoded.size} bytes>"
        }
        val keep = listOf("content-type", "referer", "origin", "x-requested-with", "accept")
        onEntry(
            HarEntry(
                started, System.currentTimeMillis() - started, method, url,
                headers.filter { it.first.lowercase() in keep }, qmime, reqText,
                status, rmime, respText, decoded.size,
            )
        )
    }

    private fun isText(mime: String) =
        mime.contains("text") || mime.contains("json") || mime.contains("xml") ||
            mime.contains("javascript") || mime.contains("html") || mime.contains("x-www-form-urlencoded")

    private fun tunnel(cin: InputStream, cout: OutputStream, target: String) {
        val host = target.substringBeforeLast(':')
        val port = target.substringAfterLast(':', "443").toIntOrNull() ?: 443
        sockets.createSocket().use { up ->
            up.connect(InetSocketAddress(host, port), 10_000)
            cout.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            cout.flush()
            val t = Thread { runCatching { cin.copyTo(up.getOutputStream()) }; runCatching { up.shutdownOutput() } }
            t.isDaemon = true
            t.start()
            runCatching { up.getInputStream().copyTo(cout) }
        }
    }

    // ---- HTTP helpers ----

    private fun header(h: List<Pair<String, String>>, name: String) =
        h.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second ?: ""

    private fun parseHeaders(lines: List<String>): List<Pair<String, String>> =
        lines.filter { it.contains(':') }.map { it.substringBefore(':').trim() to it.substringAfter(':').trim() }

    private fun readHead(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        var matched = 0
        val end = byteArrayOf(13, 10, 13, 10)
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else throw Failure("Connection closed mid-header")
            buf.write(b)
            matched = if (b.toByte() == end[matched]) matched + 1 else if (b.toByte() == end[0]) 1 else 0
            if (matched == 4) break
            if (buf.size() > 65_536) throw Failure("Header too large")
        }
        return buf.toString(Charsets.ISO_8859_1.name()).removeSuffix("\r\n\r\n")
    }

    private fun readLine(input: InputStream): String {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0 || b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun readBody(input: InputStream, headers: List<Pair<String, String>>, isResponse: Boolean, method: String, status: Int): ByteArray {
        val max = 8 * 1024 * 1024
        if (isResponse && (method.equals("HEAD", true) || status in 100..199 || status == 204 || status == 304)) return ByteArray(0)
        if (header(headers, "transfer-encoding").contains("chunked", true)) {
            val out = ByteArrayOutputStream()
            while (true) {
                val size = readLine(input).substringBefore(';').trim().toIntOrNull(16) ?: throw Failure("Bad chunk")
                if (size == 0) {
                    while (readLine(input).isNotEmpty()) { /* skip trailers */ }
                    break
                }
                if (out.size() + size > max) throw Failure("Response too large")
                out.write(readExactly(input, size))
                readLine(input)
            }
            return out.toByteArray()
        }
        val length = header(headers, "content-length").toIntOrNull()
        if (length != null) {
            if (length > max) throw Failure("Response too large")
            return readExactly(input, length)
        }
        if (!isResponse) return ByteArray(0)
        val out = ByteArrayOutputStream()
        val tmp = ByteArray(8192)
        while (true) {
            val n = try { input.read(tmp) } catch (_: java.net.SocketTimeoutException) { -1 }
            if (n < 0) break
            out.write(tmp, 0, n)
            if (out.size() > max) throw Failure("Response too large")
        }
        return out.toByteArray()
    }

    private fun readExactly(input: InputStream, n: Int): ByteArray {
        val data = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(data, off, n - off)
            if (r < 0) throw Failure("Connection closed early")
            off += r
        }
        return data
    }
}
