package com.nitestand.standbyclock.ui.clock

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.widgets.ClockFaceData
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Hand angles (degrees clockwise from 12) shared by every analog face. */
private class HandAngles(data: ClockFaceData) {
    private val t = data.time
    val hour = (t.hour % 12 + t.minute / 60f) * 30f
    val minute = (t.minute + if (data.showSeconds) t.second / 60f else 0f) * 6f
    val second = t.second * 6f
}

/** Square dial centred in the box, with an optional date line in the lower half. */
@Composable
private fun AnalogScaffold(
    data: ClockFaceData,
    style: StandByStyle,
    dateFont: ClockFont = ClockFont.ROUNDED,
    dateWeight: Int = 650,
    dateTracking: Float = 0.12f,
    dateOffset: Float = 0.2f,
    draw: DrawScope.(radius: Float, angles: HandAngles) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val diameter: Dp = min(maxWidth.value, maxHeight.value).dp * 0.94f
        val angles = HandAngles(data)
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            if (data.showDate) {
                Caption(
                    data.text.shortDate, dateFont, dateWeight, diameter * 0.052f, style.accent,
                    Modifier.offset(y = diameter * dateOffset), tracking = dateTracking,
                )
            }
            Canvas(Modifier.fillMaxSize()) { draw(size.minDimension / 2f, angles) }
        }
    }
}

private fun DrawScope.polar(angleDeg: Float, radius: Float): Offset {
    val a = Math.toRadians(angleDeg.toDouble() - 90.0)
    return Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
}

private fun DrawScope.ticks(count: Int, outer: Float, inner: Float, width: Float, color: Color, skipEvery: Int = 0) {
    for (i in 0 until count) {
        if (skipEvery > 0 && i % skipEvery == 0) continue
        val angle = i * 360f / count
        drawLine(color, polar(angle, outer), polar(angle, inner), width, StrokeCap.Round)
    }
}

/** Straight hand with a thin stem near the centre (watch-style). */
private fun DrawScope.stemmedHand(angle: Float, length: Float, width: Float, stem: Float, color: Color) {
    rotate(angle, center) {
        drawLine(color, center, Offset(center.x, center.y - stem), width * 0.38f, StrokeCap.Round)
        drawLine(color, Offset(center.x, center.y - stem), Offset(center.x, center.y - length), width, StrokeCap.Round)
    }
}

private fun DrawScope.simpleHand(angle: Float, length: Float, width: Float, color: Color, tail: Float = 0f) {
    rotate(angle, center) {
        drawLine(color, Offset(center.x, center.y + tail), Offset(center.x, center.y - length), width, StrokeCap.Round)
    }
}

/** Tapered hand: wide at the base, sharp at the tip. */
private fun DrawScope.taperedHand(angle: Float, length: Float, base: Float, color: Color, tail: Float) {
    rotate(angle, center) {
        val path = Path().apply {
            moveTo(center.x - base / 2, center.y + tail)
            lineTo(center.x + base / 2, center.y + tail)
            lineTo(center.x + base * 0.12f, center.y - length)
            lineTo(center.x - base * 0.12f, center.y - length)
            close()
        }
        drawPath(path, color)
    }
}

private fun DrawScope.secondHand(angle: Float, r: Float, color: Color, width: Float = r * 0.011f) {
    simpleHand(angle, r * 0.9f, width, color, tail = r * 0.16f)
}

private fun DrawScope.centerCap(r: Float, color: Color, background: Color) {
    drawCircle(color, r * 0.032f, center)
    drawCircle(background, r * 0.012f, center)
}

/** Draws text centred on a point using a native Paint (reused between frames). */
private class DialText(typeface: Typeface, weight: Int) {
    private val bounds = Rect()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        fontVariationSettings = "'wght' $weight"
        textAlign = Paint.Align.CENTER
    }

    fun DrawScope.draw(text: String, at: Offset, size: Float, color: Color) {
        paint.textSize = size
        paint.color = color.toArgb()
        paint.getTextBounds(text, 0, text.length, bounds)
        drawIntoCanvas { it.nativeCanvas.drawText(text, at.x, at.y - (bounds.top + bounds.bottom) / 2f, paint) }
    }
}

// ------------------------------------------------------------------ faces

/** Analog Classic: rounded numerals 1–12, fine minute track, watch-style hands, accent second hand. */
@Composable
fun AnalogClassicFace(data: ClockFaceData, style: StandByStyle) {
    val typeface = rememberTypeface(ClockFont.ROUNDED)
    val numerals = remember(typeface) { DialText(typeface, 560) }
    AnalogScaffold(data, style, dateOffset = 0.24f) { r, a ->
        ticks(60, r * 0.985f, r * 0.94f, r * 0.008f, style.secondary.copy(alpha = 0.5f), skipEvery = 5)
        ticks(12, r * 0.985f, r * 0.92f, r * 0.016f, style.primary.copy(alpha = 0.85f))
        with(numerals) {
            for (h in 1..12) draw(h.toString(), polar(h * 30f, r * 0.76f), r * 0.16f, style.primary)
        }
        stemmedHand(a.hour, r * 0.5f, r * 0.05f, r * 0.12f, style.hands)
        stemmedHand(a.minute, r * 0.8f, r * 0.036f, r * 0.12f, style.hands)
        if (data.showSeconds) secondHand(a.second, r, style.accent)
        centerCap(r, if (data.showSeconds) style.accent else style.hands, style.background)
    }
}

/** Analog Minimal: twelve hairline marks and two thin hands. */
@Composable
fun AnalogMinimalFace(data: ClockFaceData, style: StandByStyle) {
    AnalogScaffold(data, style) { r, a ->
        ticks(12, r * 0.97f, r * 0.9f, r * 0.012f, style.primary.copy(alpha = 0.55f))
        simpleHand(a.hour, r * 0.52f, r * 0.022f, style.hands)
        simpleHand(a.minute, r * 0.82f, r * 0.015f, style.hands)
        if (data.showSeconds) secondHand(a.second, r, style.accent, width = r * 0.007f)
        drawCircle(style.hands, r * 0.02f, center)
    }
}

/** Analog Modern: geometric dots and bars, bold skeleton hands, coloured accents. */
@Composable
fun AnalogModernFace(data: ClockFaceData, style: StandByStyle) {
    AnalogScaffold(data, style, dateWeight = 800, dateOffset = 0.26f) { r, a ->
        for (h in 0 until 12) {
            val angle = h * 30f
            if (h % 3 == 0) {
                drawLine(style.accent, polar(angle, r * 0.95f), polar(angle, r * 0.8f), r * 0.045f, StrokeCap.Round)
            } else {
                drawCircle(style.primary.copy(alpha = 0.8f), r * 0.028f, polar(angle, r * 0.88f))
            }
        }
        // Skeleton hands: solid stem, hollow (black) inner slot near the tip.
        fun skeleton(angle: Float, length: Float, width: Float) {
            simpleHand(angle, length, width, style.hands)
            rotate(angle, center) {
                drawLine(
                    style.background,
                    Offset(center.x, center.y - length * 0.45f),
                    Offset(center.x, center.y - length + width * 0.6f),
                    width * 0.42f,
                    StrokeCap.Round,
                )
            }
        }
        skeleton(a.hour, r * 0.52f, r * 0.075f)
        skeleton(a.minute, r * 0.8f, r * 0.058f)
        if (data.showSeconds) secondHand(a.second, r, style.accent, width = r * 0.016f)
        drawCircle(style.accent, r * 0.045f, center)
        drawCircle(style.background, r * 0.018f, center)
    }
}

/** Analog Elegant: serif Roman numerals, hairline track and tapered hands. */
@Composable
fun AnalogElegantFace(data: ClockFaceData, style: StandByStyle) {
    val typeface = rememberTypeface(ClockFont.SERIF)
    val numerals = remember(typeface) { DialText(typeface, 500) }
    AnalogScaffold(data, style, dateFont = ClockFont.SERIF, dateWeight = 500, dateTracking = 0.2f, dateOffset = 0.19f) { r, a ->
        drawCircle(style.secondary.copy(alpha = 0.35f), r * 0.985f, center, style = Stroke(r * 0.004f))
        ticks(60, r * 0.96f, r * 0.925f, r * 0.004f, style.secondary.copy(alpha = 0.6f), skipEvery = 5)
        listOf(1, 2, 4, 5, 7, 8, 10, 11).forEach { h ->
            drawLine(style.primary.copy(alpha = 0.8f), polar(h * 30f, r * 0.96f), polar(h * 30f, r * 0.88f), r * 0.009f)
        }
        with(numerals) {
            listOf(12 to "XII", 3 to "III", 6 to "VI", 9 to "IX").forEach { (h, label) ->
                draw(label, polar(h * 30f, r * 0.8f), r * 0.15f, style.primary)
            }
        }
        taperedHand(a.hour, r * 0.5f, r * 0.05f, style.hands, tail = r * 0.08f)
        taperedHand(a.minute, r * 0.78f, r * 0.036f, style.hands, tail = r * 0.1f)
        if (data.showSeconds) {
            simpleHand(a.second, r * 0.88f, r * 0.005f, style.accent, tail = r * 0.2f)
            rotate(a.second, center) { drawCircle(style.accent, r * 0.022f, Offset(center.x, center.y + r * 0.16f)) }
        }
        drawCircle(style.hands, r * 0.022f, center)
    }
}
