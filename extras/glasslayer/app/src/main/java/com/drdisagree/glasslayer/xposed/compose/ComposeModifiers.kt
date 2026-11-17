package com.drdisagree.glasslayer.xposed.compose

import com.drdisagree.glasslayer.xposed.hook.call
import java.lang.reflect.Method

object ComposeModifiers {

    private val drawBehindMethod: Method? by lazy {
        ComposeToolkit.modifierFunction(ComposeToolkit.DRAW_MODIFIER_CLASS, "drawBehind")
    }

    private val drawWithContentMethod: Method? by lazy {
        ComposeToolkit.modifierFunction(ComposeToolkit.DRAW_MODIFIER_CLASS, "drawWithContent")
    }

    fun proxy(name: String, onDraw: (Any) -> Unit): Any? =
        ComposeToolkit.function1(name) { drawScope -> onDraw(drawScope!!) }

    fun contentProxy(name: String, onDraw: (Any) -> Unit): Any? =
        ComposeToolkit.function1(
            name,
            fallback = { drawScope -> drawScope?.call("drawContent") }
        ) { drawScope -> onDraw(drawScope!!) }

    fun drawBehind(modifier: Any, drawer: Any): Any? = try {
        drawBehindMethod?.invoke(null, modifier, drawer)
    } catch (_: Throwable) {
        null
    }

    fun drawWithContent(modifier: Any, drawer: Any): Any? = try {
        drawWithContentMethod?.invoke(null, modifier, drawer)
    } catch (_: Throwable) {
        null
    }
}
