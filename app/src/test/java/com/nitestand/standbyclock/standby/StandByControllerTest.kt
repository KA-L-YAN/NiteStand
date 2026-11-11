package com.nitestand.standbyclock.standby

import com.nitestand.standbyclock.charging.BatterySnapshot
import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.charging.PowerSource
import com.nitestand.standbyclock.display.NightMode
import com.nitestand.standbyclock.sensors.DeviceOrientation
import com.nitestand.standbyclock.sensors.OrientationConfig
import com.nitestand.standbyclock.sensors.OrientationSource
import com.nitestand.standbyclock.settings.SettingsRepository
import com.nitestand.standbyclock.testutil.InMemoryDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class StandByControllerTest {

    private val battery = MutableStateFlow(BatterySnapshot(50, ChargingStatus.NOT_CHARGING, PowerSource.NONE))
    private val orientation = MutableStateFlow(DeviceOrientation.PORTRAIT)
    private var sensorSubscriptions = 0
    private val orientationSource = OrientationSource { _: OrientationConfig ->
        sensorSubscriptions++
        orientation
    }
    private val settings = SettingsRepository(InMemoryDataStore())
    private val midnight = ZonedDateTime.of(2026, 1, 1, 23, 30, 0, 0, ZoneId.systemDefault())

    private fun TestScope.controller(force: Boolean = false, dream: Boolean = false): StandByController {
        val c = StandByController(settings, battery, orientationSource, flowOf(midnight), backgroundScope, forceActive = force, dream = dream)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.uiState.collect { } }
        return c
    }

    private fun StandByController.state() = uiState.value!!.activation.state

    @Test
    fun `full transition sequence`() = runTest {
        val c = controller()
        runCurrent()
        assertEquals(StandByState.IDLE, c.state())

        battery.value = battery.value.copy(status = ChargingStatus.CHARGING) // not charging -> charging
        runCurrent()
        assertEquals(StandByState.WAITING_FOR_LANDSCAPE, c.state())

        orientation.value = DeviceOrientation.LANDSCAPE_LEFT // portrait -> landscape
        runCurrent()
        assertEquals(StandByState.ACTIVE, c.state())

        orientation.value = DeviceOrientation.PORTRAIT // landscape -> portrait
        runCurrent()
        assertEquals(StandByState.WAITING_FOR_LANDSCAPE, c.state())

        orientation.value = DeviceOrientation.LANDSCAPE_RIGHT
        runCurrent()
        assertEquals(StandByState.ACTIVE, c.state())

        battery.value = battery.value.copy(status = ChargingStatus.NOT_CHARGING) // unplugged
        runCurrent()
        assertEquals(StandByState.IDLE, c.state())
    }

    @Test
    fun `standby to exit`() = runTest {
        battery.value = battery.value.copy(status = ChargingStatus.CHARGING)
        orientation.value = DeviceOrientation.LANDSCAPE_LEFT
        val c = controller()
        runCurrent()
        assertEquals(StandByState.ACTIVE, c.state())
        c.requestExit()
        runCurrent()
        assertEquals(StandByState.READY, c.state())
    }

    @Test
    fun `forced preview is active immediately and exit ends it`() = runTest {
        val c = controller(force = true)
        runCurrent()
        assertTrue(c.uiState.value!!.activation.forced)
        assertEquals(StandByState.ACTIVE, c.state())
        c.requestExit()
        runCurrent()
        assertEquals(StandByState.IDLE, c.state())
    }

    @Test
    fun `orientation sensor is not used when landscape is not required`() = runTest {
        settings.update { it.copy(activationMode = ActivationMode.CHARGING_ONLY) }
        val c = controller()
        battery.value = battery.value.copy(status = ChargingStatus.CHARGING)
        runCurrent()
        assertEquals(StandByState.ACTIVE, c.state())
        assertEquals(0, sensorSubscriptions)
    }

    @Test
    fun `night mode follows the schedule`() = runTest {
        settings.update { it.copy(nightMode = NightMode.SCHEDULED, nightStartMinutes = 22 * 60, nightEndMinutes = 7 * 60) }
        val c = controller()
        runCurrent()
        assertTrue(c.uiState.value!!.nightActive)
        settings.update { it.copy(nightMode = NightMode.OFF) }
        runCurrent()
        assertFalse(c.uiState.value!!.nightActive)
    }

    @Test
    fun `cycling theme persists`() = runTest {
        val c = controller()
        c.cycleTheme(1)
        runCurrent()
        assertEquals(com.nitestand.standbyclock.clock.ClockTheme.MINIMAL, settings.current().clockTheme)
    }

    @Test
    fun `screen saver mode shows in landscape inside the dream`() = runTest {
        settings.update { it.copy(activationMode = ActivationMode.SCREEN_SAVER, autoStart = false) }
        battery.value = battery.value.copy(status = ChargingStatus.CHARGING)
        orientation.value = DeviceOrientation.LANDSCAPE_LEFT
        val c = controller(dream = true)
        runCurrent()
        assertEquals(StandByState.ACTIVE, c.state())
        orientation.value = DeviceOrientation.PORTRAIT
        runCurrent()
        assertEquals(StandByState.WAITING_FOR_LANDSCAPE, c.state())
    }

    @Test
    fun `battery state is exposed for the battery timeout`() = runTest {
        val c = controller()
        runCurrent()
        assertEquals(ChargingStatus.NOT_CHARGING, c.uiState.value!!.battery?.status)
    }
}
