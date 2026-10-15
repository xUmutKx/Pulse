package com.umutk.pulse.features

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.tr
import kotlin.math.abs

/** Back camera into a preview surface plus a YUV reader; every frame's luminance goes to ZXing until a QR code reads. */
private class QrCamera(ctx: Context, private val preview: Surface, private val size: Size, private val onText: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("qr").apply { start() }
    private val bg = Handler(thread.looper)
    private val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    @Volatile private var done = false
    private val zx = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)) }

    fun start(id: String) = bg.post {
        try {
            val r = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2).also { reader = it }
            r.setOnImageAvailableListener({ frame(it) }, bg)
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    if (done) { d.close(); return }
                    device = d
                    @Suppress("DEPRECATION")
                    d.createCaptureSession(listOf(preview, r.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            if (done) return
                            session = s
                            val req = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(preview); addTarget(r.surface)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            }
                            runCatching { s.setRepeatingRequest(req.build(), null, bg) }
                        }
                        override fun onConfigureFailed(s: CameraCaptureSession) {}
                    }, bg)
                }
                override fun onDisconnected(d: CameraDevice) { d.close() }
                override fun onError(d: CameraDevice, e: Int) { d.close() }
                override fun onClosed(d: CameraDevice) { thread.quitSafely() }
            }, bg)
        } catch (e: Exception) { }
    }

    private fun frame(r: ImageReader) {
        val img = r.acquireLatestImage() ?: return
        try {
            if (done) return
            val p = img.planes[0]
            val buf = p.buffer
            val data = ByteArray(buf.remaining()).also { buf.get(it) }
            // the Y plane is the grey picture; rows may be padded, so the row stride is the data width
            val src = PlanarYUVLuminanceSource(data, p.rowStride, img.height, 0, 0, img.width, img.height, false)
            val text = runCatching { zx.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text }.getOrNull()
            zx.reset()
            if (text != null) { done = true; main.post { onText(text) } }
        } finally { img.close() }
    }

    fun stop() {
        done = true
        bg.post {
            runCatching { session?.close(); device?.close(); reader?.close() }
            if (device == null) thread.quitSafely()
            session = null; device = null; reader = null
        }
    }

    companion object {
        fun backCamera(cm: CameraManager): String? = runCatching {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
                ?: cm.cameraIdList.firstOrNull()
        }.getOrNull()

        /** A 16:9 size near 1280x720: sharp enough for small codes, light enough to decode every frame. */
        fun pick(cm: CameraManager, id: String): Size =
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.YUV_420_888)
                ?.filter { it.width * 9 == it.height * 16 }?.minByOrNull { abs(it.width - 1280) } ?: Size(1280, 720)
    }
}

/** Full-screen camera finder; [onText] gets the first QR code read, then the window closes. Needs the camera permission. */
@Composable
fun QrScanDialog(onText: (String) -> Unit, onClose: () -> Unit) {
    var cam by remember { mutableStateOf<QrCamera?>(null) }
    DisposableEffect(Unit) { onDispose { cam?.stop() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(factory = { c ->
                TextureView(c).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                            val id = QrCamera.backCamera(cm) ?: return
                            val size = QrCamera.pick(cm, id)
                            st.setDefaultBufferSize(size.width, size.height)
                            cam = QrCamera(c, Surface(st), size) { onText(it); onClose() }.also { it.start(id) }
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { cam?.stop(); cam = null; return true }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            }, modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f))   // the phone is upright: the 16:9 picture stands as 9:16
            Box(Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)).border(3.dp, Look.accent, RoundedCornerShape(16.dp)))
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp)) { PButton(tr("Cancel", "İptal")) { onClose() } }
        }
    }
}
