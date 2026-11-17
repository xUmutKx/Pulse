package com.drdisagree.glasslayer.xposed.compose

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

object ComposeToolkit {

    const val FUNCTION0_CLASS = "kotlin.jvm.functions.Function0"
    const val MODIFIER_CLASS = "androidx.compose.ui.Modifier"
    const val SHAPE_CLASS = "androidx.compose.ui.graphics.Shape"
    const val COMPOSER_CLASS = "androidx.compose.runtime.Composer"
    const val DRAW_MODIFIER_CLASS = "androidx.compose.ui.draw.DrawModifierKt"
    private const val SHAPES_KT = "androidx.compose.foundation.shape.RoundedCornerShapeKt"
    private const val CIRCLE_SHAPE = "CircleShape"

    private val functionClasses = ConcurrentHashMap<Int, Class<*>>()
    private val invokeMethods = ConcurrentHashMap<Int, Method>()
    private val staticMethods = ConcurrentHashMap<String, Method>()

    private val kotlinUnit: Any? by lazy {
        try {
            loadClass("kotlin.Unit")?.getField("INSTANCE")?.get(null)
        } catch (_: Throwable) {
            null
        }
    }

    val emptyModifier: Any? by lazy {
        try {
            loadClass(MODIFIER_CLASS)?.getField("Companion")?.get(null)
        } catch (_: Throwable) {
            null
        }
    }

    val circleShape: Any? by lazy {
        try {
            loadClass(SHAPES_KT)?.getDeclaredField(CIRCLE_SHAPE)?.apply { isAccessible = true }?.get(null)
        } catch (_: Throwable) {
            null
        }
    }

    private val invalidateDrawMethod: Method? by lazy {
        staticMethod("androidx.compose.ui.node.DrawModifierNodeKt", "invalidateDraw") { method ->
            method.name == "invalidateDraw" && method.parameterTypes.size == 1
        }
    }

    fun invalidateDraw(node: Any) {
        try {
            invalidateDrawMethod?.invoke(null, node)
        } catch (_: Throwable) {
        }
    }

    private val toArgbMethod: Method? by lazy {
        staticMethod("androidx.compose.ui.graphics.ColorKt", "toArgb") { method ->
            method.name.startsWith("toArgb") &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Long::class.javaPrimitiveType
        }
    }

    fun loadClass(name: String): Class<*>? = HookEnv.findClass(name)

    fun colorOf(argb: Int): Long = argb.toLong() shl 32

    fun toArgb(color: Long): Int? = try {
        toArgbMethod?.invoke(null, color) as? Int
    } catch (_: Throwable) {
        null
    }

    fun function0(name: String, fallback: (() -> Any?)? = null, block: () -> Any?): Any? =
        function(0, name, fallback?.let { { _: Array<Any?> -> it() } }) { block() }

    fun function1(name: String, fallback: ((Any?) -> Any?)? = null, block: (Any?) -> Any?): Any? =
        function(1, name, fallback?.let { { args: Array<Any?> -> it(args[0]) } }) { args -> block(args[0]) }

    fun invokeFunction(function: Any, vararg args: Any?): Any? {
        val method = invokeMethods[args.size] ?: functionClass(args.size)
            ?.methods
            ?.firstOrNull { it.name == "invoke" && it.parameterTypes.size == args.size }
            ?.also { invokeMethods[args.size] = it }
            ?: return null
        return method.invoke(function, *args)
    }

    fun staticMethod(className: String, cacheKey: String, predicate: (Method) -> Boolean): Method? {
        val key = "$className#$cacheKey"
        staticMethods[key]?.let { return it }
        return loadClass(className)
            ?.declaredMethods
            ?.firstOrNull(predicate)
            ?.apply { isAccessible = true }
            ?.also { staticMethods[key] = it }
    }

    fun modifierFunction(className: String, name: String): Method? =
        staticMethod(className, "$name/1") { method ->
            val types = method.parameterTypes
            (method.name == name || method.name.startsWith("$name-")) &&
                    types.size == 2 &&
                    types[0].name == MODIFIER_CLASS
        }

    fun clearDefaultBit(method: Method, args: Array<Any?>, index: Int) {
        val maskIndex = defaultMaskIndex(method, index) ?: return
        val mask = args.getOrNull(maskIndex) as? Int ?: return
        args[maskIndex] = mask and (1 shl (index % 31)).inv()
    }

    private fun defaultMaskIndex(method: Method, index: Int): Int? {
        val types = method.parameterTypes
        val composerIndex = types.indexOfFirst { it.name == COMPOSER_CLASS }
        if (composerIndex == -1) return null

        val changedCount = maxOf(1, (composerIndex + 9) / 10)
        val trailing = types.size - composerIndex - 1
        if (trailing <= changedCount) return null

        return composerIndex + 1 + changedCount + index / 31
    }

    fun nativeCanvas(drawScope: Any): Canvas? {
        val composeCanvas = drawScope.call("getDrawContext").call("getCanvas") ?: return null
        return composeCanvas.javaClass.declaredFields
            .firstOrNull { it.type == Canvas::class.java }
            ?.apply { isAccessible = true }
            ?.get(composeCanvas) as? Canvas
    }

    fun ownerView(drawScope: Any): View? =
        drawScope.field("drawNode")?.field("coordinator")?.field("layoutNode")?.field("owner") as? View

    fun drawSize(drawScope: Any): Long? =
        drawScope.call("getDrawContext")?.callByPrefix("getSize") as? Long

    fun outline(shape: Any, drawScope: Any): Any? {
        val packedSize = drawSize(drawScope) ?: return null
        return shape.callByPrefix(
            "createOutline",
            packedSize,
            drawScope.call("getLayoutDirection"),
            drawScope
        )
    }

    fun outlineCornerRadius(outline: Any): Float? {
        val roundRect = outline.field("roundRect") ?: return null
        val packed = roundRect.field("topLeftCornerRadius") as? Long ?: return null
        return Float.fromBits((packed ushr 32).toInt())
    }

    fun outlinePath(outline: Any): Path? {
        outline.field("roundRect")?.let { roundRect ->
            val radii = listOf(
                "topLeftCornerRadius",
                "topRightCornerRadius",
                "bottomRightCornerRadius",
                "bottomLeftCornerRadius"
            ).flatMap { name ->
                val packed = roundRect.field(name) as? Long ?: 0L
                listOf(Float.fromBits((packed ushr 32).toInt()), Float.fromBits((packed and 0xFFFFFFFFL).toInt()))
            }.toFloatArray()

            return Path().apply {
                addRoundRect(
                    RectF(
                        roundRect.float("left"),
                        roundRect.float("top"),
                        roundRect.float("right"),
                        roundRect.float("bottom")
                    ),
                    radii,
                    Path.Direction.CW
                )
            }
        }

        outline.field("rect")?.let { rect ->
            return Path().apply {
                addRect(rect.float("left"), rect.float("top"), rect.float("right"), rect.float("bottom"), Path.Direction.CW)
            }
        }

        val pathHolder = outline.field("path") ?: return null
        return pathHolder.javaClass.declaredFields
            .firstOrNull { it.type == Path::class.java }
            ?.apply { isAccessible = true }
            ?.get(pathHolder) as? Path
    }

    fun Any.callByPrefix(prefix: String, vararg args: Any?): Any? {
        val method = javaClass.methods.firstOrNull {
            it.name.startsWith(prefix) && it.parameterTypes.size == args.size
        } ?: return null
        return method.invoke(this, *args)
    }

    private fun Any.float(name: String): Float = field(name) as? Float ?: 0f

    private fun functionClass(arity: Int): Class<*>? = functionClasses[arity]
        ?: loadClass("kotlin.jvm.functions.Function$arity")?.also { functionClasses[arity] = it }

    private fun function(
        arity: Int,
        name: String,
        fallback: ((Array<Any?>) -> Any?)?,
        block: (Array<Any?>) -> Any?
    ): Any? {
        val functionClass = functionClass(arity) ?: return null
        return Proxy.newProxyInstance(functionClass.classLoader, arrayOf(functionClass)) { proxy, method, args ->
            when (method.name) {
                "invoke" -> {
                    val values = args ?: emptyArray()
                    var failed = true
                    val result = Guard.run(name, null) { block(values).also { failed = false } }
                    val output = if (failed && fallback != null) {
                        Guard.run("$name.fallback", null) { fallback(values) }
                    } else {
                        result
                    }
                    if (output === Unit || output == null) kotlinUnit else output
                }

                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> name
                else -> null
            }
        }
    }
}
