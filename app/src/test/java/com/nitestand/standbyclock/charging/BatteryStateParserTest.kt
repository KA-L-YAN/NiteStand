package com.nitestand.standbyclock.charging

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryStateParserTest {

    @Test
    fun `unplugged discharging battery is NOT_CHARGING`() {
        val s = BatteryStateParser.parse(BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0, level = 42, scale = 100)
        assertEquals(ChargingStatus.NOT_CHARGING, s.status)
        assertEquals(PowerSource.NONE, s.source)
        assertEquals(42, s.levelPercent)
        assertFalse(s.status.isPowered)
    }

    @Test
    fun `plugged in AC charging is CHARGING`() {
        val s = BatteryStateParser.parse(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, 85, 100)
        assertEquals(ChargingStatus.CHARGING, s.status)
        assertEquals(PowerSource.AC, s.source)
        assertTrue(s.status.isPowered)
    }

    @Test
    fun `plugged in and full is FULL and still powered`() {
        val s = BatteryStateParser.parse(BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_PLUGGED_USB, 100, 100)
        assertEquals(ChargingStatus.FULL, s.status)
        assertTrue(s.status.isPowered)
    }

    @Test
    fun `charge limit (plugged but not charging) counts as powered`() {
        val s = BatteryStateParser.parse(BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, 80, 100)
        assertEquals(ChargingStatus.CHARGING, s.status)
    }

    @Test
    fun `wireless source detected`() {
        val s = BatteryStateParser.parse(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_WIRELESS, 50, 100)
        assertEquals(PowerSource.WIRELESS, s.source)
    }

    @Test
    fun `charging status without plug flag is still CHARGING`() {
        val s = BatteryStateParser.parse(BatteryManager.BATTERY_STATUS_CHARGING, plugged = 0, level = 10, scale = 100)
        assertEquals(ChargingStatus.CHARGING, s.status)
    }

    @Test
    fun `level is scaled and missing level is null`() {
        assertEquals(50, BatteryStateParser.parse(BatteryManager.BATTERY_STATUS_DISCHARGING, 0, 128, 256).levelPercent)
        assertNull(BatteryStateParser.parse(BatteryManager.BATTERY_STATUS_UNKNOWN, 0, -1, -1).levelPercent)
    }

    @Test
    fun `power hints transition not charging to charging and back`() {
        val unplugged = BatterySnapshot(70, ChargingStatus.NOT_CHARGING, PowerSource.NONE)
        val connected = BatteryStateParser.applyPowerHint(unplugged, connected = true)
        assertEquals(ChargingStatus.CHARGING, connected.status)
        assertEquals(70, connected.levelPercent)

        val full = BatterySnapshot(100, ChargingStatus.FULL, PowerSource.AC)
        assertEquals(ChargingStatus.FULL, BatteryStateParser.applyPowerHint(full, connected = true).status)

        val disconnected = BatteryStateParser.applyPowerHint(full, connected = false)
        assertEquals(ChargingStatus.NOT_CHARGING, disconnected.status)
        assertEquals(PowerSource.NONE, disconnected.source)
    }

    @Test
    fun `power hint without previous snapshot`() {
        assertEquals(ChargingStatus.CHARGING, BatteryStateParser.applyPowerHint(null, connected = true).status)
    }
}
