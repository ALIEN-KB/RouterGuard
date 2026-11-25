package app.routerguard.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun ago(t: Long): String {
    val s = (System.currentTimeMillis() - t) / 1000
    return when {
        s < 90 -> "just now"
        s < 5400 -> "${s / 60} min ago"
        s < 172800 -> "${s / 3600} h ago"
        else -> "${s / 86400} days ago"
    }
}

fun fullDate(t: Long): String =
    if (t <= 0) "—" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))

fun fmtMinutes(m: Int): String {
    val h = m / 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.US, "%d:%02d %s", h12, m % 60, if (h < 12) "AM" else "PM")
}
