package com.drdisagree.glasslayer.ui.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.drdisagree.glasslayer.GlassLayerApp
import com.drdisagree.glasslayer.domain.model.GlassToggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GlassTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    private val container get() = (application as GlassLayerApp).container

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            container.observeSettings().collect { settings -> render(settings.isOn(GlassToggle.ENABLED)) }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val enabled = container.observeSettings().first().isOn(GlassToggle.ENABLED)
            render(!enabled)
            container.setToggle(GlassToggle.ENABLED, !enabled)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
