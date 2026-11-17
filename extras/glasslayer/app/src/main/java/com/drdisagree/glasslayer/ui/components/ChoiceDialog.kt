package com.drdisagree.glasslayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.ui.components.liquid.InteractiveHighlight
import com.drdisagree.glasslayer.ui.theme.Accent
import com.drdisagree.glasslayer.ui.theme.Ink
import com.drdisagree.glasslayer.ui.theme.OnInk
import com.drdisagree.glasslayer.ui.theme.OnInkFaint
import com.drdisagree.glasslayer.ui.theme.OnInkMuted

@Composable
fun BoxScope.ChoiceDialog(
    visible: Boolean,
    title: String,
    options: List<ChoiceOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(visible, onDismiss)
    val pop = remember { PopAnimation() }
    LaunchedEffect(visible) { if (visible) pop.show() else pop.hide() }
    if (!visible && pop.hidden) return
    val panelTint = Ink.copy(alpha = PANEL_TINT)
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = pop.alpha }
            .background(SCRIM)
            .pointerInput(visible) {
                if (visible) detectTapGestures { onDismiss() }
            }
    )
    Column(
        Modifier
            .align(Alignment.Center)
            .padding(horizontal = 28.dp)
            .widthIn(max = MAX_WIDTH)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pop.scaleX
                scaleY = pop.scaleY
                alpha = pop.alpha
            }
            .pointerInput(Unit) { detectTapGestures { } }
            .stretchyGlass(LocalBackdrop.current, DialogShape, panelTint, highlight, PANEL_BLUR)
            .clip(DialogShape)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = OnInk,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
        )
        Column(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                OptionRow(option, index == selected) {
                    if (visible) {
                        onSelect(index)
                        onDismiss()
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun OptionRow(option: ChoiceOption, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(Role.RadioButton, onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioMark(selected)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(option.title, style = MaterialTheme.typography.titleSmall, color = OnInk)
            Text(option.summary, style = MaterialTheme.typography.bodySmall, color = OnInkMuted)
        }
    }
}

@Composable
private fun RadioMark(selected: Boolean) {
    val amount by animateFloatAsState(if (selected) 1f else 0f, spring(DAMPING, STIFFNESS), label = "radioMark")
    val idle = OnInkFaint
    val accent = Accent
    Canvas(Modifier.size(RADIO_SIZE)) {
        val stroke = RING_WIDTH.toPx()
        val radius = size.minDimension / 2f
        drawCircle(lerp(idle, accent, amount), radius - stroke / 2f, Offset(radius, radius), style = Stroke(stroke))
        if (amount > 0f) drawCircle(accent, (radius - stroke * 2.2f) * amount.coerceIn(0f, 1.2f), Offset(radius, radius))
    }
}

private val DialogShape = RoundedCornerShape(32.dp)
private val SCRIM = Color.Black.copy(alpha = 0.32f)
private val MAX_WIDTH = 400.dp
private val PANEL_BLUR = 18.dp
private val RADIO_SIZE = 22.dp
private val RING_WIDTH = 2.dp
private const val DAMPING = 0.78f
private const val STIFFNESS = 420f
private const val PANEL_TINT = 0.72f
