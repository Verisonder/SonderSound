package com.verisonder.sondersound.tile

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.verisonder.sondersound.R
import com.verisonder.sondersound.Settings
import com.verisonder.sondersound.audio.ClipPlayer
import com.verisonder.sondersound.audio.ListenService
import com.verisonder.sondersound.ui.MainActivity

/**
 * Quick settings: turns listening on and off. Long-press opens the app, through the
 * QS_TILE_PREFERENCES filter on MainActivity.
 *
 * The tile shows what the service is doing, not the saved switch.
 */
class ListenTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (ListenService.state.value.running) {
            Settings.setListening(this, false)
            ClipPlayer.stop()
            ListenService.stop(this)
            refresh(forceOn = false)
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        Settings.setListening(this, true)
        if (!granted || !Settings.setupDone(this)) {
            // The app asks for the microphone; it starts listening once it is allowed.
            openApp()
            return
        }
        ListenService.start(this)
        refresh(forceOn = true)
    }

    private fun refresh(forceOn: Boolean? = null) {
        val tile = qsTile ?: return
        val on = forceOn ?: ListenService.state.value.running
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = getString(if (on) R.string.tile_on else R.string.tile_off)
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Asks the system to redraw both tiles, after listening starts or stops. */
        fun update(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, ListenTile::class.java)) }
            runCatching { requestListeningState(context, ComponentName(context, LastClipTile::class.java)) }
        }
    }
}
