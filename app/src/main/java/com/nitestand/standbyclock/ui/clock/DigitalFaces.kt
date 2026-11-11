package com.nitestand.standbyclock.ui.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.widgets.ClockFaceData
import com.nitestand.standbyclock.widgets.WorldTime
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

// ------------------------------------------------------------------ shared pieces

/** Small text line (date, city…) sized relative to the clock box, immune to font scaling. */
@Composable
internal fun Caption(
    text: String,
    font: ClockFont,
    weight: Int,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    tracking: Float = 0.02f,
    upper: Boolean = false,
    /** Trim the line box to the glyphs (for very large single-line numbers). */
    tight: Boolean = false,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    Text(
        if (upper) text.uppercase(locale) else text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = font.family(weight),
            fontSize = size.asFixedSp(),
            letterSpacing = tracking.em,
            color = color,
            textAlign = TextAlign.Center,
            // Lining figures: serif faces default to old-style numerals that sit low in dates.
            fontFeatureSettings = "lnum",
            lineHeight = if (tight) 1.0.em else TextUnit.Unspecified,
            lineHeightStyle = if (tight) LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both) else null,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Time centred in the box, with an optional caption under it that never competes with the digits. */
@Composable
private fun TimeWithCaption(
    data: ClockFaceData,
    spec: DigitalSpec,
    colors: TimeColors,
    caption: (@Composable BoxWithConstraintsScope.() -> Unit)?,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val withCaption = caption != null
        TimeText(
            data.text,
            if (withCaption) spec.copy(heightFraction = spec.heightFraction * 0.86f, verticalBias = 0.44f) else spec,
            colors,
            Modifier.fillMaxSize(),
        )
        if (caption != null) caption()
    }
}

private fun Dp.coerceAtMostDp(max: Dp): Dp = if (this > max) max else this

private fun BoxWithConstraintsScope.captionSize(fraction: Float): Dp = (min(maxHeight.value * fraction, maxWidth.value * fraction * 0.6f)).dp

// ------------------------------------------------------------------ Digital

/** Large StandBy digits: huge, light, rounded — the default face. */
private val LargeSpec = DigitalSpec(
    font = ClockFont.ROUNDED, weight = 300, tracking = 0.015f,
    heightFraction = 0.76f, widthFraction = 0.94f,
    secondsScale = 0.27f, secondsWeight = 300, amPmScale = 0.16f, amPmWeight = 600,
)

@Composable
fun DigitalLargeFace(data: ClockFaceData, style: StandByStyle) {
    TimeText(data.text, LargeSpec, TimeColors(style.primary, style.secondary), Modifier.fillMaxSize())
}

private val ColorSpec = DigitalSpec(
    font = ClockFont.ROUNDED, weight = 560, tracking = 0.0f, heightFraction = 0.72f,
    secondsScale = 0.27f, amPmScale = 0.16f, amPmWeight = 750,
)

/** Expressive gradient digits. */
@Composable
fun DigitalColorFace(data: ClockFaceData, style: StandByStyle) {
    TimeWithCaption(
        data, ColorSpec,
        TimeColors(style.primary, lerp(style.gradient.last(), Color.Black, 0.15f), gradient = style.gradient),
        caption = if (data.showDate) {
            {
                Caption(
                    data.dateText, ClockFont.ROUNDED, 650, captionSize(0.075f), style.gradient.first(),
                    Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.06f),
                )
            }
        } else {
            null
        },
    )
}

private val MinimalSpec = DigitalSpec(
    font = ClockFont.GEOMETRIC, weight = 200, tracking = 0.03f, heightFraction = 0.5f,
    secondsScale = 0.26f, amPmScale = 0.15f, amPmWeight = 400, amPmTracking = 0.14f,
)

/** Thin geometric numerals with a lot of negative space. */
@Composable
fun MinimalFace(data: ClockFaceData, style: StandByStyle) {
    TimeWithCaption(
        data, MinimalSpec, TimeColors(style.primary, style.secondary),
        caption = if (data.showDate) {
            {
                Caption(
                    data.dateText, ClockFont.GEOMETRIC, 400, captionSize(0.045f), style.secondary,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.14f),
                    tracking = 0.22f, upper = true,
                )
            }
        } else {
            null
        },
    )
}

private val BoldSpec = DigitalSpec(
    font = ClockFont.ROUNDED, weight = 760, tracking = -0.01f, heightFraction = 0.7f,
    secondsScale = 0.27f, amPmScale = 0.16f, amPmWeight = 850,
)

/** Rounded bold digits (the v1 "Classic" face, refined). */
@Composable
fun RoundedBoldFace(data: ClockFaceData, style: StandByStyle) {
    TimeWithCaption(
        data, BoldSpec, TimeColors(style.primary, style.secondary),
        caption = if (data.showDate) {
            {
                Caption(
                    data.dateText, ClockFont.ROUNDED, 750, captionSize(0.07f), style.accent,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.06f),
                )
            }
        } else {
            null
        },
    )
}

private val WorldSpec = DigitalSpec(
    font = ClockFont.ROUNDED, weight = 300, tracking = 0.015f, heightFraction = 0.82f,
    secondsScale = 0.27f, amPmScale = 0.16f, amPmWeight = 600,
)

/** Time in a chosen city, with the city label and an optional second time zone underneath. */
@Composable
fun WorldFace(data: ClockFaceData, style: StandByStyle) {
    val place = data.primaryPlace
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val label = captionSize(0.075f)
        // In portrait keep label, time and second zone together instead of spreading them out.
        val portrait = maxHeight > maxWidth
        val boxHeight = if (portrait) maxWidth * 0.75f else maxHeight
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().height(boxHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(boxHeight * 0.04f))
            Caption(place?.label ?: "", ClockFont.ROUNDED, 760, label, style.accent, tracking = 0.28f, upper = true)
            TimeText(data.text, WorldSpec, TimeColors(style.primary, style.secondary), Modifier.fillMaxWidth().weight(1f))
            val secondary = data.secondaryPlace
            if (secondary != null) {
                Caption(secondaryLine(secondary), ClockFont.ROUNDED, 500, label * 0.85f, style.secondary, tracking = 0.06f)
            } else if (data.showDate) {
                Caption(data.dateText, ClockFont.ROUNDED, 500, label * 0.85f, style.secondary)
            }
            Spacer(Modifier.height(boxHeight * 0.05f))
        }
    }
}

private fun secondaryLine(place: WorldTime): String {
    val t = place.text
    val time = t.hoursAndMinutes + (t.amPm?.let { " $it" } ?: "")
    val sign = if (place.offsetMinutes < 0) "−" else "+"
    val h = abs(place.offsetMinutes) / 60
    val m = abs(place.offsetMinutes) % 60
    val offset = when {
        place.offsetMinutes == 0 -> ""
        m == 0 -> "$sign${h}h"
        else -> "$sign${h}h ${m}m"
    }
    val day = when (place.dayOffset) {
        1 -> "  (+1 day)"
        -1 -> "  (−1 day)"
        else -> ""
    }
    return listOf(place.label.uppercase(Locale.getDefault()), time, offset + day).filter { it.isNotBlank() }.joinToString("   ")
}

// ------------------------------------------------------------------ Expressive

private val BubbleSpec = DigitalSpec(
    font = ClockFont.ROUNDED, weight = 950, tracking = 0.01f, heightFraction = 0.72f,
    amPmScale = 0.16f, amPmWeight = 950,
)

/** Playful heavy rounded numerals with alternating soft colours. */
@Composable
fun BubbleFace(data: ClockFaceData, style: StandByStyle) {
    val a = style.gradient.first()
    val b = style.gradient.last()
    val digits = listOf(a, lerp(a, b, 0.5f), b, lerp(b, a, 0.35f))
    TimeText(
        data.text, BubbleSpec,
        TimeColors(style.primary, style.secondary, perDigit = digits, separator = lerp(a, b, 0.5f).copy(alpha = 0.75f)),
        Modifier.fillMaxSize(),
    )
}

private val SerifSpec = DigitalSpec(
    font = ClockFont.SERIF, weight = 480, tracking = 0.0f, heightFraction = 0.66f,
    secondsScale = 0.28f, amPmScale = 0.16f, amPmWeight = 600, amPmTracking = 0.12f, features = "lnum",
)

/** High-contrast display serif, editorial and calm. */
@Composable
fun SerifFace(data: ClockFaceData, style: StandByStyle) {
    TimeWithCaption(
        data, SerifSpec, TimeColors(style.primary, style.secondary),
        caption = if (data.showDate) {
            {
                Caption(
                    data.dateText, ClockFont.SERIF, 500, captionSize(0.06f), style.accent,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.07f),
                    tracking = 0.12f, upper = true,
                )
            }
        } else {
            null
        },
    )
}

private val LineSpec = DigitalSpec(
    font = ClockFont.GEOMETRIC, weight = 100, tracking = 0.04f, heightFraction = 0.52f,
    secondsScale = 0.26f, secondsWeight = 200, amPmScale = 0.15f, amPmWeight = 300, amPmTracking = 0.16f,
    verticalBias = 0.43f,
)

/** Hairline numerals over a thin line that fills as the hour passes. */
@Composable
fun LineFace(data: ClockFaceData, style: StandByStyle) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        TimeText(data.text, LineSpec, TimeColors(style.primary, style.secondary), Modifier.fillMaxSize())
        val progress = (data.time.minute + data.time.second / 60f) / 60f
        val trackWidth = maxWidth * 0.56f
        val boxHeight = maxHeight
        val dateSize = captionSize(0.045f)
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.width(trackWidth).height((boxHeight * 0.03f).coerceAtMostDp(12.dp))) {
                val y = size.height / 2
                val stroke = minOf(1.2.dp.toPx(), size.height / 3)
                drawLine(style.secondary.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
                val x = size.width * progress
                drawLine(style.primary.copy(alpha = 0.8f), Offset(0f, y), Offset(x, y), stroke, StrokeCap.Round)
                drawCircle(style.accent, minOf(3.dp.toPx(), size.height / 2), Offset(x, y))
            }
            if (data.showDate) {
                Spacer(Modifier.height(boxHeight * 0.025f))
                Caption(data.dateText, ClockFont.GEOMETRIC, 300, dateSize, style.secondary, tracking = 0.2f, upper = true)
            }
        }
    }
}

private val CalendarTimeSpec = DigitalSpec(
    font = ClockFont.ROUNDED, weight = 300, tracking = 0.01f, heightFraction = 0.4f, widthFraction = 0.9f,
    secondsScale = 0.27f, amPmScale = 0.17f, amPmWeight = 650,
)

/** Calendar page: weekday and a large day number, next to the time. */
@Composable
fun CalendarFace(data: ClockFaceData, style: StandByStyle) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight * 1.2f
        val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
        val weekday = data.time.dayOfWeek.getDisplayName(DayTextStyle.FULL, locale)
        val month = data.time.month.getDisplayName(DayTextStyle.FULL_STANDALONE, locale)
        val unit = min(maxHeight.value, if (landscape) maxWidth.value / 2 else maxWidth.value).dp
        // Calendar-page typography: coloured weekday, large neutral day number, quiet month.
        val ink = style.hands
        val page: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                // The number's line box carries ~0.15 em of space above/below the figures; the
                // offsets pull weekday and month in so the group reads as one calendar page.
                Caption(
                    weekday, ClockFont.ROUNDED, 800, (unit * 0.085f).coerceAtMostDp(40.dp), style.primary,
                    Modifier.offset(y = unit * 0.07f), tracking = 0.14f, upper = true,
                )
                Caption(data.time.dayOfMonth.toString(), ClockFont.ROUNDED, 300, unit * 0.5f, ink, tracking = -0.02f, tight = true)
                Caption(
                    month, ClockFont.ROUNDED, 600, (unit * 0.065f).coerceAtMostDp(30.dp), style.secondary,
                    Modifier.offset(y = -unit * 0.06f), tracking = 0.06f,
                )
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                page(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight(0.42f).padding(vertical = 2.dp)) {
                    Canvas(Modifier.fillMaxSize()) { drawRect(style.secondary.copy(alpha = 0.25f)) }
                }
                TimeText(data.text, CalendarTimeSpec.copy(heightFraction = 0.32f), TimeColors(ink, style.secondary), Modifier.weight(1.15f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                page(Modifier.weight(1.2f).fillMaxWidth())
                TimeText(data.text, CalendarTimeSpec.copy(heightFraction = 0.5f), TimeColors(ink, style.secondary), Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

private val AmbientSpec = DigitalSpec(
    font = ClockFont.ROUNDED, weight = 200, tracking = 0.02f, heightFraction = 0.3f,
    secondsScale = 0.27f, amPmScale = 0.16f, amPmWeight = 400,
)

/** Very small and dim, for dark bedrooms. */
@Composable
fun AmbientFace(data: ClockFaceData, style: StandByStyle) {
    TimeWithCaption(
        data, AmbientSpec, TimeColors(style.primary, style.secondary),
        caption = if (data.showDate) {
            {
                Caption(
                    data.dateText, ClockFont.ROUNDED, 500, captionSize(0.04f), style.secondary,
                    Modifier.align(Alignment.Center).padding(top = maxHeight * 0.26f),
                    tracking = 0.1f,
                )
            }
        } else {
            null
        },
    )
}
