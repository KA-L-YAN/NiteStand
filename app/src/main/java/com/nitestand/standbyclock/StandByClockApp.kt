package com.nitestand.standbyclock

import android.app.Application
import android.content.Context
import com.nitestand.standbyclock.di.AppContainer

class StandByClockApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as StandByClockApp).container
