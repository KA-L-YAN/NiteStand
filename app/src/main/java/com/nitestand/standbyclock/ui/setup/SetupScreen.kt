@file:OptIn(ExperimentalMaterial3Api::class)

package com.nitestand.standbyclock.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.charging.BatterySnapshot
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.ui.components.label
import com.nitestand.standbyclock.ui.components.statusLabel
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.util.DeviceTips
import com.nitestand.standbyclock.widgets.WidgetDataSources
import com.nitestand.standbyclock.widgets.WidgetHost
import com.nitestand.standbyclock.widgets.WidgetRegistry

@Composable
fun SetupScreen(
    settings: StandBySettings,
    battery: BatterySnapshot?,
    sources: WidgetDataSources,
    onStartStandBy: () -> Unit,
    onPreview: () -> Unit,
    onOpenScreenSaverSettings: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.setup_tagline),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { PreviewCard(settings, sources, onPreview) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onStartStandBy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.action_start_standby))
                        }
                        OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.action_preview))
                        }
                    }
                }
                item { StatusCard(settings, battery) }
                item { StepsCard() }
                item { ScreenSaverCard(onOpenScreenSaverSettings) }
                item {
                    InfoCard(
                        title = stringResource(R.string.lockscreen_title),
                        body = stringResource(R.string.lockscreen_body),
                    )
                }
                item {
                    InfoCard(
                        title = stringResource(R.string.device_tips_title),
                        body = stringResource(DeviceTips.forManufacturer()),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PreviewCard(settings: StandBySettings, sources: WidgetDataSources, onPreview: () -> Unit) {
    val style = remember(settings.accent, settings.clockTheme) {
        StandByStyle.create(settings.accent, settings.clockTheme, night = false, reduceMotion = true)
    }
    val previewLabel = stringResource(R.string.action_preview)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2.1f)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black)
            .clickable(onClickLabel = previewLabel, onClick = onPreview)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        WidgetHost(WidgetRegistry.clock, sources, settings.copy(showSeconds = false), style, Modifier.fillMaxSize())
    }
}

@Composable
private fun StatusCard(settings: StandBySettings, battery: BatterySnapshot?) {
    SectionCard {
        StatusRow(stringResource(R.string.status_power), battery.statusLabel())
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StatusRow(
            stringResource(R.string.status_activation),
            if (settings.standByEnabled) settings.activationMode.label() else stringResource(R.string.status_disabled),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StatusRow(stringResource(R.string.status_clock), settings.clockTheme.label())
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StepsCard() {
    val steps = listOf(R.string.setup_step_1, R.string.setup_step_2, R.string.setup_step_3, R.string.setup_step_4)
    SectionCard {
        Text(
            stringResource(R.string.setup_how_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        steps.forEachIndexed { index, step ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(14.dp))
                Text(stringResource(step), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.setup_how_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScreenSaverCard(onOpen: () -> Unit) {
    SectionCard {
        Text(
            stringResource(R.string.screensaver_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.screensaver_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_open_screensaver))
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}
