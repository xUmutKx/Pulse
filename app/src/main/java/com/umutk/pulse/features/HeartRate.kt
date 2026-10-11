package com.umutk.pulse.features

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private fun flashCameraId(cm: CameraManager): String? = cm.cameraIdList.firstOrNull { id ->
    val c = cm.getCameraCharacteristics(id)
    c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
}

fun hasFlash(ctx: Context): Boolean = try {
    flashCameraId(ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager) != null
} catch (e: Exception) { false }

/** Red-channel pulse from the back camera with the torch on and a fingertip over the lens. */
class HeartMeter(private val ctx: Context, private val onProgress: (Float) -> Unit, private val onDone: (Int?) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("heart").apply { start() }
    private val bg = Handler(thread.looper)
    private val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var opening = false
    private val samples = ArrayList<Pair<Long, Float>>() // (nanoTime, red), only used on bg
    private var t0 = 0L
    @Volatile private var done = false

    fun start() {
        bg.post {
            try {
                val id = flashCameraId(cm) ?: return@post finish(null)
                val size = cm.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.YUV_420_888)
                    ?.minByOrNull { abs(it.width * it.height - 320 * 240) } ?: Size(320, 240)
                val r = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2).also { reader = it }
                r.setOnImageAvailableListener({ onFrame(it) }, bg)
                opening = true
                cm.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(d: CameraDevice) {
                        opening = false
                        if (done) { d.close(); return }
                        device = d
                        d.createCaptureSession(listOf(r.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                if (done) return
                                session = s
                                val req = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                    addTarget(r.surface)
                                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                                }
                                t0 = System.nanoTime()
                                s.setRepeatingRequest(req.build(), null, bg)
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) = finish(null)
                        }, bg)
                    }
                    override fun onDisconnected(d: CameraDevice) { opening = false; finish(null) }
                    override fun onError(d: CameraDevice, e: Int) { opening = false; finish(null) }
                    override fun onClosed(d: CameraDevice) { thread.quitSafely() }
                }, bg)
            } catch (e: Exception) { finish(null) }
        }
    }

    /** Stops without a result, for example when the screen closes. The torch goes off with the camera. */
    fun cancel() {
        done = true
        bg.post { release() }
    }

    private fun onFrame(r: ImageReader) {
        val img = r.acquireLatestImage() ?: return
        try {
            if (done) return
            val now = System.nanoTime()
            // YUV: red is roughly Y + 1.402 * (V - 128)
            val red = mean(img.planes[0], img.width, img.height) + 1.402f * (mean(img.planes[2], img.width / 2, img.height / 2) - 128f)
            samples.add(now to red)
            main.post { onProgress(((now - t0) / 15e9f).coerceAtMost(1f)) }
            if (now - t0 >= 15_000_000_000L) finish(bpmFrom(samples.filter { it.first - t0 >= 2_000_000_000L }))
        } finally { img.close() }
    }

    private fun mean(p: Image.Plane, w: Int, h: Int): Float {
        val b = p.buffer
        var sum = 0L
        var n = 0
        for (y in 0 until h step 4) for (x in 0 until w step 4) {
            sum += b.get(y * p.rowStride + x * p.pixelStride).toInt() and 0xFF
            n++
        }
        return sum.toFloat() / n
    }

    private fun finish(bpm: Int?) {
        if (done) return
        done = true
        bg.post { release() }
        main.post { onDone(bpm) }
    }

    private fun release() {
        try { session?.close(); device?.close(); reader?.close() } catch (e: Exception) { }
        session = null; reader = null
        // the thread quits when the camera reports closed; with no camera opened it quits here
        if (device == null && !opening) thread.quitSafely()
        device = null
    }
}

/** BPM from the red signal: fast average minus slow average, peaks above half the spread, at least 0.33 s apart. */
fun bpmFrom(s: List<Pair<Long, Float>>): Int? {
    if (s.size < 90) return null
    val r = FloatArray(s.size) { s[it].second }
    fun avg(i: Int, k: Int): Float {
        val a = (i - k).coerceAtLeast(0)
        val b = (i + k).coerceAtMost(r.size - 1)
        var v = 0f
        for (j in a..b) v += r[j]
        return v / (b - a + 1)
    }
    val d = FloatArray(r.size) { avg(it, 2) - avg(it, 15) }
    val thr = sqrt(d.sumOf { (it * it).toDouble() } / d.size).toFloat() * 0.5f
    val peaks = ArrayList<Long>()
    for (i in 1 until d.size - 1) {
        val t = s[i].first
        if (d[i] > thr && d[i] >= d[i - 1] && d[i] > d[i + 1] && (peaks.isEmpty() || t - peaks.last() >= 330_000_000L)) peaks.add(t)
    }
    if (peaks.size < 4) return null
    val bpm = 60.0 * (peaks.size - 1) / ((peaks.last() - peaks.first()) / 1e9)
    return bpm.roundToInt().takeIf { it in 40..200 }
}
