package com.nitestand.standbyclock.ui.clock

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
import com.nitestand.standbyclock.ui.motion.LocalMotion
import com.nitestand.standbyclock.ui.motion.Motion

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
    /**
     * OpenType features. Tabular lining figures: every digit has the same advance, so a minute
     * change never shifts the colon or neighbouring digits — the clock is physically stable.
     * (The layout only changes when the digit count changes, e.g. 9:59 → 10:00 in 12-hour mode.)
     */
    val features: String = "tnum, lnum",
    /** Vertical position of the digit band's centre within the box (0.5 = optical centre). */
    val verticalBias: Float = 0.5f,
    /** Draw the colon as two round dots matched to the digits' stroke instead of the font glyph. */
    val drawnColon: Boolean = true,
    /** Space on each side of a drawn colon, in em. */
    val colonGap: Float = 0.085f,
    /** Distance of each colon dot from the digit-band centre, as a fraction of the digit height. */
    val colonSpread: Float = 0.2f,
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
 * Draws "hh:mm" (+ optional seconds / AM-PM) for every digital face.
 *
 * - **Constant size**: scaled to the widest possible time for the font, so the clock never changes
 *   size when the digits change.
 * - **Optical centring** on the real glyph bounds of the digits, not the line box.
 * - **Drawn colon**: two round dots sized from the digits' stroke and centred on the digit band.
 * - **Glyph transitions**: when the minute changes, changed glyphs slide up 0.12 em and fade out
 *   while their successors rise in; unchanged glyphs glide to their new position. All animation
 *   values are read in the draw phase, so animating never recomposes.
 */
@Composable
fun TimeText(text: ClockText, spec: DigitalSpec, colors: TimeColors, modifier: Modifier = Modifier) {
    val typeface = rememberTypeface(spec.font)
    val paints = remember(typeface, spec) { TimePaints(typeface, spec) }
    val motion = LocalMotion.current
    val latest by rememberUpdatedState(text)

    // Transition bookkeeping is updated synchronously during composition, so the very first frame
    // after a minute change already draws the transition at progress 0 (no one-frame flash of the
    // new time before the animation starts).
    val anim = remember { GlyphAnimState(text) }
    if (anim.shown.mainKey() != text.mainKey()) {
        anim.from = anim.shown
        anim.generation++
    }
    anim.shown = text
    if (anim.shownSeconds != text.seconds) {
        if (anim.shownSeconds != null && text.seconds != null) {
            anim.fromSeconds = anim.shownSeconds
            anim.secondsGeneration++
        }
        anim.shownSeconds = text.seconds
    }
    val progress = remember { Animatable(1f) }
    val secondsProgress = remember { Animatable(1f) }
    LaunchedEffect(anim.generation) {
        if (anim.generation == 0) return@LaunchedEffect
        progress.snapTo(0f)
        anim.started = anim.generation
        progress.animateTo(1f, tween(motion.ms(Motion.GLYPH_MS), easing = Motion.Standard))
        anim.from = null
    }
    LaunchedEffect(anim.secondsGeneration) {
        if (anim.secondsGeneration == 0) return@LaunchedEffect
        secondsProgress.snapTo(0f)
        anim.secondsStarted = anim.secondsGeneration
        secondsProgress.animateTo(1f, tween(motion.ms(Motion.SECONDS_MS), easing = Motion.Standard))
        anim.fromSeconds = null
    }

    Canvas(modifier) {
        val current = latest
        val textSize = paints.fitTextSize(current, size.width, size.height)
        paints.setSizes(textSize)
        val band = paints.digitBand()
        val baseline = size.height * spec.verticalBias - (band.first + band.second) / 2f
        val from = anim.from
        val p = if (anim.started != anim.generation) 0f else progress.value
        val newLayout = paints.layout(current, size.width)
        val oldLayout = from?.let { paints.layout(it, size.width) }

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            paints.applyShader(colors, newLayout, baseline, band)
            if (oldLayout == null || p >= 1f) {
                newLayout.main.forEachIndexed { i, g -> paints.drawGlyph(nc, g, i, baseline, band, colors, 1f, 0f) }
            } else {
                val shift = textSize * 0.16f
                // Staggered hand-off: the old glyph leaves in the first ~55 % of the timeline, the
                // new one arrives from ~20 %, so the two barely overlap.
                val outA = (1f - p / 0.55f).coerceIn(0f, 1f)
                val inA = ((p - 0.2f) / 0.8f).coerceIn(0f, 1f)
                GlyphTransition.pair(oldLayout.main.map { it.text }, newLayout.main.map { it.text }).forEach { pair ->
                    val ng = pair.newIndex?.let { newLayout.main[it] }
                    val og = pair.oldIndex?.let { oldLayout.main[it] }
                    if (pair.unchanged && ng != null && og != null) {
                        paints.drawGlyph(nc, ng.copy(x = og.x + (ng.x - og.x) * p), pair.newIndex, baseline, band, colors, 1f, 0f)
                    } else {
                        if (og != null) paints.drawGlyph(nc, og, pair.oldIndex, baseline, band, colors, outA, -shift * p)
                        if (ng != null) paints.drawGlyph(nc, ng, pair.newIndex, baseline, band, colors, inA, shift * (1f - p))
                    }
                }
            }
            paints.clearShader()
            // Small column: AM/PM cross-fades with the main glyphs, seconds with their own short fade.
            val amPmAlpha = if (oldLayout != null && from.amPm != current.amPm) p else 1f
            newLayout.amPm?.let { paints.drawSmall(nc, it, baseline, band, colors.small, amPmAlpha, isAmPm = true) }
            if (oldLayout != null && from.amPm != current.amPm) {
                oldLayout.amPm?.let { paints.drawSmall(nc, it, baseline, band, colors.small, 1f - p, isAmPm = true) }
            }
            newLayout.seconds?.let { g ->
                val sp = if (anim.secondsStarted != anim.secondsGeneration) 0f else secondsProgress.value
                val old = anim.fromSeconds
                if (old != null && sp < 1f) {
                    paints.drawSmall(nc, g.copy(text = old), baseline, band, colors.small, 1f - sp, isAmPm = false)
                    paints.drawSmall(nc, g, baseline, band, colors.small, sp, isAmPm = false)
                } else {
                    paints.drawSmall(nc, g, baseline, band, colors.small, 1f, isAmPm = false)
                }
            }
        }
    }
}

private fun ClockText.mainKey() = hours + separator + minutes + "|" + amPm

/** Plain (non-snapshot) transition bookkeeping for [TimeText]; mutated only during composition/effects. */
private class GlyphAnimState(initial: ClockText) {
    var shown: ClockText = initial
    var from: ClockText? = null
    var generation = 0
    var started = 0
    var shownSeconds: String? = initial.seconds
    var fromSeconds: String? = null
    var secondsGeneration = 0
    var secondsStarted = 0
}

private const val DIGITS = "0123456789"

/** One positioned glyph. [attach] is used by the small column. */
internal data class Glyph(val text: String, val x: Float, val isColon: Boolean = false, val attach: Attach = Attach.BASELINE)

internal class TimeLayout(val main: List<Glyph>, val amPm: Glyph?, val seconds: Glyph?, val start: Float, val end: Float)

/** Paint objects and metrics reused across frames (no allocation per tick beyond the small layout). */
private class TimePaints(typeface: Typeface, private val spec: DigitalSpec) {
    private val bounds = Rect()
    val main = paint(typeface, spec.font.clamp(spec.weight), spec.tracking, spec.features)
    val small = paint(typeface, spec.font.clamp(spec.secondsWeight), spec.tracking, spec.features)
    val amPm = paint(typeface, spec.font.clamp(spec.amPmWeight), spec.amPmTracking, "")
    private var digitTop = 0f
    private var digitBottom = 0f
    private var stroke = 0f

    private fun paint(typeface: Typeface, weight: Int, tracking: Float, features: String) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            this.typeface = typeface
            fontVariationSettings = "'wght' $weight"
            letterSpacing = tracking
            if (features.isNotEmpty()) fontFeatureSettings = features.split(",").joinToString(",") { "'${it.trim()}'" }
        }

    private val drawnColon get() = spec.drawnColon

    private fun colonAdvance(textSize: Float): Float =
        if (drawnColon) stroke * 1.15f + 2f * spec.colonGap * textSize else main.measureText(":")

    /** Text size that fits the widest possible time ("88:88" with this font's widest digit). */
    fun fitTextSize(text: ClockText, width: Float, height: Float): Float {
        val ref = 100f
        setSizes(ref)
        val widest = DIGITS.maxOf { main.measureText(it.toString()) }
        val mainRef = 4 * widest + if (text.separator == ":") colonAdvance(ref) else main.measureText(text.separator)
        val secondsRef = if (text.seconds != null) 2 * DIGITS.maxOf { small.measureText(it.toString()) } else 0f
        val amPmRef = text.amPm?.let { maxOf(amPm.measureText("AM"), amPm.measureText("PM"), amPm.measureText(it.uppercase())) } ?: 0f
        val column = maxOf(secondsRef, amPmRef)
        val groupRef = mainRef + if (column > 0f) ref * spec.smallGap + column else 0f
        val digitHeightRef = (digitBottom - digitTop).coerceAtLeast(1f)
        val scale = minOf(height * spec.heightFraction / digitHeightRef, width * spec.widthFraction / groupRef)
        return (ref * scale).coerceAtLeast(1f)
    }

    fun setSizes(textSize: Float) {
        main.textSize = textSize
        small.textSize = textSize * spec.secondsScale
        amPm.textSize = textSize * spec.amPmScale
        main.getTextBounds(DIGITS, 0, DIGITS.length, bounds)
        digitTop = bounds.top.toFloat()
        digitBottom = bounds.bottom.toFloat()
        // Colon dot size follows the visual stroke weight of the digits: thin faces get fine dots,
        // heavy faces round, full ones. Derived from digit height so it is independent of glyph quirks.
        val weightT = ((spec.font.clamp(spec.weight) - 100) / 850f).coerceIn(0f, 1f)
        stroke = (digitBottom - digitTop) * (0.052f + 0.105f * weightT)
    }

    fun digitBand(): kotlin.Pair<Float, Float> = digitTop to digitBottom

    fun layout(text: ClockText, width: Float): TimeLayout {
        val glyphs = ArrayList<Glyph>(8)
        var x = 0f
        text.hours.forEach { c -> glyphs += Glyph(c.toString(), x); x += main.measureText(c.toString()) }
        if (text.separator == ":" && drawnColon) {
            glyphs += Glyph(":", x, isColon = true)
            x += colonAdvance(main.textSize)
        } else {
            glyphs += Glyph(text.separator, x, isColon = text.separator == ":")
            x += main.measureText(text.separator)
        }
        text.minutes.forEach { c -> glyphs += Glyph(c.toString(), x); x += main.measureText(c.toString()) }
        val mainWidth = x
        val amPmLabel = text.amPm?.uppercase()
        val secondsWidth = text.seconds?.let { small.measureText(it) } ?: 0f
        val amPmWidth = amPmLabel?.let { amPm.measureText(it) } ?: 0f
        val column = maxOf(secondsWidth, amPmWidth)
        val gap = if (column > 0f) main.textSize * spec.smallGap else 0f
        val total = mainWidth + gap + column
        val start = (width - total) / 2f
        val both = text.seconds != null && amPmLabel != null
        val columnX = start + mainWidth + gap
        return TimeLayout(
            main = glyphs.map { it.copy(x = it.x + start) },
            amPm = amPmLabel?.let { Glyph(it, columnX, attach = if (both) Attach.TOP else spec.singleSmallAttach) },
            seconds = text.seconds?.let { Glyph(it, columnX, attach = if (both) Attach.BASELINE else spec.singleSmallAttach) },
            start = start,
            end = start + mainWidth,
        )
    }

    fun applyShader(colors: TimeColors, layout: TimeLayout, baseline: Float, band: kotlin.Pair<Float, Float>) {
        val g = colors.gradient?.takeIf { it.size >= 2 }
        main.shader = g?.let {
            LinearGradient(layout.start, baseline + band.first, layout.end, baseline + band.second, it[0].toArgb(), it[1].toArgb(), Shader.TileMode.CLAMP)
        }
    }

    fun clearShader() {
        main.shader = null
    }

    fun drawGlyph(
        canvas: android.graphics.Canvas,
        glyph: Glyph,
        index: Int?,
        baseline: Float,
        band: kotlin.Pair<Float, Float>,
        colors: TimeColors,
        alpha: Float,
        dy: Float,
    ) {
        if (alpha <= 0.003f) return
        val perDigit = colors.perDigit
        val color = when {
            glyph.isColon && !perDigit.isNullOrEmpty() -> colors.separator ?: colors.small
            !perDigit.isNullOrEmpty() && index != null -> perDigit[index % perDigit.size]
            else -> colors.main
        }
        val shaded = main.shader != null && perDigit.isNullOrEmpty()
        main.color = if (shaded) android.graphics.Color.WHITE else color.toArgb()
        main.alpha = ((if (shaded) 1f else color.alpha) * alpha * 255).toInt().coerceIn(0, 255)
        if (glyph.isColon && drawnColon) {
            val r = stroke * 0.56f
            val cx = glyph.x + colonAdvance(main.textSize) / 2f
            val cy = baseline + (band.first + band.second) / 2f + dy
            val spread = (band.second - band.first) * spec.colonSpread
            main.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy - spread, r, main)
            canvas.drawCircle(cx, cy + spread, r, main)
        } else if (glyph.isColon) {
            main.getTextBounds(":", 0, 1, bounds)
            val shift = (band.first + band.second) / 2f - (bounds.top + bounds.bottom) / 2f
            canvas.drawText(glyph.text, glyph.x, baseline + shift + dy, main)
        } else {
            canvas.drawText(glyph.text, glyph.x, baseline + dy, main)
        }
    }

    fun drawSmall(
        canvas: android.graphics.Canvas,
        glyph: Glyph,
        baseline: Float,
        band: kotlin.Pair<Float, Float>,
        color: Color,
        alpha: Float,
        isAmPm: Boolean,
    ) {
        if (alpha <= 0.003f) return
        val paint = if (isAmPm) amPm else small
        paint.color = color.toArgb()
        paint.alpha = (color.alpha * alpha * 255).toInt().coerceIn(0, 255)
        val y = if (glyph.attach == Attach.TOP) {
            if (isAmPm) paint.getTextBounds(glyph.text, 0, glyph.text.length, bounds) else paint.getTextBounds(DIGITS, 0, DIGITS.length, bounds)
            baseline + band.first - bounds.top
        } else {
            baseline
        }
        canvas.drawText(glyph.text, glyph.x, y, paint)
    }
}
