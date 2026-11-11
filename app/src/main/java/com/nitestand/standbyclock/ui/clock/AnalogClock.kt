package com.nitestand.standbyclock.ui.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.widgets.ClockFaceData
import kotlin.math.min

/** Theme 4 — large analog dial with hour, minute and optional second hand. */
@Composable
fun AnalogClock(data: ClockFaceData, style: StandByStyle) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val diameter = min(maxWidth.value, maxHeight.value).dp * 0.94f
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            val t = data.time
            val hourAngle = (t.hour % 12 + t.minute / 60f) * 30f
            val minuteAngle = (t.minute + if (data.showSeconds) t.second / 60f else 0f) * 6f
            val secondAngle = t.second * 6f
            if (data.showDate) {
                Text(
                    data.text.shortDate,
                    modifier = Modifier.offset(y = diameter * 0.2f),
                    style = TextStyle(
                        fontSize = (diameter * 0.06f).asFixedSp(),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.15.em,
                        color = style.accent,
                    ),
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawTicks(r, style)
                hand(hourAngle, r * 0.5f, r * 0.06f, style.primary)
                hand(minuteAngle, r * 0.78f, r * 0.035f, style.primary)
                if (data.showSeconds) {
                    hand(secondAngle, r * 0.86f, r * 0.012f, style.accent, tail = r * 0.14f)
                }
                drawCircle(style.accent, r * 0.035f, center)
                drawCircle(style.background, r * 0.014f, center)
            }
        }
    }
}

private fun DrawScope.drawTicks(r: Float, style: StandByStyle) {
    for (i in 0 until 60) {
        val major = i % 5 == 0
        rotate(i * 6f, center) {
            val outer = center.y - r * 0.98f
            val inner = center.y - r * if (major) 0.86f else 0.93f
            drawLine(
                color = if (major) style.primary.copy(alpha = 0.9f) else style.secondary.copy(alpha = 0.45f),
                start = Offset(center.x, outer),
                end = Offset(center.x, inner),
                strokeWidth = if (major) r * 0.022f else r * 0.008f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.hand(angle: Float, length: Float, width: Float, color: Color, tail: Float = 0f) {
    rotate(angle, center) {
        drawLine(
            color = color,
            start = Offset(center.x, center.y + tail),
            end = Offset(center.x, center.y - length),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}
