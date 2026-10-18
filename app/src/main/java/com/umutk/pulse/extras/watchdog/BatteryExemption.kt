package com.umutk.pulse.extras.watchdog

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Asks the user to exempt Pulse from battery optimization, so music, video and ambient playback survive in the background.
 * The system asks for consent itself; [request] is only ever called from a button the user tapped.
 */
object BatteryExemption {

    fun isExempt(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    /** Opens the system consent prompt; falls back to the battery settings list when the prompt is not available. */
    fun request(activity: Activity) {
        if (isExempt(activity)) return
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}"))
        try {
            activity.startActivity(direct)
        } catch (e: ActivityNotFoundException) {
            // shortcut: some ROMs drop the direct prompt, the settings list lets the user toggle it by hand
            runCatching { activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }
}

/** Settings button that asks for the exemption; draws nothing once Pulse is exempt. Put it where playback is set up (Music, White noise, Video). */
@androidx.compose.runtime.Composable
fun BatteryExemptionButton(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var exempt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(BatteryExemption.isExempt(ctx)) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    // re-check when the user comes back from the system prompt
    androidx.compose.runtime.DisposableEffect(owner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) exempt = BatteryExemption.isExempt(ctx) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val act = ctx as? Activity ?: return
    if (!exempt) com.umutk.pulse.PButton(com.umutk.pulse.tr("Keep playing in background", "Arka planda çalmaya devam"), modifier) { BatteryExemption.request(act) }
}
