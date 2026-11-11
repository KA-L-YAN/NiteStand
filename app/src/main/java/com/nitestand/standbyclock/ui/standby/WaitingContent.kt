package com.nitestand.standbyclock.ui.standby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.clock.ClockPalette
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.standby.StandByState
import com.nitestand.standbyclock.standby.StandByUiState
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.widgets.WidgetDataSources
import com.nitestand.standbyclock.widgets.WidgetHost
import com.nitestand.standbyclock.widgets.WidgetRegistry

/**
 * Shown by the StandBy activity while the activation conditions are not met. Deliberately dim
 * and the screen is allowed to time out normally here (no keep-screen-on).
 */
@Composable
fun WaitingContent(
    state: StandByUiState,
    sources: WidgetDataSources,
    onStart: (force: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val activation = state.activation
    val settings = state.settings
    val (title, body) = when {
        !activation.enabled -> R.string.waiting_disabled_title to R.string.waiting_disabled_body
        !activation.mode.isAutomatic -> R.string.waiting_manual_title to R.string.waiting_manual_body
        activation.state == StandByState.READY -> R.string.waiting_ready_title to R.string.waiting_ready_body
        !activation.powerRequirementMet -> R.string.waiting_power_title to R.string.waiting_power_body
        activation.state == StandByState.CHARGING -> R.string.waiting_checking_title to R.string.waiting_checking_body
        else -> R.string.waiting_landscape_title to R.string.waiting_landscape_body
    }
    val canStart = activation.state == StandByState.READY || !activation.mode.isAutomatic
    val dimStyle = StandByStyle.create(ClockTheme.MINIMAL, ClockPalette.WHITE, if (state.nightActive) 1f else 0f, reduceMotion = true)

    val clock: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            WidgetHost(
                WidgetRegistry.clock,
                sources,
                settings.copy(clockTheme = ClockTheme.MINIMAL, showSeconds = false, showDate = false),
                dimStyle,
                Modifier.fillMaxSize(),
            )
        }
    }
    val details: @Composable (Alignment.Horizontal) -> Unit = { alignment ->
        Column(horizontalAlignment = alignment) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFFE0DEE4),
                textAlign = if (alignment == Alignment.Start) TextAlign.Start else TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFA5A2AB),
                textAlign = if (alignment == Alignment.Start) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.widthIn(max = 460.dp),
            )
            if (activation.enabled && activation.mode.isAutomatic) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (activation.mode.requiresCharging) {
                        ConditionRow(R.string.condition_power, activation.powerRequirementMet)
                    }
                    if (activation.mode.requiresLandscape) {
                        ConditionRow(R.string.condition_landscape, activation.orientationRequirementMet)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canStart) {
                    Button(onClick = { onStart(false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_start_standby))
                    }
                } else {
                    OutlinedButton(onClick = { onStart(true) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_start_anyway))
                    }
                }
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (maxWidth > maxHeight && maxHeight < 520.dp) {
            // Short landscape screen: clock on the left, status and actions on the right.
            Row(
                Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                clock(Modifier.weight(1f).height(140.dp))
                Spacer(Modifier.width(32.dp))
                Column(Modifier.weight(1.3f).verticalScroll(rememberScrollState())) { details(Alignment.Start) }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                clock(Modifier.fillMaxWidth().height(110.dp))
                Spacer(Modifier.height(16.dp))
                details(Alignment.CenterHorizontally)
            }
        }
    }
}

@Composable
private fun ConditionRow(label: Int, met: Boolean) {
    val stateText = stringResource(if (met) R.string.condition_met else R.string.condition_not_met)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .semantics(mergeDescendants = true) { stateDescription = stateText },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (met) R.drawable.ic_check_circle else R.drawable.ic_circle_outline),
            contentDescription = null,
            tint = if (met) MaterialTheme.colorScheme.primary else Color(0xFF6E6B74),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(stringResource(label), color = if (met) Color(0xFFE0DEE4) else Color(0xFF9C99A3))
    }
}

/** Dream fallback while not in landscape: tiny and dim; the dream ends itself after a grace period. */
@Composable
fun DreamIdleContent(state: StandByUiState, sources: WidgetDataSources) {
    val style = StandByStyle.create(ClockTheme.AMBIENT, ClockPalette.WHITE, if (state.nightActive) 1f else 0f, reduceMotion = true)
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(160.dp)) {
            WidgetHost(
                WidgetRegistry.clock,
                sources,
                state.settings.copy(clockTheme = ClockTheme.AMBIENT, showSeconds = false),
                style,
                Modifier.fillMaxSize(),
            )
        }
        Text(stringResource(R.string.dream_rotate_hint), color = style.secondary, textAlign = TextAlign.Center)
    }
}
