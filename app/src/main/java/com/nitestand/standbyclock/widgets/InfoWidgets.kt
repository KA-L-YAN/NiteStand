package com.nitestand.standbyclock.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.alarm.NextAlarm
import com.nitestand.standbyclock.charging.BatterySnapshot
import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.clock.TickInterval
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.ui.standby.PanelLabel
import com.nitestand.standbyclock.ui.standby.PanelValue
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.weather.WeatherCondition
import com.nitestand.standbyclock.weather.WeatherState
import com.nitestand.standbyclock.weather.weatherRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Date

data class DateWidgetData(val weekday: String, val day: String, val month: String)

object DateWidget : StandByWidget<DateWidgetData> {
    override val id = WidgetId.DATE
    override val titleRes = R.string.widget_date
    override fun refreshPolicy(settings: StandBySettings) = RefreshPolicy.Ticking(TickInterval.MINUTE)
    override fun isEnabled(settings: StandBySettings) = settings.showDateWidget

    override fun observe(sources: WidgetDataSources, settings: StandBySettings): Flow<DateWidgetData> =
        sources.ticker.ticks(TickInterval.MINUTE)
            .map { it.toLocalDate() }
            .distinctUntilChanged()
            .map { date ->
                val locale = sources.timeFormat.locale()
                DateWidgetData(
                    weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                    day = DateTimeFormatter.ofPattern("d", locale).format(date),
                    month = date.month.getDisplayName(TextStyle.FULL_STANDALONE, locale),
                )
            }

    @Composable
    override fun Content(data: DateWidgetData, style: StandByStyle, modifier: Modifier) {
        Column(
            modifier.semantics(mergeDescendants = true) { contentDescription = "${data.weekday} ${data.day} ${data.month}" },
            verticalArrangement = Arrangement.Center,
        ) {
            PanelLabel(data.weekday.uppercase(), style, accent = true)
            Row(verticalAlignment = Alignment.Bottom) {
                PanelValue(data.day, style)
                Spacer(Modifier.width(10.dp))
                PanelLabel(data.month, style)
            }
        }
    }
}

// ---------------------------------------------------------------- Battery

object BatteryWidget : StandByWidget<BatterySnapshot> {
    override val id = WidgetId.BATTERY
    override val titleRes = R.string.widget_battery
    override fun refreshPolicy(settings: StandBySettings) = RefreshPolicy.EventDriven
    override fun isEnabled(settings: StandBySettings) = settings.showBatteryWidget
    override fun observe(sources: WidgetDataSources, settings: StandBySettings) = sources.battery

    @Composable
    override fun Content(data: BatterySnapshot, style: StandByStyle, modifier: Modifier) {
        val status = stringResource(
            when (data.status) {
                ChargingStatus.CHARGING -> R.string.battery_charging
                ChargingStatus.FULL -> R.string.battery_full
                ChargingStatus.NOT_CHARGING -> R.string.battery_not_charging
            },
        )
        val percent = data.levelPercent?.let { "$it%" } ?: "—"
        val description = stringResource(R.string.battery_content_description, percent, status)
        Column(
            modifier.semantics(mergeDescendants = true) { contentDescription = description },
            verticalArrangement = Arrangement.Center,
        ) {
            PanelLabel(status.uppercase(), style, accent = data.status.isPowered)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelValue(percent, style)
                Spacer(Modifier.width(12.dp))
                BatteryGlyph(data, style)
            }
        }
    }

    /** Thin charge ring instead of an icon: reads as part of the typography. */
    @Composable
    private fun BatteryGlyph(data: BatterySnapshot, style: StandByStyle) {
        val level = (data.levelPercent ?: 0) / 100f
        val scale = com.nitestand.standbyclock.ui.standby.LocalPanelScale.current
        Canvas(Modifier.size(24.dp * scale)) {
            val stroke = 2.2.dp.toPx() * scale
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(style.secondary.copy(alpha = style.secondary.alpha * 0.35f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                if (data.status.isPowered) style.accent else style.primary,
                -90f, 360f * level.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
    }
}

// ---------------------------------------------------------------- Next alarm

data class AlarmWidgetData(val time: String?, val date: LocalDate?, val today: LocalDate, val locale: Locale)

object NextAlarmWidget : StandByWidget<AlarmWidgetData> {
    override val id = WidgetId.NEXT_ALARM
    override val titleRes = R.string.widget_alarm
    override fun refreshPolicy(settings: StandBySettings) = RefreshPolicy.EventDriven
    override fun isEnabled(settings: StandBySettings) = settings.showAlarmWidget

    override fun observe(sources: WidgetDataSources, settings: StandBySettings): Flow<AlarmWidgetData> =
        combine(
            sources.nextAlarm,
            sources.ticker.ticks(TickInterval.MINUTE).map { it.toLocalDate() }.distinctUntilChanged(),
        ) { alarm: NextAlarm?, today: LocalDate ->
            val formatter = sources.timeFormat.formatter(settings.hourFormat, showSeconds = false)
            val zoned = alarm?.triggerAt?.atZone(ZoneId.systemDefault())
            AlarmWidgetData(
                time = zoned?.let(formatter::formatTime),
                date = zoned?.toLocalDate(),
                today = today,
                locale = sources.timeFormat.locale(),
            )
        }

    @Composable
    override fun Content(data: AlarmWidgetData, style: StandByStyle, modifier: Modifier) {
        val dayLabel = when (data.date) {
            null -> null
            data.today -> stringResource(R.string.alarm_today)
            data.today.plusDays(1) -> stringResource(R.string.alarm_tomorrow)
            else -> data.date.dayOfWeek.getDisplayName(TextStyle.SHORT, data.locale)
        }
        val title = stringResource(R.string.widget_alarm)
        val none = stringResource(R.string.alarm_none)
        val description = if (data.time == null) none else "$title ${dayLabel.orEmpty()} ${data.time}"
        Column(
            modifier.semantics(mergeDescendants = true) { contentDescription = description },
            verticalArrangement = Arrangement.Center,
        ) {
            PanelLabel(
                if (dayLabel != null) "${title.uppercase()} · ${dayLabel.uppercase()}" else title.uppercase(),
                style,
                accent = data.time != null,
            )
            if (data.time != null) PanelValue(data.time, style) else PanelLabel(none, style)
        }
    }
}

// ---------------------------------------------------------------- Weather (Open-Meteo)

data class WeatherWidgetData(val state: WeatherState, val fahrenheit: Boolean)

/** Current weather for the chosen city or the phone's approximate location. */
object WeatherWidget : StandByWidget<WeatherWidgetData> {
    override val id = WidgetId.WEATHER
    override val titleRes = R.string.widget_weather
    override fun refreshPolicy(settings: StandBySettings) = RefreshPolicy.Periodic(minutes = 30)
    override fun isEnabled(settings: StandBySettings) = settings.showWeatherWidget

    override fun observe(sources: WidgetDataSources, settings: StandBySettings): Flow<WeatherWidgetData> =
        sources.weather.observe(settings.weatherRequest()).map { WeatherWidgetData(it, usesFahrenheit(sources.timeFormat.locale())) }

    private fun usesFahrenheit(locale: Locale) = locale.country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

    private fun format(celsius: Double, fahrenheit: Boolean): String =
        if (fahrenheit) "${(celsius * 9 / 5 + 32).roundToInt()}°" else "${celsius.roundToInt()}°"

    @Composable
    override fun Content(data: WeatherWidgetData, style: StandByStyle, modifier: Modifier) {
        val report = data.state.report
        val title = stringResource(R.string.widget_weather)
        val place = report?.placeName?.takeIf { it.isNotBlank() } ?: if (report != null) stringResource(R.string.weather_here) else title
        val condition = report?.condition?.let { stringResource(it.labelRes()) }
        val message = when (data.state.status) {
            WeatherState.Status.NO_LOCATION -> stringResource(R.string.weather_set_location)
            WeatherState.Status.NO_PERMISSION -> stringResource(R.string.weather_allow_location)
            WeatherState.Status.LOADING -> "—"
            WeatherState.Status.ERROR -> stringResource(R.string.weather_unavailable)
            WeatherState.Status.OK -> stringResource(R.string.weather_unavailable)
        }
        val highLow = report?.let { "H ${format(it.highCelsius, data.fahrenheit)}  L ${format(it.lowCelsius, data.fahrenheit)}" }
        Column(
            modifier.semantics(mergeDescendants = true) {
                contentDescription = if (report == null) {
                    "$title. $message"
                } else {
                    "$title $place ${format(report.temperatureCelsius, data.fahrenheit)} $condition, $highLow"
                }
            },
            verticalArrangement = Arrangement.Center,
        ) {
            PanelLabel(
                if (report?.isSample == true) "$place · ${stringResource(R.string.weather_sample)}" else place,
                style,
                accent = true,
            )
            if (report == null) {
                PanelLabel(message, style)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    PanelValue(format(report.temperatureCelsius, data.fahrenheit), style)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        PanelLabel(condition.orEmpty(), style)
                        highLow?.let { PanelLabel(it, style) }
                    }
                }
            }
        }
    }

    private fun WeatherCondition.labelRes(): Int = when (this) {
        WeatherCondition.CLEAR -> R.string.weather_clear
        WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
        WeatherCondition.CLOUDY -> R.string.weather_cloudy
        WeatherCondition.FOG -> R.string.weather_fog
        WeatherCondition.DRIZZLE -> R.string.weather_drizzle
        WeatherCondition.RAIN -> R.string.weather_rain
        WeatherCondition.SNOW -> R.string.weather_snow
        WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
    }
}
