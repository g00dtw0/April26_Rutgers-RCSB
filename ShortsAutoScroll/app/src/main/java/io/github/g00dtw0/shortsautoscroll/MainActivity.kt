package io.github.g00dtw0.shortsautoscroll

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.g00dtw0.shortsautoscroll.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.openAccessibility.setOnClickListener {
            startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.masterSwitch.setOnCheckedChangeListener { _, checked -> prefs.enabled = checked }

        binding.modeGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.mode = if (checkedId == R.id.modeTimer) Prefs.MODE_TIMER else Prefs.MODE_SMART
        }

        binding.methodGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.scrollMethod =
                if (checkedId == R.id.methodAction) Prefs.METHOD_ACTION else Prefs.METHOD_GESTURE
        }

        binding.timerSlider.addOnChangeListener { _, value, _ ->
            prefs.timerSeconds = value.toInt()
            binding.timerLabel.text = getString(R.string.timer_seconds, value.toInt())
        }
        binding.thresholdSlider.addOnChangeListener { _, value, _ ->
            prefs.endThresholdPercent = value.toInt()
            binding.thresholdLabel.text = getString(R.string.end_threshold, value.toInt())
        }
        binding.delaySlider.addOnChangeListener { _, value, _ ->
            prefs.extraDelayMs = value.toInt()
            binding.delayLabel.text = getString(R.string.extra_delay, value.toInt())
        }
        binding.maxWatchSlider.addOnChangeListener { _, value, _ ->
            prefs.maxWatchSeconds = value.toInt()
            binding.maxWatchLabel.text = getString(R.string.max_watch, value.toInt())
        }

        binding.pauseAwareSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.pauseAware = checked
        }
        binding.respectPanelsSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.respectPanels = checked
        }
        binding.hapticSwitch.setOnCheckedChangeListener { _, checked -> prefs.haptic = checked }

        binding.overlaySwitch.setOnCheckedChangeListener { view, checked ->
            if (checked && !Settings.canDrawOverlays(this)) {
                view.isChecked = false
                startSafely(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                prefs.overlayEnabled = checked
            }
        }

        binding.openYoutube.setOnClickListener { openYouTubeShorts() }
        binding.openDiagnostics.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        bindState()
    }

    private fun bindState() {
        val serviceOn = ServiceStatus.isAccessibilityServiceEnabled(this)
        binding.statusTitle.setText(
            if (serviceOn) R.string.status_service_on else R.string.status_service_off
        )
        binding.statusHint.setText(
            if (serviceOn) R.string.status_hint_on else R.string.status_hint_off
        )

        binding.masterSwitch.isChecked = prefs.enabled
        binding.modeGroup.check(
            if (prefs.mode == Prefs.MODE_TIMER) R.id.modeTimer else R.id.modeSmart
        )
        binding.methodGroup.check(
            if (prefs.scrollMethod == Prefs.METHOD_ACTION) R.id.methodAction else R.id.methodGesture
        )

        val timer = prefs.timerSeconds
        binding.timerSlider.value = timer.toFloat()
        binding.timerLabel.text = getString(R.string.timer_seconds, timer)

        val threshold = prefs.endThresholdPercent
        binding.thresholdSlider.value = threshold.toFloat()
        binding.thresholdLabel.text = getString(R.string.end_threshold, threshold)

        // The sliders only accept values that sit on their step grid.
        val delay = (prefs.extraDelayMs / 50) * 50
        binding.delaySlider.value = delay.toFloat()
        binding.delayLabel.text = getString(R.string.extra_delay, delay)

        val maxWatch = ((prefs.maxWatchSeconds - Prefs.MAX_WATCH_MIN) / 10) * 10 + Prefs.MAX_WATCH_MIN
        binding.maxWatchSlider.value = maxWatch.toFloat()
        binding.maxWatchLabel.text = getString(R.string.max_watch, maxWatch)

        binding.pauseAwareSwitch.isChecked = prefs.pauseAware
        binding.respectPanelsSwitch.isChecked = prefs.respectPanels
        binding.hapticSwitch.isChecked = prefs.haptic

        val overlayAllowed = Settings.canDrawOverlays(this)
        if (!overlayAllowed && prefs.overlayEnabled) prefs.overlayEnabled = false
        binding.overlaySwitch.isChecked = prefs.overlayEnabled && overlayAllowed
    }

    private fun openYouTubeShorts() {
        val shorts = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/shorts"))
            .setPackage(Prefs.YOUTUBE_PACKAGE)
        if (shorts.resolveActivity(packageManager) != null) {
            startActivity(shorts)
            return
        }
        val launch = packageManager.getLaunchIntentForPackage(Prefs.YOUTUBE_PACKAGE)
        if (launch != null) {
            startActivity(launch)
        } else {
            Toast.makeText(this, R.string.youtube_missing, Toast.LENGTH_LONG).show()
        }
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, e.message ?: "Not available", Toast.LENGTH_LONG).show()
        }
    }
}
