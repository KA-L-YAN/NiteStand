package com.nitestand.standbyclock.util

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Minimal Lifecycle + SavedState owner so Compose can run inside a non-Activity window such as
 * a DreamService. Driving it to STARTED/STOPPED makes lifecycle-aware collectors (sensors,
 * receivers, timers) start and stop with the dream.
 */
class ComposeLifecycleHost : LifecycleOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun create() {
        savedStateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun start() = registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    fun resume() = registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun pause() = registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun stop() = registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

    fun destroy() {
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    fun attachTo(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }
}
