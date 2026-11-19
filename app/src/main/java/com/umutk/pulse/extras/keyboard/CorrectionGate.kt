package com.umutk.pulse.extras.keyboard

import kotlin.math.exp
import kotlin.math.tanh

/**
 * The hybrid correction's confidence gate: how likely the correction found for a typed word is the word meant. A small network (9 inputs, 12 hidden)
 * in the spirit of WM Keyboard's Confidence (MIT), fitted for Pulse on Pulse's own word lists (tr, en): typos made with the keys around each letter
 * (a neighbour, a letter missed or doubled, two swapped), and rare words the list was not told about, typed right (what a wrong correction looks like).
 * On held-out words, at the 0.6 threshold it kept 81 % of the right fixes and stopped 83 % of the wrong ones.
 */
object CorrectionGate {
    const val THRESHOLD = 0.6f
    private val MEAN = floatArrayOf(9.67931f, 8.20798f, 1.94820f, 1.28853f, 1.83435f, 0.87920f, 0.05536f, 6.99112f, -0.37016f)
    private val SD = floatArrayOf(2.50849f, 3.68978f, 1.68225f, 0.45308f, 0.61629f, 0.32590f, 0.22869f, 2.39583f, 0.72917f)
    private val W1 = arrayOf(
        floatArrayOf(0.04026f, -0.00581f, -0.18643f, 0.23846f, -0.14729f, 0.04257f, -0.05916f, -0.03461f, 0.03491f),
        floatArrayOf(0.35599f, -0.09136f, -0.18139f, -0.01021f, -0.47529f, -0.28666f, 0.09796f, -0.44974f, -0.86299f),
        floatArrayOf(-0.00805f, -0.03541f, -0.24410f, 0.29021f, -0.21152f, 0.07531f, -0.07749f, -0.07646f, 0.05217f),
        floatArrayOf(0.49696f, 0.40483f, -0.47574f, -0.00136f, -0.37335f, 0.00283f, 0.01453f, 0.30779f, -0.26256f),
        floatArrayOf(0.29279f, -0.20199f, -0.08592f, 0.43919f, -0.39151f, -0.07457f, 0.06304f, 0.09756f, -1.05687f),
        floatArrayOf(-0.01383f, -0.09579f, 0.28904f, -0.64814f, -0.30282f, -0.06169f, 0.06506f, 0.42252f, 0.06396f),
        floatArrayOf(0.08793f, 0.04724f, 0.24179f, -0.27507f, 0.20242f, -0.09814f, 0.10311f, 0.14090f, -0.08398f),
        floatArrayOf(-0.07303f, 0.37910f, -0.02125f, -0.92636f, 0.11442f, 0.10088f, 0.22856f, -0.49075f, -0.18163f),
        floatArrayOf(-0.10355f, -0.14423f, 0.18537f, 0.56747f, 0.31455f, 0.11737f, 0.17174f, 0.31404f, -1.31487f),
        floatArrayOf(0.06286f, 0.28660f, 0.05802f, -0.03303f, 0.22818f, 0.25346f, 0.10261f, -0.69190f, -0.07435f),
        floatArrayOf(0.00116f, -0.17431f, 0.06573f, 0.03589f, -0.06343f, -0.14160f, 0.17927f, -0.36826f, 0.98973f),
        floatArrayOf(0.53738f, 0.39595f, -0.11487f, -0.67699f, -0.42820f, -0.03335f, 0.19512f, 0.51425f, -0.04866f),
    )
    private val B1 = floatArrayOf(0.03428f, -0.03237f, 0.09532f, 0.31703f, -0.93330f, 0.36363f, -0.22603f, -0.17267f, 0.40949f, 0.26373f, 0.18142f, -0.97864f)
    private val W2 = floatArrayOf(-0.41308f, -1.03510f, -0.58280f, -0.91085f, -1.33609f, 0.97112f, 0.58370f, 1.13707f, 1.62749f, 0.75319f, 0.94883f, 1.12770f)
    private const val B2 = -0.82438f

    /** The edit distance the gate was fitted with (a swap of two neighbours counts about as one). */
    fun dist(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val t = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[j] = minOf(d[j], prev)
                prev = t
            }
        }
        return d[b.length]
    }

    private fun isSwap(a: String, b: String): Boolean {
        if (a.length != b.length || a == b) return false
        val i = a.indices.first { a[it] != b[it] }
        return i + 1 < a.length && a[i] == b[i + 1] && a[i + 1] == b[i] && a.substring(i + 2) == b.substring(i + 2)
    }

    /**
     * P(the correction [cand] of [typed] is right). [candScore] and [runScore] are the words' list scores (ln of their counts), [run] the next best
     * word the bar had for it (null when none).
     */
    fun probability(typed: String, cand: String, candScore: Float, run: String?, runScore: Float): Float {
        val e = dist(typed, cand); val er = if (run != null) dist(typed, run) else 3
        val f = floatArrayOf(
            candScore, if (run != null) runScore else 0f,
            (candScore - 2.6f * e) - (if (run != null) runScore - 2.6f * er else 0f),
            e.toFloat(), er.toFloat(), if (run != null) 1f else 0f, if (isSwap(typed, cand)) 1f else 0f,
            typed.length.toFloat(), (cand.length - typed.length).toFloat(),
        )
        var logit = B2
        for (h in W1.indices) {
            var a = B1[h]
            for (i in f.indices) a += W1[h][i] * (f[i] - MEAN[i]) / SD[i]
            logit += W2[h] * tanh(a)
        }
        return (1.0 / (1.0 + exp(-logit.toDouble()))).toFloat()
    }
}
