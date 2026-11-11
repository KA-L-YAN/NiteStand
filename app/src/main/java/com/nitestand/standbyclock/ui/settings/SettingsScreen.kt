@file:OptIn(ExperimentalMaterial3Api::class)

package com.nitestand.standbyclock.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nitestand.standbyclock.BuildConfig
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.clock.ClockFormatter
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.clock.HourFormat
import com.nitestand.standbyclock.clock.TimeFormatPreference
import com.nitestand.standbyclock.display.BrightnessLevel
import com.nitestand.standbyclock.display.NightMode
import com.nitestand.standbyclock.sensors.OrientationSensitivity
import com.nitestand.standbyclock.settings.AccentColor
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.standby.ActivationMode
import com.nitestand.standbyclock.ui.components.label
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun SettingsScreen(
    settings: StandBySettings,
    hasOrientationSensor: Boolean,
    onUpdate: ((StandBySettings) -> StandBySettings) -> Unit,
    onBack: () -> Unit,
    onOpenScreenSaverSettings: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                settingsContent(settings, hasOrientationSensor, onUpdate, onOpenScreenSaverSettings)
            }
        }
    }
}

private fun LazyListScope.settingsContent(
    s: StandBySettings,
    hasOrientationSensor: Boolean,
    update: ((StandBySettings) -> StandBySettings) -> Unit,
    onOpenScreenSaverSettings: () -> Unit,
) {
    // ---------------------------------------------------------------- General
    item { SectionHeader(R.string.section_general) }
    item {
        SwitchRow(R.string.pref_enable, R.string.pref_enable_summary, s.standByEnabled) { v -> update { it.copy(standByEnabled = v) } }
    }
    item {
        SwitchRow(R.string.pref_auto_start, R.string.pref_auto_start_summary, s.autoStart, enabled = s.standByEnabled) { v ->
            update { it.copy(autoStart = v) }
        }
    }
    item {
        SwitchRow(R.string.pref_require_charging, R.string.pref_require_charging_summary, s.requireCharging, enabled = s.standByEnabled) { v ->
            update { it.copy(activationMode = ActivationMode.from(v, it.requireLandscape)) }
        }
    }
    item {
        SwitchRow(
            R.string.pref_require_landscape,
            if (hasOrientationSensor) R.string.pref_require_landscape_summary else R.string.pref_require_landscape_no_sensor,
            s.requireLandscape,
            enabled = s.standByEnabled,
        ) { v -> update { it.copy(activationMode = ActivationMode.from(it.requireCharging, v)) } }
    }
    item {
        SwitchRow(R.string.pref_lock_screen, R.string.pref_lock_screen_summary, s.showOnLockScreen) { v ->
            update { it.copy(showOnLockScreen = v) }
        }
    }

    // ---------------------------------------------------------------- Activation
    item { SectionHeader(R.string.section_activation) }
    item {
        ChoiceRow(
            title = R.string.pref_activation_mode,
            options = ActivationMode.entries,
            selected = s.activationMode,
            label = { it.label() },
            enabled = s.standByEnabled,
        ) { v -> update { it.copy(activationMode = v) } }
    }
    item {
        ChoiceRow(
            title = R.string.pref_sensitivity,
            options = OrientationSensitivity.entries,
            selected = s.orientationSensitivity,
            label = { it.label() },
            enabled = s.standByEnabled && s.requireLandscape && hasOrientationSensor,
        ) { v -> update { it.copy(orientationSensitivity = v) } }
    }

    // ---------------------------------------------------------------- Clock
    item { SectionHeader(R.string.section_clock) }
    item {
        ChoiceRow(R.string.pref_clock_theme, ClockTheme.entries, s.clockTheme, { it.label() }) { v ->
            update { it.copy(clockTheme = v) }
        }
    }
    item {
        ChoiceRow(R.string.pref_hour_format, HourFormat.entries, s.hourFormat, { it.label() }) { v ->
            update { it.copy(hourFormat = v) }
        }
    }
    item { SwitchRow(R.string.pref_show_seconds, R.string.pref_show_seconds_summary, s.showSeconds) { v -> update { it.copy(showSeconds = v) } } }
    item { SwitchRow(R.string.pref_show_date, null, s.showDate) { v -> update { it.copy(showDate = v) } } }

    // ---------------------------------------------------------------- Widgets
    item { SectionHeader(R.string.section_widgets) }
    item { SwitchRow(R.string.pref_show_panel, R.string.pref_show_panel_summary, s.showPanel) { v -> update { it.copy(showPanel = v) } } }
    item { SwitchRow(R.string.widget_date, null, s.showDateWidget, enabled = s.showPanel) { v -> update { it.copy(showDateWidget = v) } } }
    item { SwitchRow(R.string.widget_battery, null, s.showBatteryWidget, enabled = s.showPanel) { v -> update { it.copy(showBatteryWidget = v) } } }
    item {
        SwitchRow(R.string.widget_alarm, R.string.pref_alarm_summary, s.showAlarmWidget, enabled = s.showPanel) { v ->
            update { it.copy(showAlarmWidget = v) }
        }
    }
    item {
        SwitchRow(R.string.pref_weather, R.string.pref_weather_summary, s.showWeatherWidget, enabled = s.showPanel) { v ->
            update { it.copy(showWeatherWidget = v) }
        }
    }

    // ---------------------------------------------------------------- Appearance
    item { SectionHeader(R.string.section_appearance) }
    item {
        ChoiceRow(R.string.pref_brightness, BrightnessLevel.entries, s.brightness, { it.label() }, summary = R.string.pref_brightness_summary) { v ->
            update { it.copy(brightness = v) }
        }
    }
    item {
        ChoiceRow(R.string.pref_night_mode, NightMode.entries, s.nightMode, { it.label() }, summary = R.string.pref_night_mode_summary) { v ->
            update { it.copy(nightMode = v) }
        }
    }
    item {
        TimeRow(R.string.pref_night_start, s.nightStartMinutes, s.hourFormat, enabled = s.nightMode == NightMode.SCHEDULED) { v ->
            update { it.copy(nightStartMinutes = v) }
        }
    }
    item {
        TimeRow(R.string.pref_night_end, s.nightEndMinutes, s.hourFormat, enabled = s.nightMode == NightMode.SCHEDULED) { v ->
            update { it.copy(nightEndMinutes = v) }
        }
    }
    item {
        ChoiceRow(R.string.pref_accent, AccentColor.entries, s.accent, { it.label() }) { v -> update { it.copy(accent = v) } }
    }
    item {
        SwitchRow(R.string.pref_reduce_motion, R.string.pref_reduce_motion_summary, s.reduceMotion) { v -> update { it.copy(reduceMotion = v) } }
    }

    // ---------------------------------------------------------------- Burn-in
    item { SectionHeader(R.string.section_burn_in) }
    item {
        SwitchRow(R.string.pref_burn_in, R.string.pref_burn_in_summary, s.burnInProtection) { v -> update { it.copy(burnInProtection = v) } }
    }
    item {
        ChoiceRow(
            title = R.string.pref_burn_in_interval,
            options = StandBySettings.BURN_IN_INTERVALS,
            selected = s.burnInIntervalSeconds,
            label = { pluralStringResource(R.plurals.seconds_value, it, it) },
            enabled = s.burnInProtection,
        ) { v -> update { it.copy(burnInIntervalSeconds = v) } }
    }

    // ---------------------------------------------------------------- Screen saver & about
    item { SectionHeader(R.string.section_screensaver) }
    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.action_open_screensaver)) },
            supportingContent = { Text(stringResource(R.string.pref_screensaver_summary)) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = onOpenScreenSaverSettings),
        )
    }
    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.about_version)) },
            supportingContent = { Text(BuildConfig.VERSION_NAME) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )
    }
}

@Composable
private fun SectionHeader(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SwitchRow(
    title: Int,
    summary: Int?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = summary?.let { { Text(stringResource(it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun <T> ChoiceRow(
    title: Int,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    enabled: Boolean = true,
    summary: Int? = null,
    onSelect: (T) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            Column {
                Text(label(selected), color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                summary?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .heightIn(min = 56.dp)
            .selectable(selected = false, enabled = enabled, role = Role.Button) { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(title)) },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .selectable(selected = option == selected, role = Role.RadioButton) {
                                    onSelect(option)
                                    open = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == selected, onClick = null)
                            Spacer(Modifier.size(16.dp))
                            Text(label(option), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun TimeRow(title: Int, minutes: Int, hourFormat: HourFormat, enabled: Boolean, onSelect: (Int) -> Unit) {
    val context = LocalContext.current
    val timeFormat = remember(context) { TimeFormatPreference(context) }
    val is24 = timeFormat.is24Hour(hourFormat)
    val formatted = remember(minutes, is24) {
        val time = LocalTime.of(minutes / 60, minutes % 60).atDate(LocalDate.now()).atZone(ZoneId.systemDefault())
        ClockFormatter(timeFormat.locale(), is24, showSeconds = false).formatTime(time)
    }
    var open by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            Text(formatted, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .heightIn(min = 56.dp)
            .selectable(selected = false, enabled = enabled, role = Role.Button) { open = true },
    )
    if (open) {
        val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = is24)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(title)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onSelect(state.hour * 60 + state.minute)
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
