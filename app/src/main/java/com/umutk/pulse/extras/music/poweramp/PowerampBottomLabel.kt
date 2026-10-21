package com.umutk.pulse.extras.music.poweramp

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PIcon
import com.umutk.pulse.PRadio
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** What the small chip under the Poweramp transport buttons says. [sample] is what the settings preview shows. */
enum class BottomLabel(val icon: String, val sample: String) {
    QUALITY("performance", "FLAC · 44.1 kHz · 16-bit · 940 kbps"),
    ARTIST("person", "Daft Punk"),
    ALBUM("image", "Discovery"),
    TITLE("music", "One More Time"),
    FOLDER("folder", "Music"),
    ;

    fun title() = when (this) {
        QUALITY -> tr("Sound quality", "Ses kalitesi")
        ARTIST -> tr("Artist", "Sanatçı")
        ALBUM -> tr("Album", "Albüm")
        TITLE -> tr("Song name", "Şarkı adı")
        FOLDER -> tr("Folder", "Klasör")
    }

    fun description() = when (this) {
        QUALITY -> tr("Format, sample rate, bit depth and bitrate of the file", "Dosyanın biçimi, örnekleme hızı, bit derinliği ve bit hızı")
        ARTIST -> tr("Who made the song", "Şarkıyı yapan sanatçı")
        ALBUM -> tr("The album the song is on", "Şarkının bulunduğu albüm")
        TITLE -> tr("The title of the song", "Şarkının adı")
        FOLDER -> tr("The folder the file sits in", "Dosyanın bulunduğu klasör")
    }
}

/** The saved choice. Sound quality is the default. */
object PowerampBottomLabel {
    private fun sp(c: Context) = c.getSharedPreferences("pulse_poweramp", Context.MODE_PRIVATE)
    fun get(c: Context): BottomLabel = BottomLabel.entries.firstOrNull { it.name == sp(c).getString("bottom_label", null) } ?: BottomLabel.QUALITY
    fun set(c: Context, v: BottomLabel) = sp(c).edit().putString("bottom_label", v.name).apply()
}

/** Settings: one row per choice with its icon, a short description and a preview of the chip, picked with a radio. */
@Composable
fun BottomLabelPicker(c: Context) {
    var cur by remember { mutableStateOf(PowerampBottomLabel.get(c)) }
    Text(tr("Text under the buttons", "Tuşların altındaki yazı"), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    BottomLabel.entries.forEach { o ->
        Row(Modifier.fillMaxWidth().clickable { cur = o; PowerampBottomLabel.set(c, o) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { PIcon(o.icon, if (cur == o) Look.accent else T.sub, 22.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(o.title(), color = T.text, fontSize = 15.sp)
                Text(o.description(), color = T.sub, fontSize = 12.sp)
                // the chip as the player draws it
                Box(Modifier.padding(top = 6.dp).clip(CircleShape).background(T.panel).padding(horizontal = 9.dp, vertical = 3.dp)) {
                    Text(o.sample, color = T.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            PRadio(cur == o) { cur = o; PowerampBottomLabel.set(c, o) }
        }
    }
}
