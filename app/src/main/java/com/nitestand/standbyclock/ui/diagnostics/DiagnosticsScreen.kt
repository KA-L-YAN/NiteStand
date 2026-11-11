@file:OptIn(ExperimentalMaterial3Api::class)

package com.nitestand.standbyclock.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.diagnostics.DiagnosticsSection

/** Actions offered by the diagnostics screen; all open system settings pages. */
class DiagnosticsActions(
    val onBack: () -> Unit,
    val onCopy: () -> Unit,
    val onOpenScreenSaver: () -> Unit,
    val onOpenBattery: () -> Unit,
    val onOpenNotifications: () -> Unit,
    val onOpenFullScreenIntent: (() -> Unit)?,
)

@Composable
fun DiagnosticsScreen(sections: List<DiagnosticsSection>, events: List<String>, actions: DiagnosticsActions) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.diagnostics_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ActionButton(R.string.diagnostics_copy, actions.onCopy)
                        ActionButton(R.string.action_open_screensaver, actions.onOpenScreenSaver)
                        ActionButton(R.string.diagnostics_open_battery, actions.onOpenBattery)
                        ActionButton(R.string.diagnostics_open_notifications, actions.onOpenNotifications)
                        actions.onOpenFullScreenIntent?.let { ActionButton(R.string.diagnostics_open_fsi, it) }
                    }
                }
                items(sections) { section -> SectionCard(section) }
                item {
                    SectionCard(
                        DiagnosticsSection(
                            stringResource(R.string.diagnostics_events),
                            if (events.isEmpty()) listOf("" to stringResource(R.string.diagnostics_no_events)) else events.takeLast(30).reversed().map { "" to it },
                        ),
                        monospace = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(label)) }
}

@Composable
private fun SectionCard(section: DiagnosticsSection, monospace: Boolean = false) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            section.rows.forEach { (label, value) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (label.isNotEmpty()) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium.let { if (monospace) it.copy(fontFamily = FontFamily.Monospace) else it },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = if (label.isNotEmpty()) TextAlign.End else TextAlign.Start,
                        modifier = if (label.isNotEmpty()) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
