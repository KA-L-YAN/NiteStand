package com.nitestand.standbyclock.ui.standby

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nitestand.standbyclock.display.BurnInShifter
import com.nitestand.standbyclock.display.ShiftOffset
import kotlinx.coroutines.delay

/**
 * Current burn-in protection offset. Every [intervalSeconds] the content drifts a few dp to a
 * new position over several seconds (or snaps when reduced motion is requested). The timer
 * runs only while the hosting lifecycle is STARTED.
 */
@Composable
fun rememberBurnInOffset(enabled: Boolean, intervalSeconds: Int, reduceMotion: Boolean): IntOffset {
    val shifter = remember { BurnInShifter() }
    var target by remember { mutableStateOf(ShiftOffset(0, 0)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(enabled, intervalSeconds, lifecycleOwner) {
        if (!enabled) {
            target = ShiftOffset(0, 0)
            return@LaunchedEffect
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(intervalSeconds * 1_000L)
                target = shifter.next()
            }
        }
    }

    val density = LocalDensity.current
    val targetPx = with(density) { IntOffset(target.xDp.dp.roundToPx(), target.yDp.dp.roundToPx()) }
    val animated by animateIntOffsetAsState(
        targetValue = targetPx,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 4_000, easing = LinearEasing),
        label = "burnIn",
    )
    return animated
}
