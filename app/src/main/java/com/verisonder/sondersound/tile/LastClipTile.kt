package com.verisonder.sondersound.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.verisonder.sondersound.audio.ClipPlayer
import com.verisonder.sondersound.audio.ListenService

/** Quick settings: plays the buffer. Unavailable while not listening. */
class LastClipTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = if (ListenService.state.value.running) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        ListenService.freeze()?.let { ClipPlayer.play(this, it) }
    }
}
