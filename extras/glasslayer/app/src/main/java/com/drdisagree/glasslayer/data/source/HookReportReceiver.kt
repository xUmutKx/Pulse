package com.drdisagree.glasslayer.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HookReportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReportContract.ACTION_REPORT) return
        val json = intent.getStringExtra(ReportContract.EXTRA_REPORT) ?: return
        HookReportSource(context.applicationContext).save(json)
    }
}
