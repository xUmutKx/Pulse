package com.drdisagree.glasslayer.xposed.systemui

import android.graphics.drawable.Drawable
import android.view.View
import com.drdisagree.glasslayer.xposed.glass.GlassJoin
import com.drdisagree.glasslayer.xposed.glass.ViewGlassDrawable
import java.lang.ref.WeakReference

internal class UnderlayTarget(
    val surfaceKey: String,
    val active: () -> Boolean,
    val tint: () -> Float,
    var glassEnabled: Boolean,
    var openEdges: Boolean,
    var joinWith: ((View, GlassJoin) -> Boolean)?,
    var clipJoin: Boolean,
    var tintInGlass: Boolean,
    var source: ((View) -> Drawable?)?,
    var behind: ((View) -> Int)?
) {
    var glass: ViewGlassDrawable? = null
    var glassSource: Drawable? = null
    var tinted: Drawable? = null
    var stockAlpha: Int = OPAQUE
    var appliedAlpha: Int = -1
    val join = GlassJoin()
    var joinActive = false
    var isolate = false
    var scrim: (() -> Int)? = null
    var dim = 0
    var glassDim = 0
    var base: WeakReference<View>? = null
    var baseResolved = false
    var imageContent = false
    var rimOnTop = false
    var segmented = false
    var sheenOnTop = false
    var backdrop: ((View) -> Drawable?)? = null

    companion object {
        const val OPAQUE = 255
    }
}
