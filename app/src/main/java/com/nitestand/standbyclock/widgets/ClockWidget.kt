package com.nitestand.standbyclock.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.clock.ClockFaceCatalog
import com.nitestand.standbyclock.clock.ClockFormatter
import com.nitestand.standbyclock.clock.ClockPalette
import com.nitestand.standbyclock.clock.ClockText
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.clock.DateStyle
import com.nitestand.standbyclock.clock.TickInterval
import com.nitestand.standbyclock.clock.WorldCities
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.ui.clock.ClockFaceView
import com.nitestand.standbyclock.ui.theme.StandByStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/** Time in another place, for the world-clock face. */
data class WorldTime(
    val label: String,
    val text: ClockText,
    /** Calendar-day difference to the phone's local date (-1, 0, +1). */
    val dayOffset: Int,
    /** Offset to the phone's zone in minutes. */
    val offsetMinutes: Int,
)

/** Everything a clock face needs to draw one frame. Built in one place for every face. */
data class ClockFaceData(
    val time: ZonedDateTime,
    val text: ClockText,
    val theme: ClockTheme,
    val showDate: Boolean,
    val showSeconds: Boolean,
    val palette: ClockPalette = ClockFaceCatalog.spec(theme).defaultPalette,
    val dateStyle: DateStyle = DateStyle.LONG,
    val primaryPlace: WorldTime? = null,
    val secondaryPlace: WorldTime? = null,
) {
    /** The date in the user's chosen style. */
    val dateText: String get() = if (dateStyle == DateStyle.LONG) text.longDate else text.mediumDate

    companion object {
        /**
         * Applies the face's capabilities to the global settings: a face that does not support
         * seconds / date / AM-PM never receives them, so faces cannot drift apart in behaviour.
         */
        fun build(now: ZonedDateTime, settings: StandBySettings, formatterFor: (zone: ZoneId) -> ClockFormatter): ClockFaceData {
            val theme = settings.clockTheme
            val spec = ClockFaceCatalog.spec(theme)
            val localFormatter = formatterFor(now.zone)
            var primary: WorldTime? = null
            var secondary: WorldTime? = null
            var text = localFormatter.format(now)
            if (spec.usesWorldClock) {
                primary = worldTime(now, settings.worldPrimaryCity, localFormatter) ?: WorldTime(
                    WorldCities.labelForZone(now.zone), text, 0, 0,
                )
                text = primary.text
                secondary = settings.worldSecondaryCity.takeIf { it != WorldCities.NONE }
                    ?.let { worldTime(now, it, localFormatter) }
            }
            return ClockFaceData(
                time = if (spec.usesWorldClock && settings.worldPrimaryCity != WorldCities.LOCAL) {
                    WorldCities.find(settings.worldPrimaryCity)?.let { now.withZoneSameInstant(it.zone) } ?: now
                } else {
                    now
                },
                text = text,
                theme = theme,
                showDate = settings.showDate && spec.supportsDate,
                showSeconds = settings.showSeconds && spec.supportsSeconds,
                palette = ClockFaceCatalog.paletteFor(settings, theme),
                dateStyle = settings.dateStyle,
                primaryPlace = primary,
                secondaryPlace = secondary,
            )
        }

        private fun worldTime(now: ZonedDateTime, key: String, formatter: ClockFormatter): WorldTime? {
            val city = WorldCities.find(key) ?: return null
            val there = now.withZoneSameInstant(city.zone)
            val offset = Duration.ofSeconds(
                (there.offset.totalSeconds - now.offset.totalSeconds).toLong(),
            ).toMinutes().toInt()
            val dayOffset = (there.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()).toInt()
            return WorldTime(city.name, formatter.format(there), dayOffset, offset)
        }
    }
}

/** The main clock, in the selected face. */
object ClockWidget : StandByWidget<ClockFaceData> {
    override val id = WidgetId.CLOCK
    override val titleRes = R.string.widget_clock

    override fun refreshPolicy(settings: StandBySettings): RefreshPolicy {
        val seconds = settings.showSeconds && ClockFaceCatalog.spec(settings.clockTheme).supportsSeconds
        return RefreshPolicy.Ticking(if (seconds) TickInterval.SECOND else TickInterval.MINUTE)
    }

    override fun isEnabled(settings: StandBySettings) = true

    override fun observe(sources: WidgetDataSources, settings: StandBySettings): Flow<ClockFaceData> {
        val interval = (refreshPolicy(settings) as RefreshPolicy.Ticking).interval
        val spec = ClockFaceCatalog.spec(settings.clockTheme)
        var cached: Pair<Boolean, ClockFormatter>? = null
        return sources.ticker.ticks(interval).map { now ->
            // Re-check the system 12/24h setting each tick so a change applies within a minute.
            val is24 = sources.timeFormat.is24Hour(settings.hourFormat)
            val formatter = cached?.takeIf { it.first == is24 }?.second
                ?: ClockFormatter(
                    sources.timeFormat.locale(),
                    is24,
                    showSeconds = settings.showSeconds && spec.supportsSeconds,
                    showAmPm = settings.showAmPm && spec.supportsAmPm,
                    datePatterns = sources.timeFormat.datePatterns(),
                ).also { cached = is24 to it }
            ClockFaceData.build(now, settings) { formatter }
        }
    }

    @Composable
    override fun Content(data: ClockFaceData, style: StandByStyle, modifier: Modifier) {
        ClockFaceView(data, style, modifier)
    }
}
