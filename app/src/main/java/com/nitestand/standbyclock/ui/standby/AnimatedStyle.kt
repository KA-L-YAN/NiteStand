package com.nitestand.standbyclock.ui.standby

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nitestand.standbyclock.ui.motion.Motion
import com.nitestand.standbyclock.ui.theme.StandByStyle

/**
 * Smoothly interpolates between styles: a colour change blends in ~450 ms, entering or leaving
 * night mode dims over ~2.5 s (×1.6 at night). Interrupted transitions continue from the colour
 * currently on screen, so there is never a jump.
 */
@Composable
fun rememberAnimatedStyle(target: StandByStyle, motion: Motion): StandByStyle {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = from.lerpTo(to, progress.value)
        val nightChange = from.night != target.night
        to = target
        progress.snapTo(0f)
        val duration = if (nightChange) motion.ms(Motion.NIGHT_MS) else motion.ms(Motion.COLOR_MS)
        progress.animateTo(1f, tween(duration, easing = Motion.Standard))
    }
    return if (from.night != to.night) nightPath(from, to, progress.value) else from.lerpTo(to, progress.value)
}

/**
 * Day ⇄ night goes through a warm, dimmed middle state instead of a direct white → red blend:
 * normal → gradual dim → warm amber → muted red (and the reverse when morning comes).
 */
internal fun nightPath(from: StandByStyle, to: StandByStyle, p: Float): StandByStyle {
    val warm = from.lerpTo(to, 0.5f).warmed()
    return if (p < 0.5f) from.lerpTo(warm, p / 0.5f) else warm.lerpTo(to, (p - 0.5f) / 0.5f)
}

private val Amber = androidx.compose.ui.graphics.Color(0xFFE08A4E)

private fun StandByStyle.warmed(): StandByStyle {
    fun w(c: androidx.compose.ui.graphics.Color) =
        androidx.compose.ui.graphics.lerp(c, Amber.copy(alpha = c.alpha * 0.62f), 0.8f)
    return copy(
        primary = w(primary), secondary = w(secondary), tertiary = w(tertiary), accent = w(accent),
        retro = w(retro), hands = w(hands), gradient = gradient.map(::w),
    )
}
