package com.umutk.pulse.extras.gamehub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.umutk.pulse.tr
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/** The shelf drawn by the Rust renderer (native/shelf3d, wgpu: Vulkan, or OpenGL ES where there is no Vulkan). [AVAILABLE] is false when libpulse_shelf.so is not in the APK. */
internal object NativeShelf {
    val AVAILABLE: Boolean = try { System.loadLibrary("pulse_shelf"); true } catch (t: Throwable) { false }
    @JvmStatic external fun create(surface: Surface, w: Int, h: Int): Long
    @JvmStatic external fun destroy(h: Long)
    @JvmStatic external fun resize(h: Long, w: Int, hh: Int)
    @JvmStatic external fun addGame(h: Long, front: ByteArray, fw: Int, fh: Int, back: ByteArray?, bw: Int, bh: Int, r: Float, g: Float, b: Float)
    @JvmStatic external fun commit(h: Long)
    @JvmStatic external fun drag(h: Long, di: Float)
    @JvmStatic external fun release(h: Long, v: Float)
    @JvmStatic external fun select(h: Long, i: Int)
    @JvmStatic external fun flip(h: Long, back: Boolean)
    @JvmStatic external fun selected(h: Long): Int
    @JvmStatic external fun flipped(h: Long): Boolean
    @JvmStatic external fun frame(h: Long, dt: Float): Boolean
}

private const val ART_W = 384
private const val ART_H = 576

private fun decode(f: File): Bitmap? = if (f.isFile) runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var s = 1
    while (o.outWidth / s > ART_W * 2 && o.outHeight / s > ART_H * 2) s *= 2
    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
}.getOrNull() else null

/** The picture cut to the box's 2:3 shape (the middle of it), ART_W x ART_H. */
private fun cover(src: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(ART_W, ART_H, Bitmap.Config.ARGB_8888)
    val k = maxOf(ART_W / src.width.toFloat(), ART_H / src.height.toFloat())
    val w = src.width * k; val h = src.height * k
    Canvas(out).drawBitmap(src, null, android.graphics.RectF((ART_W - w) / 2, (ART_H - h) / 2, (ART_W + w) / 2, (ART_H + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}

private fun rgba(b: Bitmap): ByteArray {
    val buf = java.nio.ByteBuffer.allocate(b.byteCount); b.copyPixelsToBuffer(buf); return buf.array()
}

private fun average(b: Bitmap): FloatArray {
    val s = Bitmap.createScaledBitmap(b, 1, 1, true); val p = s.getPixel(0, 0)
    return floatArrayOf(AColor.red(p) / 255f, AColor.green(p) / 255f, AColor.blue(p) / 255f)
}

/** The front of a game's box: its cover, or its icon on a dark gradient with its name when it has none. */
private fun frontOf(c: Context, g: GameRow): Bitmap {
    // the box in the chosen case (the console's top band with its logo and the title) first; the built-in and web games' boxes are drawn here when asked, never stored
    val st = CaseCover.styleOf(c, g.pkg)
    if (CaseCover.isLive(g.pkg)) {
        if (st != "plain") CaseCover.liveBox(c, g.pkg, st)?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.let { return cover(it) } }
        val own = if (MegaGames.isMega(g.pkg)) MegaGames.cover(c, g.pkg.removePrefix(MegaGames.PREFIX)) else decode(HtmlGames.coverFile(c, HtmlGames.idOf(g.pkg)))
        own?.let { return cover(it) }
    }
    val file = listOf(caseFile(c, g.pkg), coverFile(c, g.pkg), steamCoverFile(c, g.pkg), playCoverFile(c, g.pkg)).firstOrNull { it.isFile }
    file?.let { decode(it) }?.let { return cover(it) }
    val out = Bitmap.createBitmap(ART_W, ART_H, Bitmap.Config.ARGB_8888)
    val cv = Canvas(out); val hue = (g.pkg.hashCode() and 0x7fffffff) % 360
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 0f, 0f, ART_H.toFloat(), AColor.HSVToColor(floatArrayOf(hue.toFloat(), .55f, .55f)), AColor.HSVToColor(floatArrayOf(((hue + 40) % 360).toFloat(), .6f, .22f)), Shader.TileMode.CLAMP)
    cv.drawRect(0f, 0f, ART_W.toFloat(), ART_H.toFloat(), p); p.shader = null
    runCatching {
        val icon = c.packageManager.getApplicationIcon(g.pkg)
        val s = ART_W * .5f; icon.setBounds(((ART_W - s) / 2).toInt(), (ART_H * .28f).toInt(), ((ART_W + s) / 2).toInt(), (ART_H * .28f + s).toInt()); icon.draw(cv)
    }
    p.color = AColor.WHITE; p.textSize = 34f; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
    cv.drawText(g.name.take(18), ART_W / 2f, ART_H * .84f, p)
    return out
}

/** The back: the game's first screenshot at the top and its name, on the front's own colour. */
private fun backOf(c: Context, g: GameRow, side: FloatArray): Bitmap {
    val out = Bitmap.createBitmap(ART_W, ART_H, Bitmap.Config.ARGB_8888)
    val cv = Canvas(out); val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 0f, 0f, ART_H.toFloat(), AColor.rgb((side[0] * 140).toInt(), (side[1] * 140).toInt(), (side[2] * 140).toInt()), AColor.rgb((side[0] * 56).toInt(), (side[1] * 56).toInt(), (side[2] * 56).toInt()), Shader.TileMode.CLAMP)
    cv.drawRect(0f, 0f, ART_W.toFloat(), ART_H.toFloat(), p); p.shader = null
    p.color = AColor.WHITE; p.textSize = 30f; p.isFakeBoldText = true
    cv.drawText(g.name.take(22), 28f, 56f, p)
    decode(steamShotFile(c, g.pkg, 0))?.let { s ->
        val w = ART_W - 56f; val h = w * s.height / s.width
        cv.drawBitmap(s, null, android.graphics.RectF(28f, 84f, 28f + w, 84f + h), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    return out
}

/** The surface and its render thread: every call into the Rust renderer happens on that thread. */
internal class NativeShelfView(c: Context, private val onSelected: (Int) -> Unit, private val onTapCentre: () -> Unit) : SurfaceView(c), SurfaceHolder.Callback {
    private val jobs = ConcurrentLinkedQueue<(Long) -> Unit>()
    @Volatile private var run = false
    private var thread: Thread? = null
    private var handle = 0L
    private var sw = 1; private var sh = 1
    private var pendingGames: List<GameRow>? = null
    private val ui = Handler(Looper.getMainLooper())
    private var flung = false
    private val unit get() = width * .42f

    private val detector = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean { post2 { NativeShelf.drag(it, -dx / unit) }; return true }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean { flung = true; post2 { NativeShelf.release(it, vx / unit) }; return true }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val x = e.x / width
            when {
                x < .22f -> post2 { NativeShelf.select(it, NativeShelf.selected(it) - 1) }
                x > .78f -> post2 { NativeShelf.select(it, NativeShelf.selected(it) + 1) }
                else -> ui.post { onTapCentre() }
            }
            return true
        }
    })

    init { holder.addCallback(this) }

    private fun post2(f: (Long) -> Unit) { jobs.add(f) }
    fun flip() = post2 { NativeShelf.flip(it, !NativeShelf.flipped(it)) }
    fun select(i: Int) = post2 { NativeShelf.select(it, i) }

    /** The games (and their pictures, read off the render thread's way: on a thread of their own) go to the renderer. */
    fun setGames(games: List<GameRow>) {
        pendingGames = games
        Thread {
            val ctx = context.applicationContext
            val boxes = games.map { g -> val f = frontOf(ctx, g); val side = average(f); Triple(f, backOf(ctx, g, side), side) }
            val data = boxes.map { (f, b, s) -> arrayOf<Any>(rgba(f), rgba(b), s) }
            post2 { h -> data.forEach { d -> NativeShelf.addGame(h, d[0] as ByteArray, ART_W, ART_H, d[1] as ByteArray, ART_W, ART_H, (d[2] as FloatArray)[0], (d[2] as FloatArray)[1], (d[2] as FloatArray)[2]) }; NativeShelf.commit(h) }
            boxes.forEach { it.first.recycle(); it.second.recycle() }
        }.start()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) flung = false
        val r = detector.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) { if (!flung) post2 { NativeShelf.release(it, 0f) } }
        return r || true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        sw = w; sh = h
        if (thread == null) start(holder.surface) else post2 { NativeShelf.resize(it, sw, sh) }
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) { stop() }

    private fun start(surface: Surface) {
        run = true
        thread = Thread {
            handle = NativeShelf.create(surface, sw, sh)
            if (handle == 0L) { run = false; return@Thread }
            pendingGames?.let { setGames(it) }
            var last = System.nanoTime(); var lastSel = -1
            while (run) {
                while (true) { val j = jobs.poll() ?: break; j(handle) }
                val now = System.nanoTime(); val dt = (now - last) / 1e9f; last = now
                if (!NativeShelf.frame(handle, dt)) { Thread.sleep(50) }
                val sel = NativeShelf.selected(handle)
                if (sel != lastSel) { lastSel = sel; ui.post { onSelected(sel) } }
            }
            NativeShelf.destroy(handle); handle = 0L
        }.also { it.name = "native-shelf"; it.start() }
    }

    private fun stop() { run = false; thread?.join(2000); thread = null }
}

/** The native shelf with its title and buttons. */
@Composable
internal fun NativeShelfScreen(games: List<GameRow>, modifier: Modifier = Modifier, onLaunch: (String) -> Unit, onDetails: (String) -> Unit) {
    var sel by remember { mutableIntStateOf(0) }
    var view by remember { mutableStateOf<NativeShelfView?>(null) }
    val g = games.getOrNull(sel)
    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { c -> NativeShelfView(c, { sel = it }, { games.getOrNull(sel)?.let { onLaunch(it.pkg) } }).also { view = it; it.setGames(games) } }, modifier = Modifier.fillMaxSize())
        // the title sits under the middle box, as on the web shelf: big white name, a small line under it; the buttons are at the bottom
        Column(Modifier.fillMaxSize().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(.70f))
            Text(g?.name ?: "", color = Color.White, fontSize = 28.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            Text(tr("Game", "Oyun"), color = Color(0xB3FFFFFF), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.weight(.30f))
            Row(Modifier.padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                Box(Modifier.androidx_clickable { g?.let { onLaunch(it.pkg) } }) { FluentIcon("play", Color.White, 34.dp) }
                Box(Modifier.androidx_clickable { view?.flip() }) { FluentIcon("refresh", Color.White, 30.dp) }
                Box(Modifier.androidx_clickable { g?.let { onDetails(it.pkg) } }) { FluentIcon("info", Color.White, 30.dp) }
            }
        }
    }
}

private fun Modifier.androidx_clickable(f: () -> Unit): Modifier = this.then(Modifier.clickable { f() })
