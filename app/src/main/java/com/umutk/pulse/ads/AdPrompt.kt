package com.umutk.pulse.ads

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr

/**
 * The ad offer as one of the app's own dialog boxes (the same window as the confirm boxes, in the look the person picked): a text, the native ad
 * card of the app's look, and two buttons. [onWatch] starts the rewarded ad; closing the box or Cancel gives nothing. Nothing is shown in the foss build.
 */
@Composable
fun RewardAdDialog(title: String, text: String, watch: String, cancel: String, onWatch: () -> Unit, onCancel: () -> Unit) {
    if (!PulseAds.ENABLED) return
    val look = LocalWindowLook.current
    WinAlert(
        onDismissRequest = onCancel,
        title = { Text(title, color = look.text) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(text, color = look.sub, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                PulseAds.NativeCard(Modifier.fillMaxWidth(), bg = look.panel, text = look.text, sub = look.sub, accent = look.accent)
            }
        },
        confirmButton = { PButton(watch, onClick = onWatch) },
        dismissButton = { PButton(cancel, onClick = onCancel) },
    )
}

/**
 * A gate for an action that the play build pays for with a rewarded ad: the returned function takes the action; in the play build the ad offer
 * (the themed dialog) comes first and the action runs only after the ad is watched to the end. The foss build runs the action at once.
 */
@Composable
fun rememberAdGate(title: String, text: String): (() -> Unit) -> Unit {
    if (!PulseAds.ENABLED) return { action -> action() }
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val action = pending
    if (action != null) {
        RewardAdDialog(
            title = title,
            text = text,
            watch = tr("Watch", "İzle"),
            cancel = tr("Cancel", "İptal"),
            onWatch = {
                pending = null
                val act = ctx as? Activity
                val noAd = { Toast.makeText(ctx, tr("No ad now, try again", "Şu an reklam yok, tekrar dene"), Toast.LENGTH_SHORT).show() }
                if (act == null) noAd() else PulseAds.showRewarded(act, onReward = action, onFail = noAd)
            },
            onCancel = { pending = null },
        )
    }
    return { cb -> pending = cb }
}
