package com.umutk.pulse.extras.video

import androidx.compose.foundation.clickable
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
        onDismissRequest = onDismiss,
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable { keep = !keep; onRemember(keep) }, verticalAlignment = Alignment.CenterVertically) {
                        PCheck(keep, { keep = it; onRemember(it) })
                        Text(tr("Keep for the next videos", "Sonraki videolarda da kalsın"), color = w.text, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp), maxLines = 2)
                    }
                    PButton("1x", compact = true) { set(1f) }
                }
            }
        },
        confirmButton = { PButton(tr("OK", "Tamam"), onClick = onDismiss) },
    )
}
