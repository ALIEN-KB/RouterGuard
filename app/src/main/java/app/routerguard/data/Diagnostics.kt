package app.routerguard.data

import android.content.Context
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.net.SocketFactory
import kotlin.math.abs

data class LatencyResult(
    val label: String,
    val avgMs: Int?,
    val minMs: Int?,
    val maxMs: Int?,
    val jitterMs: Int?,
    val lossPct: Int,
)

data class DnsResult(val label: String, val server: String, val medianMs: Int?, val failed: Int)

data class WifiSnapshot(val band: String, val rssi: Int, val linkMbps: Int, val rxMbps: Int, val txMbps: Int) {
    val quality: String
        get() = when {
            rssi >= -55 -> "Excellent"
            rssi >= -67 -> "Good"
            rssi >= -75 -> "Fair"
            else -> "Weak"
        }
    /** 0..1 for a signal bar. */
    val level: Float get() = ((rssi + 95) / 45f).coerceIn(0f, 1f)
}

/** Measurements done on the phone itself, bound to Wi-Fi so mobile data never skews them. */
object Diagnostics {

    @Suppress("DEPRECATION")
    fun wifi(context: Context): WifiSnapshot? {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        val i = wm.connectionInfo ?: return null
        if (i.rssi <= -127) return null
        val f = i.frequency
        val band = when {
            f >= 5925 -> "6 GHz"
            f >= 4900 -> "5 GHz"
            f >= 2400 -> "2.4 GHz"
            else -> "Wi-Fi"
        }
        val rx = if (Build.VERSION.SDK_INT >= 29) i.rxLinkSpeedMbps else -1
        val tx = if (Build.VERSION.SDK_INT >= 29) i.txLinkSpeedMbps else -1
        return WifiSnapshot(band, i.rssi, i.linkSpeed, rx, tx)
    }

    /** Time to open a TCP connection, repeated, which is a good stand-in for ping without root. */
    suspend fun latency(label: String, host: String, port: Int, network: Network?, count: Int = 10): LatencyResult =
        withContext(Dispatchers.IO) {
            val times = ArrayList<Long>()
            var lost = 0
            val factory = network?.socketFactory ?: SocketFactory.getDefault()
            repeat(count) {
                val t0 = System.nanoTime()
                try {
                    factory.createSocket().use { it.connect(InetSocketAddress(host, port), 1500) }
                    times.add((System.nanoTime() - t0) / 1_000_000)
                } catch (_: IOException) {
                    lost++
                }
                delay(120)
            }
            if (times.isEmpty()) {
                LatencyResult(label, null, null, null, null, 100)
            } else {
                val jitter = if (times.size > 1) times.zipWithNext { a, b -> abs(a - b) }.average().toInt() else 0
                LatencyResult(label, times.average().toInt(), times.min().toInt(), times.max().toInt(), jitter, lost * 100 / count)
            }
        }

    private val dnsDomains = listOf("google.com", "wikipedia.org", "amazon.com", "cloudflare.com", "microsoft.com")

    private fun dnsPacket(name: String, id: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id shr 8); out.write(id and 0xFF)
        out.write(0x01); out.write(0x00) // standard query, recursion desired
        out.write(0); out.write(1)       // one question
        repeat(6) { out.write(0) }       // no answers / authority / additional
        name.split('.').forEach { out.write(it.length); out.write(it.toByteArray()) }
        out.write(0); out.write(0); out.write(1); out.write(0); out.write(1) // type A, class IN
        return out.toByteArray()
    }

    /** Asks one DNS server for a few popular names directly and reports the median answer time. */
    suspend fun dns(label: String, server: String, network: Network?): DnsResult = withContext(Dispatchers.IO) {
        val times = ArrayList<Long>()
        var failed = 0
        for ((n, domain) in dnsDomains.withIndex()) {
            try {
                DatagramSocket().use { socket ->
                    network?.bindSocket(socket)
                    socket.soTimeout = 2000
                    val data = dnsPacket(domain, 0x4200 + n)
                    val t0 = System.nanoTime()
                    socket.send(DatagramPacket(data, data.size, InetAddress.getByName(server), 53))
                    val buf = ByteArray(512)
                    socket.receive(DatagramPacket(buf, buf.size))
                    times.add((System.nanoTime() - t0) / 1_000_000)
                }
            } catch (_: IOException) {
                failed++
            }
        }
        DnsResult(label, server, times.sorted().let { if (it.isEmpty()) null else it[it.size / 2].toInt() }, failed)
    }

    private fun client(network: Network?): OkHttpClient = OkHttpClient.Builder()
        .apply { if (network != null) socketFactory(network.socketFactory) }
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Download speed in Mbit/s, using four parallel streams from Cloudflare for up to [seconds]. */
    suspend fun download(network: Network?, seconds: Int = 8): Double = coroutineScope {
        val http = client(network)
        val total = AtomicLong(0)
        val first = AtomicLong(0)
        val last = AtomicLong(0)
        val deadline = System.nanoTime() + seconds * 1_000_000_000L
        (1..4).map {
            async(Dispatchers.IO) {
                try {
                    http.newCall(Request.Builder().url("https://speed.cloudflare.com/__down?bytes=60000000").build())
                        .execute().use { r ->
                            val src = r.body?.source() ?: return@use
                            val buf = ByteArray(65536)
                            while (System.nanoTime() < deadline) {
                                val n = src.read(buf)
                                if (n < 0) break
                                val now = System.nanoTime()
                                first.compareAndSet(0, now)
                                last.set(now)
                                total.addAndGet(n.toLong())
                            }
                        }
                } catch (_: Exception) {
                }
            }
        }.awaitAll()
        val secs = (last.get() - first.get()) / 1e9
        if (first.get() == 0L || secs <= 0.0) 0.0 else total.get() * 8 / secs / 1e6
    }

    /** Upload speed in Mbit/s, three parallel 4 MB uploads to Cloudflare. */
    suspend fun upload(network: Network?): Double = coroutineScope {
        val http = client(network)
        val size = 4_000_000
        val body = ByteArray(size) { 0x61 }.toRequestBody("application/octet-stream".toMediaType())
        val done = AtomicLong(0)
        val start = System.nanoTime()
        (1..3).map {
            async(Dispatchers.IO) {
                try {
                    http.newCall(Request.Builder().url("https://speed.cloudflare.com/__up").post(body).build())
                        .execute().use { done.addAndGet(size.toLong()) }
                } catch (_: Exception) {
                }
            }
        }.awaitAll()
        val secs = (System.nanoTime() - start) / 1e9
        if (done.get() == 0L || secs <= 0.0) 0.0 else done.get() * 8 / secs / 1e6
    }
}
