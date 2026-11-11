@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nitestand.standbyclock.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.clock.ClockFaceCatalog
import com.nitestand.standbyclock.clock.ClockFaceSpec
import com.nitestand.standbyclock.clock.ClockPalette
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.clock.DateStyle
import com.nitestand.standbyclock.clock.FaceCategory
import com.nitestand.standbyclock.clock.HourFormat
import com.nitestand.standbyclock.clock.TickInterval
import com.nitestand.standbyclock.clock.WorldCities
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.ui.clock.ClockFaceView
import com.nitestand.standbyclock.ui.components.description
import com.nitestand.standbyclock.ui.components.label
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.widgets.ClockFaceData
import com.nitestand.standbyclock.widgets.WidgetDataSources
import java.time.ZonedDateTime

/**
 * Clock Faces gallery: live previews of every face, grouped by category. Tapping a face selects it
 * (persisted immediately) and opens its customisation sheet.
 */
@Composable
fun ClockGalleryScreen(
    settings: StandBySettings,
    sources: WidgetDataSources,
    onUpdate: ((StandBySettings) -> StandBySettings) -> Unit,
    onBack: () -> Unit,
    onPreviewFullScreen: () -> Unit,
    initiallyOpen: ClockTheme? = null,
) {
    // One shared minute tick drives every preview (no per-card timers).
    val ticks = remember(sources) { sources.ticker.ticks(TickInterval.MINUTE) }
    val now by ticks.collectAsStateWithLifecycle(initialValue = null)
    var detail by rememberSaveable { mutableStateOf(initiallyOpen) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gallery_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val time = now ?: return@Scaffold
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.gallery_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FaceCategory.entries.forEach { category ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "header_$category") {
                    Text(
                        category.label().uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp).semantics { heading() },
                    )
                }
                items(ClockFaceCatalog.byCategory(category), key = { it.id.name }) { spec ->
                    FaceCard(
                        spec = spec,
                        settings = settings,
                        sources = sources,
                        now = time,
                        selected = settings.clockTheme == spec.id,
                        onClick = {
                            onUpdate { it.copy(clockTheme = spec.id) }
                            detail = spec.id
                        },
                    )
                }
            }
        }
    }

    detail?.let { id ->
        val time = now ?: return@let
        FaceDetailSheet(
            spec = ClockFaceCatalog.spec(id),
            settings = settings,
            sources = sources,
            now = time,
            onUpdate = onUpdate,
            onDismiss = { detail = null },
            onPreviewFullScreen = onPreviewFullScreen,
        )
    }
}

/** Builds preview data with the same central formatting the StandBy screen uses. */
internal fun previewData(sources: WidgetDataSources, settings: StandBySettings, face: ClockTheme, now: ZonedDateTime): ClockFaceData {
    val s = settings.copy(clockTheme = face)
    val spec = ClockFaceCatalog.spec(face)
    val formatter = sources.timeFormat.formatter(
        s.hourFormat,
        showSeconds = s.showSeconds && spec.supportsSeconds,
        showAmPm = s.showAmPm && spec.supportsAmPm,
    )
    return ClockFaceData.build(now, s) { formatter }
}

@Composable
internal fun FacePreview(data: ClockFaceData, style: StandByStyle, selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(2.1f)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        ClockFaceView(data, style, Modifier.fillMaxSize())
    }
}

@Composable
private fun FaceCard(
    spec: ClockFaceSpec,
    settings: StandBySettings,
    sources: WidgetDataSources,
    now: ZonedDateTime,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val data = remember(settings, spec.id, now) { previewData(sources, settings.copy(showSeconds = false), spec.id, now) }
    val style = remember(settings, spec.id) { StandByStyle.forSettings(settings, spec.id, reduceMotion = true) }
    val name = spec.id.label()
    val selectedLabel = stringResource(R.string.gallery_selected)
    Column(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = if (selected) "$name, $selectedLabel" else name }
            .padding(4.dp),
    ) {
        FacePreview(data, style, selected)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            spec.id.description(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun FaceDetailSheet(
    spec: ClockFaceSpec,
    settings: StandBySettings,
    sources: WidgetDataSources,
    now: ZonedDateTime,
    onUpdate: ((StandBySettings) -> StandBySettings) -> Unit,
    onDismiss: () -> Unit,
    onPreviewFullScreen: () -> Unit,
) {
    var nightPreview by rememberSaveable { mutableStateOf(false) }
    val data = remember(settings, spec.id, now) { previewData(sources, settings, spec.id, now) }
    val style = StandByStyle.forSettings(settings, spec.id, nightFraction = if (nightPreview) 1f else 0f, reduceMotion = true)
    val selected = settings.clockTheme == spec.id

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FacePreview(data, style, selected = false)
            Text(spec.id.label(), style = MaterialTheme.typography.headlineSmall)
            Text(spec.id.description(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (spec.palettes.isNotEmpty()) {
                SheetLabel(R.string.gallery_color)
                val current = ClockFaceCatalog.paletteFor(settings, spec.id)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    spec.palettes.forEach { palette ->
                        ColorSwatch(palette, palette == current, expressive = spec.category == FaceCategory.DECORATIVE || spec.id == ClockTheme.DIGITAL_COLOR) {
                            onUpdate { it.copy(faceColors = it.faceColors + (spec.id to palette)) }
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.gallery_fixed_colors), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SheetLabel(R.string.gallery_time)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HourFormat.entries.forEach { format ->
                    FilterChip(
                        selected = settings.hourFormat == format,
                        onClick = { onUpdate { it.copy(hourFormat = format) } },
                        label = { Text(format.label()) },
                    )
                }
            }
            if (spec.supportsSeconds) {
                OptionSwitch(R.string.pref_show_seconds, settings.showSeconds) { v -> onUpdate { it.copy(showSeconds = v) } }
            }
            if (spec.supportsAmPm) {
                OptionSwitch(R.string.pref_show_am_pm, settings.showAmPm) { v -> onUpdate { it.copy(showAmPm = v) } }
            }
            if (spec.supportsDate) {
                OptionSwitch(R.string.pref_show_date, settings.showDate) { v -> onUpdate { it.copy(showDate = v) } }
                if (settings.showDate) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateStyle.entries.forEach { ds ->
                            FilterChip(
                                selected = settings.dateStyle == ds,
                                onClick = { onUpdate { it.copy(dateStyle = ds) } },
                                label = { Text(ds.label()) },
                            )
                        }
                    }
                }
            }
            if (spec.usesWorldClock) {
                SheetLabel(R.string.gallery_world)
                CityPicker(R.string.world_primary, settings.worldPrimaryCity, allowLocal = true, allowNone = false) { key ->
                    onUpdate { it.copy(worldPrimaryCity = key) }
                }
                CityPicker(R.string.world_secondary, settings.worldSecondaryCity, allowLocal = false, allowNone = true) { key ->
                    onUpdate { it.copy(worldSecondaryCity = key) }
                }
            }
            OptionSwitch(R.string.gallery_night_preview, nightPreview) { nightPreview = it }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!selected) {
                    Button(onClick = { onUpdate { it.copy(clockTheme = spec.id) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.gallery_use))
                    }
                }
                OutlinedButton(
                    onClick = {
                        onUpdate { it.copy(clockTheme = spec.id) }
                        onPreviewFullScreen()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.gallery_preview_full)) }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: Int) {
    Text(
        stringResource(text).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp).semantics { heading() },
    )
}

@Composable
private fun OptionSwitch(title: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun ColorSwatch(palette: ClockPalette, selected: Boolean, expressive: Boolean, onClick: () -> Unit) {
    val label = palette.label()
    val fill = if (expressive) {
        Brush.linearGradient(listOf(Color(palette.gradientStart), Color(palette.gradientEnd)))
    } else {
        Brush.linearGradient(listOf(Color(palette.primary), Color(palette.primary)))
    }
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .border(2.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(fill),
        )
    }
}

@Composable
private fun CityPicker(title: Int, key: String, allowLocal: Boolean, allowNone: Boolean, onSelect: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val localLabel = stringResource(R.string.world_local)
    val noneLabel = stringResource(R.string.world_none)
    fun nameOf(k: String) = when (k) {
        WorldCities.LOCAL -> localLabel
        WorldCities.NONE -> noneLabel
        else -> WorldCities.find(k)?.name ?: k
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button) { open = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(nameOf(key), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
    if (open) {
        val options = buildList {
            if (allowLocal) add(WorldCities.LOCAL)
            if (allowNone) add(WorldCities.NONE)
            addAll(WorldCities.all.map { it.key })
        }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(title)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(options) { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = option == key, role = Role.RadioButton) {
                                    onSelect(option)
                                    open = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == key, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Text(nameOf(option))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
