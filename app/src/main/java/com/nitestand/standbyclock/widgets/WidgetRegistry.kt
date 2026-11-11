package com.nitestand.standbyclock.widgets

import com.nitestand.standbyclock.settings.StandBySettings

/** Ordered list of widgets that can appear in the StandBy side panel. */
object WidgetRegistry {

    val clock: ClockWidget = ClockWidget

    val panelWidgets: List<StandByWidget<*>> = listOf(DateWidget, BatteryWidget, NextAlarmWidget, WeatherWidget)

    fun enabledPanelWidgets(settings: StandBySettings): List<StandByWidget<*>> =
        if (!settings.showPanel) emptyList() else panelWidgets.filter { it.isEnabled(settings) }
}
