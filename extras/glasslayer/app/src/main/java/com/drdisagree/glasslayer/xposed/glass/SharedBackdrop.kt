package com.drdisagree.glasslayer.xposed.glass

import android.graphics.RenderNode

internal class SharedBackdrop(val node: RenderNode) {

    var frame: BackdropFrame? = null
    var scale = Float.NaN
}
