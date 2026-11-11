package com.nitestand.standbyclock.ui.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.widgets.ClockFaceData
import kotlin.math.min

/** Segment masks a,b,c,d,e,f,g for digits 0–9. */
private val DIGIT_SEGMENTS = intArrayOf(
    0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110,
    0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111,
)

private const val DIGIT_ASPECT = 0.56f
private const val DIGIT_GAP = 0.16f
private const val COLON_WIDTH = 0.28f
private const val SLANT = 0.07f

/** Theme 3 — seven-segment LED clock, drawn natively with unlit "ghost" segments. */
@Composable
fun RetroClock(data: ClockFaceData, style: StandByStyle) {
    val lit = style.retro
    val time = data.time
    val hour = data.text.hours.filter(Char::isDigit).let { if (it.isEmpty()) time.hour else it.toInt() }
    val digits = buildList {
        add(if (hour >= 10 || data.text.amPm == null) hour / 10 else -1) // blank leading digit in 12 h mode
        add(hour % 10)
        add(time.minute / 10)
        add(time.minute % 10)
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val secondsFactor = if (data.showSeconds) 2 * DIGIT_ASPECT * 0.45f + DIGIT_GAP else 0f
        val widthFactor = 4 * DIGIT_ASPECT + 3 * DIGIT_GAP + COLON_WIDTH + secondsFactor + SLANT
        val digitHeight: Dp = min(
            (maxHeight * if (data.showDate) 0.5f else 0.62f).value,
            (maxWidth / widthFactor * 0.94f).value,
        ).dp
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                SegmentDisplay(digits, colonAfter = 1, height = digitHeight, lit = lit, ghost = lit.copy(alpha = if (style.night) 0.04f else 0.07f))
                if (data.showSeconds) {
                    Spacer(Modifier.size(digitHeight * DIGIT_GAP))
                    SegmentDisplay(
                        listOf(time.second / 10, time.second % 10),
                        colonAfter = -1,
                        height = digitHeight * 0.45f,
                        lit = lit.copy(alpha = 0.8f),
                        ghost = lit.copy(alpha = 0.05f),
                    )
                }
            }
            if (data.showDate || data.text.amPm != null) {
                Spacer(Modifier.height(digitHeight * 0.1f))
                val label = listOfNotNull(data.text.amPm, data.text.shortDate.takeIf { data.showDate }).joinToString("  ·  ")
                Text(
                    label,
                    modifier = Modifier.padding(top = 4.dp),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = (digitHeight * 0.13f).asFixedSp(),
                        letterSpacing = 0.2.em,
                        color = lit.copy(alpha = 0.75f),
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SegmentDisplay(digits: List<Int>, colonAfter: Int, height: Dp, lit: Color, ghost: Color) {
    val widthFactor = digits.size * DIGIT_ASPECT + (digits.size - 1) * DIGIT_GAP +
        (if (colonAfter >= 0) COLON_WIDTH else 0f) + SLANT
    Canvas(Modifier.size(width = height * widthFactor, height = height)) {
        val h = size.height
        val w = h * DIGIT_ASPECT
        var x = 0f
        digits.forEachIndexed { index, digit ->
            drawDigit(x, w, h, if (digit < 0) 0 else DIGIT_SEGMENTS[digit], lit, ghost)
            x += w + h * DIGIT_GAP
            if (index == colonAfter) {
                val cx = x - h * DIGIT_GAP / 2 + h * COLON_WIDTH / 2
                val r = h * 0.045f
                drawCircle(lit, r, Offset(cx + (h - h * 0.33f) * SLANT, h * 0.33f))
                drawCircle(lit, r, Offset(cx + (h - h * 0.67f) * SLANT, h * 0.67f))
                x += h * COLON_WIDTH
            }
        }
    }
}

private fun DrawScope.drawDigit(x0: Float, w: Float, h: Float, mask: Int, lit: Color, ghost: Color) {
    val t = h * 0.1f // segment thickness
    val gap = t * 0.18f
    val left = x0 + t / 2
    val right = x0 + w - t / 2
    val top = t / 2
    val mid = h / 2
    val bottom = h - t / 2

    fun slant(px: Float, py: Float) = Offset(px + (h - py) * SLANT, py)

    fun horizontal(y: Float): Path = Path().apply {
        val a = left + gap
        val b = right - gap
        moveTo(slant(a, y)); lineTo(slant(a + t / 2, y - t / 2)); lineTo(slant(b - t / 2, y - t / 2))
        lineTo(slant(b, y)); lineTo(slant(b - t / 2, y + t / 2)); lineTo(slant(a + t / 2, y + t / 2)); close()
    }

    fun vertical(x: Float, y0: Float, y1: Float): Path = Path().apply {
        val a = y0 + gap
        val b = y1 - gap
        moveTo(slant(x, a)); lineTo(slant(x + t / 2, a + t / 2)); lineTo(slant(x + t / 2, b - t / 2))
        lineTo(slant(x, b)); lineTo(slant(x - t / 2, b - t / 2)); lineTo(slant(x - t / 2, a + t / 2)); close()
    }

    val segments = listOf(
        horizontal(top), // a
        vertical(right, top, mid), // b
        vertical(right, mid, bottom), // c
        horizontal(bottom), // d
        vertical(left, mid, bottom), // e
        vertical(left, top, mid), // f
        horizontal(mid), // g
    )
    segments.forEachIndexed { i, path ->
        drawPath(path, if (mask and (1 shl i) != 0) lit else ghost)
    }
}

private fun Path.moveTo(o: Offset) = moveTo(o.x, o.y)
private fun Path.lineTo(o: Offset) = lineTo(o.x, o.y)
