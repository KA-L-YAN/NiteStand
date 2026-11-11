package com.nitestand.standbyclock

import android.app.Application
import android.content.Context
import com.nitestand.standbyclock.di.AppContainer
import com.nitestand.standbyclock.standby.StandBySession
import com.nitestand.standbyclock.standby.StandByTileService
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class StandByClockApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Keep the Quick Settings tile's active/inactive state in sync with the StandBy display.
        container.applicationScope.launch {
            StandBySession.showing.drop(1).collect { StandByTileService.refresh(this@StandByClockApp) }
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as StandByClockApp).container
