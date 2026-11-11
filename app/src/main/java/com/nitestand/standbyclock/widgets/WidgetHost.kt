package com.nitestand.standbyclock.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.ui.theme.StandByStyle

/**
 * Collects a widget's data with the lifecycle of the hosting surface (activity or dream), so
 * the underlying receivers/timers stop as soon as the surface is not started.
 */
@Composable
fun WidgetHost(
    widget: StandByWidget<*>,
    sources: WidgetDataSources,
    settings: StandBySettings,
    style: StandByStyle,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNCHECKED_CAST")
    TypedWidgetHost(widget as StandByWidget<Any>, sources, settings, style, modifier)
}

@Composable
private fun <T : Any> TypedWidgetHost(
    widget: StandByWidget<T>,
    sources: WidgetDataSources,
    settings: StandBySettings,
    style: StandByStyle,
    modifier: Modifier,
) {
    val flow = remember(widget, sources, settings) { widget.observe(sources, settings) }
    val data by flow.collectAsStateWithLifecycle(initialValue = null)
    val current = data
    if (current != null) {
        widget.Content(current, style, modifier)
    } else {
        Box(modifier)
    }
}
