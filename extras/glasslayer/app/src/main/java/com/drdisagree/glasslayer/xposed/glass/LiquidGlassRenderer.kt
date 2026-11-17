package com.drdisagree.glasslayer.xposed.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import kotlin.math.ceil

class LiquidGlassRenderer(private val resolution: Float = 1f) {

    private val shader: RuntimeShader = LiquidGlassShader.create()
    private val node = RenderNode("GlassLayerLiquidGlass")
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private var sourceBitmap: Bitmap? = null
    private val directPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var directBitmap: Bitmap? = null
    private var directShader: BitmapShader? = null
    private val scaledRadii = FloatArray(4)

    fun draw(
        canvas: Canvas,
        clip: Path,
        frame: BackdropFrame,
        screenLeft: Float,
        screenTop: Float,
        width: Float,
        height: Float,
        radii: FloatArray,
        blurRadius: Float,
        tint: Int,
        style: LiquidGlassStyle,
        alpha: Float = 1f,
        screenScaleX: Float = 1f,
        screenScaleY: Float = 1f
    ): Boolean {
        if (!canvas.isHardwareAccelerated || frame.bitmap.isRecycled) return false
        if (width <= 0f || height <= 0f || alpha <= 0f) return true

        val pad = ceil(blurRadius + minOf(width, height) * 0.25f).toInt()
        val nodeWidth = ceil((width + pad * 2) * resolution).toInt()
        val nodeHeight = ceil((height + pad * 2) * resolution).toInt()

        if (sourceBitmap !== frame.bitmap) {
            sourceBitmap = frame.bitmap
            bitmapPaint.shader = BitmapShader(frame.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        backdropMatrix(matrix, frame, screenLeft, screenTop, screenScaleX, screenScaleY, pad.toFloat(), resolution)
        bitmapPaint.shader.setLocalMatrix(matrix)

        node.setPosition(0, 0, nodeWidth, nodeHeight)
        val recording = node.beginRecording(nodeWidth, nodeHeight)
        recording.drawRect(0f, 0f, nodeWidth.toFloat(), nodeHeight.toFloat(), bitmapPaint)
        node.endRecording()

        LiquidGlassShader.update(
            shader,
            pad * resolution,
            pad * resolution,
            width * resolution,
            height * resolution,
            scaledRadii.also { out -> radii.forEachIndexed { index, value -> out[index] = value * resolution } },
            tint,
            style,
            resolution
        )
        node.setRenderEffect(effectFor(blurRadius * resolution))
        node.alpha = alpha

        canvas.save()
        canvas.clipPath(clip)
        canvas.translate(-pad.toFloat(), -pad.toFloat())
        canvas.scale(1f / resolution, 1f / resolution)
        canvas.drawRenderNode(node)
        canvas.restore()
        return true
    }

    fun drawShared(
        canvas: Canvas,
        clip: Path,
        frame: BackdropFrame,
        backdrop: RenderNode,
        screenLeft: Float,
        screenTop: Float,
        width: Float,
        height: Float,
        radii: FloatArray,
        tint: Int,
        style: LiquidGlassStyle,
        alpha: Float = 1f,
        topExtend: Float = 0f,
        bottomExtend: Float = 0f,
        screenScaleX: Float = 1f,
        screenScaleY: Float = 1f,
        join: GlassJoin? = null
    ): Boolean {
        if (!canvas.isHardwareAccelerated || frame.bitmap.isRecycled) return false
        if (width <= 0f || height <= 0f || alpha <= 0f) return true

        val pad = ceil(minOf(width, height) * 0.25f).toInt()
        val nodeWidth = ceil((width + pad * 2) * resolution).toInt()
        val nodeHeight = ceil((height + pad * 2) * resolution).toInt()

        node.setPosition(0, 0, nodeWidth, nodeHeight)
        val recording = node.beginRecording(nodeWidth, nodeHeight)
        backdropMatrix(matrix, frame, screenLeft, screenTop, screenScaleX, screenScaleY, pad.toFloat(), resolution)
        recording.concat(matrix)
        recording.drawRenderNode(backdrop)
        node.endRecording()

        LiquidGlassShader.update(
            shader,
            pad * resolution,
            (pad - topExtend) * resolution,
            width * resolution,
            (height + topExtend + bottomExtend) * resolution,
            scaledRadii.also { out -> radii.forEachIndexed { index, value -> out[index] = value * resolution } },
            tint,
            style,
            resolution
        )
        join?.let { LiquidGlassShader.join(shader, it, pad * resolution, resolution) }
        node.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "image"))
        node.alpha = alpha

        canvas.save()
        canvas.clipPath(clip)
        canvas.translate(-pad.toFloat(), -pad.toFloat())
        canvas.scale(1f / resolution, 1f / resolution)
        canvas.drawRenderNode(node)
        canvas.restore()
        return true
    }

    fun drawSource(
        canvas: Canvas,
        clip: Path,
        source: RenderNode,
        sourceMatrix: Matrix,
        width: Float,
        height: Float,
        radii: FloatArray,
        tint: Int,
        style: LiquidGlassStyle,
        alpha: Float = 1f
    ): Boolean {
        if (!canvas.isHardwareAccelerated) return false
        if (width <= 0f || height <= 0f || alpha <= 0f) return true

        val pad = ceil(minOf(width, height) * 0.25f).toInt()
        val nodeWidth = ceil((width + pad * 2) * resolution).toInt()
        val nodeHeight = ceil((height + pad * 2) * resolution).toInt()

        node.setPosition(0, 0, nodeWidth, nodeHeight)
        val recording = node.beginRecording(nodeWidth, nodeHeight)
        matrix.set(sourceMatrix)
        matrix.postTranslate(pad.toFloat(), pad.toFloat())
        matrix.postScale(resolution, resolution)
        recording.concat(matrix)
        recording.drawRenderNode(source)
        node.endRecording()

        LiquidGlassShader.update(
            shader,
            pad * resolution,
            pad * resolution,
            width * resolution,
            height * resolution,
            scaledRadii.also { out -> radii.forEachIndexed { index, value -> out[index] = value * resolution } },
            tint,
            style,
            resolution
        )
        node.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "image"))
        node.alpha = alpha

        canvas.save()
        canvas.clipPath(clip)
        canvas.translate(-pad.toFloat(), -pad.toFloat())
        canvas.scale(1f / resolution, 1f / resolution)
        canvas.drawRenderNode(node)
        canvas.restore()
        return true
    }

    fun drawDirect(
        canvas: Canvas,
        clip: Path,
        frame: BackdropFrame,
        backdrop: Bitmap,
        screenLeft: Float,
        screenTop: Float,
        width: Float,
        height: Float,
        radii: FloatArray,
        tint: Int,
        style: LiquidGlassStyle,
        alpha: Float = 1f,
        topExtend: Float = 0f,
        bottomExtend: Float = 0f,
        screenScaleX: Float = 1f,
        screenScaleY: Float = 1f,
        join: GlassJoin? = null
    ): Boolean {
        if (!canvas.isHardwareAccelerated || backdrop.isRecycled) return false
        if (width <= 0f || height <= 0f || alpha <= 0f) return true

        val bitmapShader = directShader?.takeIf { directBitmap === backdrop }
            ?: BitmapShader(backdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            }.also {
                directShader = it
                directBitmap = backdrop
            }

        backdropMatrix(matrix, frame, screenLeft, screenTop, screenScaleX, screenScaleY, 0f, 1f)
        bitmapShader.setLocalMatrix(matrix)
        shader.setInputShader("image", bitmapShader)

        LiquidGlassShader.update(
            shader,
            0f,
            -topExtend,
            width,
            height + topExtend + bottomExtend,
            radii,
            tint,
            style
        )
        join?.let { LiquidGlassShader.join(shader, it, 0f, 1f) }
        directPaint.shader = shader
        directPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawPath(clip, directPaint)
        return true
    }

    private fun backdropMatrix(
        out: Matrix,
        frame: BackdropFrame,
        screenLeft: Float,
        screenTop: Float,
        screenScaleX: Float,
        screenScaleY: Float,
        pad: Float,
        scale: Float
    ) {
        out.setScale(1f / frame.scale, 1f / frame.scale)
        out.postTranslate(frame.screenRect.left.toFloat(), frame.screenRect.top.toFloat())
        val zoom = BackdropZoom.scale
        if (zoom > 0f && zoom != 1f) out.postScale(zoom, zoom, BackdropZoom.centerX, BackdropZoom.centerY)
        out.postTranslate(-screenLeft, -screenTop)
        out.postScale(1f / screenScaleX, 1f / screenScaleY)
        out.postTranslate(pad, pad)
        out.postScale(scale, scale)
    }

    private fun effectFor(blurRadius: Float): RenderEffect {
        val glass = RenderEffect.createRuntimeShaderEffect(shader, "image")
        if (blurRadius <= 0f) return glass
        return RenderEffect.createChainEffect(
            glass,
            RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
        )
    }

    fun release() {
        node.discardDisplayList()
        sourceBitmap = null
        bitmapPaint.shader = null
        directBitmap = null
        directShader = null
        directPaint.shader = null
    }
}
