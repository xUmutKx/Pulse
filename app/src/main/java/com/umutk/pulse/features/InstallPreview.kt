package com.umutk.pulse.features

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.umutk.pulse.bevel

/** A miniature of the install window in look [id]; follows the name / version / status switches. */
@Composable
fun InstallPreview(id: String) {
    val w = installLook(id)
    val r = w.radius / 3
    val classic = w.id == "win95" || w.id == "xp"
    val bodyBg = when (w.id) { "win95" -> Color(0xFFC0C0C0); "xp" -> Color(0xFFECE9D8); "win7" -> Color.White; else -> w.panel }
    val ink = if (classic || w.id == "win7") Color.Black else w.text
    val sub = if (classic || w.id == "win7") Color(0xFF555555) else w.sub
    val frame = when (w.id) {
        "win95" -> Modifier.background(bodyBg).bevel(true).padding(2.dp)
        "xp" -> Modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Color(0xFF0054E3)).padding(horizontal = 1.dp).padding(bottom = 1.dp)
        "win7" -> Modifier.clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Color(0xFFC9DEF3), Color(0xFF9DBDE0)))).padding(2.dp)
        "win10" -> Modifier.background(bodyBg).border(1.dp, w.accent)
        else -> Modifier.clip(RoundedCornerShape(r)).background(bodyBg).border(1.dp, w.line, RoundedCornerShape(r))
    }
    Column(Modifier.fillMaxWidth().then(frame)) {
        if (w.id in setOf("win95", "xp", "win7", "win10", "win11")) {
            Box(Modifier.fillMaxWidth().height(9.dp).background(when (w.id) { "win95" -> Color(0xFF000080); "xp" -> Color(0xFF0A60EE); else -> Color.Transparent }))
        }
        Column(Modifier.fillMaxWidth().background(bodyBg).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(18.dp).background(w.accent, RoundedCornerShape(if (w.radius > 0.dp) 5.dp else 0.dp)))
            if (InstallShow.name) Text("App", color = ink, fontSize = 9.sp, maxLines = 1)
            if (InstallShow.version) Text("1.2.0 (12)", color = sub, fontSize = 7.sp, maxLines = 1)
            if (InstallShow.chip) Box(Modifier.background(Color(0xFFC8E6C9), RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                Text("New", color = Color(0xFF1B5E20), fontSize = 6.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(true, false).forEach { main ->
                    val shape = if (w.id == "oneui" || w.id == "coloros") RoundedCornerShape(50) else RoundedCornerShape(if (w.radius > 0.dp) 4.dp else 0.dp)
                    val b = when {
                        w.bevel || w.id == "win95" -> Modifier.background(Color(0xFFC0C0C0)).bevel(true)
                        main -> Modifier.background(w.accent, shape)
                        else -> Modifier.background(w.line.copy(alpha = .6f), shape)
                    }
                    Box(Modifier.size(24.dp, 9.dp).then(b), contentAlignment = Alignment.Center) {
                        Text(if (main) "Install" else "Cancel", fontSize = 5.sp, maxLines = 1,
                            color = if (main && !classic && w.id != "win95") Color.White else Color.Black)
                    }
                }
            }
        }
    }
}
