package com.drdisagree.glasslayer.xposed.glass

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader

class BackdropBlur {

    private val node = RenderNode("GlassLayerBackdropBlur")
    private val images = ArrayDeque<Image>()
    private var renderer: HardwareRenderer? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var radius = Float.NaN

    fun blur(source: Bitmap, blurRadius: Float): Bitmap? {
        if (blurRadius <= 0f || source.isRecycled) return null

        return try {
            ensure(source.width, source.height)
            val currentRenderer = renderer ?: return null
            val currentReader = reader ?: return null

            val recording = node.beginRecording(width, height)
            recording.drawBitmap(source, 0f, 0f, null)
            node.endRecording()

            if (radius != blurRadius) {
                node.setRenderEffect(
                    RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                )
                radius = blurRadius
            }

            currentRenderer.createRenderRequest()
                .setWaitForPresent(true)
                .syncAndDraw()

            val image = currentReader.acquireLatestImage() ?: return null
            val buffer = image.hardwareBuffer
            if (buffer == null) {
                image.close()
                return null
            }

            val bitmap = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))
            buffer.close()
            images.addLast(image)
            while (images.size > KEPT_IMAGES) images.removeFirst().close()
            bitmap
        } catch (_: Throwable) {
            release()
            null
        }
    }

    fun release() {
        images.forEach { runCatching { it.close() } }
        images.clear()
        renderer?.destroy()
        renderer = null
        reader?.close()
        reader = null
        node.discardDisplayList()
        width = 0
        height = 0
        radius = Float.NaN
    }

    private fun ensure(targetWidth: Int, targetHeight: Int) {
        if (renderer != null && targetWidth == width && targetHeight == height) return
        release()

        val imageReader = ImageReader.newInstance(
            targetWidth,
            targetHeight,
            PixelFormat.RGBA_8888,
            KEPT_IMAGES + 2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        node.setPosition(0, 0, targetWidth, targetHeight)
        renderer = HardwareRenderer().apply {
            setSurface(imageReader.surface)
            setContentRoot(node)
            isOpaque = false
        }
        reader = imageReader
        width = targetWidth
        height = targetHeight
    }

    private companion object {
        const val KEPT_IMAGES = 3
    }
}
