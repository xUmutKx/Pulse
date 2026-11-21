package com.umutk.pulse.extras.launcher

import kotlin.math.abs

/*
 * The rules and the computer player of the three board games of the Games folder: Chess, Checkers and Power of 4 (four in a row). Written for Pulse; the positions are
 * small immutable objects, the computer player is a plain alpha-beta search over them.
 */

/** One move: from and to (squares 0..63, or the column in Power of 4), a kind (chess: 0 normal, 1 castling, 2 en passant, 3 double push, 5 queen promotion), and the squares a checkers move takes. */
internal class Mv(val from: Int, val to: Int, val kind: Int = 0, val cap: List<Int> = emptyList(), val pri: Int = 0) {
    fun encode() = "$from,$to,$kind," + cap.joinToString(";")
    fun same(o: Mv) = from == o.from && to == o.to && kind == o.kind && cap == o.cap
    companion object {
        fun decode(s: String): Mv? = runCatching {
            val p = s.split(",")
            Mv(p[0].toInt(), p[1].toInt(), p[2].toInt(), p.getOrElse(3) { "" }.split(";").filter { it.isNotEmpty() }.map { it.toInt() })
        }.getOrNull()
    }
}

/** A position of a two player game. [whiteTurn]: who moves; [outcome] is -1 while the game goes on, 0 white (the first player) won, 1 black won, 2 a draw. */
internal interface GPos {
    val whiteTurn: Boolean
    fun moves(): List<Mv>
    fun play(m: Mv): GPos
    /** Static value for white (positive: white is better). */
    fun eval(): Int
    fun outcome(): Int
}

/** The computer player: an alpha-beta search, deeper for a higher [level] (1..4). Level 1 sometimes plays a random move. */
internal object TinyBot {
    private const val WIN = 90000

    fun best(p: GPos, level: Int, chess: Boolean): Mv? {
        val ms = p.moves()
        if (ms.isEmpty()) return null
        if (level <= 1 && Math.random() < .45) return ms.random()
        val depth = when (level) { 1 -> 1; 2 -> 2; 3 -> 3; else -> if (chess) 3 else 5 }
        val nodes = intArrayOf(0)
        val limit = if (level >= 4) 220_000 else 60_000
        var bestMv = ms[0]; var bestScore = Int.MIN_VALUE
        // a little randomness among equal moves so a game is not always the same
        for (m in ms.sortedByDescending { it.pri }.shuffled().sortedByDescending { it.pri }) {
            val s = -negamax(p.play(m), depth - 1, -WIN * 2, WIN * 2, nodes, limit)
            if (s > bestScore) { bestScore = s; bestMv = m }
        }
        return bestMv
    }

    private fun negamax(p: GPos, d: Int, alpha0: Int, beta: Int, nodes: IntArray, limit: Int): Int {
        nodes[0]++
        val out = p.outcome()
        if (out >= 0) return if (out == 2) 0 else if ((out == 0) == p.whiteTurn) WIN + d else -(WIN + d)
        if (d <= 0 || nodes[0] > limit) return if (p.whiteTurn) p.eval() else -p.eval()
        var alpha = alpha0
        var best = Int.MIN_VALUE + 1
        for (m in p.moves().sortedByDescending { it.pri }) {
            val s = -negamax(p.play(m), d - 1, -beta, -alpha, nodes, limit)
            if (s > best) best = s
            if (best > alpha) alpha = best
            if (alpha >= beta) break
        }
        return best
    }
}

// ---- Chess ---------------------------------------------------------------------------------------------------------------

/** Chess: 64 squares (row 0 is the black side), pieces 1 pawn, 2 knight, 3 bishop, 4 rook, 5 queen, 6 king; white positive, black negative. */
internal class ChessPos(val b: IntArray, override val whiteTurn: Boolean, val castle: Int, val ep: Int, val half: Int) : GPos {
    companion object {
        fun start(): ChessPos {
            val b = IntArray(64)
            val back = intArrayOf(4, 2, 3, 5, 6, 3, 2, 4)
            for (c in 0..7) { b[c] = -back[c]; b[8 + c] = -1; b[48 + c] = 1; b[56 + c] = back[c] }
            return ChessPos(b, true, 15, -1, 0)
        }
        private val VAL = intArrayOf(0, 100, 320, 330, 500, 900, 0)
    }

    private fun r(s: Int) = s shr 3
    private fun c(s: Int) = s and 7
    private fun ok(r: Int, c: Int) = r in 0..7 && c in 0..7

    fun kingSq(white: Boolean): Int { val k = if (white) 6 else -6; for (i in 0..63) if (b[i] == k) return i; return -1 }

    /** Whether [sq] is hit by a piece of the side [byWhite]. */
    fun attacked(sq: Int, byWhite: Boolean): Boolean {
        val sg = if (byWhite) 1 else -1
        val rr = r(sq); val cc = c(sq)
        // pawns: a white pawn at (rr+1, cc±1) hits (rr, cc)
        val pr = if (byWhite) rr + 1 else rr - 1
        for (dc in intArrayOf(-1, 1)) if (ok(pr, cc + dc) && b[pr * 8 + cc + dc] == sg) return true
        for (dr in -2..2) for (dc in -2..2) if (abs(dr) + abs(dc) == 3 && dr != 0 && dc != 0 && ok(rr + dr, cc + dc) && b[(rr + dr) * 8 + cc + dc] == 2 * sg) return true
        for (dr in -1..1) for (dc in -1..1) if ((dr != 0 || dc != 0) && ok(rr + dr, cc + dc) && b[(rr + dr) * 8 + cc + dc] == 6 * sg) return true
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            var y = rr + dr; var x = cc + dc
            while (ok(y, x)) {
                val p = b[y * 8 + x]
                if (p != 0) { if (p * sg > 0 && (abs(p) == 5 || (abs(p) == 4 && (dr == 0 || dc == 0)) || (abs(p) == 3 && dr != 0 && dc != 0))) return true; break }
                y += dr; x += dc
            }
        }
        return false
    }

    fun inCheck(white: Boolean): Boolean { val k = kingSq(white); return k >= 0 && attacked(k, !white) }

    private fun pseudo(): List<Mv> {
        val out = ArrayList<Mv>()
        val sg = if (whiteTurn) 1 else -1
        for (s in 0..63) {
            val p = b[s]
            if (p * sg <= 0) continue
            val rr = r(s); val cc = c(s)
            when (abs(p)) {
                1 -> {
                    val dir = -sg; val start = if (whiteTurn) 6 else 1; val last = if (whiteTurn) 0 else 7
                    val r1 = rr + dir
                    if (ok(r1, cc) && b[r1 * 8 + cc] == 0) {
                        out += Mv(s, r1 * 8 + cc, if (r1 == last) 5 else 0, pri = if (r1 == last) 800 else 0)
                        val r2 = rr + 2 * dir
                        if (rr == start && b[r2 * 8 + cc] == 0) out += Mv(s, r2 * 8 + cc, 3)
                    }
                    for (dc in intArrayOf(-1, 1)) {
                        val x = cc + dc
                        if (!ok(r1, x)) continue
                        val t = r1 * 8 + x
                        if (b[t] * sg < 0) out += Mv(s, t, if (r1 == last) 5 else 0, pri = 100 * abs(b[t]) + (if (r1 == last) 800 else 0))
                        else if (t == ep) out += Mv(s, t, 2, pri = 100)
                    }
                }
                else -> {}
            }
        }
        // knights, sliders and kings by explicit direction tables (clearer than packed offsets)
        for (s in 0..63) {
            val p = b[s]
            if (p * sg <= 0) continue
            val rr = r(s); val cc = c(s); val a = abs(p)
            if (a == 2) {
                for ((dr, dc) in listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)) {
                    val y = rr + dr; val x = cc + dc
                    if (ok(y, x) && b[y * 8 + x] * sg <= 0) out += Mv(s, y * 8 + x, 0, pri = if (b[y * 8 + x] != 0) 100 * abs(b[y * 8 + x]) else 0)
                }
            } else if (a == 3 || a == 4 || a == 5) {
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val diag = dr != 0 && dc != 0
                    if (a == 3 && !diag) continue
                    if (a == 4 && diag) continue
                    var y = rr + dr; var x = cc + dc
                    while (ok(y, x)) {
                        val t = b[y * 8 + x]
                        if (t * sg > 0) break
                        out += Mv(s, y * 8 + x, 0, pri = if (t != 0) 100 * abs(t) else 0)
                        if (t != 0) break
                        y += dr; x += dc
                    }
                }
            } else if (a == 6) {
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val y = rr + dr; val x = cc + dc
                    if (ok(y, x) && b[y * 8 + x] * sg <= 0) out += Mv(s, y * 8 + x, 0, pri = if (b[y * 8 + x] != 0) 100 * abs(b[y * 8 + x]) else 0)
                }
                // castling: the king and the rook have not moved, the squares between are empty and the king does not cross a hit square
                val home = if (whiteTurn) 60 else 4
                if (s == home && !attacked(home, !whiteTurn)) {
                    val ks = if (whiteTurn) 1 else 4; val qs = if (whiteTurn) 2 else 8
                    if (castle and ks != 0 && b[home + 1] == 0 && b[home + 2] == 0 && b[home + 3] == 4 * sg && !attacked(home + 1, !whiteTurn) && !attacked(home + 2, !whiteTurn)) out += Mv(home, home + 2, 1)
                    if (castle and qs != 0 && b[home - 1] == 0 && b[home - 2] == 0 && b[home - 3] == 0 && b[home - 4] == 4 * sg && !attacked(home - 1, !whiteTurn) && !attacked(home - 2, !whiteTurn)) out += Mv(home, home - 2, 1)
                }
            }
        }
        return out
    }

    private val legal: List<Mv> by lazy { pseudo().filter { !play(it).inCheck(whiteTurn) } }
    override fun moves(): List<Mv> = legal

    override fun play(m: Mv): ChessPos {
        val nb = b.copyOf()
        val p = nb[m.from]
        nb[m.to] = p; nb[m.from] = 0
        var ne = -1
        var nc = castle
        when (m.kind) {
            1 -> { if (m.to > m.from) { nb[m.from + 1] = nb[m.from + 3]; nb[m.from + 3] = 0 } else { nb[m.from - 1] = nb[m.from - 4]; nb[m.from - 4] = 0 } }
            2 -> nb[r(m.from) * 8 + c(m.to)] = 0
            3 -> ne = (m.from + m.to) / 2
            5 -> nb[m.to] = if (p > 0) 5 else -5
        }
        // the rights go when the king or a rook moves, or a rook is taken
        fun lose(sq: Int) { when (sq) { 60 -> nc = nc and 3.inv(); 4 -> nc = nc and 12.inv(); 63 -> nc = nc and 1.inv(); 56 -> nc = nc and 2.inv(); 7 -> nc = nc and 4.inv(); 0 -> nc = nc and 8.inv() } }
        lose(m.from); lose(m.to)
        val reset = abs(p) == 1 || b[m.to] != 0 || m.kind == 2
        return ChessPos(nb, !whiteTurn, nc, ne, if (reset) 0 else half + 1)
    }

    override fun eval(): Int {
        var s = 0
        for (i in 0..63) {
            val p = b[i]
            if (p == 0) continue
            val a = abs(p); val rr = r(i); val cc = c(i)
            var v = VAL[a]
            val centre = 3 - maxOf(abs(rr - 3) , abs(rr - 4)).coerceAtMost(3) + 3 - maxOf(abs(cc - 3), abs(cc - 4)).coerceAtMost(3)
            when (a) {
                1 -> v += (if (p > 0) 6 - rr else rr - 1) * 8 + centre * 2
                2, 3 -> v += centre * 6
                5 -> v += centre * 2
                6 -> v += 0
            }
            s += if (p > 0) v else -v
        }
        return s
    }

    override fun outcome(): Int {
        if (moves().isEmpty()) return if (inCheck(whiteTurn)) (if (whiteTurn) 1 else 0) else 2
        if (half >= 100) return 2
        // two bare kings
        var others = 0; for (v in b) if (v != 0 && abs(v) != 6) others++
        return if (others == 0) 2 else -1
    }
}

// ---- Checkers ------------------------------------------------------------------------------------------------------------

/** Checkers (draughts, 8 x 8, captures are compulsory, men take forward, kings both ways, a man that reaches the far row is crowned). 1 man, 2 king; white positive and moves up the board. */
internal class CheckersPos(val b: IntArray, override val whiteTurn: Boolean, val quiet: Int) : GPos {
    companion object {
        fun start(): CheckersPos {
            val b = IntArray(64)
            for (r in 0..7) for (c in 0..7) if ((r + c) % 2 == 1) { if (r < 3) b[r * 8 + c] = -1 else if (r > 4) b[r * 8 + c] = 1 }
            return CheckersPos(b, true, 0)
        }
    }

    private fun ok(r: Int, c: Int) = r in 0..7 && c in 0..7

    private fun jumps(s: Int, piece: Int, board: IntArray, taken: List<Int>, path: Int, out: MutableList<Mv>, from: Int) {
        val sg = if (piece > 0) 1 else -1
        val kingPiece = abs(piece) == 2
        var any = false
        for (dr in intArrayOf(-1, 1)) for (dc in intArrayOf(-1, 1)) {
            if (!kingPiece && dr != -sg) continue
            val r = (s shr 3); val c = s and 7
            val mr = r + dr; val mc = c + dc; val lr = r + 2 * dr; val lc = c + 2 * dc
            if (!ok(lr, lc)) continue
            val mid = mr * 8 + mc
            if (board[mid] * sg < 0 && mid !in taken && board[lr * 8 + lc] == 0) {
                any = true
                val land = lr * 8 + lc
                // a man that lands on the far row is crowned and stops
                val crown = !kingPiece && (lr == 0 && sg > 0 || lr == 7 && sg < 0)
                val nt = taken + mid
                if (crown) out += Mv(from, land, 0, nt, 100 * nt.size + 50)
                else {
                    val before = out.size
                    // the piece has left its start square for the rest of the jump
                    board[from] = 0
                    jumps(land, piece, board, nt, path + 1, out, from)
                    board[from] = piece
                    if (out.size == before) out += Mv(from, land, 0, nt, 100 * nt.size)
                }
            }
        }
        if (!any) { /* a jump ends where no more can be made: the caller added it */ }
    }

    private val all: List<Mv> by lazy {
        val sg = if (whiteTurn) 1 else -1
        val caps = ArrayList<Mv>()
        val work = b.copyOf()
        for (s in 0..63) if (b[s] * sg > 0) jumps(s, b[s], work, emptyList(), 0, caps, s)
        if (caps.isNotEmpty()) caps
        else {
            val out = ArrayList<Mv>()
            for (s in 0..63) {
                val p = b[s]
                if (p * sg <= 0) continue
                val r = s shr 3; val c = s and 7
                for (dr in intArrayOf(-1, 1)) for (dc in intArrayOf(-1, 1)) {
                    if (abs(p) == 1 && dr != -sg) continue
                    val y = r + dr; val x = c + dc
                    if (ok(y, x) && b[y * 8 + x] == 0) out += Mv(s, y * 8 + x, 0, emptyList(), if ((y == 0 && sg > 0 || y == 7 && sg < 0) && abs(p) == 1) 60 else 0)
                }
            }
            out
        }
    }
    override fun moves(): List<Mv> = all

    override fun play(m: Mv): CheckersPos {
        val nb = b.copyOf()
        val p = nb[m.from]
        nb[m.from] = 0
        for (t in m.cap) nb[t] = 0
        val tr = m.to shr 3
        nb[m.to] = if (abs(p) == 1 && (tr == 0 && p > 0 || tr == 7 && p < 0)) 2 * (if (p > 0) 1 else -1) else p
        val quietNow = if (m.cap.isNotEmpty() || abs(p) == 1) 0 else quiet + 1
        return CheckersPos(nb, !whiteTurn, quietNow)
    }

    override fun eval(): Int {
        var s = 0
        for (i in 0..63) {
            val p = b[i]
            if (p == 0) continue
            val r = i shr 3
            val v = if (abs(p) == 2) 330 else 100 + (if (p > 0) 7 - r else r) * 4
            s += if (p > 0) v else -v
        }
        return s
    }

    override fun outcome(): Int {
        if (moves().isEmpty()) return if (whiteTurn) 1 else 0
        return if (quiet >= 80) 2 else -1
    }
}

// ---- Power of 4 (four in a row) -------------------------------------------------------------------------------------------

/** Four in a row: seven columns, six rows, a disc falls to the lowest free place. 1 first player (white), -1 second. The move is the column. */
internal class Power4Pos(val b: IntArray, override val whiteTurn: Boolean, val last: Int) : GPos {
    companion object {
        const val W = 7; const val H = 6
        fun start() = Power4Pos(IntArray(W * H), true, -1)
    }

    fun rowOf(col: Int): Int { for (r in H - 1 downTo 0) if (b[r * W + col] == 0) return r; return -1 }

    private val win: Int by lazy {
        var w = 0
        outer@ for (r in 0 until H) for (c in 0 until W) {
            val p = b[r * W + c]
            if (p == 0) continue
            for ((dr, dc) in listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
                var n = 1
                while (n < 4) { val y = r + dr * n; val x = c + dc * n; if (y !in 0 until H || x !in 0 until W || b[y * W + x] != p) break; n++ }
                if (n == 4) { w = p; break@outer }
            }
        }
        w
    }

    override fun moves(): List<Mv> = if (win != 0) emptyList() else (0 until W).filter { b[it] == 0 }.sortedBy { abs(it - 3) }.map { Mv(it, it, 0, pri = 3 - abs(it - 3)) }

    override fun play(m: Mv): Power4Pos {
        val nb = b.copyOf()
        val r = rowOf(m.to)
        nb[r * W + m.to] = if (whiteTurn) 1 else -1
        return Power4Pos(nb, !whiteTurn, m.to)
    }

    override fun eval(): Int {
        var s = 0
        for (r in 0 until H) for (c in 0 until W) {
            val p = b[r * W + c]
            if (p != 0) s += p * (3 - abs(c - 3)) * 3
            for ((dr, dc) in listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
                var white = 0; var black = 0; var ok = true
                for (n in 0 until 4) { val y = r + dr * n; val x = c + dc * n; if (y !in 0 until H || x !in 0 until W) { ok = false; break }; val q = b[y * W + x]; if (q > 0) white++ else if (q < 0) black++ }
                if (!ok) continue
                if (black == 0 && white > 0) s += when (white) { 1 -> 1; 2 -> 6; 3 -> 40; else -> 0 }
                if (white == 0 && black > 0) s -= when (black) { 1 -> 1; 2 -> 6; 3 -> 40; else -> 0 }
            }
        }
        return s
    }

    override fun outcome(): Int = if (win > 0) 0 else if (win < 0) 1 else if (b.none { it == 0 }) 2 else -1
}
