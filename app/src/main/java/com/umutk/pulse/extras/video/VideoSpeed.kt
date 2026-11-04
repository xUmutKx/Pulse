package com.umutk.pulse.extras.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSlider
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import kotlin.math.roundToInt

/** A speed as a whole number of hundredths, so the slider and the arrows never drift (1.07, not 1.0700001). */
internal fun snapSpeed(v: Float, lo: Float, hi: Float): Float = ((v * 100f).roundToInt() / 100f).coerceIn(lo, hi)

/**
 * The playback speed as a bar with an arrow on each side: drag the bar for any value between the two ends the user set in the settings
 * (0.25 to 2 at first), the arrows move it by 0.01. The button on the right puts it back to 1x.
 */
@Composable
internal fun VideoSpeedDialog(current: Float, min: Float, max: Float, onDismiss: () -> Unit, onChange: (Float) -> Unit) {
    val w = LocalWindowLook.current
    var value by remember { mutableFloatStateOf(snapSpeed(current, min, max)) }
    fun set(v: Float) { value = snapSpeed(v, min, max); onChange(value) }
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(tr("Speed", "Hız"), color = w.text, maxLines = 1) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "%.2fx".format(value), color = w.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // the arrow on the left lowers the speed by 0.01
                    Box(Modifier.size(44.dp).clickable { set(value - 0.01f) }, contentAlignment = Alignment.Center) {
                        PIcon("chevron", w.text, 22.dp, Modifier.graphicsLayer { rotationZ = 90f })
                    }
                    Box(Modifier.weight(1f)) { PSlider(value, { set(it) }, min..max, onDone = {}) }
                    // the arrow on the right raises it by 0.01
                    Box(Modifier.size(44.dp).clickable { set(value + 0.01f) }, contentAlignment = Alignment.Center) {
                        PIcon("chevron", w.text, 22.dp, Modifier.graphicsLayer { rotationZ = -90f })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(speedName(min), color = w.sub, fontSize = 12.sp)
                    Text(speedName(max), color = w.sub, fontSize = 12.sp)
                }
                Spacer(Modifier.height(2.dp))
                PButton(tr("Normal speed", "Normal hız"), Modifier.fillMaxWidth()) { set(1f) }
            }
        },
        confirmButton = { PButton(tr("OK", "Tamam"), onClick = onDismiss) },
    )
}
