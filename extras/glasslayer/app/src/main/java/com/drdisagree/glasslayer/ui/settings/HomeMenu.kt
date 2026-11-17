package com.drdisagree.glasslayer.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.ui.components.AppIcon
import com.drdisagree.glasslayer.ui.components.LocalBackdrop
import com.drdisagree.glasslayer.ui.components.PopAnimation
import com.drdisagree.glasslayer.ui.components.liquid.InteractiveHighlight
import com.drdisagree.glasslayer.ui.components.pressable
import com.drdisagree.glasslayer.ui.components.stretchyGlass
import com.drdisagree.glasslayer.ui.theme.Ink
import com.drdisagree.glasslayer.ui.theme.OnInk

@Composable
internal fun BoxScope.HomeMenu(
    visible: Boolean,
    issue: Color?,
    onStatus: () -> Unit,
    onAbout: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(visible, onDismiss)
    val pop = remember { PopAnimation() }
    LaunchedEffect(visible) { if (visible) pop.show() else pop.hide() }
    if (!visible && pop.hidden) return
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val tint = Ink.copy(alpha = PANEL_TINT)
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    if (visible) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )
    }
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .padding(top = statusTop + MENU_TOP, end = 12.dp)
            .width(MENU_WIDTH)
            .graphicsLayer {
                transformOrigin = TransformOrigin(1f, 0f)
                scaleX = pop.scaleX
                scaleY = pop.scaleY
                alpha = pop.alpha
            }
            .stretchyGlass(LocalBackdrop.current, MenuShape, tint, highlight, PANEL_BLUR)
            .clip(MenuShape)
            .padding(vertical = 6.dp)
    ) {
        MenuItem(R.drawable.ic_pulse, stringResource(R.string.status_title), issue) {
            onDismiss()
            onStatus()
        }
        // One UI edition: which apps get glass, the idle frame rate and the inspector
        val ctx = androidx.compose.ui.platform.LocalContext.current
        MenuItem(R.drawable.ic_section_system_apps, stringResource(R.string.oneui_title), null) {
            onDismiss()
            ctx.startActivity(android.content.Intent(ctx, com.drdisagree.glasslayer.ui.oneui.OneUiActivity::class.java))
        }
        MenuItem(R.drawable.ic_info, stringResource(R.string.about_title), null) {
            onDismiss()
            onAbout()
        }
    }
}

@Composable
private fun MenuItem(@DrawableRes icon: Int, label: String, dot: Color?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(Role.Button, onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(icon, tint = OnInk, size = 20.dp)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = OnInk, modifier = Modifier.weight(1f))
        if (dot != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(DOT_SIZE)
                    .background(dot, CircleShape)
            )
        }
    }
}

private val MenuShape = RoundedCornerShape(24.dp)
private val MENU_WIDTH = 210.dp
private val MENU_TOP = 60.dp
private val PANEL_BLUR = 16.dp
private val DOT_SIZE = 8.dp
private const val PANEL_TINT = 0.62f
