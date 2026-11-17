package com.drdisagree.glasslayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import com.drdisagree.glasslayer.ui.theme.OnInk

@Composable
fun Modifier.pressable(role: Role, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val overlay by animateFloatAsState(if (pressed) PRESSED_ALPHA else 0f, tween(PRESS_MS), label = "press")
    val ink = OnInk
    return this
        .drawBehind { if (overlay > 0f) drawRect(ink.copy(alpha = overlay)) }
        .clickable(interaction, null, role = role, onClick = onClick)
}

private const val PRESSED_ALPHA = 0.06f
private const val PRESS_MS = 160
