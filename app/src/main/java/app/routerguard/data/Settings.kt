package app.routerguard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Block this device every day from [startMin] until [endMin] (minutes after midnight). */
data class Schedule(
    val mac: String,
    val name: String,
    val startMin: Int,
    val endMin: Int,
    val enabled: Boolean = true,
) {
    fun isActive(nowMin: Int): Boolean = when {
        startMin == endMin -> false
        startMin < endMin -> nowMin in startMin until endMin
        else -> nowMin >= startMin || nowMin < endMin // crosses midnight
    }
}

class AppSettings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 0 = follow system, 1 = dark, 2 = light. Dark is the default. */
    var theme: Int
        get() = p.getInt("theme", 1)
        set(v) = p.edit().putInt("theme", v).apply()

    var alerts: Boolean
        get() = p.getBoolean("alerts", false)
        set(v) = p.edit().putBoolean("alerts", v).apply()

    var intervalMin: Int
        get() = p.getInt("interval", 30)
        set(v) = p.edit().putInt("interval", v).apply()

    var schedules: List<Schedule>
        get() = runCatching {
            val arr = JSONArray(p.getString("schedules", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Schedule(o.getString("mac"), o.optString("name"), o.getInt("start"), o.getInt("end"), o.optBoolean("enabled", true))
            }
        }.getOrDefault(emptyList())
        set(v) {
            val arr = JSONArray()
            v.forEach {
                arr.put(JSONObject().put("mac", it.mac).put("name", it.name).put("start", it.startMin)
                    .put("end", it.endMin).put("enabled", it.enabled))
            }
            p.edit().putString("schedules", arr.toString()).apply()
        }

    /** Devices that a schedule (not the user) blocked, so only those get unblocked again. */
    var scheduleOwned: Set<String>
        get() = p.getStringSet("owned", emptySet())?.toSet() ?: emptySet()
        set(v) = p.edit().putStringSet("owned", v).apply()
}
