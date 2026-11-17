package com.drdisagree.glasslayer.xposed.systemui

import android.view.View

internal class DimWindowRelease(private val onDetached: (View) -> Unit) : View.OnAttachStateChangeListener {

    override fun onViewAttachedToWindow(view: View) = Unit

    override fun onViewDetachedFromWindow(view: View) {
        view.removeOnAttachStateChangeListener(this)
        onDetached(view)
    }
}
