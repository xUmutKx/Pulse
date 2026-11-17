package com.drdisagree.glasslayer.xposed.systemui

import android.content.res.ColorStateList
import com.drdisagree.glasslayer.xposed.glass.PressSpring

internal class ViewPress(val active: () -> Boolean) {
    val spring = PressSpring()
    var x = 0f
    var y = 0f
    var stockRipple: ColorStateList? = null
}
