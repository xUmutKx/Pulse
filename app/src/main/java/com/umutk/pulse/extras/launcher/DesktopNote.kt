package com.umutk.pulse.extras.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.tr

private val NOTE_COLORS = listOf(0xFFFFF4A3, 0xFFFFD6E0, 0xFFCDEBFF, 0xFFD3F5C8, 0xFFE8D8FF, 0xFFFFFFFF).map { Color(it) }

/**
 * The Windows 7 "Sticky Notes" gadget on the desktop: a yellow note you type into with the keyboard, moved by its top bar, and turned over with the
 * "i" button to pick another colour or clear it (the gadget flips like the real ones). The text is kept as you type.
 */
@Composable
fun MovableDesktopNote() {
    if (!LauncherPrefs.desktopNote) return
    val ctx = LocalContext.current
    val d = LocalDensity.current.density
    val prefs = remember { ctx.getSharedPreferences("launcher_note", android.content.Context.MODE_PRIVATE) }
    var text by remember { mutableStateOf(prefs.getString("text", "").orEmpty()) }
    var colour by remember { mutableIntStateOf(prefs.getInt("colour", 0).coerceIn(0, NOTE_COLORS.lastIndex)) }
    var back by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (back) 180f else 0f, tween(420), label = "noteTurn")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        fun pos(): FloatArray = LauncherPrefs.widgetPos[-2]?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 2 }?.toFloatArray() ?: floatArrayOf(14f, 120f)
        val p = pos()
        Box(Modifier.offset(p[0].dp, p[1].dp).size(200.dp, 210.dp).graphicsLayer { rotationY = turn; cameraDistance = 14f * density }) {
            val base = NOTE_COLORS[colour]
            val showBack = turn > 90f
            Column(Modifier.fillMaxSize().graphicsLayer { if (showBack) rotationY = 180f }.clip(RoundedCornerShape(3.dp)).background(base)) {
                // the top bar moves the note; "i" turns it over, the cross takes it off the desktop
                Row(Modifier.fillMaxWidth().height(26.dp).background(Color.Black.copy(alpha = .08f)).pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { LauncherPrefs.save(ctx) }) { c, drag -> c.consume(); val q = pos(); LauncherPrefs.widgetPos = LauncherPrefs.widgetPos + (-2 to "${(q[0] + drag.x / d).coerceAtLeast(0f)},${(q[1] + drag.y / d).coerceAtLeast(0f)}") }
                }, verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(26.dp).clickable { back = !back }, contentAlignment = Alignment.Center) { Text("i", color = Color(0xFF444444), fontSize = 14.sp) }
                    Box(Modifier.size(26.dp).clickable { LauncherPrefs.desktopNote = false; LauncherPrefs.save(ctx) }, contentAlignment = Alignment.Center) { PIcon("close", Color(0xFF444444), 14.dp) }
                }
                if (!showBack) BasicTextField(
                    text, { text = it; prefs.edit().putString("text", it).apply() },
                    Modifier.fillMaxSize().padding(10.dp),
                    textStyle = TextStyle(color = Color(0xFF2B2B2B), fontSize = 15.sp, lineHeight = 20.sp),
                    cursorBrush = SolidColor(Color(0xFF2B2B2B)),
                    decorationBox = { inner -> if (text.isEmpty()) Text(tr("Type a note…", "Not yaz…"), color = Color(0x66000000), fontSize = 15.sp); inner() },
                ) else Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    Text(tr("Colour", "Renk"), color = Color(0xFF444444), fontSize = 13.sp)
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        NOTE_COLORS.forEachIndexed { i, c ->
                            Box(Modifier.size(24.dp).clip(CircleShape).background(c).clickable { colour = i; prefs.edit().putInt("colour", i).apply() }
                                .then(if (i == colour) Modifier.background(Color.Black.copy(alpha = .18f)) else Modifier))
                        }
                    }
                    Text(tr("Clear the note", "Notu temizle"), color = Color(0xFF8A1C1C), fontSize = 14.sp, modifier = Modifier.clickable { text = ""; prefs.edit().putString("text", "").apply() })
                }
            }
        }
    }
}
