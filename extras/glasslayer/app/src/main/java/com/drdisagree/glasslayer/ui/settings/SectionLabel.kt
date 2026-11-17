package com.drdisagree.glasslayer.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.ui.components.AppIcon
import com.drdisagree.glasslayer.ui.theme.OnInkMuted

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    Row(
        modifier = modifier.padding(start = 20.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) AppIcon(icon, tint = OnInkMuted, size = 16.dp)
        Text(text, style = MaterialTheme.typography.labelLarge, color = OnInkMuted)
    }
}
