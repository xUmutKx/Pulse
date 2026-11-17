package com.drdisagree.glasslayer.xposed.systemui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.BatteryManager
import android.os.Looper
import android.os.PowerManager
import com.drdisagree.glasslayer.domain.model.BatterySaverAction
import com.drdisagree.glasslayer.domain.model.ThermalAction
import com.drdisagree.glasslayer.xposed.glass.BackdropCapture
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.HostApplication

object CapturePacing {

    private const val RETRY_MS = 1_000L
    private const val MAX_ATTEMPTS = 60
    private const val TAG = "CapturePacing"
    private const val HEADROOM_SECONDS = 0
    private const val HOT_TENTHS = 400
    private const val COOL_TENTHS = 380

    private val mainHandler = Handler(Looper.getMainLooper())
    private var attempts = 0
    private var registered = false

    @Volatile
    private var powerSave = false

    @Volatile
    private var thermalHot = false

    @Volatile
    private var batteryHot = false

    @Volatile
    private var thermalSupported = false

    private val hot: Boolean get() = if (thermalSupported) thermalHot else batteryHot

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        Guard.run(TAG, Unit) {
            thermalHot = status >= PowerManager.THERMAL_STATUS_MODERATE
            apply()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Guard.run(TAG, Unit) { onBattery(intent) }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Guard.run(TAG, Unit) {
                powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
                apply()
            }
        }
    }

    private var onPauseChanged: () -> Unit = {}

    fun start(onPauseChanged: () -> Unit) {
        this.onPauseChanged = onPauseChanged
        apply()
        attach()
    }

    fun refresh() {
        apply()
    }

    private fun attach() {
        if (registered) return
        val context = HostApplication.get()
        if (context == null) {
            if (attempts++ < MAX_ATTEMPTS) mainHandler.postDelayed(::attach, RETRY_MS)
            return
        }
        try {
            context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            registered = true
            powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
            apply()
        } catch (throwable: Throwable) {
            HookEnv.log("Power save watch failed", throwable)
        }
        try {
            val power = context.getSystemService(PowerManager::class.java) ?: return
            thermalSupported = !power.getThermalHeadroom(HEADROOM_SECONDS).isNaN()
            if (thermalSupported) {
                power.addThermalStatusListener(context.mainExecutor, thermalListener)
                thermalHot = power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
            } else {
                context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { onBattery(it) }
            }
            apply()
        } catch (throwable: Throwable) {
            HookEnv.log("Thermal watch failed", throwable)
        }
    }

    private fun apply() {
        val saver = if (powerSave) GlassConfig.batterySaverAction else BatterySaverAction.NONE
        val thermal = if (hot) GlassConfig.thermalAction else ThermalAction.NONE
        var fps = GlassConfig.captureFps
        saver.mode?.let { fps = capped(fps, it.maxFps) }
        thermal.mode?.let { fps = capped(fps, it.maxFps) }
        BackdropCapture.maxFps = fps
        BackdropCapture.idleFps = GlassConfig.idleFps
        val paused = saver == BatterySaverAction.PAUSE || thermal == ThermalAction.PAUSE
        if (GlassConfig.powerPaused != paused) {
            GlassConfig.powerPaused = paused
            onPauseChanged()
        }
    }

    private fun onBattery(intent: Intent) {
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (tenths == Int.MIN_VALUE) return
        val next = if (batteryHot) tenths >= COOL_TENTHS else tenths >= HOT_TENTHS
        if (next == batteryHot) return
        batteryHot = next
        apply()
    }

    private fun capped(current: Int, limit: Int): Int = if (current == 0 || limit < current) limit else current
}
