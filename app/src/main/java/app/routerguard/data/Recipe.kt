package app.routerguard.data

/**
 * A router driver written as data. The app only ever *interprets* it (it never runs code from it),
 * and it can only talk to the router itself, so installing a recipe is safe.
 */
class Recipe(val raw: Map<String, Any?>) {
    val id: String get() = raw["id"] as String
    val name: String get() = (raw["name"] as? String) ?: id
    val version: Int get() = (raw["version"] as? Double)?.toInt() ?: 1
    val status: String get() = (raw["status"] as? String) ?: "experimental"
    val defaults: Map<String, Any?> get() = MiniJson.asMap(raw["defaults"]) ?: emptyMap()

    fun steps(key: String): List<Map<String, Any?>> = stepsOf(raw[key])
    fun section(key: String): Map<String, Any?>? = MiniJson.asMap(raw[key])

    companion object {
        private val idRe = Regex("[a-z0-9][a-z0-9\\-]{1,40}")

        fun stepsOf(v: Any?): List<Map<String, Any?>> = when (v) {
            is List<*> -> v.mapNotNull { MiniJson.asMap(it) }
            is Map<*, *> -> stepsOf(v["steps"])
            else -> emptyList()
        }

        /** Parses and checks a recipe. Throws [IllegalArgumentException] with a readable reason. */
        fun parse(text: String): Recipe {
            val raw = MiniJson.asMap(MiniJson.parse(text)) ?: throw IllegalArgumentException("Recipe must be a JSON object")
            require((raw["schema"] as? Double)?.toInt() == 1) { "Unsupported recipe schema" }
            val id = raw["id"] as? String ?: throw IllegalArgumentException("Recipe has no id")
            require(idRe.matches(id)) { "Bad recipe id" }
            require(raw["version"] is Double) { "Recipe has no version" }
            for (key in listOf("login", "devices", "block", "unblock")) {
                require(raw[key] != null) { "Recipe is missing \"$key\"" }
            }
            var total = 0
            for (key in listOf("login", "logout", "devices", "blocked", "block", "unblock")) {
                val steps = stepsOf(raw[key])
                require(steps.size <= 40) { "Too many steps in $key" }
                for (s in steps) {
                    total++
                    for (m in listOf("get", "post")) {
                        val p = s[m] as? String ?: continue
                        require(p.startsWith("/") && !p.startsWith("//")) { "Step paths must be relative to the router (\"$p\")" }
                    }
                    for (k in listOf("extract")) MiniJson.asMap(s[k])?.values?.forEach { Regex(it as String) }
                    (s["expect"] as? String)?.let { Regex(it) }
                }
            }
            require(total <= 120) { "Recipe is too large" }
            for (key in listOf("devices", "blocked")) {
                MiniJson.asMap(MiniJson.asMap(raw[key])?.get("parse"))?.get("regex")?.let { Regex(it as String) }
            }
            return Recipe(raw)
        }
    }
}
