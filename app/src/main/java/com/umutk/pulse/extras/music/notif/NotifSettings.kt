package com.umutk.pulse.extras.music.notif

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.umutk.pulse.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PSwitch
import com.umutk.pulse.R
import com.umutk.pulse.T
import com.umutk.pulse.extras.music.MusicPlaybackService
import com.umutk.pulse.extras.music.MusicSettings
import com.umutk.pulse.extras.ui.SettingsSection
import com.umutk.pulse.extras.ui.SwitchRow
import com.umutk.pulse.tr

/**
 * "Notification" section of the music settings: which buttons the media notification shows and in what order.
 * Enabled buttons come first in their order; Android has room for [NotifBtn.MAX], the rest are dimmed.
 */
@Composable
fun NotificationSettingsSection() {
    val c = LocalContext.current
    var ids by remember { mutableStateOf(MusicSettings.notificationButtonIds(c).filter { NotifBtn.of(it) != null }) }
    var unplug by remember { mutableStateOf(MusicSettings.pauseOnUnplug(c)) }
    var allowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    fun save(l: List<String>) { ids = l; MusicSettings.setNotificationButtonIds(c, l); MusicPlaybackService.relayout() }

    SettingsSection(tr("Buttons", "Tuşlar"), startOpen = true) {
        if (!allowed) SwitchRow(tr("Show notification", "Bildirimi göster"), false) { if (it) ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
        SwitchRow(tr("Pause on unplug", "Çıkarınca duraklat"), unplug) { unplug = it; MusicSettings.setPauseOnUnplug(c, it) }
        NotifPreview(ids)
        val rest = NotifBtn.values().filter { it.id !in ids }
        // the buttons in their order: a long press on a row lifts it, and it is dragged to its place (the arrows still work)
        var dragId by remember { mutableStateOf<String?>(null) }
        var dragY by remember { mutableStateOf(0f) }
        val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { 46.dp.toPx() }
        val idsNow by androidx.compose.runtime.rememberUpdatedState(ids)
        ids.mapNotNull { NotifBtn.of(it) }.forEachIndexed { i, b ->
            androidx.compose.runtime.key(b.id) {
                val lifted = dragId == b.id
                Row(
                    Modifier.fillMaxWidth().height(46.dp).alpha(if (i < NotifBtn.MAX) 1f else .4f)
                        .graphicsLayer { translationY = if (lifted) dragY else 0f; scaleX = if (lifted) 1.02f else 1f; scaleY = if (lifted) 1.02f else 1f }
                        .zIndex(if (lifted) 1f else 0f)
                        .background(if (lifted) T.text.copy(alpha = .12f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                        .pointerInput(b.id) {
                            androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress(
                                onDragStart = { dragId = b.id; dragY = 0f },
                                onDragEnd = { dragId = null; dragY = 0f },
                                onDragCancel = { dragId = null; dragY = 0f },
                            ) { change, drag ->
                                change.consume()
                                dragY += drag.y
                                val cur = idsNow.indexOf(b.id)
                                // past half a row: it swaps with the neighbour and the finger keeps its place under it
                                if (dragY > rowPx / 2 && cur < idsNow.lastIndex) { save(idsNow.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }); dragY -= rowPx }
                                else if (dragY < -rowPx / 2 && cur > 0) { save(idsNow.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }); dragY += rowPx }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(b.icon), null, tint = T.text, modifier = Modifier.size(22.dp))
                    Text(b.label, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Arrow(R.drawable.ic_notif_up, i > 0) { save(ids.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }
                    Arrow(R.drawable.ic_notif_down, i < ids.lastIndex) { save(ids.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }
                    PSwitch(true, { save(ids - b.id) })
                }
            }
        }
        rest.forEach { b ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(b.icon), null, tint = T.sub, modifier = Modifier.size(22.dp))
                Text(b.label, color = T.sub, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                PSwitch(false, { save(ids + b.id) })
            }
        }
    }
}

@Composable
private fun Arrow(icon: Int, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).alpha(if (on) 1f else .25f).clickable(enabled = on, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = T.text, modifier = Modifier.size(22.dp))
    }
}

/** A sample media notification that shows the enabled buttons (the first [NotifBtn.MAX]); the toggle buttons react to taps. */
@Composable
private fun NotifPreview(ids: List<String>) {
    var playing by remember { mutableStateOf(true) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(0) }
    var fav by remember { mutableStateOf(false) }
    val shown = ids.mapNotNull { NotifBtn.of(it) }.take(NotifBtn.MAX)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(T.sub.copy(alpha = .16f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(T.sub.copy(alpha = .35f)))
            Column(Modifier.padding(start = 12.dp)) {
                Text(tr("Song title", "Şarkı adı"), color = T.text, fontSize = 15.sp)
                Text(tr("Artist", "Sanatçı"), color = T.sub, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            shown.forEach { b ->
                val icon = when (b) {
                    NotifBtn.PLAY -> if (playing) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
                    NotifBtn.SHUFFLE -> if (shuffle) R.drawable.ic_notif_shuffle else R.drawable.ic_notif_shuffle_off
                    NotifBtn.REPEAT -> when (repeat) { 1 -> R.drawable.ic_notif_repeat; 2 -> R.drawable.ic_notif_repeat_one; else -> R.drawable.ic_notif_repeat_off }
                    NotifBtn.FAV -> if (fav) R.drawable.ic_notif_heart_on else R.drawable.ic_notif_heart
                    else -> b.icon
                }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable {
                        when (b) {
                            NotifBtn.PLAY -> playing = !playing
                            NotifBtn.SHUFFLE -> shuffle = !shuffle
                            NotifBtn.REPEAT -> repeat = (repeat + 1) % 3
                            NotifBtn.FAV -> fav = !fav
                            else -> Unit
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(icon), null, tint = T.text, modifier = Modifier.size(24.dp)) }
            }
        }
    }
}
