package io.github.g00dtw0.shortsautoscroll

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Small draggable bubble drawn over YouTube so auto scroll can be paused without leaving
 * the app. Entirely optional: it only appears once the user grants "display over other
 * apps" and turns the option on.
 */
class OverlayController(private val service: AutoScrollService) {

    private val windowManager =
        service.getSystemService(WindowManager::class.java)
    private val prefs = Prefs(service)
    private var bubble: ImageView? = null

    fun sync(show: Boolean, enabled: Boolean) {
        if (show && Settings.canDrawOverlays(service)) {
            add()
            bubble?.alpha = if (enabled) 1f else 0.45f
        } else {
            remove()
        }
    }

    fun remove() {
        val view = bubble ?: return
        bubble = null
        try {
            windowManager.removeView(view)
        } catch (ignored: Throwable) {
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun add() {
        if (bubble != null) return
        val density = service.resources.displayMetrics.density
        val sizePx = (44 * density).roundToInt()
        val padPx = (10 * density).roundToInt()

        val view = ImageView(service).apply {
            setImageResource(R.drawable.ic_tile)
            setBackgroundResource(R.drawable.bubble_background)
            setPadding(padPx, padPx, padPx, padPx)
            contentDescription = service.getString(R.string.tile_label)
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.overlayX
            y = prefs.overlayY
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val touchSlop = 8 * density

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (dragging || abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        dragging = true
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (ignored: Throwable) {
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        prefs.overlayX = params.x
                        prefs.overlayY = params.y
                    } else {
                        val nowEnabled = service.toggleEnabled()
                        view.alpha = if (nowEnabled) 1f else 0.45f
                        view.performClick()
                    }
                    true
                }

                else -> false
            }
        }

        try {
            windowManager.addView(view, params)
            bubble = view
        } catch (t: Throwable) {
            Diagnostics.log("overlay add failed: ${t.message}")
        }
    }
}

/** Visibility helpers shared by the UI and the Quick Settings tile. */
object ServiceStatus {

    fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
        val expected = "${context.packageName}/${AutoScrollService::class.java.name}"
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return flat.split(':').any { entry ->
            entry.equals(expected, ignoreCase = true) ||
                entry.equals(
                    "${context.packageName}/.${AutoScrollService::class.java.simpleName}",
                    ignoreCase = true
                )
        }
    }
}
