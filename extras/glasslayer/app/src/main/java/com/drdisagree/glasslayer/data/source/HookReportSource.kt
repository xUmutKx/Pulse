package com.drdisagree.glasslayer.data.source

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import com.drdisagree.glasslayer.data.mapper.HookReportJsonMapper
import com.drdisagree.glasslayer.domain.model.HookReport
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class HookReportSource(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PrefKeys.GROUP, Context.MODE_PRIVATE)

    private val current: HookReport?
        get() = prefs.getString(PrefKeys.HOOK_REPORT, null)?.let(HookReportJsonMapper::fromJson)

    val report: Flow<HookReport?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PrefKeys.HOOK_REPORT) trySend(current)
        }
        trySend(current)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun installedAt(): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    } catch (_: Exception) {
        0L
    }

    fun installedApkPath(): String = context.applicationInfo.sourceDir.orEmpty()

    fun save(json: String) {
        if (HookReportJsonMapper.fromJson(json) == null) return
        prefs.edit { putString(PrefKeys.HOOK_REPORT, json) }
    }

    fun request() {
        context.sendBroadcast(
            Intent(ReportContract.ACTION_REQUEST)
                .setPackage(ReportContract.SYSTEMUI_PACKAGE)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        )
    }
}
