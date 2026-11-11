package com.nitestand.standbyclock.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nitestand.standbyclock.appContainer
import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.sensors.DeviceOrientation
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.standby.ActivationSnapshot
import com.nitestand.standbyclock.standby.ActivationMode
import com.nitestand.standbyclock.standby.StandByState
import com.nitestand.standbyclock.standby.StandByUiState
import com.nitestand.standbyclock.ui.standby.StandByScreen
import com.nitestand.standbyclock.ui.standby.StandByTestTags
import com.nitestand.standbyclock.ui.standby.WaitingContent
import com.nitestand.standbyclock.ui.theme.StandByClockTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandByScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val sources = InstrumentationRegistry.getInstrumentation().targetContext.appContainer.widgetDataSources

    private fun snapshot(state: StandByState, charging: ChargingStatus, orientation: DeviceOrientation) = ActivationSnapshot(
        state = state,
        mode = ActivationMode.CHARGING_AND_LANDSCAPE,
        enabled = true,
        powerRequirementMet = charging.isPowered,
        orientationRequirementMet = orientation.isLandscape,
        charging = charging,
        orientation = orientation,
        forced = false,
    )

    @Test
    fun portraitToLandscapeSwitchesFromWaitingToActive() {
        var state by mutableStateOf(
            StandByUiState(snapshot(StandByState.WAITING_FOR_LANDSCAPE, ChargingStatus.CHARGING, DeviceOrientation.PORTRAIT), StandBySettings(), false),
        )
        compose.setContent {
            StandByClockTheme {
                StandByScreen(
                    state = state,
                    sources = sources,
                    onExit = {},
                    onCycleTheme = {},
                    systemReduceMotion = true,
                    inactiveContent = { WaitingContent(state, sources, onStart = {}, onClose = {}) },
                )
            }
        }
        compose.onNodeWithTag(StandByTestTags.WAITING).assertIsDisplayed()
        compose.onNodeWithText("Turn your phone sideways").assertIsDisplayed()

        state = state.copy(activation = snapshot(StandByState.ACTIVE, ChargingStatus.CHARGING, DeviceOrientation.LANDSCAPE_LEFT))
        compose.waitForIdle()
        compose.onNodeWithTag(StandByTestTags.ACTIVE).assertIsDisplayed()
    }

    @Test
    fun unpluggedShowsConnectToPower() {
        val state = StandByUiState(snapshot(StandByState.IDLE, ChargingStatus.NOT_CHARGING, DeviceOrientation.LANDSCAPE_LEFT), StandBySettings(), false)
        compose.setContent {
            StandByClockTheme {
                StandByScreen(state, sources, {}, {}, true) { WaitingContent(state, sources, {}, {}) }
            }
        }
        compose.onNodeWithText("Connect to power").assertIsDisplayed()
    }

    @Test
    fun doubleTapExitsStandBy() {
        var exits = 0
        val state = StandByUiState(snapshot(StandByState.ACTIVE, ChargingStatus.CHARGING, DeviceOrientation.LANDSCAPE_LEFT), StandBySettings(), false)
        compose.setContent {
            StandByClockTheme {
                StandByScreen(state, sources, onExit = { exits++ }, onCycleTheme = {}, systemReduceMotion = true) {}
            }
        }
        compose.onNodeWithTag(StandByTestTags.ROOT).performTouchInput { doubleClick() }
        compose.waitForIdle()
        assertEquals(1, exits)
    }
}
