package io.legado.app.ui.book.read.page

import android.content.Context
import android.os.Build
import io.legado.app.BuildConfig
import io.legado.app.utils.externalCache
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

internal object ReadAloudVisualTrace {

    private const val MAX_AGE_MS = 5 * 60 * 1000L
    private const val MAX_EVENTS = 800
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fileFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    private val events = ArrayDeque<Entry>()

    private data class Entry(
        val time: Long,
        val event: String,
        val detail: String
    )

    fun record(event: String, detail: String) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            events.addLast(Entry(now, event, detail))
            pruneLocked(now)
        }
    }

    fun export(context: Context): File {
        val now = System.currentTimeMillis()
        val snapshot = synchronized(lock) {
            pruneLocked(now)
            events.toList()
        }
        val dir = File(context.externalCache, "read-aloud-debug")
        dir.mkdirs()
        val file = File(dir, "read-aloud-visual-${fileFormat.format(Date(now))}.txt")
        file.writeText(buildDump(now, snapshot))
        return file
    }

    private fun pruneLocked(now: Long) {
        while (events.isNotEmpty() && now - events.peekFirst().time > MAX_AGE_MS) {
            events.removeFirst()
        }
        while (events.size > MAX_EVENTS) {
            events.removeFirst()
        }
    }

    private fun buildDump(now: Long, snapshot: List<Entry>): String {
        return buildString {
            appendLine("Read aloud visual trace")
            appendLine("created=${Date(now)}")
            appendLine("version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
            appendLine("device=${Build.MANUFACTURER}/${Build.BRAND}/${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")
            appendLine("windowMs=$MAX_AGE_MS maxEvents=$MAX_EVENTS count=${snapshot.size}")
            appendLine()
            snapshot.forEach { entry ->
                append(timeFormat.format(Date(entry.time)))
                append(' ')
                append(entry.event)
                append(' ')
                appendLine(entry.detail)
            }
        }
    }
}
