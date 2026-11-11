package com.nitestand.standbyclock.widgets

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nitestand.standbyclock.alarm.NextAlarm
import com.nitestand.standbyclock.charging.BatterySnapshot
import com.nitestand.standbyclock.clock.ClockTicker
import com.nitestand.standbyclock.clock.TickInterval
import com.nitestand.standbyclock.clock.TimeFormatPreference
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.weather.WeatherProvider
import kotlinx.coroutines.flow.Flow

enum class WidgetId { CLOCK, DATE, BATTERY, NEXT_ALARM, WEATHER }

/** When a widget's data changes. Widgets never poll faster than their policy. */
sealed interface RefreshPolicy {
    /** Updates only when the system broadcasts a change (battery, alarm). */
    data object EventDriven : RefreshPolicy

    /** Updates on wall-clock boundaries. */
    data class Ticking(val interval: TickInterval) : RefreshPolicy

    /** Updates on a fixed, coarse period (e.g. weather). */
    data class Periodic(val minutes: Int) : RefreshPolicy
}

/** Everything a widget may read. Each source is idle unless a widget observes it. */
class WidgetDataSources(
    val battery: Flow<BatterySnapshot>,
    val nextAlarm: Flow<NextAlarm?>,
    val weather: WeatherProvider,
    val ticker: ClockTicker,
    val timeFormat: TimeFormatPreference,
)

/**
 * A StandBy panel element. A widget provides its own title, configuration (whether it is
 * enabled for the current settings), refresh policy, data stream and UI, so new widgets can
 * be added by implementing this interface and registering them in [WidgetRegistry].
 */
interface StandByWidget<T : Any> {
    val id: WidgetId

    @get:StringRes
    val titleRes: Int

    fun refreshPolicy(settings: StandBySettings): RefreshPolicy

    /** Configuration: whether the user enabled this widget. */
    fun isEnabled(settings: StandBySettings): Boolean

    /** Data stream for this widget. Collected only while the widget is on screen. */
    fun observe(sources: WidgetDataSources, settings: StandBySettings): Flow<T>

    @Composable
    fun Content(data: T, style: StandByStyle, modifier: Modifier)
}
