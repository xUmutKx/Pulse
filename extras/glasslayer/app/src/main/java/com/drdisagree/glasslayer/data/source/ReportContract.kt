package com.drdisagree.glasslayer.data.source

object ReportContract {
    const val APP_PACKAGE = "com.umutk.pulse.glasslayer"
    const val SYSTEMUI_PACKAGE = "com.android.systemui"
    val APP_SCOPES = OneUiKeys.APPS.map { it.first }
    const val ACTION_REPORT = "com.umutk.pulse.glasslayer.action.HOOK_REPORT"
    const val ACTION_REQUEST = "com.umutk.pulse.glasslayer.action.REQUEST_HOOK_REPORT"
    const val EXTRA_REPORT = "report"
}
