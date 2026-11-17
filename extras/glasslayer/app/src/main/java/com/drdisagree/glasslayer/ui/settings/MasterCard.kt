package com.drdisagree.glasslayer.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.ui.components.GlassContainer
import com.drdisagree.glasslayer.ui.components.LocalBackdrop
import com.drdisagree.glasslayer.ui.components.liquid.LiquidToggle
import com.drdisagree.glasslayer.ui.components.pressable
import com.drdisagree.glasslayer.ui.theme.Accent
import com.drdisagree.glasslayer.ui.theme.OnInk
import com.drdisagree.glasslayer.ui.theme.OnInkMuted
import com.drdisagree.glasslayer.ui.theme.Surface

@Composable
internal fun MasterCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val surface = Surface
    val accent = Accent
    val tint by animateColorAsState(
        if (enabled) accent.copy(alpha = ACCENT_TINT).compositeOver(surface) else surface,
        tween(TINT_MS),
        label = "masterTint"
    )
    GlassContainer(CardShape, Modifier.fillMaxWidth(), tint = { tint }) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressable(Role.Switch) { onEnabledChange(!enabled) }
                .padding(start = 20.dp, end = 16.dp, top = 22.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.master_title), style = MaterialTheme.typography.titleLarge, color = OnInk)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.master_summary), style = MaterialTheme.typography.bodySmall, color = OnInkMuted)
            }
            Spacer(Modifier.width(12.dp))
            LiquidToggle(enabled, onEnabledChange, LocalBackdrop.current)
        }
    }
}

private const val ACCENT_TINT = 0.2f
private const val TINT_MS = 360
