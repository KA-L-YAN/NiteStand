package com.nitestand.standbyclock.ui.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nitestand.standbyclock.ui.theme.ClockDigits
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.widgets.ClockFaceData
import kotlin.math.min

/**
 * Converts a physical size to sp such that the rendered text is exactly that size regardless
 * of the user's font scale. The clock is sized to the screen, so it must not grow beyond it.
 */
@Composable
internal fun Dp.asFixedSp(): TextUnit = with(LocalDensity.current) { this@asFixedSp.toSp() }

private val TightLines = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

@Composable
private fun DigitalLayout(
    data: ClockFaceData,
    style: StandByStyle,
    weight: FontWeight,
    color: Color,
    heightFraction: Float,
    dateScale: Float,
    dateColor: Color,
    letterSpacingEm: Float,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val text = data.text
        val glyphs = text.hoursAndMinutes.length + (text.seconds?.let { it.length * 0.45f } ?: 0f)
        val byWidth = maxWidth / (glyphs * 0.6f + 0.4f)
        val byHeight = maxHeight * (if (data.showDate) heightFraction * 0.85f else heightFraction)
        val digitSize = min(byWidth.value, byHeight.value).dp
        val digitStyle = ClockDigits.merge(
            TextStyle(
                fontSize = digitSize.asFixedSp(),
                lineHeight = 1.0.em,
                fontWeight = weight,
                color = color,
                letterSpacing = letterSpacingEm.em,
                lineHeightStyle = TightLines,
            ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text.hoursAndMinutes, style = digitStyle, maxLines = 1, softWrap = false)
                val small = digitStyle.copy(fontSize = (digitSize * 0.3f).asFixedSp(), color = style.secondary, letterSpacing = 0.em)
                Column(Modifier.padding(start = digitSize * 0.06f, bottom = digitSize * 0.1f)) {
                    text.amPm?.let { Text(it, style = small, maxLines = 1) }
                    text.seconds?.let { Text(it, style = small.copy(color = style.accent), maxLines = 1) }
                }
            }
            if (data.showDate) {
                Text(
                    text.shortDate,
                    modifier = Modifier.padding(top = digitSize * 0.06f),
                    style = TextStyle(
                        fontSize = (digitSize * dateScale).asFixedSp(),
                        fontWeight = FontWeight.Medium,
                        color = dateColor,
                        letterSpacing = 0.18.em,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Theme 1 — large white digits with a small date, StandBy's signature look. */
@Composable
fun ClassicClock(data: ClockFaceData, style: StandByStyle) = DigitalLayout(
    data, style,
    weight = FontWeight.SemiBold,
    color = style.primary,
    heightFraction = 0.62f,
    dateScale = 0.14f,
    dateColor = style.accent,
    letterSpacingEm = -0.03f,
)

/** Theme 2 — thin, airy typography and a very small date. */
@Composable
fun MinimalClock(data: ClockFaceData, style: StandByStyle) = DigitalLayout(
    data, style,
    weight = FontWeight.Thin,
    color = style.primary,
    heightFraction = 0.7f,
    dateScale = 0.09f,
    dateColor = style.secondary,
    letterSpacingEm = -0.02f,
)

/** Theme 5 — small, very dim clock for darkened bedrooms. */
@Composable
fun AmbientClock(data: ClockFaceData, style: StandByStyle) = DigitalLayout(
    data, style,
    weight = FontWeight.ExtraLight,
    color = style.primary,
    heightFraction = 0.32f,
    dateScale = 0.12f,
    dateColor = style.secondary,
    letterSpacingEm = 0f,
)
