package com.drdisagree.glasslayer.xposed.systemui

import android.content.pm.ApplicationInfo
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.RestartGuard
import com.drdisagree.glasslayer.xposed.prefs.ModulePrefs
import com.drdisagree.glasslayer.xposed.report.HookReporter
import java.util.concurrent.atomic.AtomicBoolean

object SystemUiModule {

    const val PACKAGE = "com.android.systemui"

    private val started = AtomicBoolean(false)

    private val installers: List<Pair<String, () -> Int>> = listOf(
        "ViewGlassUnderlay" to ViewGlassUnderlay::install,
        "DisplayRootTracker" to DisplayRootTracker::install,
        "WindowDimTracker" to WindowDimTracker::install,
        "DozeTracker" to DozeTracker::install,
        "ShadeGlass" to ShadeGlass::install,
        "QsTileGlass" to QsTileGlass::install,
        "QsFooterGlass" to QsFooterGlass::install,
        "BrightnessGlass" to BrightnessGlass::install,
        "MediaGlass" to MediaGlass::install,
        "NotificationGlass" to NotificationGlass::install,
        "LockscreenShortcutGlass" to LockscreenShortcutGlass::install,
        "BouncerGlass" to BouncerGlass::install,
        "VolumeGlass" to VolumeGlass::install,
        "VolumePanelGlass" to VolumePanelGlass::install,
        "PowerMenuGlass" to PowerMenuGlass::install,
        "DialogGlass" to DialogGlass::install,
        "ToastGlass" to ToastGlass::install,
        "BackGestureGlass" to BackGestureGlass::install,
        "StatusBarChipGlass" to StatusBarChipGlass::install,
        "TransitionGlass" to TransitionGlass::install,
        "SwitchGlass" to SwitchGlass::install,
        "SliderGlass" to SliderGlass::install,
        "ButtonGlass" to ButtonGlass::install,
        // One UI edition: writes down Samsung's panels (off unless switched on in the app)
        "Inspector" to { com.drdisagree.glasslayer.xposed.oneui.Inspector.install(PACKAGE) }
    )

    private val refreshers: List<() -> Unit> = listOf(
        ViewGlassUnderlay::refresh,
        CapturePacing::refresh,
        ShadeGlass::refresh,
        QsTileGlass::refresh,
        MediaGlass::refresh,
        NotificationGlass::refresh,
        LockscreenShortcutGlass::refresh,
        BouncerGlass::refresh,
        VolumeGlass::refresh,
        VolumePanelGlass::refresh,
        StatusBarChipGlass::refresh,
        BackGestureGlass::refresh,
        PowerMenuGlass::refresh,
        SwitchGlass::refresh,
        SliderGlass::refresh,
        ButtonGlass::refresh
    )

    fun claim(): Boolean = started.compareAndSet(false, true)

    fun start(info: ApplicationInfo?) {
        ModulePrefs.init(HookEnv.module)
        GlassConfig.reload()
        val safe = RestartGuard.check(info?.deviceProtectedDataDir, ModulePrefs.long(PrefKeys.RESTART_TOKEN, 0L))
        HookReporter.safeMode(safe)
        if (safe) {
            HookEnv.log("Safe mode: SystemUI kept restarting, hooks skipped until restarted from the app")
            startReporter()
            return
        }
        ModulePrefs.addListener {
            GlassConfig.reload()
            refreshAll()
        }

        installers.forEach { (name, install) ->
            try {
                HookEnv.owner = name
                val hooks = install()
                HookReporter.mod(name, hooks)
                HookEnv.log(if (hooks > 0) "$name: $hooks hooks" else "$name: no hooks matched, mod inactive on this build")
            } catch (throwable: Throwable) {
                HookReporter.mod(name, 0)
                HookEnv.log("$name install failed", throwable)
            }
        }
        HookEnv.owner = ""
        CapturePacing.start(::refreshAll)
        WindowBlurState.start(ShadeGlass::onBlurChanged)
        startReporter()
    }

    private fun refreshAll() {
        refreshers.forEach { refresh ->
            try {
                refresh()
            } catch (throwable: Throwable) {
                HookEnv.log("Refresh failed", throwable)
            }
        }
    }

    private fun startReporter() {
        try {
            HookReporter.framework(HookEnv.module.frameworkName + " " + HookEnv.module.frameworkVersion)
            HookReporter.start()
        } catch (throwable: Throwable) {
            HookEnv.log("HookReporter start failed", throwable)
        }
    }
}
