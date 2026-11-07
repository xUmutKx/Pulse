package com.umutk.pulse.extras.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSlider
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T

data class ThemeItem(val id: String, val name: String, val preview: @Composable () -> Unit)

@Composable
private fun ThemeCell(it: ThemeItem, sel: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val accent = com.umutk.pulse.Look.accent
    Column(modifier.clickable { onSelect(it.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.4f)) {
            Box(Modifier.fillMaxSize().clip(shape)
                .border(if (sel) BorderStroke(3.dp, accent) else BorderStroke(1.dp, T.sub.copy(alpha = .3f)), shape)) { it.preview() }
            // the check mark marks the chosen tile even when the accent colour is close to the preview
            if (sel) PIcon("check", accent, 16.dp, Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
        Text(it.name, color = if (sel) accent else T.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}

/** 3 per row; collapsed shows only the first row, fading out above a chevron. Plain Column, safe inside scroll parents. */
@Composable
fun ThemeGrid(items: List<ThemeItem>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val f by animateFloatAsState(if (open) 1f else 0f, label = "themeGrid")
    val rows = items.chunked(3)
    @Composable fun RowOf(r: List<ThemeItem>) = Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        r.forEach { ThemeCell(it, it.id == selectedId, onSelect, Modifier.weight(1f)) }
        repeat(3 - r.size) { Box(Modifier.weight(1f)) }
    }
    Column(modifier.fillMaxWidth().animateContentSize()) {
        if (rows.isNotEmpty()) Box(Modifier.fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (rows.size > 1 && f < 1f) drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height * .45f), alpha = 1f - f, blendMode = BlendMode.DstIn)
            }) { RowOf(rows[0]) }
        if (rows.size > 1) {
            AnimatedVisibility(open) { Column { rows.drop(1).forEach { RowOf(it) } } }
            Box(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                PIcon("chevron", T.sub, 24.dp, Modifier.graphicsLayer { rotationZ = 180f * f })
            }
        }
    }
}

/** A settings page that shows other groups than before (another category) starts with every group closed: it bumps [epoch]. */
object SectionReset {
    private var n = 0
    fun next() = ++n
    var epoch = 0
}

/** Collapsible settings group: title row with a chevron. */
@Composable
fun SettingsSection(title: String, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title, SectionReset.epoch) { mutableStateOf(startOpen) }
    val f by animateFloatAsState(if (open) 1f else 0f, label = "section")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = T.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            PIcon("chevron", T.sub, 24.dp, Modifier.graphicsLayer { rotationZ = 180f * f })
        }
        AnimatedVisibility(open) { Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        PSwitch(checked, onChange)
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = T.text, fontSize = 14.sp)
        PSlider(value, onChange, range, onDone = {})
    }
}

/** A settings group on its own page: the title on top and the rows always shown (no folding, no arrow). */
@Composable
fun FlatSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = {
        Text(title, color = T.sub, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        content()
    })
}
