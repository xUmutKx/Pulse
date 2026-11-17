package com.drdisagree.glasslayer.xposed.systemui

import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.HostApplication
import java.util.function.Consumer

internal object WindowBlurState {

    private const val TAG = "WindowBlurState"
    private const val RETRY_MS = 1_000L
    private const val MAX_ATTEMPTS = 60

    private val mainHandler = Handler(Looper.getMainLooper())
    private var attempts = 0
    private var attached = false
    private var onChanged: () -> Unit = {}

    @Volatile
    var enabled = true
        private set

    private val listener = Consumer<Boolean> { value ->
        Guard.run(TAG, Unit) {
            if (enabled != value) {
                enabled = value
                onChanged()
            }
        }
    }

    fun start(onChanged: () -> Unit) {
        this.onChanged = onChanged
        attach()
    }

    private fun attach() {
        if (attached) return
        val context = HostApplication.get()
        if (context == null) {
            if (attempts++ < MAX_ATTEMPTS) mainHandler.postDelayed(::attach, RETRY_MS)
            return
        }
        try {
            val windowManager = context.getSystemService(WindowManager::class.java) ?: return
            enabled = windowManager.isCrossWindowBlurEnabled
            windowManager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
            attached = true
        } catch (throwable: Throwable) {
            HookEnv.log("Window blur watch failed", throwable)
        }
    }
}
