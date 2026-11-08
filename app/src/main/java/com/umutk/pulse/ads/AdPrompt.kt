package com.umutk.pulse.ads

import android.app.Activity
import android.content.Context
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
import androidx.compose.ui.graphics.Color
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
                PulseAds.NativeCard(Modifier.fillMaxWidth(), bg = Color.Transparent, text = look.text, sub = look.sub, accent = look.accent)
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
fun rememberAdGate(title: String, text: String, key: String = "", minutes: Int = 0, pass: Boolean = true): (() -> Unit) -> Unit {
    val ctx = LocalContext.current
    if (!PulseAds.ENABLED || Premium.on(ctx)) return { action -> action() }
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
                if (act == null) noAd() else PulseAds.showRewarded(act, onReward = { if (key.isNotEmpty()) AdUnlocks.open(ctx, key, minutes); if (pass && key != AdUnlocks.PASS) AdUnlocks.open(ctx, AdUnlocks.PASS, AdUnlocks.PASS_MINUTES); action() }, onFail = noAd)
            },
            onCancel = { pending = null },
        )
    }
    return { cb ->
        // a feature unlocked by an ad stays open for its time: no new ad until it runs out
        // the pass (30 minutes, everywhere) or the feature's own time opens it without a new ad
        if ((pass && AdUnlocks.isOpen(ctx, AdUnlocks.PASS)) || (key.isNotEmpty() && AdUnlocks.isOpen(ctx, key))) cb() else pending = cb
    }
}

/**
 * Premium: one purchase in the play build (no ads, every ad-gated feature open). The flag is set only by a verified purchase;
 * the foss build has everything without it.
 */
object Premium {
    /**
     * True while Google Play confirms the purchase in this run of the app (set by PremiumBilling, play build only).
     * Nothing is stored on the phone: every start asks Play again, so a refund or a new phone is checked by Play itself.
     */
    @Volatile var verified = false

    fun on(c: Context): Boolean = verified
}

/** Features unlocked by a watched ad, each open for its own number of minutes (kept on the phone). */
object AdUnlocks {
    /** One ad watched opens this for 5 minutes in every feature that asks for an ad. */
    const val PASS = "pass"
    const val PASS_MINUTES = 5
    private fun prefs(c: Context) = c.getSharedPreferences("ad_unlocks", Context.MODE_PRIVATE)
    fun isOpen(c: Context, key: String): Boolean = System.currentTimeMillis() < prefs(c).getLong(key, 0L)
    fun open(c: Context, key: String, minutes: Int) { prefs(c).edit().putLong(key, System.currentTimeMillis() + minutes * 60_000L).apply() }
    /** When the feature closes again, in epoch milliseconds (0 when it is not open). */
    fun until(c: Context, key: String): Long = prefs(c).getLong(key, 0L).takeIf { System.currentTimeMillis() < it } ?: 0L
}
