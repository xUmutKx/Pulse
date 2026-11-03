package com.umutk.pulse.ads

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.umutk.pulse.LookHost
import com.umutk.pulse.tr

/**
 * A transparent window that asks for the rewarded ad before it starts the screen a tile or a shortcut wants. Nothing is started without the ad
 * (or while the pass is open); a closed or failed ad starts nothing.
 */
class AdGateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getParcelableExtra<Intent>("target")
        val key = intent.getStringExtra("key").orEmpty()
        val minutes = intent.getIntExtra("minutes", AdUnlocks.PASS_MINUTES)
        fun go() {
            target?.let { startActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            finish()
        }
        if (!PulseAds.ENABLED || AdUnlocks.isOpen(this, AdUnlocks.PASS) || (key.isNotEmpty() && AdUnlocks.isOpen(this, key))) {
            go()
            return
        }
        setContent {
            LookHost(transparent = true) { _ ->
                var show by remember { mutableStateOf(true) }
                if (show) RewardAdDialog(
                    title = tr("Watch an ad", "Reklam izle"),
                    text = tr("A short ad opens this. It opens only when the ad is watched to the end.", "Bunu kısa bir reklam açar. Reklam sonuna kadar izlenirse açılır."),
                    watch = tr("Watch", "İzle"),
                    cancel = tr("Cancel", "İptal"),
                    onWatch = {
                        show = false
                        PulseAds.showRewarded(this@AdGateActivity,
                            onReward = {
                                if (key.isNotEmpty()) AdUnlocks.open(this@AdGateActivity, key, minutes)
                                AdUnlocks.open(this@AdGateActivity, AdUnlocks.PASS, AdUnlocks.PASS_MINUTES)
                                go()
                            },
                            onFail = {
                                Toast.makeText(this@AdGateActivity, tr("No ad now, try again", "Şu an reklam yok, tekrar dene"), Toast.LENGTH_SHORT).show()
                                finish()
                            })
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

/** The intent a tile or a shortcut starts: its own screen, or the ad offer first (the play build; the foss build starts the screen directly). */
object TileAds {
    fun wrap(c: android.content.Context, target: Intent, key: String = "tiles"): Intent {
        if (!PulseAds.ENABLED) return target
        return Intent(c, AdGateActivity::class.java)
            .putExtra("target", target).putExtra("key", key).putExtra("minutes", AdUnlocks.PASS_MINUTES)
    }
}
