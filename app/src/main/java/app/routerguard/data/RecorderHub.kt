package app.routerguard.data

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/** Holds the current recording so it survives switching tabs. */
object RecorderHub {
    private val list = CopyOnWriteArrayList<HarEntry>()
    val recording = MutableStateFlow(false)
    val count = MutableStateFlow(0)

    val entries: List<HarEntry> get() = list.toList()

    fun add(e: HarEntry) {
        if (list.size < 600) {
            list.add(e)
            count.value = list.size
        }
    }

    fun remove(e: HarEntry) {
        list.remove(e)
        count.value = list.size
    }

    fun clear() {
        list.clear()
        count.value = 0
    }
}
