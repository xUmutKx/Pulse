package com.umutk.pulse.extras.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** The buttons the player can show on its bar; the ones not chosen stay in the ⋮ menu. */
internal val CONTROL_IDS = listOf("cc", "audio", "rotate", "repeat", "ab", "scale", "speed", "sleep", "pip", "lock", "settings")

/** The bar at the start: subtitles, rotation and picture in picture. */
internal const val DEFAULT_BAR = "cc,rotate,pip"

/** The chosen buttons in their fixed order (unknown names are dropped). */
internal fun barList(s: String): List<String> = s.split(',').map { it.trim() }.filter { it in CONTROL_IDS }.distinct()

internal fun controlName(id: String): String = when (id) {
    "cc" -> tr("Subtitles", "Altyazı")
    "audio" -> tr("Audio", "Ses")
    "rotate" -> tr("Rotate", "Döndür")
    "repeat" -> tr("Repeat", "Tekrar")
    "ab" -> tr("A-B repeat", "A-B tekrar")
    "scale" -> tr("Fit", "Sığdır")
    "speed" -> tr("Speed", "Hız")
    "sleep" -> tr("Sleep timer", "Uyku zamanlayıcı")
    "pip" -> tr("Picture in picture", "Resim içinde resim")
    "lock" -> tr("Lock", "Kilitle")
    "settings" -> tr("Settings", "Ayarlar")
    else -> id
}

/** One control's picture: the same icon on the bar, in the ⋮ menu and in the settings preview. [on] tints it with the skin's accent. */
@Composable
internal fun ControlGlyph(id: String, tint: Color, sk: VSkin, on: Boolean) {
    val c = if (on) sk.accent else tint
    when (id) {
        "cc" -> Text("CC", color = c, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        "audio" -> PIcon("volume", c, 22.dp)
        "rotate" -> PIcon("rotate", c, 22.dp)
        "repeat" -> com.umutk.pulse.extras.ui.TIcon("repeat", c, 22.dp, sk.icons)
        "ab" -> Icon(Icons.Outlined.Flip, null, Modifier.size(22.dp), tint = c)
        "scale" -> PIcon("scale", c, 22.dp)
        "speed" -> PIcon("speed", c, 22.dp)
        "sleep" -> PIcon("timer", c, 22.dp)
        "pip" -> Icon(Icons.Outlined.PictureInPictureAlt, null, Modifier.size(22.dp), tint = c)
        "lock" -> Icon(Icons.Outlined.Lock, null, Modifier.size(22.dp), tint = c)
        "settings" -> PIcon("settings", c, 22.dp)
    }
}

/**
 * The settings part that picks the player's buttons: a small preview of the bar (tap a button there to take it off) and
 * the list of every button under it (tap to put it on or take it off). The order on the bar is the list's order.
 */
@Composable
internal fun ButtonsEditor(prefs: VideoPrefs, changed: () -> Unit) {
    var cur by remember { mutableStateOf(barList(prefs.barButtons)) }
    fun toggle(id: String) {
        val next = CONTROL_IDS.filter { (it in cur && it != id) || (it == id && it !in cur) }
        cur = next
        prefs.barButtons = next.joinToString(",")
        changed()
    }
    val sk = skinOf(prefs.skin)
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x66FFFFFF)))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                cur.forEach { id ->
                    Box(Modifier.size(38.dp).clickable { toggle(id) }, contentAlignment = Alignment.Center) { ControlGlyph(id, Color.White, sk, false) }
                }
            }
        }
    }
    CONTROL_IDS.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { id ->
                val on = id in cur
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (on) com.umutk.pulse.Look.accent.copy(alpha = .22f) else T.text.copy(alpha = .06f))
                        .clickable { toggle(id) }.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlGlyph(id, if (on) com.umutk.pulse.Look.accent else T.sub, sk, on)
                    Spacer(Modifier.width(6.dp))
                    Text(controlName(id), color = T.text, fontSize = 12.sp, maxLines = 1)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
