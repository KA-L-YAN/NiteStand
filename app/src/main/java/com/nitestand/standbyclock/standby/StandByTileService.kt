package com.nitestand.standbyclock.standby

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.diagnostics.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * "NiteStand" Quick Settings tile.
 *
 * Inactive → tap starts StandBy immediately (forced: no charger or landscape needed).
 * Active (StandBy on screen) → tap closes StandBy.
 *
 * On the lock screen the system decides whether tiles can be used without unlocking; StandBy
 * itself is shown above the lock screen and never unlocks the device.
 */
class StandByTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening = scope.launch { StandBySession.showing.collect { render(it != null) } }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun render(active: Boolean) {
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(if (active) R.string.tile_subtitle_on else R.string.tile_subtitle)
            }
            contentDescription = getString(if (active) R.string.tile_description_on else R.string.tile_description)
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        if (StandBySession.showing.value != null) {
            EventLog.log("Tile: closing StandBy")
            StandBySession.requestExit()
            render(false)
            return
        }
        EventLog.log("Tile: starting StandBy (locked=$isLocked, secure=$isSecure)")
        val intent = StandByActivity.intent(this, force = true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Asks the system to refresh the tile (e.g. when StandBy starts or stops). */
        fun refresh(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, StandByTileService::class.java))
            }
        }
    }
}
