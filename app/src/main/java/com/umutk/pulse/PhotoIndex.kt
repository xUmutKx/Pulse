package com.umutk.pulse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.FaceDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * What the gallery's search knows about the pictures, found on the phone itself (nothing leaves it):
 *  - what is in a picture: EfficientNet-Lite0 (TensorFlow Lite, Apache 2.0) names the 1000 things of the ImageNet list (an animal, a food, a vehicle, a stethoscope, a guitar...);
 *    each name has its Turkish words in assets/photoai/labels.tsv, so "kedi", "cat" or "stetoskop" find the picture;
 *  - how many faces are in it: Android's own face finder (a few faces, front on); "yüz", "kişi", "selfie" and "grup" use it.
 * The pictures are read in the background, newest first, a few a second, and the result is kept in a file (a picture is read once). A search takes what is known so far.
 */
internal object PhotoIndex {
    /** What is known of one picture: when it was changed, how many faces it has, whether one face is big (a selfie, a portrait), and its tags (label number, how sure). */
    class Entry(val mod: Long, val faces: Int, val big: Boolean, val tags: List<Pair<Int, Float>>)

    private class Label(val words: List<String>)

    private val index = ConcurrentHashMap<String, Entry>()
    private var labels: List<Label> = emptyList()
    private var loaded = false
    private var job: Job? = null
    private val scope = CoroutineScope(Executors.newSingleThreadExecutor { r -> Thread(r, "photo-index").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }.asCoroutineDispatcher())

    /** Pictures read so far / pictures to read in this run; [tick] changes every few pictures so a search that is showing can look again. */
    val done = MutableStateFlow(0)
    val total = MutableStateFlow(0)
    val tick = MutableStateFlow(0)

    private fun fileOf(ctx: Context) = File(ctx.filesDir, "photo_index_v1.tsv")

    /** Lower case, without the Turkish marks (so "çiçek" and "cicek" are the same word). */
    fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s.lowercase(Locale.ROOT).replace("̇", "")) sb.append(when (c) { 'ç' -> 'c'; 'ğ' -> 'g'; 'ı' -> 'i'; 'ö' -> 'o'; 'ş' -> 's'; 'ü' -> 'u'; 'â' -> 'a'; 'î' -> 'i'; 'û' -> 'u'; else -> c })
        return sb.toString()
    }

    @Synchronized
    private fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            labels = ctx.assets.open("photoai/labels.tsv").bufferedReader().useLines { ls ->
                ls.filter { it.isNotBlank() }.map { line ->
                    val p = line.split('\t')
                    // the English name and the Turkish words: every word of them, folded
                    val words = LinkedHashSet<String>()
                    (p.getOrElse(1) { "" } + "," + p.getOrElse(2) { "" }).split(',', ' ').map { fold(it.trim()) }.filter { it.length >= 2 }.forEach { words.add(it) }
                    Label(words.toList())
                }.toList()
            }
        }
        runCatching {
            val f = fileOf(ctx)
            if (f.isFile) f.forEachLine { line ->
                val p = line.split('\t')
                if (p.size >= 5) {
                    val tags = if (p[4].isEmpty()) emptyList() else p[4].split(',').mapNotNull { t -> val q = t.split(':'); if (q.size == 2) (q[0].toIntOrNull() ?: return@mapNotNull null) to (q[1].toFloatOrNull() ?: return@mapNotNull null) else null }
                    index[p[0]] = Entry(p[1].toLongOrNull() ?: 0L, p[2].toIntOrNull() ?: 0, p[3] == "1", tags)
                }
            }
        }
    }

    /** How many pictures are known. */
    fun known(ctx: Context): Int { ensureLoaded(ctx.applicationContext); return index.size }

    /** Reads the pictures of [list] that are not known yet (or changed), newest first, in the background. A run that is going already is left alone. */
    fun start(ctx: Context, list: List<Ph>) {
        val app = ctx.applicationContext
        ensureLoaded(app)
        if (job?.isActive == true) return
        val todo = list.filter { !it.video && index[it.src.uri.toString()]?.mod != it.mod }.sortedByDescending { it.date }
        if (todo.isEmpty()) return
        done.value = 0; total.value = todo.size
        job = scope.launch {
            val tf = runCatching { Classifier(app) }.getOrNull()
            val out = StringBuilder()
            var n = 0
            fun flush() {
                if (out.isEmpty()) return
                runCatching { FileWriter(fileOf(app), true).use { it.write(out.toString()) } }
                out.setLength(0)
            }
            try {
                for (p in todo) {
                    ensureActive()
                    val key = p.src.uri.toString()
                    val e = runCatching { analyze(app, p, tf) }.getOrElse { Entry(p.mod, 0, false, emptyList()) }
                    index[key] = e
                    out.append(key).append('\t').append(e.mod).append('\t').append(e.faces).append('\t').append(if (e.big) 1 else 0).append('\t')
                        .append(e.tags.joinToString(",") { "${it.first}:${"%.3f".format(Locale.US, it.second)}" }).append('\n')
                    n++; done.value = n
                    if (n % 25 == 0) { flush(); tick.value++ }
                }
            } finally { flush(); tick.value++; tf?.close() }
        }
    }

    private fun analyze(ctx: Context, p: Ph, tf: Classifier?): Entry {
        val bmp = imgDecode(ctx, p.src, 480) ?: return Entry(p.mod, 0, false, emptyList())
        val tags = runCatching { tf?.top(bmp) ?: emptyList() }.getOrDefault(emptyList())
        val (faces, big) = faces(bmp)
        return Entry(p.mod, faces, big, tags)
    }

    /** The faces Android's own finder sees: how many, and whether one is big. (It finds faces looking at the camera; that is what a selfie or a portrait is.) */
    private fun faces(bmp: Bitmap): Pair<Int, Boolean> = runCatching {
        val w = bmp.width and 1.inv(); val h = bmp.height
        if (w < 48 || h < 48) return@runCatching 0 to false
        val b565 = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565).also { Canvas(it).drawBitmap(bmp, 0f, 0f, null) }
        val found = arrayOfNulls<FaceDetector.Face>(6)
        val n = FaceDetector(w, h, 6).findFaces(b565, found)
        var cnt = 0; var big = false
        for (i in 0 until n) { val f = found[i] ?: continue; if (f.confidence() >= 0.4f) { cnt++; if (f.eyesDistance() * 2.4f > w * 0.28f) big = true } }
        cnt to big
    }.getOrDefault(0 to false)

    // ---------------------------------------------------------------------------------------------- the search

    private val faceWords = setOf("yuz", "yuzler", "kisi", "kisiler", "insan", "insanlar", "face", "faces", "people", "person", "portre", "portrait")
    private val groupWords = setOf("grup", "group", "kalabalik", "crowd", "arkadaslar", "friends")

    /** Does the typed [q] (a start of a word, or a word with one letter wrong) fit the word [w]? */
    private fun fits(q: String, w: String): Boolean {
        if (w.startsWith(q)) return true
        if (q.length < 5 || w.length < 4 || kotlin.math.abs(q.length - w.length) > 1) return false
        // one letter more, one less or one different (a mistyped word: "astetoskop" for "stetoskop")
        var i = 0; var j = 0; var miss = 0
        while (i < q.length && j < w.length) {
            if (q[i] == w[j]) { i++; j++; continue }
            if (++miss > 1) return false
            when { q.length > w.length -> i++; q.length < w.length -> j++; else -> { i++; j++ } }
        }
        return miss + (q.length - i) + (w.length - j) <= 1
    }

    /** The pictures of [base] that fit [query], the best fit first. Only pictures that are known by now are looked at. Every word of the query must fit. */
    fun search(query: String, base: List<Ph>): List<Ph> {
        val toks = fold(query).split(' ').filter { it.isNotEmpty() }
        if (toks.isEmpty() || index.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Float, Ph>>()
        for (p in base) {
            if (p.video) continue
            val e = index[p.src.uri.toString()] ?: continue
            var sum = 0f; var all = true
            for (t in toks) {
                var best = 0f
                when {
                    t in faceWords -> if (e.faces >= 1) best = 0.9f
                    t == "selfie" -> if (e.faces == 1 && e.big) best = 0.95f
                    t in groupWords -> if (e.faces >= 3) best = 0.9f
                    else -> for ((li, pr) in e.tags) { val lab = labels.getOrNull(li) ?: continue; if (lab.words.any { fits(t, it) }) best = maxOf(best, pr) }
                }
                if (best <= 0f) { all = false; break }
                sum += best
            }
            if (all) scored.add(sum / toks.size to p)
        }
        return scored.sortedByDescending { it.first }.map { it.second }
    }

    // ---------------------------------------------------------------------------------------------- the classifier

    /** EfficientNet-Lite0 with 8-bit numbers: a 224 x 224 picture in, the chances of the 1000 things out. */
    private class Classifier(ctx: Context) {
        private val interp: Interpreter
        private val input = ByteBuffer.allocateDirect(224 * 224 * 3).order(ByteOrder.nativeOrder())
        private val out = Array(1) { ByteArray(1000) }
        private val lut = IntArray(256)

        init {
            val bytes = ctx.assets.open("photoai/efficientnet_lite0_int8.tflite").use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            model.put(bytes); model.rewind()
            interp = Interpreter(model, Interpreter.Options().setNumThreads(2))
            // a colour value 0..255 goes to the model's own number scale ((v - 127.5) / 127.5, then its quantisation)
            val q = interp.getInputTensor(0).quantizationParams()
            for (v in 0..255) lut[v] = (((v - 127.5f) / 127.5f) / q.scale + q.zeroPoint).roundToInt().coerceIn(0, 255)
        }

        /** The up to five likeliest things of [bmp] with their chance (0..1); a thing below 8 % is not kept. */
        fun top(bmp: Bitmap): List<Pair<Int, Float>> {
            val s = if (bmp.width == 224 && bmp.height == 224) bmp else Bitmap.createScaledBitmap(bmp, 224, 224, true)
            val px = IntArray(224 * 224)
            s.getPixels(px, 0, 224, 0, 0, 224, 224)
            input.rewind()
            for (c in px) { input.put(lut[(c shr 16) and 255].toByte()); input.put(lut[(c shr 8) and 255].toByte()); input.put(lut[c and 255].toByte()) }
            input.rewind()
            interp.run(input, out)
            val o = out[0]
            return (0 until 1000).map { it to (o[it].toInt() and 255) / 256f }.filter { it.second >= 0.08f }.sortedByDescending { it.second }.take(5)
        }

        fun close() { runCatching { interp.close() } }
    }
}
