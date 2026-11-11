package com.nitestand.standbyclock.charging

import android.os.BatteryManager
import kotlin.math.roundToInt

/**
 * Converts the raw extras of an ACTION_BATTERY_CHANGED intent into a [BatterySnapshot].
 * Kept free of Android framework objects so it can be unit tested on the JVM.
 */
object BatteryStateParser {

    fun parse(status: Int, plugged: Int, level: Int, scale: Int): BatterySnapshot {
        val source = when {
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> PowerSource.WIRELESS
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> PowerSource.AC
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> PowerSource.USB
            plugged and BATTERY_PLUGGED_DOCK != 0 -> PowerSource.DOCK
            else -> PowerSource.NONE
        }
        val percent = if (level >= 0 && scale > 0) {
            (level * 100f / scale).roundToInt().coerceIn(0, 100)
        } else {
            null
        }
        val chargingStatus = when {
            // Plugged in. Devices with a charge limit (battery protection / adaptive charging)
            // report NOT_CHARGING while connected; for StandBy that still counts as powered.
            source != PowerSource.NONE ->
                if (status == BatteryManager.BATTERY_STATUS_FULL) ChargingStatus.FULL else ChargingStatus.CHARGING
            // Some devices briefly report plugged == 0 while status already says charging.
            status == BatteryManager.BATTERY_STATUS_CHARGING -> ChargingStatus.CHARGING
            else -> ChargingStatus.NOT_CHARGING
        }
        return BatterySnapshot(percent, chargingStatus, source)
    }

    /**
     * Applies an ACTION_POWER_CONNECTED / ACTION_POWER_DISCONNECTED hint to the last known
     * snapshot. Those broadcasts usually arrive slightly before the next ACTION_BATTERY_CHANGED.
     */
    fun applyPowerHint(previous: BatterySnapshot?, connected: Boolean): BatterySnapshot {
        val base = previous ?: BatterySnapshot(null, ChargingStatus.NOT_CHARGING, PowerSource.NONE)
        return if (connected) {
            if (base.status.isPowered) base else base.copy(status = ChargingStatus.CHARGING)
        } else {
            base.copy(status = ChargingStatus.NOT_CHARGING, source = PowerSource.NONE)
        }
    }

    /** BatteryManager.BATTERY_PLUGGED_DOCK (API 33). Declared locally to stay usable on API 26. */
    private const val BATTERY_PLUGGED_DOCK = 8
}
