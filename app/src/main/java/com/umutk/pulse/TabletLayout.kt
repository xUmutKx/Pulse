package com.umutk.pulse

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Where the pages sit on a tablet: centred (the default), on the left, on the right, or the full width of the screen. */
object TabletLayout {
    private const val KEY = "tablet_layout"
    var mode by mutableStateOf("center")

    fun load(c: Context) {
        mode = c.getSharedPreferences("p", Context.MODE_PRIVATE).getString(KEY, "center") ?: "center"
    }

    fun set(c: Context, id: String) {
        mode = id
        c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putString(KEY, id).apply()
    }
}

/** The choice in the settings: four chips, the chosen one lit. */
@Composable
fun TabletLayoutRow() {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Tablet layout", "Tablet düzeni"), color = T.sub, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "center" to tr("Centre", "Ortada"),
                "left" to tr("Left", "Sol"),
                "right" to tr("Right", "Sağ"),
                "full" to tr("Full screen", "Tam ekran"),
            ).forEach { (id, label) ->
                val on = TabletLayout.mode == id
                Text(label, color = if (on) T.accent else T.text, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) T.accent.copy(alpha = .18f) else T.panel)
                        .clickable { TabletLayout.set(ctx, id) }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}
