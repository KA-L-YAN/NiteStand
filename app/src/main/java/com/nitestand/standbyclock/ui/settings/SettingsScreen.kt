@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nitestand.standbyclock.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitestand.standbyclock.clock.ClockFaceCatalog
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.clock.TickInterval
import com.nitestand.standbyclock.ui.gallery.ColorSwatch
import com.nitestand.standbyclock.ui.gallery.FacePreview
import com.nitestand.standbyclock.ui.gallery.previewData
import com.nitestand.standbyclock.ui.motion.Motion
import com.nitestand.standbyclock.ui.standby.rememberAnimatedStyle
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.widgets.WidgetDataSources
import com.nitestand.standbyclock.lockscreen.LaunchIssue
import com.nitestand.standbyclock.lockscreen.LaunchReadiness
import com.nitestand.standbyclock.lockscreen.StandByNotifications
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
import com.nitestand.standbyclock.clock.DateStyle
import com.nitestand.standbyclock.clock.HourFormat
import com.nitestand.standbyclock.clock.TimeFormatPreference
import com.nitestand.standbyclock.display.BrightnessLevel
import com.nitestand.standbyclock.display.NightMode
import com.nitestand.standbyclock.sensors.OrientationSensitivity
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.standby.ActivationMode
import com.nitestand.standbyclock.ui.components.description
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
    onToggleWatcher: (Boolean) -> Unit = { enable -> onUpdate { it.copy(lockScreenWatcher = enable) } },
    onOpenDiagnostics: () -> Unit = {},
    onOpenClockFaces: () -> Unit = {},
    sources: WidgetDataSources? = null,
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
                settingsContent(settings, hasOrientationSensor, onUpdate, onOpenScreenSaverSettings, onToggleWatcher, onOpenDiagnostics, onOpenClockFaces, sources)
            }
        }
    }
}

private fun LazyListScope.settingsContent(
    s: StandBySettings,
    hasOrientationSensor: Boolean,
    update: ((StandBySettings) -> StandBySettings) -> Unit,
    onOpenScreenSaverSettings: () -> Unit,
    onToggleWatcher: (Boolean) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenClockFaces: () -> Unit,
    sources: WidgetDataSources?,
) {
    // ---------------------------------------------------------------- Clock face
    group(R.string.section_clock_face) {
        if (sources != null) CurrentFacePreview(s, sources, onOpenClockFaces)
        NavRow(
            R.string.pref_choose_clock,
            "${ClockFaceCatalog.spec(s.clockTheme).category.label()} · ${s.clockTheme.label()}",
            onOpenClockFaces,
        )
    }

    // ---------------------------------------------------------------- Appearance
    group(R.string.section_appearance) {
        ColorRow(s, update)
        GroupDivider()
        ChoiceRow(R.string.pref_brightness, BrightnessLevel.entries, s.brightness, { it.label() }, summary = R.string.pref_brightness_summary) { v ->
            update { it.copy(brightness = v) }
        }
        GroupDivider()
        ChoiceRow(R.string.pref_night_mode, NightMode.entries, s.nightMode, { it.label() }, summary = R.string.pref_night_mode_summary) { v ->
            update { it.copy(nightMode = v) }
        }
        if (s.nightMode == NightMode.SCHEDULED) {
            TimeRow(R.string.pref_night_start, s.nightStartMinutes, s.hourFormat, enabled = true) { v -> update { it.copy(nightStartMinutes = v) } }
            TimeRow(R.string.pref_night_end, s.nightEndMinutes, s.hourFormat, enabled = true) { v -> update { it.copy(nightEndMinutes = v) } }
        }
    }

    // ---------------------------------------------------------------- Time
    group(R.string.section_time) {
        ChoiceRow(R.string.pref_hour_format, HourFormat.entries, s.hourFormat, { it.label() }) { v -> update { it.copy(hourFormat = v) } }
        if (s.hourFormat != HourFormat.TWENTY_FOUR_HOUR) {
            SwitchRow(R.string.pref_show_am_pm, R.string.pref_show_am_pm_summary, s.showAmPm) { v -> update { it.copy(showAmPm = v) } }
        }
        GroupDivider()
        SwitchRow(R.string.pref_show_seconds, R.string.pref_show_seconds_summary, s.showSeconds) { v -> update { it.copy(showSeconds = v) } }
        GroupDivider()
        SwitchRow(R.string.pref_show_date, R.string.pref_show_date_summary, s.showDate) { v -> update { it.copy(showDate = v) } }
        if (s.showDate) {
            ChoiceRow(R.string.pref_date_style, DateStyle.entries, s.dateStyle, { it.label() }) { v -> update { it.copy(dateStyle = v) } }
        }
    }

    // ---------------------------------------------------------------- Information
    group(R.string.section_information) {
        SwitchRow(R.string.pref_info_page, R.string.pref_info_page_summary, s.showInfoPage) { v -> update { it.copy(showInfoPage = v) } }
        GroupDivider()
        SwitchRow(R.string.pref_show_panel, R.string.pref_show_panel_summary, s.showPanel) { v -> update { it.copy(showPanel = v) } }
        GroupDivider()
        val any = s.showInfoPage || s.showPanel
        SwitchRow(R.string.widget_date, null, s.showDateWidget, enabled = any) { v -> update { it.copy(showDateWidget = v) } }
        SwitchRow(R.string.widget_battery, null, s.showBatteryWidget, enabled = any) { v -> update { it.copy(showBatteryWidget = v) } }
        SwitchRow(R.string.widget_alarm, R.string.pref_alarm_summary, s.showAlarmWidget, enabled = any) { v -> update { it.copy(showAlarmWidget = v) } }
        SwitchRow(R.string.pref_weather, R.string.pref_weather_summary, s.showWeatherWidget, enabled = any) { v -> update { it.copy(showWeatherWidget = v) } }
    }

    // ---------------------------------------------------------------- Behavior
    group(R.string.section_behavior) {
        SwitchRow(R.string.pref_enable, R.string.pref_enable_summary, s.standByEnabled) { v -> update { it.copy(standByEnabled = v) } }
        GroupDivider()
        ChoiceRow(
            title = R.string.pref_activation_mode,
            options = ActivationMode.entries,
            selected = s.activationMode,
            label = { it.label() },
            description = { it.description() },
            enabled = s.standByEnabled,
            summaryText = s.activationMode.description(),
        ) { v -> update { it.copy(activationMode = v) } }
        if (s.activationMode.isAutomatic) {
            SwitchRow(R.string.pref_auto_start, R.string.pref_auto_start_summary, s.autoStart, enabled = s.standByEnabled) { v ->
                update { it.copy(autoStart = v) }
            }
        }
        SwitchRow(
            R.string.pref_watcher,
            if (s.activationMode.supportsLockScreenWatcher) R.string.pref_watcher_summary else R.string.pref_watcher_unavailable,
            s.lockScreenWatcher && s.activationMode.supportsLockScreenWatcher,
            enabled = s.standByEnabled && s.activationMode.supportsLockScreenWatcher,
        ) { v -> onToggleWatcher(v) }
        if (s.lockScreenWatcher && s.activationMode.supportsLockScreenWatcher) LaunchReadinessRows()
        SwitchRow(R.string.pref_lock_screen, R.string.pref_lock_screen_summary, s.showOnLockScreen) { v -> update { it.copy(showOnLockScreen = v) } }
        GroupDivider()
        ChoiceRow(
            title = R.string.pref_battery_timeout,
            options = StandBySettings.BATTERY_TIMEOUTS,
            selected = s.batteryTimeoutMinutes,
            label = { if (it == 0) stringResource(R.string.battery_timeout_never) else pluralStringResource(R.plurals.minutes_value, it, it) },
            summary = R.string.pref_battery_timeout_summary,
        ) { v -> update { it.copy(batteryTimeoutMinutes = v) } }
        GroupDivider()
        SwitchRow(R.string.pref_burn_in, R.string.pref_burn_in_summary, s.burnInProtection) { v -> update { it.copy(burnInProtection = v) } }
    }

    // ---------------------------------------------------------------- Advanced
    group(R.string.section_advanced) {
        ChoiceRow(
            title = R.string.pref_sensitivity,
            options = OrientationSensitivity.entries,
            selected = s.orientationSensitivity,
            label = { it.label() },
            enabled = s.standByEnabled && s.requireLandscape && hasOrientationSensor,
        ) { v -> update { it.copy(orientationSensitivity = v) } }
        ChoiceRow(
            title = R.string.pref_burn_in_interval,
            options = StandBySettings.BURN_IN_INTERVALS,
            selected = s.burnInIntervalSeconds,
            label = { pluralStringResource(R.plurals.seconds_value, it, it) },
            enabled = s.burnInProtection,
        ) { v -> update { it.copy(burnInIntervalSeconds = v) } }
        SwitchRow(R.string.pref_reduce_motion, R.string.pref_reduce_motion_summary, s.reduceMotion) { v -> update { it.copy(reduceMotion = v) } }
        GroupDivider()
        NavRow(R.string.action_open_screensaver, stringResource(R.string.pref_screensaver_summary), onOpenScreenSaverSettings)
        NavRow(R.string.diagnostics_title, stringResource(R.string.pref_diagnostics_summary), onOpenDiagnostics)
        LicensesRow()
        ListItem(
            headlineContent = { Text(stringResource(R.string.about_version)) },
            supportingContent = { Text(BuildConfig.VERSION_NAME) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
    item { Spacer(Modifier.height(24.dp)) }
}

/** One inset group: a small header and rows on a single softly rounded surface. */
private fun LazyListScope.group(title: Int, content: @Composable ColumnScope.() -> Unit) {
    item(key = "header_$title") { SectionHeader(title) }
    item(key = "group_$title") {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(vertical = 6.dp),
            content = content,
        )
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun NavRow(title: Int, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.heightIn(min = 56.dp).selectable(selected = false, role = Role.Button, onClick = onClick),
    )
}

/** Live preview of the selected face; tapping opens the gallery. */
@Composable
private fun CurrentFacePreview(s: StandBySettings, sources: WidgetDataSources, onOpen: () -> Unit) {
    val ticks = remember(sources) { sources.ticker.ticks(TickInterval.MINUTE) }
    val now by ticks.collectAsStateWithLifecycle(initialValue = null)
    val time = now ?: return
    val data = remember(s, time) { previewData(sources, s.copy(showSeconds = false), s.clockTheme, time) }
    val style = StandByStyle.forSettings(s, reduceMotion = true)
    Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp)).selectable(selected = false, role = Role.Button, onClick = onOpen)) {
        FacePreview(data, rememberAnimatedStyle(style, Motion()), selected = false)
    }
}

/** Visual colour picker for the selected face (only the colours that face supports). */
@Composable
private fun ColorRow(s: StandBySettings, update: ((StandBySettings) -> StandBySettings) -> Unit) {
    val spec = ClockFaceCatalog.spec(s.clockTheme)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.gallery_color), style = MaterialTheme.typography.bodyLarge)
        if (spec.palettes.isEmpty()) {
            Text(stringResource(R.string.gallery_fixed_colors), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val current = ClockFaceCatalog.paletteFor(s)
            FlowRow(Modifier.padding(top = 6.dp)) {
                spec.palettes.forEach { palette ->
                    ColorSwatch(palette, palette == current, expressive = spec.id == ClockTheme.DIGITAL_COLOR || spec.id == ClockTheme.BUBBLE) {
                        update { it.copy(faceColors = it.faceColors + (s.clockTheme to palette)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 32.dp, end = 32.dp, top = 26.dp, bottom = 8.dp)
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
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
    description: (@Composable (T) -> String)? = null,
    summaryText: String? = null,
    onSelect: (T) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            Column {
                Text(label(selected), color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                summary?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                summaryText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Text(label(option), style = MaterialTheme.typography.bodyLarge)
                                description?.let {
                                    Text(
                                        it(option),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
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
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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

/** Shows the SIL Open Font License texts of the bundled fonts (required attribution). */
@Composable
private fun LicensesRow() {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.licenses_title)) },
        supportingContent = { Text(stringResource(R.string.licenses_summary)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.heightIn(min = 56.dp).selectable(selected = false, role = Role.Button) { open = true },
    )
    if (open) {
        val text = remember {
            listOf("OFL-Nunito.txt", "OFL-Outfit.txt", "OFL-PlayfairDisplay.txt").joinToString("\n\n────────\n\n") { name ->
                runCatching { context.assets.open("licenses/$name").bufferedReader().use { it.readText() } }.getOrDefault(name)
            }
        }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.licenses_title)) },
            text = { Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/**
 * What automatic start still needs on this phone. Only genuinely required items are "Required";
 * battery optimisation is a recommendation (OxygenOS may stop the watcher otherwise).
 */
@Composable
private fun LaunchReadinessRows() {
    val context = LocalContext.current
    var readiness by remember { mutableStateOf(LaunchReadiness.read(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        readiness = LaunchReadiness.read(context)
        onPauseOrDispose { }
    }
    val issues = readiness.issues
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        if (issues.none { it.blocking }) {
            Text(
                stringResource(R.string.readiness_ok),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        issues.forEach { issue ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (issue.blocking) R.string.readiness_required else R.string.readiness_recommended),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (issue.blocking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(issue.labelRes()), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = { openFix(context, issue) }) { Text(stringResource(R.string.readiness_fix)) }
            }
        }
    }
}

private fun LaunchIssue.labelRes(): Int = when (this) {
    LaunchIssue.NOTIFICATIONS_OFF -> R.string.readiness_notifications
    LaunchIssue.FULL_SCREEN_DENIED -> R.string.readiness_fsi
    LaunchIssue.CHANNEL_DEMOTED -> R.string.readiness_channel
    LaunchIssue.DO_NOT_DISTURB -> R.string.readiness_dnd
    LaunchIssue.BATTERY_RESTRICTED -> R.string.readiness_battery
}

private fun openFix(context: android.content.Context, issue: LaunchIssue) {
    val pkg = android.net.Uri.fromParts("package", context.packageName, null)
    val intent = when (issue) {
        LaunchIssue.FULL_SCREEN_DENIED ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            } else {
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            }
        LaunchIssue.CHANNEL_DEMOTED ->
            android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, StandByNotifications.CHANNEL_LAUNCH)
        LaunchIssue.NOTIFICATIONS_OFF ->
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        LaunchIssue.DO_NOT_DISTURB -> android.content.Intent(android.provider.Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
        // OxygenOS keeps "Battery usage: Unrestricted" under App info › Battery.
        LaunchIssue.BATTERY_RESTRICTED -> android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
    }.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
