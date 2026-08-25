package io.github.g00dtw0.shortsautoscroll

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads the YouTube window's accessibility tree and answers two questions:
 * "are we looking at a Short right now?" and "how far into it are we?".
 *
 * Everything here is defensive on purpose. YouTube's view ids are private
 * implementation details, so each signal is optional and the caller degrades to a
 * plain timer when nothing can be read.
 */
object ShortsProbe {

    private const val MAX_NODES = 2500

    /** Matches "0:07 of 0:31", "0:07 / 0:31", "0:07 · 0:31". */
    private val TIME_PAIR = Regex("(\\d{1,3}):(\\d{2})\\s*(?:of|/|·|-)\\s*(\\d{1,3}):(\\d{2})")

    data class Result(
        val inShorts: Boolean,
        /** 0f..1f playback position, or null when no progress bar could be read. */
        val progress: Float?,
        val elapsedMs: Long?,
        val durationMs: Long?,
        /** Comments / share sheet / a text field is open, so the user is busy. */
        val panelOpen: Boolean,
        val nodesScanned: Int,
        val progressSource: String?,
        val shortsSource: String?
    )

    fun probe(root: AccessibilityNodeInfo, screen: Rect): Result {
        var shortsSource: String? = null
        var panelOpen = false
        var elapsedMs: Long? = null
        var durationMs: Long? = null

        // Best progress-bar candidate found so far: widest range-info node in the lower
        // half of the screen wins, since that is where the Shorts scrubber lives.
        var bestWidth = -1
        var bestFraction: Float? = null
        var bestSource: String? = null
        var rangeNodeCount = 0
        var lastRangeFraction: Float? = null
        var lastRangeSource: String? = null

        val bounds = Rect()
        val scanned = bfs(root, MAX_NODES) { node ->
            val viewId = node.viewIdResourceName?.lowercase()
            if (shortsSource == null && viewId != null &&
                (viewId.contains("reel") || viewId.contains("shorts"))
            ) {
                shortsSource = "id:$viewId"
            }

            node.getBoundsInScreen(bounds)

            if (!panelOpen) {
                val tall = bounds.height() > screen.height() * 0.3
                if (viewId != null && tall &&
                    (viewId.contains("engagement_panel") || viewId.contains("comment"))
                ) {
                    panelOpen = true
                } else if (node.isEditable && node.isVisibleToUser) {
                    panelOpen = true
                }
            }

            val range = node.rangeInfo
            if (range != null) {
                val span = range.max - range.min
                if (span > 0f) {
                    val fraction = ((range.current - range.min) / span).coerceIn(0f, 1f)
                    rangeNodeCount++
                    lastRangeFraction = fraction
                    lastRangeSource = "range:${viewId ?: node.className}"
                    val wideEnough = bounds.width() >= screen.width() * 0.5
                    val flatEnough = bounds.height() <= screen.height() * 0.12
                    val lowEnough = bounds.centerY() >= screen.height() * 0.5
                    if (wideEnough && flatEnough && lowEnough && bounds.width() > bestWidth) {
                        bestWidth = bounds.width()
                        bestFraction = fraction
                        bestSource = lastRangeSource
                    }
                }
            }

            if (durationMs == null) {
                val text = node.text?.toString()
                val desc = node.contentDescription?.toString()
                val match = (text?.let { TIME_PAIR.find(it) } ?: desc?.let { TIME_PAIR.find(it) })
                if (match != null) {
                    val e = toMs(match.groupValues[1], match.groupValues[2])
                    val d = toMs(match.groupValues[3], match.groupValues[4])
                    if (d > 0 && e <= d) {
                        elapsedMs = e
                        durationMs = d
                    }
                }
            }

            if (shortsSource == null) {
                val desc = node.contentDescription?.toString()
                if (desc != null && desc.startsWith("Shorts", ignoreCase = true) &&
                    bounds.height() > screen.height() * 0.5
                ) {
                    shortsSource = "desc:Shorts(fullscreen)"
                }
            }
            false
        }

        // If the geometry filter rejected everything but the window exposes exactly one
        // range node, that single node is almost certainly the scrubber.
        var fraction = bestFraction
        var source = bestSource
        if (fraction == null && rangeNodeCount == 1) {
            fraction = lastRangeFraction
            source = lastRangeSource?.plus("(sole)")
        }
        if (fraction == null && durationMs != null && elapsedMs != null && durationMs!! > 0) {
            fraction = (elapsedMs!!.toFloat() / durationMs!!.toFloat()).coerceIn(0f, 1f)
            source = "timeText"
        }

        return Result(
            inShorts = shortsSource != null,
            progress = fraction,
            elapsedMs = elapsedMs,
            durationMs = durationMs,
            panelOpen = panelOpen,
            nodesScanned = scanned,
            progressSource = source,
            shortsSource = shortsSource
        )
    }

    /**
     * Finds the vertical pager that holds the Shorts feed. The caller owns the returned
     * node and must recycle it.
     */
    fun findScrollTarget(root: AccessibilityNodeInfo, screen: Rect): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestScore = -1
        val bounds = Rect()
        bfs(root, MAX_NODES) { node ->
            if (!node.isScrollable) return@bfs false
            node.getBoundsInScreen(bounds)
            if (bounds.height() < screen.height() * 0.5 || bounds.width() < screen.width() * 0.5) {
                return@bfs false
            }
            val viewId = node.viewIdResourceName?.lowercase().orEmpty()
            var score = 1
            if (viewId.contains("reel") || viewId.contains("shorts")) score += 10
            if (node.className?.contains("RecyclerView") == true) score += 2
            if (node.className?.contains("ViewPager") == true) score += 2
            if (score <= bestScore) return@bfs false
            recycleQuiet(best)
            best = node
            bestScore = score
            true
        }
        return best
    }

    /** Human readable dump of the current window, used by the Diagnostics screen. */
    fun dumpTree(root: AccessibilityNodeInfo, screen: Rect): String {
        val sb = StringBuilder()
        sb.append("screen=").append(screen.width()).append("x").append(screen.height()).append("\n")
        val bounds = Rect()
        bfs(root, 600) { node ->
            val viewId = node.viewIdResourceName
            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()
            val range = node.rangeInfo
            val interesting = viewId != null || !text.isNullOrBlank() || !desc.isNullOrBlank() ||
                range != null || node.isScrollable
            if (interesting) {
                node.getBoundsInScreen(bounds)
                sb.append(node.className ?: "?")
                if (viewId != null) sb.append(" id=").append(viewId.substringAfterLast('/'))
                if (!text.isNullOrBlank()) sb.append(" text=").append(text.take(40))
                if (!desc.isNullOrBlank()) sb.append(" desc=").append(desc.take(40))
                if (range != null) {
                    sb.append(" range=").append(range.current)
                        .append("/[").append(range.min).append("..").append(range.max).append("]")
                }
                if (node.isScrollable) sb.append(" SCROLLABLE")
                sb.append(" @").append(bounds.toShortString()).append("\n")
            }
            false
        }
        return sb.toString()
    }

    private fun toMs(minutes: String, seconds: String): Long =
        (minutes.toLongOrNull() ?: 0L) * 60_000L + (seconds.toLongOrNull() ?: 0L) * 1000L

    /**
     * Breadth-first walk that recycles as it goes. [visit] returns true to keep a node
     * alive (ownership then transfers to the caller).
     */
    private inline fun bfs(
        root: AccessibilityNodeInfo,
        max: Int,
        visit: (AccessibilityNodeInfo) -> Boolean
    ): Int {
        var count = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        while (queue.isNotEmpty() && count < max) {
            val node = queue.removeFirst()
            count++
            var retain = false
            try {
                retain = visit(node)
                val children = node.childCount
                for (i in 0 until children) {
                    node.getChild(i)?.let { queue.addLast(it) }
                }
            } catch (ignored: Throwable) {
                // A node can go stale mid-walk; skip it rather than losing the whole pass.
            }
            if (node !== root && !retain) recycleQuiet(node)
        }
        while (queue.isNotEmpty()) {
            val leftover = queue.removeFirst()
            if (leftover !== root) recycleQuiet(leftover)
        }
        return count
    }

    @Suppress("DEPRECATION")
    fun recycleQuiet(node: AccessibilityNodeInfo?) {
        try {
            node?.recycle()
        } catch (ignored: Throwable) {
            // recycle() is a no-op on API 33+ and throws if a node was already recycled.
        }
    }
}
