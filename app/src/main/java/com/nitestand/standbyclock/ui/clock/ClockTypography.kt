package com.nitestand.standbyclock.ui.clock

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.res.ResourcesCompat
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.clock.ClockText

/**
 * Bundled, openly licensed typefaces (SIL Open Font License 1.1, see assets/licenses):
 * - ROUNDED: Nunito (variable 200–1000) — rounded terminals, the closest open match to the
 *   rounded system face used by StandBy-style clocks
 * - GEOMETRIC: Outfit (variable 100–900) — thin geometric numerals
 * - SERIF: Playfair Display (variable 400–900) — high-contrast display serif
 */
enum class ClockFont(@FontRes val res: Int, val minWeight: Int, val maxWeight: Int) {
    ROUNDED(R.font.nunito_variable, 200, 1000),
    GEOMETRIC(R.font.outfit_variable, 100, 900),
    SERIF(R.font.playfair_display_variable, 400, 900),
    ;

    fun clamp(weight: Int) = weight.coerceIn(minWeight, maxWeight)

    /** Compose font family pinned to one weight of the variable font. */
    fun family(weight: Int): FontFamily {
        val w = clamp(weight)
        return FontFamily(Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))))
    }
}

/** Converts a physical size to sp so that text keeps that exact size regardless of font scale. */
@Composable
internal fun Dp.asFixedSp(): TextUnit = with(LocalDensity.current) { this@asFixedSp.toSp() }

@Composable
internal fun rememberTypeface(font: ClockFont): Typeface {
    val context = LocalContext.current
    return remember(font) {
        runCatching { ResourcesCompat.getFont(context, font.res) }.getOrNull() ?: Typeface.DEFAULT
    }
}

/** How a small element (seconds, AM/PM) attaches to the main digits. */
enum class Attach { TOP, BASELINE }

/** Typographic recipe for one digital face. All sizes are relative (em of the main digits). */
@Immutable
data class DigitalSpec(
    val font: ClockFont,
    val weight: Int,
    /** Letter spacing of the main digits, in em. */
    val tracking: Float = 0f,
    /** Max height of the digit band as a fraction of the available height. */
    val heightFraction: Float = 0.7f,
    /** Max width of the whole group as a fraction of the available width. */
    val widthFraction: Float = 0.92f,
    val secondsScale: Float = 0.3f,
    val secondsWeight: Int = weight,
    val amPmScale: Float = 0.2f,
    val amPmWeight: Int = weight + 150,
    val amPmTracking: Float = 0.08f,
    /** Attachment used when only one of seconds / AM-PM is shown (with both: AM/PM top, seconds baseline). */
    val singleSmallAttach: Attach = Attach.TOP,
    /** Gap between the minutes and the small column, in em. */
    val smallGap: Float = 0.07f,
    /** OpenType features, e.g. tabular lining figures so digits never shift. */
    val features: String = "tnum, lnum",
    /** Vertical position of the digit band's centre within the box (0.5 = optical centre). */
    val verticalBias: Float = 0.5f,
)

/** Colouring for [TimeText]: solid, two-stop gradient, or per-digit colours. */
@Immutable
data class TimeColors(
    val main: Color,
    val small: Color,
    val gradient: List<Color>? = null,
    val perDigit: List<Color>? = null,
    /** Colour of the separator when [perDigit] is used. */
    val separator: Color? = null,
)

/**
 * Draws "hh:mm" (+ optional seconds / AM-PM) as large as the box allows, centred on the actual
 * glyph bounds of the digits (optical centring), with the colon raised to the middle of the
 * digits. Drawn with a native Paint on a Canvas: one draw call per element, no text layout
 * objects, so per-second updates stay cheap.
 */
@Composable
fun TimeText(text: ClockText, spec: DigitalSpec, colors: TimeColors, modifier: Modifier = Modifier) {
    val typeface = rememberTypeface(spec.font)
    val paints = remember(typeface, spec) { TimePaints(typeface, spec) }
    Canvas(modifier) {
        val main = paints.main
        val small = paints.small
        val amPmPaint = paints.amPm
        val bounds = paints.bounds

        // 1) Measure at a reference size.
        val ref = 100f
        main.textSize = ref
        main.getTextBounds(DIGITS, 0, DIGITS.length, bounds)
        val digitHeightRef = bounds.height().toFloat().coerceAtLeast(1f)
        val hm = text.hours + text.separator + text.minutes
        val mainWidthRef = main.measureText(hm)
        small.textSize = ref * spec.secondsScale
        amPmPaint.textSize = ref * spec.amPmScale
        val secondsWidthRef = text.seconds?.let { small.measureText(it) } ?: 0f
        val amPmWidthRef = text.amPm?.let { amPmPaint.measureText(it.uppercase()) } ?: 0f
        val columnRef = maxOf(secondsWidthRef, amPmWidthRef)
        val groupRef = mainWidthRef + if (columnRef > 0f) ref * spec.smallGap + columnRef else 0f

        // 2) Scale to fit height and width.
        val scale = minOf(
            size.height * spec.heightFraction / digitHeightRef,
            size.width * spec.widthFraction / groupRef,
        ).coerceAtLeast(0.01f)
        val textSize = ref * scale
        main.textSize = textSize
        small.textSize = textSize * spec.secondsScale
        amPmPaint.textSize = textSize * spec.amPmScale

        // 3) Optical vertical centring on the digits' real bounds.
        main.getTextBounds(DIGITS, 0, DIGITS.length, bounds)
        val top = bounds.top.toFloat()
        val bottom = bounds.bottom.toFloat()
        val baseline = size.height * spec.verticalBias - (top + bottom) / 2f
        val groupWidth = groupRef * scale
        var x = (size.width - groupWidth) / 2f

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val mainWidth = mainWidthRef * scale
            main.shader = colors.gradient?.takeIf { it.size >= 2 }?.let { g ->
                LinearGradient(x, baseline + top, x + mainWidth, baseline + bottom, g[0].toArgb(), g[1].toArgb(), Shader.TileMode.CLAMP)
            }
            main.color = colors.main.toArgb()

            fun drawRun(run: String, startIndex: Int) {
                if (colors.perDigit.isNullOrEmpty()) {
                    nc.drawText(run, x, baseline, main)
                    x += main.measureText(run)
                } else {
                    run.forEachIndexed { i, ch ->
                        main.color = colors.perDigit[(startIndex + i) % colors.perDigit.size].toArgb()
                        val s = ch.toString()
                        nc.drawText(s, x, baseline, main)
                        x += main.measureText(s)
                    }
                }
            }

            drawRun(text.hours, 0)
            // Separator: a colon is centred on the digit band; other separators stay on the baseline.
            val sepShift = if (text.separator == ":") {
                main.getTextBounds(":", 0, 1, bounds)
                (top + bottom) / 2f - (bounds.top + bounds.bottom) / 2f
            } else {
                0f
            }
            val sepColor = main.color
            if (!colors.perDigit.isNullOrEmpty()) main.color = (colors.separator ?: colors.small).toArgb()
            nc.drawText(text.separator, x, baseline + sepShift, main)
            x += main.measureText(text.separator)
            main.color = sepColor
            drawRun(text.minutes, text.hours.length)
            main.shader = null

            if (columnRef > 0f) {
                x += textSize * spec.smallGap
                val both = text.seconds != null && text.amPm != null
                text.amPm?.let { marker ->
                    val label = marker.uppercase()
                    amPmPaint.color = colors.small.toArgb()
                    amPmPaint.getTextBounds(label, 0, label.length, bounds)
                    val attach = if (both) Attach.TOP else spec.singleSmallAttach
                    val y = if (attach == Attach.TOP) baseline + top - bounds.top else baseline
                    nc.drawText(label, x, y, amPmPaint)
                }
                text.seconds?.let { seconds ->
                    small.color = colors.small.toArgb()
                    small.getTextBounds(DIGITS, 0, DIGITS.length, bounds)
                    val attach = if (both) Attach.BASELINE else spec.singleSmallAttach
                    val y = if (attach == Attach.TOP) baseline + top - bounds.top else baseline
                    nc.drawText(seconds, x, y, small)
                }
            }
        }
    }
}

private const val DIGITS = "0123456789"

/** Paint objects reused across frames (no allocation per tick). */
private class TimePaints(typeface: Typeface, spec: DigitalSpec) {
    val bounds = Rect()
    val main = paint(typeface, spec.font.clamp(spec.weight), spec.tracking, spec.features)
    val small = paint(typeface, spec.font.clamp(spec.secondsWeight), spec.tracking, spec.features)
    val amPm = paint(typeface, spec.font.clamp(spec.amPmWeight), spec.amPmTracking, "")

    private fun paint(typeface: Typeface, weight: Int, tracking: Float, features: String) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            this.typeface = typeface
            fontVariationSettings = "'wght' $weight"
            letterSpacing = tracking
            if (features.isNotEmpty()) fontFeatureSettings = features.split(",").joinToString(",") { "'${it.trim()}'" }
        }
}
