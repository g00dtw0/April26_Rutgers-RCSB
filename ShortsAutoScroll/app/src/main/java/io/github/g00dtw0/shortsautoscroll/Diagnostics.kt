package io.github.g00dtw0.shortsautoscroll

import android.os.SystemClock
import java.util.Locale

/**
 * In-memory ring buffer that records what the service is seeing. It exists because the
 * YouTube app's view hierarchy is not a public contract: when a YouTube update moves things
 * around, this is what tells you which signal stopped working.
 */
object Diagnostics {

    private const val MAX_LINES = 300

    private val lines = ArrayDeque<String>()
    private val throttleStamps = HashMap<String, Long>()

    @Volatile
    var serviceConnected: Boolean = false

    /** Set by the Diagnostics screen; the service picks it up on its next tick. */
    @Volatile
    var dumpRequested: Boolean = false

    @Volatile
    var lastDump: String = ""

    fun log(message: String) {
        val line = String.format(Locale.US, "%8.2f  %s", SystemClock.elapsedRealtime() / 1000.0, message)
        synchronized(lines) {
            while (lines.size >= MAX_LINES) lines.removeFirst()
            lines.addLast(line)
        }
    }

    /** Logs at most once per [minIntervalMs] for a given [key]; for per-tick status lines. */
    fun logThrottled(key: String, minIntervalMs: Long, message: () -> String) {
        val now = SystemClock.elapsedRealtime()
        synchronized(throttleStamps) {
            val last = throttleStamps[key] ?: 0L
            if (now - last < minIntervalMs) return
            throttleStamps[key] = now
        }
        log(message())
    }

    fun snapshot(): String = synchronized(lines) { lines.joinToString("\n") }

    fun clear() {
        synchronized(lines) { lines.clear() }
        synchronized(throttleStamps) { throttleStamps.clear() }
    }
}
