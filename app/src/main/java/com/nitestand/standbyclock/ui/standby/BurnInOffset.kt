package com.nitestand.standbyclock.ui.standby

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nitestand.standbyclock.display.BurnInShifter
import com.nitestand.standbyclock.ui.motion.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Current burn-in drift in px; read it in a graphicsLayer so movement never recomposes. */
class BurnInDrift internal constructor(
    private val x: Animatable<Float, *>,
    private val y: Animatable<Float, *>,
) {
    val dx: Float get() = x.value
    val dy: Float get() = y.value
}

/**
 * Invisible burn-in protection: every [intervalSeconds] the stage glides a few dp to the next
 * point of a deterministic path, with a slow symmetric ease (8 s by day, ~13 s at night) and
 * sub-pixel precision, so it never visibly steps or jumps. Runs only while the surface is STARTED.
 */
@Composable
fun rememberBurnInDrift(enabled: Boolean, intervalSeconds: Int, motion: Motion): BurnInDrift {
    val shifter = remember { BurnInShifter() }
    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }
    val density = LocalDensity.current.density
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(enabled, intervalSeconds, motion, lifecycleOwner) {
        if (!enabled) {
            launch { x.animateTo(0f, tween(motion.ms(Motion.BURN_IN_MS), easing = Motion.EaseInOut)) }
            launch { y.animateTo(0f, tween(motion.ms(Motion.BURN_IN_MS), easing = Motion.EaseInOut)) }
            return@LaunchedEffect
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(intervalSeconds * 1_000L)
                val next = shifter.next()
                val spec = tween<Float>(motion.ms(Motion.BURN_IN_MS), easing = Motion.EaseInOut)
                launch { x.animateTo(next.xDp * density, spec) }
                launch { y.animateTo(next.yDp * density, spec) }
            }
        }
    }
    return remember { BurnInDrift(x, y) }
}
