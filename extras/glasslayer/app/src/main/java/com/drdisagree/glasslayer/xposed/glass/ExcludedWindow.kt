package com.drdisagree.glasslayer.xposed.glass

import android.view.SurfaceControl
import android.view.View
import java.lang.ref.WeakReference

internal class ExcludedWindow(root: View, val surface: SurfaceControl, val ambient: Boolean) {
    val root = WeakReference(root)
}
