package com.umutk.pulse.extras.palette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.PNameField
import com.umutk.pulse.T
import com.umutk.pulse.extras.palette.core.icon.IconOverride
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr

/** Per-app control: search, favourite, lock, leave out, or force one colour of the palette. */
@Composable
fun AppsTab(st: PaletteState) {
    val spec = st.spec
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    val filtered = remember(st.apps, query) { if (query.isBlank()) st.apps else st.apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) } }
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        PNameField(query, { query = it }, tr("Search ${st.apps.size} apps", "${st.apps.size} uygulamada ara"), Modifier.fillMaxWidth().padding(vertical = 8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.key }) { app ->
                val ov = st.overrides[app.key] ?: IconOverride(app.key)
                val idx = st.apps.indexOf(app)
                Column {
                    Row(Modifier.fillMaxWidth().clickable { expanded = if (expanded == app.key) null else app.key }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ThemedIcon(app, spec, st.engine, idx, 48.dp, ov)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(app.label, color = T.text, fontSize = 16.sp, maxLines = 1)
                            Text(app.packageName, color = T.sub, fontSize = 11.sp, maxLines = 1)
                        }
                        Toggle("★", ov.favorite) { st.setOverride(ov.copy(favorite = !ov.favorite)) }
                        Toggle("🔒", ov.locked) { st.setOverride(ov.copy(locked = !ov.locked)) }
                        Toggle("⊘", ov.excluded) { st.setOverride(ov.copy(excluded = !ov.excluded)) }
                    }
                    if (expanded == app.key) {
                        Row(Modifier.padding(start = 62.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            spec.palette.colors.forEachIndexed { i, c ->
                                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c)).border(if (ov.colorIndex == i) 3.dp else 0.dp, T.text, CircleShape).clickable { st.setOverride(ov.copy(colorIndex = if (ov.colorIndex == i) -1 else i)) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A small round on/off mark: star (favourite), lock (keep the colour), leave out. */
@Composable
private fun Toggle(glyph: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(if (on) T.accent.copy(alpha = .22f) else Color.Transparent).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(glyph, color = if (on) T.accent else T.sub, fontSize = 17.sp)
    }
}
