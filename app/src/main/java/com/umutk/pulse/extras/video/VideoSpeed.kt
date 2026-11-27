package com.umutk.pulse.extras.video

import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.PCheck
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSlider
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import kotlin.math.roundToInt

/** A speed as a whole number of hundredths, so the slider and the arrows never drift (1.07, not 1.0700001). */
internal fun snapSpeed(v: Float, lo: Float, hi: Float): Float = ((v * 100f).roundToInt() / 100f).coerceIn(lo, hi)

/**
 * The playback speed, short: the value in the title, a bar with an arrow on each side (0.01 steps, between the two ends set in the settings), and one row with
 * the "remember for the next videos" box and a button back to 1x.
 */
@Composable
internal fun VideoSpeedDialog(current: Float, min: Float, max: Float, remember: Boolean, onRemember: (Boolean) -> Unit, onDismiss: () -> Unit, onChange: (Float) -> Unit) {
    val w = LocalWindowLook.current
    var value by remember { mutableFloatStateOf(snapSpeed(current, min, max)) }
    var keep by remember { mutableStateOf(remember) }
    fun set(v: Float) { value = snapSpeed(v, min, max); onChange(value) }
    WinAlert(
        onDismissRequest = onDismiss, containerColor = androidx.compose.ui.graphics.Color.Black,
        title = { Text(tr("Speed", "Hız") + "  %.2fx".format(value), color = w.text, maxLines = 1, fontSize = 18.sp) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clickable { set(value - 0.01f) }, contentAlignment = Alignment.Center) {
                        PIcon("chevron", w.text, 20.dp, Modifier.graphicsLayer { rotationZ = 90f })
                    }
                    Box(Modifier.weight(1f)) { PSlider(value, { set(it) }, min..max, onDone = {}) }
                    Box(Modifier.size(40.dp).clickable { set(value + 0.01f) }, contentAlignment = Alignment.Center) {
                        PIcon("chevron", w.text, 20.dp, Modifier.graphicsLayer { rotationZ = -90f })
                    }
                }
                // the usual speeds in one row of their own, the one in use lit
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f).filter { it in min..max }.forEach { sp ->
                        val on = kotlin.math.abs(value - sp) < .005f
                        val label = (if (sp % 1f == 0f) "${sp.toInt()}" else "$sp") + "x"
                        Text(label, color = if (on) com.umutk.pulse.onAccent(com.umutk.pulse.Look.accent) else w.text, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).then(if (on) Modifier.background(com.umutk.pulse.Look.accent) else Modifier.border(1.dp, w.text.copy(alpha = .28f), RoundedCornerShape(16.dp)))
                                .clickable { set(sp) }.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                // keeping the speed for the next videos: a row under the speeds (it lay over them)
                Row(Modifier.fillMaxWidth().clickable { keep = !keep; onRemember(keep) }.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PCheck(keep, { keep = it; onRemember(it) })
                    Text(tr("Keep for the next videos", "Sonraki videolarda da kalsın"), color = w.text, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp), maxLines = 2)
                }
            }
        },
        confirmButton = { PButton(tr("OK", "Tamam"), icon = androidx.compose.material.icons.Icons.Outlined.Check, onClick = onDismiss) },
    )
}
