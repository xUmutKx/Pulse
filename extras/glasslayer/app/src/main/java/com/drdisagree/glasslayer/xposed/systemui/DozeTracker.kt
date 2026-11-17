package com.drdisagree.glasslayer.xposed.systemui

import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import java.util.concurrent.CopyOnWriteArrayList

object DozeTracker {

    private const val STATE_CONTROLLER = "com.android.systemui.statusbar.StatusBarStateControllerImpl"

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var isDozing = false
        private set

    fun install(): Int {
        var hooks = 0
        hooks += HookEnv.findClass(STATE_CONTROLLER).hookAllAfter("setDozeAmountInternal") { chain, _ ->
            val amount = chain.args.firstOrNull() as? Float ?: return@hookAllAfter
            val dozing = amount > 0f
            if (dozing == isDozing) return@hookAllAfter
            isDozing = dozing
            listeners.forEach { it() }
        }
        return hooks
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }
}
