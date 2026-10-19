package com.umutk.pulse.extras.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSlider
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.extras.ambient.AmbientMixer
import com.umutk.pulse.tr

/** Ambient sounds on top of the music, using the white noise mixer (same sounds, same service). */
@Composable
fun NoiseMixerDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val sounds by AmbientMixer.sounds.collectAsState()
    val levels by AmbientMixer.levels.collectAsState()
    val running by AmbientMixer.running.collectAsState()
    val master by AmbientMixer.master.collectAsState()
    LaunchedEffect(Unit) { if (AmbientMixer.sounds.value.isEmpty()) AmbientMixer.load(ctx) {} }
    val turkish = tr("en", "tr") == "tr"
    WinAlert(
        onDismissRequest = onClose,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PIcon("volume", T.text, 22.dp)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp).clickable { AmbientMixer.stopAll() }, contentAlignment = Alignment.Center) { PIcon("pause", T.text, 22.dp) }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 440.dp)) {
                PSlider(master.toFloat(), { AmbientMixer.setMaster(it.toInt()) }, 0f..100f, onDone = {})
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(sounds, key = { it.id }) { s ->
                        val on = s.id in running
                        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(if (turkish) s.tr else s.en, color = T.text, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = true))
                                PSwitch(on, { AmbientMixer.setEnabled(s.id, it) })
                            }
                            if (on) PSlider((levels[s.id] ?: 50).toFloat(), { AmbientMixer.setLevel(s.id, it.toInt()) }, 0f..100f, onDone = {})
                        }
                    }
                }
            }
        },
        confirmButton = { PButton("OK", onClick = onClose) },
    )
}
