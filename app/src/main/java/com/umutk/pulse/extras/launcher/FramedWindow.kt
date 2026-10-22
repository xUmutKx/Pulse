package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.bevel

/** The frame for a launcher look: Windows 95 / XP / 7 / 10 / 11 as that Windows draws a window, anything else as a Material card. */
internal fun frameSkin(look: String): WinSkin = when (look) {
    "win7" -> W7_WINDOW
    "xp" -> XP_WINDOW
    "win95" -> W95_WINDOW
    "win10", "win10black" -> WinSkin("10", SolidColor(Color.White), Color(0xFF1A1A1A), Color(0xFF0078D7), 1.dp, 0.dp,
        Color.White, Color(0xFFF2F2F2), Color.White, Color(0xFF1A1A1A), Color(0xFF666666), Color(0xFFCCE4F7), Color(0xFF1A1A1A), Color(0xFF0078D7))
    "win11" -> WinSkin("11", SolidColor(Color(0xFFF3F3F3)), Color(0xFF1A1A1A), Color(0xFFD0D0D0), 1.dp, 10.dp,
        Color.White, Color(0xFFF3F3F3), Color.White, Color(0xFF1A1A1A), Color(0xFF666666), Color(0xFFE5E5E5), Color(0xFF1A1A1A), Color(0xFFD0D0D0))
    else -> WinSkin("mat", SolidColor(Color(0xFF1E1E22)), Color.White, Color(0xFF2B2B30), 0.dp, 24.dp,
        Color(0xFF121214), Color(0xFF1E1E22), Color(0xFF26262B), Color.White, Color(0xFFAAAAAA), Color(0xFF34343A), Color.White, Color(0xFF3A3A40))
}

/**
 * A Pulse page drawn inside a window with a title bar, caption buttons and a frame, floating over the launcher (the activity is see-through).
 * Any caption button, or a tap on the desktop around the window, closes it.
 */
@Composable
fun FramedWindow(look: String, title: String, onClose: () -> Unit, onBack: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val skin = remember(look) { frameSkin(look) }
    val none = remember { MutableInteractionSource() }
    // the window opens in the middle of the screen; the middle caption button grows it to the whole screen and back
    var maxed by remember { androidx.compose.runtime.mutableStateOf(false) }
    val kw by androidx.compose.animation.core.animateFloatAsState(if (maxed) 1f else .88f, androidx.compose.animation.core.tween(220), label = "winW")
    val kh by androidx.compose.animation.core.animateFloatAsState(if (maxed) 1f else .7f, androidx.compose.animation.core.tween(220), label = "winH")
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)).clickable(interactionSource = none, indication = null, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.Center).statusBarsPadding().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 14.dp).fillMaxWidth(kw).fillMaxHeight(kh)
                .clip(RoundedCornerShape(skin.corner)).background(skin.frame).then(if (skin.kind == "95") Modifier.bevel(true) else Modifier)
                .padding(skin.frameWidth).clickable(interactionSource = none, indication = null) {},
        ) {
            Row(Modifier.fillMaxWidth().height(if (skin.kind == "95") 20.dp else if (skin.kind == "7" || skin.kind == "xp") 30.dp else 36.dp).background(skin.title).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // inside a page of the window (a Control Panel item) a back arrow leads to where it came from
                if (onBack != null) Box(Modifier.size(28.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) { com.umutk.pulse.PIcon("back", skin.titleInk, 16.dp) }
                Text(title, color = skin.titleInk, fontSize = 13.sp, fontWeight = if (skin.kind == "xp" || skin.kind == "95") FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Caption(skin, 0, onClose); Caption(skin, 1) { maxed = !maxed }; Caption(skin, 2, onClose)
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(skin.body)) { content() }
        }
    }
}
