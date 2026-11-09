package com.umutk.pulse.extras.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.umutk.pulse.Look
import com.umutk.pulse.tr

/** The mascot's customize sheet (as in Claude Chat): the real mascot for every option, nothing explained. Opens with a long press on the mascot, or from the menu. */
@Composable
internal fun MascotSheet(look: MascotStyle.Look, onChange: (MascotStyle.Look) -> Unit, onClose: () -> Unit) {
    var tab by remember { mutableStateOf("hat") }
    val tabs = listOf("hat" to tr("Hat", "Şapka"), "skin" to tr("Colour", "Renk"), "face" to tr("Face", "Yüz"), "eyes" to tr("Eyes", "Gözler"), "feet" to tr("Shoes", "Ayakkabı"),
        "legs" to tr("Trousers", "Pantolon"), "body" to tr("Body", "Gövde"), "scene" to tr("Scene", "Arka plan"), "move" to tr("Move", "Hareket"))
    fun options(): List<String> = when (tab) {
        "hat" -> MascotStyle.HATS; "skin" -> MascotStyle.SKINS; "face" -> MascotStyle.FACE; "eyes" -> MascotStyle.EYES; "feet" -> MascotStyle.FEET
        "legs" -> MascotStyle.LEGS; "body" -> MascotStyle.BODY; "scene" -> MascotStyle.SCENES; else -> MascotStyle.MOVES
    }
    fun with(o: String): MascotStyle.Look = when (tab) {
        "hat" -> look.copy(hat = o); "skin" -> look.copy(skin = o); "face" -> look.copy(face = o); "eyes" -> look.copy(eyes = o); "feet" -> look.copy(feet = o)
        "legs" -> look.copy(legs = o); "body" -> look.copy(body = o); "scene" -> look.copy(scene = o); else -> look.copy(move = o)
    }
    fun chosen(): String = when (tab) {
        "hat" -> look.hat; "skin" -> look.skin; "face" -> look.face; "eyes" -> look.eyes; "feet" -> look.feet; "legs" -> look.legs; "body" -> look.body; "scene" -> look.scene; else -> look.move
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color(0xFF161616)).navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(120.dp).clip(CircleShape).background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
                    MascotView(look, Modifier.size(90.dp, 81.dp), scene = false)
                    if (look.scene != "none") MascotView(look, Modifier.size(120.dp), scene = true)
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tabs.forEach { (k, l) ->
                        Text(l, color = if (tab == k) Color.Black else Color(0xFFEDEDED), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (tab == k) Look.accent else Color(0xFF2A2A2A)).clickable { tab = k }.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
                LazyVerticalGrid(GridCells.Adaptive(64.dp), Modifier.fillMaxWidth().height(260.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(options()) { o ->
                        val on = chosen() == o
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF222222))
                            .border(if (on) BorderStroke(2.dp, Look.accent) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(14.dp)).clickable { onChange(with(o)) }, contentAlignment = Alignment.Center) {
                            when {
                                tab == "skin" && o in MascotStyle.SKIN_SWATCH -> Box(Modifier.size(34.dp).clip(CircleShape).background(Color(MascotStyle.SKIN_SWATCH.getValue(o))))
                                tab == "skin" -> MascotView(with(o), Modifier.size(44.dp, 40.dp))
                                tab == "move" -> Text(if (o == "none") "–" else o, color = Color(0xFFEDEDED), fontSize = 11.sp)
                                tab == "scene" -> MascotView(with(o), Modifier.size(64.dp), scene = true)
                                o == "none" || o == "dark" -> Text("–", color = Color(0xFF9A9A9A), fontSize = 20.sp)
                                else -> MascotView(with(o), Modifier.size(46.dp, 41.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
