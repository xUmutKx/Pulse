package com.umutk.pulse.extras.lucid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.umutk.pulse.extras.lucid.service.NowBarNotification
import com.umutk.pulse.extras.lucid.service.StopwatchService
import com.umutk.pulse.extras.lucid.service.TimerService

class NowBarActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val serviceClass = when (intent.getStringExtra(NowBarNotification.EXTRA_TARGET_SERVICE)) {
            NowBarNotification.TARGET_TIMER -> TimerService::class.java
            NowBarNotification.TARGET_STOPWATCH -> StopwatchService::class.java
            else -> return
        }

        val forward = Intent(context, serviceClass).apply { this.action = action }
        ContextCompat.startForegroundService(context, forward)
    }
}
