package com.drdisagree.glasslayer.xposed.systemui

import android.util.SparseArray
import android.view.SurfaceControl
import com.drdisagree.glasslayer.xposed.glass.ScreenCaptureBridge
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.hookAllAfter
import java.lang.ref.WeakReference

object DisplayRootTracker {

    private const val ORGANIZER = "com.android.wm.shell.RootDisplayAreaOrganizer"
    private const val LEASHES = "mLeashes"

    @Volatile
    private var organizer: WeakReference<Any>? = null

    fun install(): Int {
        val hooks = HookEnv.findClass(ORGANIZER).hookAllAfter("onDisplayAreaAppeared") { chain, _ ->
            organizer = WeakReference(chain.thisObject)
        }
        if (hooks > 0) ScreenCaptureBridge.rootLayer = ::leash
        return hooks
    }

    private fun leash(displayId: Int): SurfaceControl? = try {
        (organizer?.get()?.field(LEASHES) as? SparseArray<*>)?.get(displayId) as? SurfaceControl
    } catch (_: Throwable) {
        null
    }
}
