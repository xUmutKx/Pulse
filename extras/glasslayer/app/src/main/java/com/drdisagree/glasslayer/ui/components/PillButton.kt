package com.drdisagree.glasslayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.ui.components.liquid.LiquidButton
import com.drdisagree.glasslayer.ui.theme.onTint

@Composable
fun PillButton(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    icon: Int? = null,
    spinning: Boolean = false,
    enabled: Boolean = true
) {
    val animatedTint by animateColorAsState(tint, tween(TINT_MS), label = "pillTint")
    val angle by rememberSpin(spinning, SPIN_MS)
    val contentAlpha by animateFloatAsState(if (enabled) 1f else BUSY_ALPHA, tween(TINT_MS), label = "pillContent")
    LiquidButton(onClick, LocalBackdrop.current, tint = animatedTint, height = 38.dp, horizontalPadding = 16.dp, enabled = enabled) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                AppIcon(
                    icon,
                    tint = onTint(tint).copy(alpha = contentAlpha),
                    size = 18.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = angle }
                )
            }
            AnimatedContent(
                targetState = label,
                transitionSpec = { fadeIn(tween(LABEL_IN_MS)) togetherWith fadeOut(tween(LABEL_OUT_MS)) },
                label = "pillLabel"
            ) { text ->
                Text(text, style = MaterialTheme.typography.labelLarge, color = onTint(tint).copy(alpha = contentAlpha), maxLines = 1)
            }
        }
    }
}

private const val TINT_MS = 300
private const val LABEL_IN_MS = 220
private const val LABEL_OUT_MS = 120
private const val SPIN_MS = 800
private const val BUSY_ALPHA = 0.5f
