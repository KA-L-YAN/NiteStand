package com.nitestand.standbyclock.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.clock.ClockFormatter
import com.nitestand.standbyclock.clock.ClockText
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.clock.TickInterval
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.ui.clock.ClockFace
import com.nitestand.standbyclock.ui.theme.StandByStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZonedDateTime

data class ClockFaceData(
    val time: ZonedDateTime,
    val text: ClockText,
    val theme: ClockTheme,
    val showDate: Boolean,
    val showSeconds: Boolean,
)

/** The main (left) panel: the clock in the selected design. */
object ClockWidget : StandByWidget<ClockFaceData> {
    override val id = WidgetId.CLOCK
    override val titleRes = R.string.widget_clock

    override fun refreshPolicy(settings: StandBySettings): RefreshPolicy =
        RefreshPolicy.Ticking(if (settings.showSeconds) TickInterval.SECOND else TickInterval.MINUTE)

    override fun isEnabled(settings: StandBySettings) = true

    override fun observe(sources: WidgetDataSources, settings: StandBySettings): Flow<ClockFaceData> {
        val interval = (refreshPolicy(settings) as RefreshPolicy.Ticking).interval
        var cached: Pair<Boolean, ClockFormatter>? = null
        return sources.ticker.ticks(interval).map { now ->
            // Re-check the system 12/24h setting each tick so a change applies within a minute.
            val is24 = sources.timeFormat.is24Hour(settings.hourFormat)
            val formatter = cached?.takeIf { it.first == is24 }?.second
                ?: ClockFormatter(sources.timeFormat.locale(), is24, settings.showSeconds).also { cached = is24 to it }
            ClockFaceData(now, formatter.format(now), settings.clockTheme, settings.showDate, settings.showSeconds)
        }
    }

    @Composable
    override fun Content(data: ClockFaceData, style: StandByStyle, modifier: Modifier) {
        ClockFace(data, style, modifier)
    }
}
