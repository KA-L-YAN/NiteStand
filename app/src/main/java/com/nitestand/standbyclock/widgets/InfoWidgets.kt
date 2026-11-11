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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
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
import com.nitestand.standbyclock.weather.WeatherReport
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

    @Composable
    private fun BatteryGlyph(data: BatterySnapshot, style: StandByStyle) {
        val level = (data.levelPercent ?: 0) / 100f
        Canvas(Modifier.size(width = 40.dp, height = 20.dp)) {
            val stroke = 2.dp.toPx()
            val tip = 3.dp.toPx()
            val bodyW = size.width - tip
            drawRoundRect(
                color = style.secondary,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(bodyW - stroke, size.height - stroke),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(stroke),
            )
            drawRoundRect(
                color = style.secondary,
                topLeft = Offset(bodyW, size.height * 0.32f),
                size = Size(tip, size.height * 0.36f),
                cornerRadius = CornerRadius(1.5.dp.toPx()),
            )
            val inset = stroke * 2
            val fillW = (bodyW - inset * 2) * level.coerceIn(0f, 1f)
            drawRoundRect(
                color = if (data.status.isPowered) style.accent else style.primary,
                topLeft = Offset(inset, inset),
                size = Size(fillW, size.height - inset * 2),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            if (data.status == ChargingStatus.CHARGING) {
                val bolt = Path().apply {
                    val cx = bodyW / 2
                    val h = size.height
                    moveTo(cx + h * 0.08f, h * 0.12f)
                    lineTo(cx - h * 0.22f, h * 0.56f)
                    lineTo(cx - h * 0.02f, h * 0.56f)
                    lineTo(cx - h * 0.08f, h * 0.88f)
                    lineTo(cx + h * 0.22f, h * 0.44f)
                    lineTo(cx + h * 0.02f, h * 0.44f)
                    close()
                }
                drawPath(bolt, color = style.background)
            }
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

// ---------------------------------------------------------------- Weather (placeholder)

data class WeatherWidgetData(val report: WeatherReport?, val fahrenheit: Boolean)

object WeatherWidget : StandByWidget<WeatherWidgetData> {
    override val id = WidgetId.WEATHER
    override val titleRes = R.string.widget_weather
    override fun refreshPolicy(settings: StandBySettings) = RefreshPolicy.Periodic(minutes = 30)
    override fun isEnabled(settings: StandBySettings) = settings.showWeatherWidget

    override fun observe(sources: WidgetDataSources, settings: StandBySettings): Flow<WeatherWidgetData> =
        sources.weather.observe().map { WeatherWidgetData(it, usesFahrenheit(sources.timeFormat.locale())) }

    private fun usesFahrenheit(locale: Locale) = locale.country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

    private fun format(celsius: Double, fahrenheit: Boolean): String =
        if (fahrenheit) "${(celsius * 9 / 5 + 32).roundToInt()}°" else "${celsius.roundToInt()}°"

    @Composable
    override fun Content(data: WeatherWidgetData, style: StandByStyle, modifier: Modifier) {
        val report = data.report
        val condition = report?.condition?.let { stringResource(it.labelRes()) }
        val sample = stringResource(R.string.weather_sample)
        val title = stringResource(R.string.widget_weather)
        Column(
            modifier.semantics(mergeDescendants = true) {
                contentDescription = if (report == null) title else
                    "$title ${format(report.temperatureCelsius, data.fahrenheit)} $condition. $sample"
            },
            verticalArrangement = Arrangement.Center,
        ) {
            PanelLabel(
                if (report?.isSample == true) "${title.uppercase()} · $sample" else title.uppercase(),
                style,
                accent = true,
            )
            if (report == null) {
                PanelLabel(stringResource(R.string.weather_unavailable), style)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    PanelValue(format(report.temperatureCelsius, data.fahrenheit), style)
                    Spacer(Modifier.width(10.dp))
                    PanelLabel(condition.orEmpty(), style)
                }
            }
        }
    }

    private fun WeatherCondition.labelRes(): Int = when (this) {
        WeatherCondition.CLEAR -> R.string.weather_clear
        WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
        WeatherCondition.CLOUDY -> R.string.weather_cloudy
        WeatherCondition.RAIN -> R.string.weather_rain
        WeatherCondition.SNOW -> R.string.weather_snow
        WeatherCondition.FOG -> R.string.weather_fog
    }
}
