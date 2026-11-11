package com.nitestand.standbyclock.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nitestand.standbyclock.appContainer
import com.nitestand.standbyclock.diagnostics.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restarts the opt-in lock-screen watcher after a reboot or app update, if the user enabled it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = context.appContainer.settingsRepository.current()
                val enabled = settings.standByEnabled && settings.lockScreenWatcher && settings.activationMode.supportsLockScreenWatcher
                EventLog.log("Boot/update received; watcher enabled=$enabled")
                if (enabled) LockScreenWatcherService.sync(context.applicationContext, enabled = true)
            } finally {
                pending.finish()
            }
        }
    }
}
