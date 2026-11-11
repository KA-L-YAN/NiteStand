package com.nitestand.standbyclock.standby

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandByActivityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun previewStartsAndBackExits() {
        ActivityScenario.launch<StandByActivity>(StandByActivity.intent(context, force = true)).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            pressBackUnconditionally()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }
}
