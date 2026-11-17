package com.drdisagree.glasslayer.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.ui.components.GlassContainer
import com.drdisagree.glasslayer.ui.components.LocalBackdrop
import com.drdisagree.glasslayer.ui.components.liquid.LiquidButton
import com.drdisagree.glasslayer.ui.theme.Caution
import com.drdisagree.glasslayer.ui.theme.OnInk
import com.drdisagree.glasslayer.ui.theme.OnInkMuted
import com.drdisagree.glasslayer.ui.theme.Surface

@Composable
internal fun RestartBanner(state: RestartState, safeMode: Boolean, onRestart: () -> Unit, modifier: Modifier = Modifier) {
    val running = state == RestartState.RUNNING
    val ready = !running
    val labelAlpha by animateFloatAsState(if (ready) 1f else BUSY_ALPHA, tween(FADE_MS), label = "restartLabel")
    val tint = Caution.copy(alpha = CAUTION_TINT).compositeOver(Surface)
    GlassContainer(CardShape, modifier.fillMaxWidth(), tint = { tint }) {
        Row(
            Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (safeMode) R.string.safe_mode_title else R.string.restart_title), style = MaterialTheme.typography.titleSmall, color = OnInk)
                val summary = when {
                    state == RestartState.RUNNING -> stringResource(R.string.restart_running)
                    state == RestartState.FAILED -> stringResource(R.string.restart_failed)
                    safeMode -> stringResource(R.string.safe_mode_summary)
                    else -> stringResource(R.string.restart_summary)
                }
                AnimatedContent(
                    targetState = summary,
                    transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) },
                    label = "restartSummary"
                ) { current ->
                    Text(
                        current,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnInkMuted
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            LiquidButton(
                onClick = onRestart,
                backdrop = LocalBackdrop.current,
                tint = Caution,
                height = 40.dp,
                horizontalPadding = 18.dp,
                enabled = ready
            ) {
                Text(stringResource(R.string.action_restart), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = labelAlpha))
            }
        }
    }
}

private const val FADE_MS = 220
private const val BUSY_ALPHA = 0.5f
private const val CAUTION_TINT = 0.22f
