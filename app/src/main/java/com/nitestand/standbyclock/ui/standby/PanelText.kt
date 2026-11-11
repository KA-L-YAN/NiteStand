package com.nitestand.standbyclock.ui.standby

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nitestand.standbyclock.ui.clock.ClockFont
import com.nitestand.standbyclock.ui.clock.asFixedSp
import com.nitestand.standbyclock.ui.theme.StandByStyle
import java.util.Locale

/** Size multiplier for floating information: 1 in the side panel, larger on the info page. */
val LocalPanelScale = staticCompositionLocalOf { 1f }

/**
 * Small caption of floating information ("CHARGING", "ALARM · TOMORROW"): tracked capitals,
 * secondary (or accent) colour. Sized in fixed dp so the composition never reflows with font scale.
 */
@Composable
fun PanelLabel(text: String, style: StandByStyle, modifier: Modifier = Modifier, accent: Boolean = false) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    Text(
        text.uppercase(locale),
        modifier = modifier,
        style = TextStyle(
            fontFamily = ClockFont.ROUNDED.family(760),
            fontSize = (13.dp * LocalPanelScale.current).asFixedSp(),
            letterSpacing = 0.12.em,
            color = if (accent) style.accent else style.secondary,
            fontFeatureSettings = "lnum",
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Main value of floating information ("85%", "06:30"): light rounded figures, primary colour. */
@Composable
fun PanelValue(text: String, style: StandByStyle, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = ClockFont.ROUNDED.family(300),
            fontSize = (38.dp * LocalPanelScale.current).asFixedSp(),
            letterSpacing = 0.0.em,
            lineHeight = 1.05.em,
            color = style.primary,
            fontFeatureSettings = "pnum, lnum",
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
