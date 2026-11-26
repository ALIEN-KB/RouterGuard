package app.routerguard.data

import java.net.URL
import java.net.URLConnection
import java.security.MessageDigest
import java.util.Base64

/** Runs a [Recipe] against a real router. */
class RecipeDriver(
    private val recipe: Recipe,
    host: String,
    user: String,
    password: String,
    open: (URL) -> URLConnection,
) : RouterDriver {
    private val base = (if (host.trim().startsWith("https://")) "https://" else "http://") +
        host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
    private val http = HttpSession(base, open)
    private val state = HashMap<String, String>().apply {
        put("host", host.trim()); put("user", user); put("password", password)
    }

    override fun login() = run(recipe.steps("login"), state)

    override fun logout() {
        runCatching { run(recipe.steps("logout"), HashMap(state)) }
    }

    override fun snapshot(): Snapshot {
        val local = HashMap(state)
        val d = recipe.section("devices") ?: throw RouterException("Driver has no device list")
        run(Recipe.stepsOf(d), local)
        val live = parseDevices(MiniJson.asMap(d["parse"]) ?: emptyMap(), local)
        return Snapshot(live, fetchBlocked(local), local["selfIp"])
    }

    override fun block(mac: String, ssid: String) {
        val local = HashMap(state)
        local["mac"] = mac.lowercase()
        local["MAC"] = mac.uppercase()
        local["ssid"] = ssid
        run(recipe.steps("block"), local)
    }

    override fun unblock(mac: String) {
        val spec = recipe.raw["unblock"]
        val each = (MiniJson.asMap(spec)?.get("each") as? String) == "blocked"
        val steps = Recipe.stepsOf(spec)
        if (!each) {
            val local = HashMap(state)
            local["mac"] = mac.lowercase()
            run(steps, local)
            return
        }
        for (entry in fetchBlocked(HashMap(state)).filter { it.mac == mac.lowercase() }) {
            val local = HashMap(state)
            local["mac"] = entry.mac
            local["id"] = entry.domain
            local["ssid"] = entry.ssid
            run(steps, local)
        }
    }

    // ---- steps ----

    private fun run(steps: List<Map<String, Any?>>, vars: MutableMap<String, String>) {
        for (s in steps) {
            MiniJson.asMap(s["set"])?.forEach { (k, v) -> vars[k] = eval(v.toString(), vars) }
            val method = when {
                s.containsKey("post") -> "POST"
                s.containsKey("get") -> "GET"
                else -> continue
            }
            val path = tpl(s[method.lowercase()].toString(), vars)
            val form = MiniJson.asMap(s["form"])?.mapValues { tpl(it.value.toString(), vars) }
            val body = (s["body"] as? String)?.let { tpl(it, vars) }
            val headers = MiniJson.asMap(s["headers"])?.mapValues { tpl(it.value.toString(), vars) } ?: emptyMap()
            val cookie = (s["cookie"] as? String)?.let { tpl(it, vars) }
            var resp = http.request(
                method, path, form = form, body = body, contentType = s["contentType"] as? String,
                headers = headers, cookie = cookie, ajax = s["ajax"] == true,
            )
            if (s["unescapeHex"] == true) resp = Parsing.unescape(resp)
            vars["body"] = resp
            (s["save"] as? String)?.let { vars[it] = resp }
            MiniJson.asMap(s["extract"])?.forEach { (k, re) ->
                Regex(re as String).find(resp)?.let { m -> vars[k] = m.groupValues.getOrElse(1) { m.value } }
            }
            (s["expect"] as? String)?.let {
                if (!Regex(it).containsMatchIn(resp)) {
                    throw RouterException((s["error"] as? String) ?: "The router gave an unexpected reply.")
                }
            }
        }
    }

    private val varRe = Regex("\\{([A-Za-z_][A-Za-z0-9_]*)\\}")

    private fun tpl(text: String, vars: Map<String, String>): String =
        varRe.replace(text) { m -> vars[m.groupValues[1]] ?: m.value }

    private val fnRe = Regex("^(md5|sha256|b64|lower|upper)\\((.*)\\)$", RegexOption.DOT_MATCHES_ALL)

    private fun eval(expr: String, vars: Map<String, String>): String {
        val m = fnRe.find(expr) ?: return tpl(expr, vars)
        val arg = tpl(m.groupValues[2], vars)
        return when (m.groupValues[1]) {
            "md5" -> hex(MessageDigest.getInstance("MD5").digest(arg.toByteArray()))
            "sha256" -> hex(MessageDigest.getInstance("SHA-256").digest(arg.toByteArray()))
            "b64" -> Base64.getEncoder().encodeToString(arg.toByteArray())
            "lower" -> arg.lowercase()
            else -> arg.uppercase()
        }
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    // ---- parsing ----

    private fun fetchBlocked(vars: MutableMap<String, String>): List<FilterEntry> {
        val sec = recipe.section("blocked") ?: return emptyList()
        run(Recipe.stepsOf(sec), vars)
        val spec = MiniJson.asMap(sec["parse"]) ?: return emptyList()
        val text = vars[(spec["from"] as? String) ?: "body"] ?: ""
        return rows(spec, text).mapNotNull { f ->
            val mac = f["mac"]?.lowercase() ?: return@mapNotNull null
            FilterEntry(f["id"] ?: "", f["ssid"] ?: "SSID-1", mac)
        }
    }

    private fun parseDevices(spec: Map<String, Any?>, vars: Map<String, String>): List<LiveDevice> {
        val text = vars[(spec["from"] as? String) ?: "body"] ?: ""
        val onlineRule = MiniJson.asMap(spec["online"])
        val wifiRule = MiniJson.asMap(spec["wifi"])
        val ssidRe = (spec["ssidPattern"] as? String)?.let { Regex(it) }
        val defaultSsid = (spec["defaultSsid"] as? String) ?: "SSID-1"
        return rows(spec, text).mapNotNull { f ->
            val mac = f["mac"]?.lowercase()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val port = f["port"] ?: ""
            val n = ssidRe?.find(port)?.groupValues?.getOrNull(1)
            LiveDevice(
                mac = mac,
                ip = f["ip"] ?: "",
                name = (f["name"] ?: "").takeIf { it != "--" } ?: "",
                ssid = if (n != null && n != "0") "SSID-$n" else (f["ssid"] ?: defaultSsid),
                online = onlineRule?.let { f[it["field"] as String] == it["equals"] } ?: true,
                wifi = wifiRule?.let { f[it["field"] as String] == it["equals"] } ?: true,
            )
        }
    }

    /** Turns a page into rows of named fields, using the recipe's regex or JSON-path description. */
    private fun rows(spec: Map<String, Any?>, text: String): List<Map<String, String>> {
        val fields = MiniJson.asMap(spec["fields"]) ?: return emptyList()
        val lower = MiniJson.asList(spec["lower"])?.map { it.toString() }?.toSet() ?: emptySet()
        val hexUnescape = spec["unescape"] == "hex"
        fun clean(name: String, v: String): String {
            val x = if (hexUnescape) Parsing.unescape(v) else v
            return if (name in lower) x.lowercase() else x
        }
        val out = ArrayList<Map<String, String>>()
        val jsonPath = spec["json"] as? String
        if (jsonPath != null) {
            val root = runCatching { MiniJson.parse(text) }.getOrNull() ?: return emptyList()
            for (item in walk(root, jsonPath)) {
                out.add(fields.mapValues { (k, p) -> clean(k, lookup(item, p.toString())) })
            }
            return out
        }
        val re = Regex(spec["regex"] as? String ?: return emptyList())
        val group = (spec["group"] as? Double)?.toInt() ?: 0
        val quoted = spec["split"] == "quoted"
        val strRe = Regex("\"([^\"]*)\"")
        for (m in re.findAll(text)) {
            val parts: List<String> = if (quoted) strRe.findAll(m.groupValues[group]).map { it.groupValues[1] }.toList()
            else m.groupValues
            val row = HashMap<String, String>()
            var ok = true
            for ((k, idx) in fields) {
                val i = (idx as Double).toInt()
                if (i >= parts.size) { ok = false; break }
                row[k] = clean(k, parts[i])
            }
            if (ok) out.add(row)
        }
        return out
    }

    private fun walk(root: Any?, path: String): List<Any?> {
        var current = listOf(root)
        for (seg in path.split('.').filter { it.isNotEmpty() }) {
            val name = seg.substringBefore('[')
            val idx = if (seg.contains('[')) seg.substringAfter('[').substringBefore(']') else null
            current = current.flatMap { node ->
                val child = if (name.isEmpty()) node else MiniJson.asMap(node)?.get(name)
                when {
                    idx == null -> listOf(child)
                    idx == "*" -> MiniJson.asList(child) ?: emptyList()
                    else -> listOfNotNull(MiniJson.asList(child)?.getOrNull(idx.toInt()))
                }
            }
        }
        return current
    }

    private fun lookup(item: Any?, path: String): String {
        var cur: Any? = item
        for (seg in path.split('.')) cur = MiniJson.asMap(cur)?.get(seg)
        return when (cur) {
            null -> ""
            is Double -> if (cur % 1.0 == 0.0) cur.toLong().toString() else cur.toString()
            else -> cur.toString()
        }
    }
}
