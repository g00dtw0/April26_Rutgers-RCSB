package io.github.g00dtw0.shortsautoscroll

import android.content.Context
import android.content.SharedPreferences

/** Thin typed wrapper around the single SharedPreferences file used by the app. */
class Prefs(context: Context) {

    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_ENABLED, value).apply()

    /** [MODE_SMART] or [MODE_TIMER]. */
    var mode: String
        get() = sp.getString(KEY_MODE, MODE_SMART) ?: MODE_SMART
        set(value) = sp.edit().putString(KEY_MODE, value).apply()

    /** [METHOD_GESTURE] or [METHOD_ACTION]. */
    var scrollMethod: String
        get() = sp.getString(KEY_METHOD, METHOD_GESTURE) ?: METHOD_GESTURE
        set(value) = sp.edit().putString(KEY_METHOD, value).apply()

    /** Fixed-timer length, and the fallback used when no progress bar can be read. */
    var timerSeconds: Int
        get() = sp.getInt(KEY_TIMER, 12).coerceIn(TIMER_MIN, TIMER_MAX)
        set(value) = sp.edit().putInt(KEY_TIMER, value).apply()

    /** Percentage of the Short that must have played before it counts as finished. */
    var endThresholdPercent: Int
        get() = sp.getInt(KEY_THRESHOLD, 98).coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)
        set(value) = sp.edit().putInt(KEY_THRESHOLD, value).apply()

    /** Grace period between "this Short is over" and the swipe itself. */
    var extraDelayMs: Int
        get() = sp.getInt(KEY_DELAY, 150).coerceIn(DELAY_MIN, DELAY_MAX)
        set(value) = sp.edit().putInt(KEY_DELAY, value).apply()

    /** Hard upper bound on how long a single Short may be watched. */
    var maxWatchSeconds: Int
        get() = sp.getInt(KEY_MAX_WATCH, 180).coerceIn(MAX_WATCH_MIN, MAX_WATCH_MAX)
        set(value) = sp.edit().putInt(KEY_MAX_WATCH, value).apply()

    var pauseAware: Boolean
        get() = sp.getBoolean(KEY_PAUSE_AWARE, true)
        set(value) = sp.edit().putBoolean(KEY_PAUSE_AWARE, value).apply()

    var respectPanels: Boolean
        get() = sp.getBoolean(KEY_RESPECT_PANELS, true)
        set(value) = sp.edit().putBoolean(KEY_RESPECT_PANELS, value).apply()

    var overlayEnabled: Boolean
        get() = sp.getBoolean(KEY_OVERLAY, false)
        set(value) = sp.edit().putBoolean(KEY_OVERLAY, value).apply()

    var haptic: Boolean
        get() = sp.getBoolean(KEY_HAPTIC, false)
        set(value) = sp.edit().putBoolean(KEY_HAPTIC, value).apply()

    var overlayX: Int
        get() = sp.getInt(KEY_OVERLAY_X, 24)
        set(value) = sp.edit().putInt(KEY_OVERLAY_X, value).apply()

    var overlayY: Int
        get() = sp.getInt(KEY_OVERLAY_Y, 320)
        set(value) = sp.edit().putInt(KEY_OVERLAY_Y, value).apply()

    companion object {
        private const val FILE = "shorts_auto_scroll"

        const val KEY_ENABLED = "enabled"
        const val KEY_MODE = "mode"
        const val KEY_METHOD = "method"
        const val KEY_TIMER = "timer_seconds"
        const val KEY_THRESHOLD = "end_threshold"
        const val KEY_DELAY = "extra_delay_ms"
        const val KEY_MAX_WATCH = "max_watch_seconds"
        const val KEY_PAUSE_AWARE = "pause_aware"
        const val KEY_RESPECT_PANELS = "respect_panels"
        const val KEY_OVERLAY = "overlay"
        const val KEY_HAPTIC = "haptic"
        const val KEY_OVERLAY_X = "overlay_x"
        const val KEY_OVERLAY_Y = "overlay_y"

        const val MODE_SMART = "smart"
        const val MODE_TIMER = "timer"
        const val METHOD_GESTURE = "gesture"
        const val METHOD_ACTION = "action"

        const val TIMER_MIN = 3
        const val TIMER_MAX = 60
        const val THRESHOLD_MIN = 80
        const val THRESHOLD_MAX = 100
        const val DELAY_MIN = 0
        const val DELAY_MAX = 2000
        const val MAX_WATCH_MIN = 20
        const val MAX_WATCH_MAX = 300

        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }
}
