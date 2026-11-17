package com.drdisagree.glasslayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.ui.theme.Negative
import com.drdisagree.glasslayer.ui.theme.OnInk
import com.drdisagree.glasslayer.ui.theme.OnInkFaint
import com.drdisagree.glasslayer.ui.theme.OnInkMuted
import com.drdisagree.glasslayer.ui.theme.Warning

@Composable
fun StatusGlyph(tone: StatusTone, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    AnimatedContent(
        targetState = tone,
        transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.6f)) togetherWith fadeOut(tween(140)) },
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
        label = "statusGlyph"
    ) { current ->
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            when (current) {
                StatusTone.OK -> AppIcon(R.drawable.ic_check, tint = OnInk, size = size)
                StatusTone.WARNING -> AppIcon(R.drawable.ic_alert, tint = Warning, size = size)
                StatusTone.ERROR -> AppIcon(R.drawable.ic_cross, tint = Negative, size = size)
                StatusTone.BUSY -> {
                    val angle by rememberSpin(true, SPIN_MS)
                    AppIcon(R.drawable.ic_refresh, tint = OnInkMuted, size = size, modifier = Modifier.graphicsLayer { rotationZ = angle })
                }
                StatusTone.IDLE -> Box(Modifier.size(size * IDLE_RATIO).border(1.5.dp, OnInkFaint, CircleShape))
            }
        }
    }
}

private const val SPIN_MS = 900
private const val IDLE_RATIO = 0.6f
