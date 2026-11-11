package com.nitestand.standbyclock.charging

/** The power state StandBy reasons about. */
enum class ChargingStatus {
    /** External power is connected and the battery is charging (or held by a charge limit). */
    CHARGING,

    /** No external power. */
    NOT_CHARGING,

    /** External power is connected and the battery reports full. */
    FULL;

    /** True when the phone is connected to a power source. */
    val isPowered: Boolean get() = this != NOT_CHARGING
}

enum class PowerSource { NONE, AC, USB, WIRELESS, DOCK }

data class BatterySnapshot(
    /** 0..100, or null when the platform did not report a level. */
    val levelPercent: Int?,
    val status: ChargingStatus,
    val source: PowerSource,
)
