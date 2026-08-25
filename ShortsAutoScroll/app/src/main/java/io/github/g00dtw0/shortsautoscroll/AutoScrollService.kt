package io.github.g00dtw0.shortsautoscroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches the YouTube app while Shorts are playing and swipes to the next Short when the
 * current one reaches its end.
 *
 * The end of a Short is detected from whichever of these signals the YouTube build exposes,
 * in order of preference:
 *  1. the scrubber's progress reaching [Prefs.endThresholdPercent];
 *  2. the progress jumping backwards, which is YouTube looping the Short — an exact
 *     "it just ended" signal;
 *  3. an "0:07 of 0:31" style content description reaching its duration;
 *  4. a plain timer, used when nothing else can be read.
 */
class AutoScrollService : AccessibilityService() {

    private lateinit var prefs: Prefs

    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlay: OverlayController? = null

    // ----- per-Short watch state (worker thread only) -----
    private var watchedMs = 0L
    private var lastTickMs = 0L
    private var lastScrollMs = 0L
    private var lastProgress = -1f
    private var lastProgressChangeMs = 0L
    private var sawProgress = false
    private var endDetectedAtMs = 0L
    private var currentPollMs = POLL_IDLE_MS
    private var lastOverlaySyncMs = 0L
    private var lastOverlayShown = false

    @Volatile
    private var resetRequested = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.KEY_ENABLED || key == Prefs.KEY_OVERLAY) {
            // Only ever show the bubble while YouTube is actually in front.
            val show = lastOverlayShown && prefs.overlayEnabled
            val enabled = prefs.enabled
            mainHandler.post { overlay?.sync(show, enabled) }
        }
        if (key == Prefs.KEY_ENABLED) {
            resetRequested = true
            Diagnostics.log("enabled -> ${prefs.enabled}")
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                doTick()
            } catch (t: Throwable) {
                Diagnostics.log("tick failed: ${t.javaClass.simpleName}: ${t.message}")
            }
            workerHandler?.postDelayed(this, currentPollMs)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)

        val thread = HandlerThread("shorts-autoscroll").also { it.start() }
        worker = thread
        workerHandler = Handler(thread.looper).also { it.post(tick) }

        // Starts hidden; the first tick that finds YouTube in the foreground shows it.
        overlay = OverlayController(this)

        Diagnostics.serviceConnected = true
        Diagnostics.log("service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName != Prefs.YOUTUBE_PACKAGE) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // A page change - ours or the user's - starts a new Short.
                if (SystemClock.elapsedRealtime() - lastScrollMs > SETTLE_MS) {
                    resetRequested = true
                }
            }
        }
    }

    override fun onInterrupt() {
        Diagnostics.log("interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        Diagnostics.serviceConnected = false
        workerHandler?.removeCallbacksAndMessages(null)
        worker?.quitSafely()
        worker = null
        workerHandler = null
        if (::prefs.isInitialized) {
            prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        }
        mainHandler.post { overlay?.remove() }
        Diagnostics.log("service disconnected")
    }

    // ------------------------------------------------------------------ tick

    private fun doTick() {
        val now = SystemClock.elapsedRealtime()
        val delta = if (lastTickMs == 0L) 0L else (now - lastTickMs).coerceAtMost(2_000L)
        lastTickMs = now

        if (resetRequested) {
            resetWatch("event")
            resetRequested = false
        }

        // The service config restricts events to YouTube, so a null root means the user is
        // somewhere else entirely and there is nothing to do.
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: run {
            currentPollMs = POLL_IDLE_MS
            syncOverlay(false)
            resetWatch("no window")
            return
        }

        try {
            // Kept outside the enabled check so the bubble stays tappable while paused.
            syncOverlay(true)

            if (!prefs.enabled) {
                currentPollMs = POLL_IDLE_MS
                resetWatch("disabled")
                return
            }

            val screen = windowBounds(root)
            val result = ShortsProbe.probe(root, screen)

            if (Diagnostics.dumpRequested) {
                Diagnostics.lastDump = ShortsProbe.dumpTree(root, screen)
                Diagnostics.dumpRequested = false
            }

            if (!result.inShorts) {
                currentPollMs = POLL_IDLE_MS
                resetWatch("not in Shorts")
                return
            }

            if (now - lastScrollMs < SETTLE_MS) {
                currentPollMs = POLL_ACTIVE_MS
                return
            }

            if (result.panelOpen && prefs.respectPanels) {
                currentPollMs = POLL_ACTIVE_MS
                Diagnostics.logThrottled("panel", 3_000L) { "holding: comments/keyboard open" }
                return
            }

            val progress = result.progress
            var paused = false
            if (progress != null) {
                sawProgress = true
                if (lastProgress >= 0f && kotlin.math.abs(progress - lastProgress) < PROGRESS_EPSILON) {
                    paused = prefs.pauseAware &&
                        now - lastProgressChangeMs > PAUSE_GRACE_MS &&
                        progress > PROGRESS_EPSILON
                } else {
                    lastProgressChangeMs = now
                }
            }

            if (!paused) watchedMs += delta

            val threshold = prefs.endThresholdPercent / 100f
            var reason: String? = null

            if (progress != null) {
                if (lastProgress >= LOOP_HIGH && progress <= LOOP_LOW) {
                    reason = "looped back to start"
                } else if (progress >= threshold) {
                    reason = "progress ${(progress * 100).toInt()}%"
                }
            }
            val duration = result.durationMs
            val elapsed = result.elapsedMs
            if (reason == null && duration != null && elapsed != null &&
                duration > 0 && elapsed >= duration - TIME_TEXT_SLACK_MS
            ) {
                reason = "time text ${elapsed}/${duration}ms"
            }
            if (reason == null) {
                val timerApplies = prefs.mode == Prefs.MODE_TIMER ||
                    (!sawProgress && watchedMs >= PROBE_GRACE_MS)
                if (timerApplies && watchedMs >= prefs.timerSeconds * 1000L) {
                    reason = if (sawProgress) "timer" else "timer (no progress bar found)"
                }
            }
            if (reason == null && watchedMs >= prefs.maxWatchSeconds * 1000L && !paused) {
                reason = "safety cap"
            }

            if (progress != null) lastProgress = progress

            Diagnostics.logThrottled("state", 2_000L) {
                "shorts=${result.shortsSource} p=${progress?.let { "%.3f".format(it) } ?: "-"}" +
                    " src=${result.progressSource ?: "-"} watched=${watchedMs}ms" +
                    " paused=$paused nodes=${result.nodesScanned}"
            }

            // Poll faster as the Short approaches its end so the swipe lands on time.
            val nearEnd = (progress != null && progress >= threshold - 0.15f) ||
                (watchedMs + 2_000L >= prefs.timerSeconds * 1000L && !sawProgress)
            currentPollMs = if (nearEnd) POLL_ACTIVE_MS else POLL_IDLE_MS

            if (reason != null) {
                if (endDetectedAtMs == 0L) {
                    endDetectedAtMs = now
                    Diagnostics.log("end detected: $reason")
                }
                if (now - endDetectedAtMs >= prefs.extraDelayMs) {
                    performScroll(root, screen, reason)
                }
            } else {
                endDetectedAtMs = 0L
            }
        } finally {
            ShortsProbe.recycleQuiet(root)
        }
    }

    /** Shows the floating bubble only while YouTube is in the foreground. */
    private fun syncOverlay(inForeground: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (inForeground == lastOverlayShown && now - lastOverlaySyncMs < OVERLAY_SYNC_MS) return
        lastOverlayShown = inForeground
        lastOverlaySyncMs = now
        val show = inForeground && prefs.overlayEnabled
        val enabled = prefs.enabled
        mainHandler.post { overlay?.sync(show, enabled) }
    }

    private fun resetWatch(why: String) {
        if (watchedMs != 0L || lastProgress >= 0f) {
            Diagnostics.logThrottled("reset:$why", 1_500L) { "reset watch ($why)" }
        }
        watchedMs = 0L
        lastProgress = -1f
        lastProgressChangeMs = SystemClock.elapsedRealtime()
        sawProgress = false
        endDetectedAtMs = 0L
    }

    // ---------------------------------------------------------------- scroll

    private fun performScroll(root: AccessibilityNodeInfo, screen: Rect, reason: String) {
        lastScrollMs = SystemClock.elapsedRealtime()
        var done = false

        if (prefs.scrollMethod == Prefs.METHOD_ACTION) {
            val target = ShortsProbe.findScrollTarget(root, screen)
            if (target != null) {
                done = try {
                    target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                } catch (t: Throwable) {
                    false
                }
                ShortsProbe.recycleQuiet(target)
            }
            if (!done) Diagnostics.log("scroll action unavailable, falling back to gesture")
        }

        if (!done) {
            done = dispatchSwipe(screen)
        }

        Diagnostics.log("scrolled ($reason) ok=$done")
        resetWatch("scrolled")
        if (prefs.haptic) vibrate()
    }

    private fun dispatchSwipe(screen: Rect): Boolean {
        val x = screen.centerX().toFloat()
        val startY = screen.top + screen.height() * 0.75f
        val endY = screen.top + screen.height() * 0.25f
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, SWIPE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return try {
            dispatchGesture(gesture, null, null)
        } catch (t: Throwable) {
            Diagnostics.log("dispatchGesture failed: ${t.message}")
            false
        }
    }

    private fun windowBounds(root: AccessibilityNodeInfo): Rect {
        val rect = Rect()
        root.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) return rect
        val metrics = resources.displayMetrics
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            } ?: return
            vibrator.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (ignored: Throwable) {
        }
    }

    /** Called by the floating bubble. */
    fun toggleEnabled(): Boolean {
        val next = !prefs.enabled
        prefs.enabled = next
        return next
    }

    companion object {
        private const val POLL_IDLE_MS = 300L
        private const val POLL_ACTIVE_MS = 100L

        /** Ignore everything for a moment after a swipe so the next Short can settle. */
        private const val SETTLE_MS = 900L

        /** How long to look for a progress bar before giving up and using the timer. */
        private const val PROBE_GRACE_MS = 3_000L

        private const val PAUSE_GRACE_MS = 1_200L
        private const val PROGRESS_EPSILON = 0.002f
        private const val LOOP_HIGH = 0.55f
        private const val LOOP_LOW = 0.30f
        private const val TIME_TEXT_SLACK_MS = 300L
        private const val SWIPE_DURATION_MS = 180L
        private const val OVERLAY_SYNC_MS = 2_000L
    }
}
