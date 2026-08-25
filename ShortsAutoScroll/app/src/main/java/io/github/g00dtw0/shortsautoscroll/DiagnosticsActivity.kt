package io.github.g00dtw0.shortsautoscroll

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.g00dtw0.shortsautoscroll.databinding.ActivityDiagnosticsBinding

/**
 * Shows what the service is currently reading from YouTube. If a YouTube update ever breaks
 * end-of-Short detection, the dump produced here is what identifies the new view ids.
 */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiagnosticsBinding
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 700L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.dumpButton.setOnClickListener {
            Diagnostics.lastDump = ""
            Diagnostics.dumpRequested = true
            Diagnostics.log("screen dump requested")
        }
        binding.copyButton.setOnClickListener {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(
                ClipData.newPlainText("shorts-auto-scroll", binding.logText.text)
            )
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun render() {
        val header = buildString {
            append("service connected: ").append(Diagnostics.serviceConnected).append('\n')
            append("accessibility enabled: ")
                .append(ServiceStatus.isAccessibilityServiceEnabled(this@DiagnosticsActivity))
                .append("\n\n")
        }
        val dump = Diagnostics.lastDump
        val body = if (dump.isNotEmpty()) {
            "--- last screen dump ---\n$dump\n--- log ---\n${Diagnostics.snapshot()}"
        } else {
            "--- log ---\n${Diagnostics.snapshot()}"
        }
        binding.logText.text = header + body
    }
}
