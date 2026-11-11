package com.nitestand.standbyclock.ui.standby

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nitestand.standbyclock.ui.theme.ClockDigits
import com.nitestand.standbyclock.ui.theme.StandByStyle

/** Small caption used by panel widgets ("CHARGING", "ALARM · TOMORROW"). */
@Composable
fun PanelLabel(text: String, style: StandByStyle, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.em,
            color = if (accent) style.accent else style.secondary,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Large value used by panel widgets ("85%", "06:30"). */
@Composable
fun PanelValue(text: String, style: StandByStyle, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = ClockDigits.merge(
            TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Light, color = style.primary, lineHeight = 1.1.em),
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
