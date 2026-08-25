package io.github.g00dtw0.shortsautoscroll

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: pause or resume auto scroll without leaving YouTube. */
class ToggleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!ServiceStatus.isAccessibilityServiceEnabled(this)) {
            openApp()
            return
        }
        Prefs(this).let { it.enabled = !it.enabled }
        updateTile()
    }

    private fun updateTile() {
        val tile: Tile = qsTile ?: return
        val serviceOn = ServiceStatus.isAccessibilityServiceEnabled(this)
        val enabled = Prefs(this).enabled
        tile.state = when {
            !serviceOn -> Tile.STATE_UNAVAILABLE
            enabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.tile_label)
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
