package com.drdisagree.glasslayer.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.ui.components.GlassIconButton
import com.drdisagree.glasslayer.ui.components.LocalBackdrop
import com.drdisagree.glasslayer.ui.components.liquid.LiquidSlider
import com.drdisagree.glasslayer.ui.theme.OnInk
import com.drdisagree.glasslayer.ui.theme.OnInkMuted
import kotlin.math.roundToInt

@Composable
internal fun LevelSlider(
    label: String,
    value: Float,
    max: Float,
    percentOfMax: Boolean,
    modified: Boolean,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
    onDraft: (Float) -> Unit = {}
) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 20.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = OnInk,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(
                visible = modified,
                enter = fadeIn(tween(RESET_FADE_MS)) + scaleIn(tween(RESET_FADE_MS), RESET_SCALE),
                exit = fadeOut(tween(RESET_FADE_MS)) + scaleOut(tween(RESET_FADE_MS), RESET_SCALE)
            ) {
                GlassIconButton(
                    R.drawable.ic_reset,
                    onReset,
                    size = 32.dp,
                    description = stringResource(R.string.action_reset),
                    iconTint = OnInkMuted
                )
            }
            Text(
                stringResource(R.string.value_percent, (if (percentOfMax) draft / max * PERCENT else draft).roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                color = OnInkMuted,
                modifier = Modifier.padding(start = 10.dp, end = 8.dp)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidSlider(
                value = draft,
                onValueChange = {
                    draft = it
                    onDraft(it)
                },
                valueRange = 0f..max,
                backdrop = LocalBackdrop.current,
                onValueChangeFinished = { onChange(draft) }
            )
        }
    }
}

private const val RESET_FADE_MS = 200
private const val RESET_SCALE = 0.6f
private const val PERCENT = 100f
