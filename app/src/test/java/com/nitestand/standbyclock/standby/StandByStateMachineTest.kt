package com.nitestand.standbyclock.standby

import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.sensors.DeviceOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandByStateMachineTest {

    private fun machine(
        mode: ActivationMode = ActivationMode.CHARGING_AND_LANDSCAPE,
        autoStart: Boolean = true,
        enabled: Boolean = true,
    ) = StandByStateMachine().apply {
        onEvent(StandByEvent.SettingsChanged(enabled, mode, autoStart))
    }

    private fun StandByStateMachine.charging(status: ChargingStatus) = onEvent(StandByEvent.ChargingChanged(status)).state
    private fun StandByStateMachine.orientation(o: DeviceOrientation) = onEvent(StandByEvent.OrientationChanged(o)).state

    @Test
    fun `starts idle`() {
        assertEquals(StandByState.IDLE, StandByStateMachine().snapshot.state)
    }

    @Test
    fun `not charging to charging moves out of idle`() {
        val m = machine()
        assertEquals(StandByState.IDLE, m.charging(ChargingStatus.NOT_CHARGING))
        assertEquals(StandByState.CHARGING, m.charging(ChargingStatus.CHARGING)) // orientation not measured yet
    }

    @Test
    fun `charging plus portrait waits for landscape`() {
        val m = machine()
        m.charging(ChargingStatus.CHARGING)
        assertEquals(StandByState.WAITING_FOR_LANDSCAPE, m.orientation(DeviceOrientation.PORTRAIT))
        assertEquals(StandByState.WAITING_FOR_LANDSCAPE, m.orientation(DeviceOrientation.FLAT))
    }

    @Test
    fun `charging plus portrait to charging plus landscape activates`() {
        val m = machine()
        m.charging(ChargingStatus.CHARGING)
        m.orientation(DeviceOrientation.PORTRAIT)
        assertEquals(StandByState.ACTIVE, m.orientation(DeviceOrientation.LANDSCAPE_LEFT))
        assertEquals(StandByState.ACTIVE, m.orientation(DeviceOrientation.LANDSCAPE_RIGHT))
    }

    @Test
    fun `full battery still counts as charging`() {
        val m = machine()
        m.orientation(DeviceOrientation.LANDSCAPE_LEFT)
        assertEquals(StandByState.ACTIVE, m.charging(ChargingStatus.FULL))
    }

    @Test
    fun `landscape to portrait deactivates`() {
        val m = machine()
        m.charging(ChargingStatus.CHARGING)
        m.orientation(DeviceOrientation.LANDSCAPE_LEFT)
        assertEquals(StandByState.WAITING_FOR_LANDSCAPE, m.orientation(DeviceOrientation.PORTRAIT))
    }

    @Test
    fun `charging plus landscape to unplugged deactivates`() {
        val m = machine()
        m.charging(ChargingStatus.CHARGING)
        m.orientation(DeviceOrientation.LANDSCAPE_RIGHT)
        assertEquals(StandByState.IDLE, m.charging(ChargingStatus.NOT_CHARGING))
    }

    @Test
    fun `landscape while not charging stays idle`() {
        val m = machine()
        m.charging(ChargingStatus.NOT_CHARGING)
        assertEquals(StandByState.IDLE, m.orientation(DeviceOrientation.LANDSCAPE_LEFT))
    }

    @Test
    fun `standby exit stays ready until a condition breaks`() {
        val m = machine()
        m.charging(ChargingStatus.CHARGING)
        m.orientation(DeviceOrientation.LANDSCAPE_LEFT)
        assertEquals(StandByState.READY, m.onEvent(StandByEvent.ExitRequested).state)
        // Wobbling between landscape directions keeps it dismissed.
        assertEquals(StandByState.READY, m.orientation(DeviceOrientation.LANDSCAPE_RIGHT))
        // Picking the phone up re-arms; putting it back activates again.
        m.orientation(DeviceOrientation.PORTRAIT)
        assertEquals(StandByState.ACTIVE, m.orientation(DeviceOrientation.LANDSCAPE_LEFT))
    }

    @Test
    fun `unplugging after exit re-arms automatic activation`() {
        val m = machine()
        m.charging(ChargingStatus.CHARGING)
        m.orientation(DeviceOrientation.LANDSCAPE_LEFT)
        m.onEvent(StandByEvent.ExitRequested)
        m.charging(ChargingStatus.NOT_CHARGING)
        assertEquals(StandByState.ACTIVE, m.charging(ChargingStatus.CHARGING))
    }

    @Test
    fun `charging only mode ignores orientation`() {
        val m = machine(mode = ActivationMode.CHARGING_ONLY)
        m.orientation(DeviceOrientation.PORTRAIT)
        assertEquals(StandByState.ACTIVE, m.charging(ChargingStatus.CHARGING))
    }

    @Test
    fun `landscape only mode does not need power`() {
        val m = machine(mode = ActivationMode.LANDSCAPE_ONLY)
        m.charging(ChargingStatus.NOT_CHARGING)
        assertEquals(StandByState.ACTIVE, m.orientation(DeviceOrientation.LANDSCAPE_LEFT))
    }

    @Test
    fun `missing orientation sensor treats landscape requirement as met`() {
        val m = machine()
        m.charging(ChargingStatus.CHARGING)
        assertEquals(StandByState.ACTIVE, m.orientation(DeviceOrientation.UNAVAILABLE))
    }

    @Test
    fun `auto start off requires a tap`() {
        val m = machine(autoStart = false)
        m.charging(ChargingStatus.CHARGING)
        assertEquals(StandByState.READY, m.orientation(DeviceOrientation.LANDSCAPE_LEFT))
        assertEquals(StandByState.ACTIVE, m.onEvent(StandByEvent.StartRequested(force = false)).state)
        // Confirmation is forgotten once conditions break.
        m.charging(ChargingStatus.NOT_CHARGING)
        assertEquals(StandByState.READY, m.charging(ChargingStatus.CHARGING))
    }

    @Test
    fun `manual mode only activates on request and exits on request`() {
        val m = machine(mode = ActivationMode.MANUAL)
        m.charging(ChargingStatus.CHARGING)
        assertEquals(StandByState.IDLE, m.orientation(DeviceOrientation.LANDSCAPE_LEFT))
        assertEquals(StandByState.ACTIVE, m.onEvent(StandByEvent.StartRequested(force = false)).state)
        m.charging(ChargingStatus.NOT_CHARGING) // manual StandBy ignores conditions
        assertEquals(StandByState.ACTIVE, m.snapshot.state)
        assertEquals(StandByState.IDLE, m.onEvent(StandByEvent.ExitRequested).state)
    }

    @Test
    fun `forced preview is active regardless and exit ends it`() {
        val m = machine()
        m.charging(ChargingStatus.NOT_CHARGING)
        val snapshot = m.onEvent(StandByEvent.StartRequested(force = true))
        assertEquals(StandByState.ACTIVE, snapshot.state)
        assertTrue(snapshot.forced)
        assertEquals(StandByState.IDLE, m.onEvent(StandByEvent.ExitRequested).state)
        assertFalse(m.snapshot.forced)
    }

    @Test
    fun `disabling standby deactivates`() {
        val m = machine()
        m.charging(ChargingStatus.CHARGING)
        m.orientation(DeviceOrientation.LANDSCAPE_LEFT)
        val s = m.onEvent(StandByEvent.SettingsChanged(enabled = false, ActivationMode.CHARGING_AND_LANDSCAPE, autoStart = true))
        assertEquals(StandByState.IDLE, s.state)
    }

    @Test
    fun `snapshot reports which requirement is missing`() {
        val m = machine()
        m.charging(ChargingStatus.CHARGING)
        val s = m.onEvent(StandByEvent.OrientationChanged(DeviceOrientation.PORTRAIT))
        assertTrue(s.powerRequirementMet)
        assertFalse(s.orientationRequirementMet)
    }

    @Test
    fun `activation mode from toggles`() {
        assertEquals(ActivationMode.CHARGING_AND_LANDSCAPE, ActivationMode.from(true, true))
        assertEquals(ActivationMode.CHARGING_ONLY, ActivationMode.from(true, false))
        assertEquals(ActivationMode.LANDSCAPE_ONLY, ActivationMode.from(false, true))
        assertEquals(ActivationMode.MANUAL, ActivationMode.from(false, false))
    }
}
