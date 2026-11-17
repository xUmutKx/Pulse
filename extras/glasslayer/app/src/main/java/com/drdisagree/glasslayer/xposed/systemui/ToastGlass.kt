package com.drdisagree.glasslayer.xposed.systemui

import android.view.View
import android.view.ViewGroup
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.field
import com.drdisagree.glasslayer.xposed.hook.intercept

object ToastGlass {

    private const val SYSTEM_UI_TOAST = "${SystemUiModule.PACKAGE}.toast.SystemUIToast"
    private const val TOAST_VIEW = "mToastView"

    fun install(): Int = HookEnv.findClass(SYSTEM_UI_TOAST)
        ?.declaredConstructors
        ?.count { constructor ->
            constructor.intercept { chain ->
                val result = chain.proceed()
                val view = chain.thisObject.field(TOAST_VIEW) as? View
                if (view != null) Guard.run("ToastGlass.view", Unit) { wrapTree(view) }
                result
            } != null
        }
        ?: 0

    private fun wrapTree(view: View) {
        if (!ViewGlassUnderlay.isAttached(view) && SolidShapes.of(view.background) != null) {
            ViewGlassUnderlay.attach(view, PrefKeys.TOASTS, { GlassConfig.toastsActive }, { GlassConfig.toastTint })
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) wrapTree(view.getChildAt(index))
        }
    }
}
