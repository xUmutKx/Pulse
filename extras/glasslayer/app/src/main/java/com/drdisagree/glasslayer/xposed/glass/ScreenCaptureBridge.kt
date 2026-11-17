package com.drdisagree.glasslayer.xposed.glass

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.view.Display
import android.view.SurfaceControl
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.call
import com.drdisagree.glasslayer.xposed.hook.callStatic
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.function.ObjIntConsumer

object ScreenCaptureBridge {

    private const val TAG = "ScreenCaptureBridge"
    private const val RESULT_TAG = "ScreenCaptureBridge.result"
    private const val ROOT_TAG = "ScreenCaptureBridge.root"
    private const val RELEASED_LAYER = Int.MIN_VALUE
    private const val DISPLAY_MANAGER_GLOBAL = "android.hardware.display.DisplayManagerGlobal"

    private val captureClassPrefixes = listOf(
        "android.window.ScreenCaptureInternal",
        "android.window.ScreenCapture"
    )

    private val builderClass: Class<*>? by lazy { firstClass("\$CaptureArgs\$Builder") }

    private val listenerConstructor: Constructor<*>? by lazy {
        firstClass("\$ScreenCaptureListener")
            ?.declaredConstructors
            ?.firstOrNull { it.parameterTypes.singleOrNull() == ObjIntConsumer::class.java }
            ?.apply { isAccessible = true }
    }

    private val layerBuilderConstructor: Constructor<*>? by lazy {
        runCatching {
            firstClass("\$LayerCaptureArgs\$Builder")
                ?.getDeclaredConstructor(SurfaceControl::class.java)
                ?.apply { isAccessible = true }
        }.getOrNull()
    }

    private val captureLayers: Method? by lazy {
        val listenerClass = firstClass("\$ScreenCaptureListener") ?: return@lazy null
        firstClass("")
            ?.declaredMethods
            ?.firstOrNull { it.name == "captureLayers" && it.parameterTypes.size == 2 && it.parameterTypes[1] == listenerClass }
            ?.apply { isAccessible = true }
    }

    var rootLayer: ((Int) -> SurfaceControl?)? = null

    @Volatile
    private var layerPathFailed = false

    @Volatile
    private var inFlightConsumer: ObjIntConsumer<Any?>? = null

    private val windowManager: Any? by lazy {
        runCatching {
            Class.forName("android.view.WindowManagerGlobal").callStatic("getWindowManagerService")
        }.getOrNull()
    }

    private val captureDisplay: Method? by lazy {
        windowManager?.javaClass?.methods?.firstOrNull {
            it.name == "captureDisplay" && it.parameterTypes.size == 3
        }
    }

    val isAvailable: Boolean
        get() = builderClass != null && listenerConstructor != null && captureDisplay != null

    fun capture(
        displayId: Int,
        crop: Rect,
        scale: Float,
        exclude: Array<SurfaceControl>,
        onResult: (Bitmap?) -> Unit
    ): Boolean {
        val builderClass = builderClass ?: return false
        val listenerConstructor = listenerConstructor ?: return false

        return try {
            val consumer = ObjIntConsumer<Any?> { buffer, _ ->
                val bitmap = Guard.run(RESULT_TAG, null) { buffer?.toBitmap() }
                Guard.run(RESULT_TAG, Unit) { onResult(bitmap) }
            }
            val listener = listenerConstructor.newInstance(consumer)
            inFlightConsumer = consumer
            val layers = exclude.filter { it.isValid }.toTypedArray()
            when (captureRoot(displayId, crop, scale, layers, listener)) {
                true -> return true
                null -> return false
                false -> Unit
            }

            val captureDisplay = captureDisplay ?: return false
            val builder = builderClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            builder.configure(crop, scale, layers)
            captureDisplay.invoke(windowManager, displayId, builder.call("build"), listener)
            true
        } catch (throwable: Throwable) {
            HookEnv.log(TAG, throwable)
            false
        }
    }

    private fun captureRoot(displayId: Int, crop: Rect, scale: Float, exclude: Array<SurfaceControl>, listener: Any): Boolean? {
        if (layerPathFailed) return false
        val root = rootLayer?.invoke(displayId)?.takeIf { it.isValid } ?: return false
        val constructor = layerBuilderConstructor ?: return false
        val captureLayers = captureLayers ?: return false
        val bounds = if (crop.isEmpty) displayBounds(displayId) ?: return false else crop
        val status = Guard.run(ROOT_TAG, null) {
            val builder = constructor.newInstance(root)
            builder.configure(bounds, scale, exclude)
            try {
                captureLayers.invoke(null, builder.call("build"), listener) as? Int ?: 0
            } catch (error: InvocationTargetException) {
                if (error.cause !is NullPointerException) throw error
                RELEASED_LAYER
            }
        } ?: return false
        if (status == RELEASED_LAYER) return null
        if (status == 0) return true
        layerPathFailed = true
        HookEnv.log("$TAG: captureLayers status $status, using display capture")
        return false
    }

    private fun Any.configure(crop: Rect, scale: Float, exclude: Array<SurfaceControl>) {
        call("setSourceCrop", crop)
        call("setFrameScale", scale)
        if (exclude.isNotEmpty()) call("setExcludeLayers", exclude)
    }

    private fun displayBounds(displayId: Int): Rect? {
        val display = HookEnv.findClass(DISPLAY_MANAGER_GLOBAL)
            .callStatic("getInstance")
            ?.call("getRealDisplay", displayId) as? Display ?: return null
        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        return Rect(0, 0, size.x, size.y)
    }

    fun reset() {
        inFlightConsumer = null
    }

    private fun Any.toBitmap(): Bitmap? {
        val bitmap = call("asBitmap") as? Bitmap
        (call("getHardwareBuffer") as? HardwareBuffer)?.close()
        return bitmap
    }

    private fun firstClass(suffix: String): Class<*>? = captureClassPrefixes.firstNotNullOfOrNull { prefix ->
        runCatching { Class.forName(prefix + suffix) }.getOrNull()
    }
}
