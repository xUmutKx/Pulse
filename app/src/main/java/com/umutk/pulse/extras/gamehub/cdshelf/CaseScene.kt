/*
 * Adapted for Pulse from KMP Odd Scroll's CD Rack by mejdi14 (https://github.com/mejdi14/KMPOddScroll), GPL-3.0.
 * Changes for Pulse: moved into this package, the Kotlin Multiplatform expect/actual of the shader merged into one Android file.
 */
package com.umutk.pulse.extras.gamehub.cdshelf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.umutk.pulse.extras.gamehub.cdshelf.internal.clamped
import com.umutk.pulse.extras.gamehub.cdshelf.internal.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Baked layers are at most this many pixels a millimetre, and at least the first. */
private const val MIN_PPM = 2.5f
private const val MAX_PPM = 8.75f

/** The largest the print is kept for the shader to sample. */
private const val ART_PX = 1024

/** The sparks are found at a half of the frame's pixels across, as the reference finds them. */
private const val SPARK_DOWN = 2f

/**
 * A drawing of one case, any pose, through any camera. Its pieces are baked
 * once for the size the case stands on screen, and shared by whatever art is
 * printed on its disc; the art can be swapped, which bakes only the disc.
 */
internal class CaseScene {
    private val shader: CaseShader? = sharedShader
    private var camera = Camera()
    private var pose = CasePose()
    private var art: ImageBitmap? = null
    private var cover: ImageBitmap? = null

    private val tray = Pose()
    private val lid = Pose()
    private val disc = Pose()
    private val loose = Pose()

    /** The cover is out of the lid this frame, drawn at [loose] instead. */
    private var coverLoose = false

    /** The manual behind the loose cover, this frame, or null for a single sheet. */
    private var book: BookPose? = null
    private var manual: CdManual? = null
    private val spine = Pose()
    private val leaf = Pose()

    /** How wide the loose cover stands, in covers: two with its manual open. */
    private var footprint = 1f

    /** Drawing the sparks for [stars]: the rods' sparks, and all that is opaque in black. */
    private var sparking = false

    /** What the manual is drawn into to be blurred as it riffles, this frame. */
    private var riffleLayer: GraphicsLayer? = null

    /** The spine's two ends and a point right of it on screen, as last drawn. */
    private val spineHull = FloatArray(6)

    private val trayLayers = trayLayers()
    private val lidLayer = lidLayer()
    private var trayBitmaps: List<ImageBitmap> = emptyList()
    private var lidBitmap: ImageBitmap? = null
    private var discArt: DiscArt? = null
    private var square: ImageBitmap? = null
    private val trayShadow = bakeShadow(lid = false)
    private val lidShadow = bakeShadow(lid = true)
    private val coverShadow = bakeCoverShadow()
    private var ppm = 0f

    private val matrix = Matrix()
    private val plane = PlaneScratch()
    private val eye = FloatArray(3)
    private val toWorld = FloatArray(9)
    private val world = FloatArray(12)
    private val screen = FloatArray(8)
    private val rotation = FloatArray(9)
    private val turn = FloatArray(9)
    private val v = FloatArray(3)
    private val n = FloatArray(3)
    private val path = Path()
    private val hull = FloatArray(24)

    /** Prints [image] on the disc from now on. */
    fun setArt(image: ImageBitmap) {
        if (image === art) return
        art = image
        square = if (shader != null) squareArt(image, ART_PX) else null
        discArt = if (shader == null && ppm > 0f) bakeDisc(image, ppm) else null
    }

    /** Slips [image] in under the lid's glass from now on, or takes it out for null. */
    fun setCover(image: ImageBitmap?) {
        cover = image
    }

    /**
     * The rest of the booklet the cover is the front of, or none; its back is
     * also what the back of the cover shows, in the lid or out.
     */
    fun setManual(manual: CdManual?) {
        this.manual = manual
    }

    /**
     * Bakes the pieces for the size [camera] shows the case at, if they are
     * not already. Touches no drawing state, so it can run off the main thread
     * before the case is first drawn.
     */
    fun prepare(camera: Camera) {
        // As sharp as the shut case stands on screen.
        val want = camera.scaleAt(0f).clamped(MIN_PPM, MAX_PPM)
        val rounded = (want * 4f).roundToInt() / 4f
        if (rounded == ppm) return
        val mode = if (shader != null) Bake.Height else Bake.Look
        val make = shader?.let { it::heightMap }
        trayBitmaps = trayLayers.map { bakeLayer(it, rounded, mode, make) }
        lidBitmap = bakeLayer(lidLayer, rounded, mode, make)
        val image = art
        discArt = if (shader == null && image != null) bakeDisc(image, rounded) else null
        ppm = rounded
    }

    /**
     * Draws the case at [pose] through [camera], its shadow on the backdrop at
     * [shadow] of its strength; with [cover], the cover is out of the lid and
     * stands there on its own, the front of [book] if it has one. Riffling,
     * the book is drawn through [riffleLayer], smeared the way it turns.
     */
    fun draw(
        scope: DrawScope,
        camera: Camera,
        pose: CasePose,
        shadow: Float = 1f,
        cover: CasePose? = null,
        book: BookPose? = null,
        riffleLayer: GraphicsLayer? = null,
    ) = with(scope) {
        if (size.width <= 0f || size.height <= 0f) return@with
        this@CaseScene.camera = camera
        this@CaseScene.pose = pose
        prepare(camera)
        if (art == null) return@with
        place()
        coverLoose = cover != null && this@CaseScene.cover != null
        if (cover != null) placeLoose(cover)
        val manual = if (coverLoose && book != null && book.spreads > 0) book else null
        this@CaseScene.book = manual
        this@CaseScene.riffleLayer = riffleLayer
        footprint = if (manual == null) 1f else lerp(manual.across(manual.from), manual.across(manual.to), manual.turn)

        // The shadows land on the backdrop, behind everything.
        if (shadow > 0f) {
            shadow(tray, -2f, trayShadow, shadow)
            shadow(lid, HINGE_Z, lidShadow, shadow)
            if (coverLoose) shadow(loose, 0f, coverShadow, shadow, widen = footprint)
        }

        pieces()
        rememberHull()
    }

    /**
     * The reference's four-point stars over the case [draw] has just drawn:
     * the case drawn again into [layer], a [SPARK_DOWN]th the size, as the
     * sparks its rods catch of the studio's lamps — the disc, the cover and
     * the manual black in front of them — and the shader's stars made of
     * them laid over it at [strength]. Nothing without a shader.
     */
    fun stars(scope: DrawScope, layer: GraphicsLayer, strength: Float = 1f) = with(scope) {
        val glass = shader ?: return@with
        if (art == null || strength <= 0f || size.width <= 0f || size.height <= 0f) return@with
        val w = ceil(size.width / SPARK_DOWN).toInt()
        val h = ceil(size.height / SPARK_DOWN).toInt()
        layer.renderEffect = glass.stars(strength)
        layer.record(this, layoutDirection, IntSize(w, h)) {
            scale(1f / SPARK_DOWN, Offset.Zero) {
                sparking = true
                try {
                    pieces()
                } finally {
                    sparking = false
                }
            }
        }
        scale(SPARK_DOWN, Offset.Zero) { drawLayer(layer) }
    }

    /** Tray, lid and the cover out of it, back to front. */
    private fun DrawScope.pieces() {
        // Back half first: whichever of tray and lid is further from the
        // camera, by the middle of each.
        tray.apply(0f, 0f, -2f, v)
        val trayDepth = camera.distance - v[2]
        lid.apply(0f, 0f, HINGE_Z, v)
        val lidDepth = camera.distance - v[2]
        if (trayDepth > lidDepth) {
            drawTray()
            drawLid()
        } else {
            drawLid()
            drawTray()
        }
        // Out of the lid, the cover is in front of the case all the way.
        val manual = book
        if (manual != null) riffledBook(manual) else if (coverLoose) insert(loose, 0f, facing(loose))
    }

    /**
     * The manual, smeared sideways while it riffles, after the sketchbook's
     * motion blur: none until a quarter of the run's top speed, then
     * growing to as wide, against the book, as the sketchbook's widest.
     */
    private fun DrawScope.riffledBook(b: BookPose) {
        val layer = riffleLayer
        val fast = ((b.riffle - RIFFLE_BLUR_FROM) / (RIFFLE_BLUR_FULL - RIFFLE_BLUR_FROM)).clamped(0f, 1f)
        // Half a spread across, from the spine as last drawn.
        val half = sqrt(
            (spineHull[4] - spineHull[0]) * (spineHull[4] - spineHull[0]) +
                (spineHull[5] - spineHull[1]) * (spineHull[5] - spineHull[1]),
        )
        if (layer == null || sparking || fast <= 0f || !(half > 0f)) {
            drawBook(b)
            return
        }
        layer.renderEffect = BlurEffect(RIFFLE_BLUR * half * fast, 0f, TileMode.Decal)
        layer.record(this, layoutDirection, IntSize(ceil(size.width).toInt(), ceil(size.height).toInt())) {
            drawBook(b)
        }
        drawLayer(layer)
    }

    // --- placing -----------------------------------------------------------------

    private fun place() {
        val s = pose.scale
        eulerXyz(pose.pitch, pose.yaw, pose.roll, tray.r)
        tray.s = s
        tray.t[0] = pose.x
        tray.t[1] = pose.y
        tray.t[2] = pose.z

        // The lid turns about the hinge pin, which runs up the case's y.
        aboutY(-pose.lid, turn)
        times(tray.r, turn, lid.r)
        lid.s = s
        // The pin stays put: offset = s·R·(pin − turn·pin) + tray's offset.
        val px = HINGE_X - (turn[0] * HINGE_X + turn[2] * HINGE_Z)
        val pz = HINGE_Z - (turn[6] * HINGE_X + turn[8] * HINGE_Z)
        for (k in 0 until 3) {
            lid.t[k] = s * (tray.r[k * 3] * px + tray.r[k * 3 + 2] * pz) + tray.t[k]
        }

        aboutZ(-pose.spin, turn)
        times(tray.r, turn, disc.r)
        disc.s = s
        tray.t.copyInto(disc.t)
    }

    private fun placeLoose(cover: CasePose) {
        eulerXyz(cover.pitch, cover.yaw, cover.roll, loose.r)
        loose.s = cover.scale
        loose.t[0] = cover.x
        loose.t[1] = cover.y
        loose.t[2] = cover.z
    }

    /** Which way a frame's face points: towards the camera, or away. */
    private fun facing(frame: Pose): Boolean {
        frame.turn(0f, 0f, 1f, n)
        frame.apply(0f, 0f, 0f, v)
        return n[0] * -v[0] + n[1] * -v[1] + n[2] * (camera.distance - v[2]) > 0f
    }

    // --- tray and lid --------------------------------------------------------------

    private fun DrawScope.drawTray() {
        val front = facing(tray)
        val hinge = hingeFacing(tray)
        if (!hinge) knuckles(tray, trayKnuckles)
        walls(tray, TRAY_BACK, TRAY_FRONT, front = false)
        // The disc sits between the floor and the rails.
        val order = if (front) TRAY_ORDER else TRAY_ORDER.reversed()
        for (k in order) {
            if (k == DISC) drawDisc(front) else layer(tray, trayLayers[k], trayBitmaps[k])
        }
        walls(tray, TRAY_BACK, TRAY_FRONT, front = true)
        if (hinge) knuckles(tray, trayKnuckles)
    }

    private fun DrawScope.drawLid() {
        val front = facing(lid)
        val hinge = hingeFacing(lid)
        if (!hinge) knuckles(lid, lidKnuckles)
        walls(lid, LID_BACK, LID_FRONT, front = false)
        // The cover is just behind the glass: under it from the front, over
        // it from behind.
        if (front && !coverLoose) insert(lid, COVER_Z, front)
        lidBitmap?.let { layer(lid, lidLayer, it) }
        if (!front && !coverLoose) insert(lid, COVER_Z, front)
        if (shader == null) glare(lid)
        walls(lid, LID_BACK, LID_FRONT, front = true)
        if (hinge) knuckles(lid, lidKnuckles)
    }

    private fun hingeFacing(frame: Pose): Boolean {
        frame.turn(-1f, 0f, 0f, n)
        frame.apply(HINGE_X, 0f, HINGE_Z, v)
        return n[0] * -v[0] + n[1] * -v[1] + n[2] * (camera.distance - v[2]) > 0f
    }

    private fun DrawScope.layer(frame: Pose, layer: CaseLayer, bitmap: ImageBitmap) {
        frame.plane(camera, layer.z, matrix, plane)
        val mmPerPx = 1f / ppm
        val glass = shader
        withTransform({
            transform(matrix)
            translate(layer.left, layer.top)
            scale(mmPerPx, mmPerPx, Offset.Zero)
        }) {
            if (glass != null) {
                frame.eye(camera.distance, eye)
                frame.toWorld(toWorld)
                val brush = glass.glass(
                    bitmap, layer.left, layer.top, mmPerPx, layer.z, eye, toWorld,
                    layer.thickness, layer.overDisc, if (sparking) SPARK_DOWN else 0f,
                )
                drawRect(brush, size = Size(bitmap.width.toFloat(), bitmap.height.toFloat()))
            } else {
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(bitmap.width, bitmap.height),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }

    /**
     * The cover on [frame]'s plane at [z] — under the lid's glass, its middle
     * square across the inside of the rim, or out on its own; from behind,
     * the back of the paper, or of the booklet.
     */
    private fun DrawScope.insert(frame: Pose, z: Float, front: Boolean) {
        val image = cover ?: return
        frame.plane(camera, z, matrix, plane)
        withTransform({ transform(matrix) }) {
            if (!front) {
                // Mirrored, so that from behind it reads the right way round.
                withTransform({ scale(-1f, 1f, Offset.Zero) }) { back(-COVER_HALF) }
            } else {
                coverPrint(image, -COVER_HALF)
            }
        }
    }

    /** The booklet's back across the cover, its left edge at [left]; plain paper with none. */
    private fun DrawScope.back(left: Float) {
        val image = manual?.back
        if (image != null && !sparking) {
            coverPrint(image, left)
        } else {
            drawRect(if (sparking) Color.Black else PAPER, Offset(left, -COVER_HALF), Size(2f * COVER_HALF, 2f * COVER_HALF))
        }
    }

    /** [image]'s middle square across the cover, its left edge at [left]. */
    private fun DrawScope.coverPrint(image: ImageBitmap, left: Float) {
        if (sparking) {
            drawRect(Color.Black, Offset(left, -COVER_HALF), Size(2f * COVER_HALF, 2f * COVER_HALF))
            return
        }
        val square = discSquare(image)
        val k = 2f * COVER_HALF / square.width
        withTransform({
            translate(left, -COVER_HALF)
            scale(k, k, Offset.Zero)
        }) {
            drawImage(
                image = image,
                srcOffset = IntOffset(square.left, square.top),
                srcSize = IntSize(square.width, square.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(square.width, square.height),
                filterQuality = FilterQuality.Medium,
            )
        }
    }

    // --- the manual ------------------------------------------------------------------

    /**
     * The manual at [b]: its spine where the open spread is centred, or along
     * the edge of a shut one, so that whatever shows stands in the middle; a
     * leaf turning over the spine on top of the two halves it uncovers and
     * lands on, as the camera sees it, its back mirrored onto it.
     */
    private fun DrawScope.drawBook(b: BookPose) {
        if (b.from < 0 && b.to < 0) {
            // Shut on its front: the cover, as it came out of the lid.
            insert(loose, 0f, facing(loose))
            rememberSpine()
            return
        }
        val at = lerp(b.spineAt(b.from), b.spineAt(b.to), b.turn)
        spine.copyFrom(loose)
        loose.turn(1f, 0f, 0f, v)
        for (k in 0 until 3) spine.t[k] += loose.s * v[k] * at
        rememberSpine()

        if (b.from == b.to) {
            side(spine, b.from, 0, mirror = false)
            side(spine, b.from, 1, mirror = false)
            return
        }
        val next = b.to > b.from
        side(spine, if (next) b.from else b.to, 0, mirror = false)
        side(spine, if (next) b.to else b.from, 1, mirror = false)
        // The leaf turns about the spine, lifting towards the camera.
        val angle = PI.toFloat() * b.turn
        aboutY(if (next) -angle else angle, turn)
        times(spine.r, turn, leaf.r)
        leaf.s = spine.s
        spine.t.copyInto(leaf.t)
        if (facing(leaf)) {
            side(leaf, b.from, if (next) 1 else 0, mirror = false)
        } else {
            side(leaf, b.to, if (next) 0 else 1, mirror = true)
        }
    }

    /**
     * What the manual shows at [at] on [side] of the spine — 0 left, 1 right
     * — on [frame]'s plane; [mirror]ed for the back of a leaf.
     */
    private fun DrawScope.side(frame: Pose, at: Int, side: Int, mirror: Boolean) {
        val b = book ?: return
        val left = if (side == 0) -2f * COVER_HALF else 0f
        frame.plane(camera, 0f, matrix, plane)
        withTransform({
            transform(matrix)
            if (mirror) scale(-1f, 1f, Offset.Zero)
        }) {
            when {
                at < 0 -> if (side == 1) cover?.let { coverPrint(it, left) }
                at >= b.spreads -> if (side == 0) back(left)
                else -> manual?.spreads?.getOrNull(at)?.let { page(it, side, left) }
            }
        }
    }

    /** [side] of the spine of [spread], a square either side of its middle, its left edge at [left]. */
    private fun DrawScope.page(spread: ImageBitmap, side: Int, left: Float) {
        if (sparking) {
            drawRect(Color.Black, Offset(left, -COVER_HALF), Size(2f * COVER_HALF, 2f * COVER_HALF))
            return
        }
        val half = minOf(spread.width / 2, spread.height)
        val k = 2f * COVER_HALF / half
        withTransform({
            translate(left, -COVER_HALF)
            scale(k, k, Offset.Zero)
        }) {
            drawImage(
                image = spread,
                srcOffset = IntOffset(spread.width / 2 - (1 - side) * half, (spread.height - half) / 2),
                srcSize = IntSize(half, half),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(half, half),
                filterQuality = FilterQuality.Medium,
            )
        }
    }

    /** Where the spine's ends and the page right of it are on screen, for [bookSide]. */
    private fun rememberSpine() {
        val b = book ?: return
        val at = if (b.from < 0 && b.to < 0) -COVER_HALF else lerp(b.spineAt(b.from), b.spineAt(b.to), b.turn)
        var ok = true
        loose.apply(at, -COVER_HALF, 0f, v)
        ok = ok && camera.project(v[0], v[1], v[2], spineHull, 0)
        loose.apply(at, COVER_HALF, 0f, v)
        ok = ok && camera.project(v[0], v[1], v[2], spineHull, 2)
        loose.apply(at + COVER_HALF, 0f, 0f, v)
        ok = ok && camera.project(v[0], v[1], v[2], spineHull, 4)
        if (!ok) spineHull.fill(Float.NaN)
    }

    // --- disc ----------------------------------------------------------------------

    private fun DrawScope.drawDisc(front: Boolean) {
        disc.plane(camera, DISC_Z, matrix, plane)
        if (sparking) {
            withTransform({ transform(matrix) }) { drawCircle(Color.Black, radius = DISC_R, center = Offset.Zero) }
            return
        }
        val glass = shader
        val square = square
        if (glass != null && square != null) {
            disc.eye(camera.distance, eye)
            eye[2] -= DISC_Z
            disc.toWorld(toWorld)
            withTransform({ transform(matrix) }) {
                drawCircle(glass.disc(square, eye, toWorld), radius = DISC_R, center = Offset.Zero)
            }
            return
        }
        val baked = discArt ?: return
        val mmPerPx = 1f / ppm
        fun DrawScope.face(image: ImageBitmap) {
            withTransform({
                translate(-DISC_R, -DISC_R)
                scale(mmPerPx, mmPerPx, Offset.Zero)
            }) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(image.width, image.height),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
        withTransform({ transform(matrix) }) {
            face(if (front) baked.front else baked.back)
            rainbow(front)
            if (front) face(baked.print)
        }
    }

    /**
     * The rainbow the tracks throw, after the reference's grating: for each
     * studio light it is brightest along the radius that lies across the half
     * vector between the light and the eye, both ways from the middle, and
     * its colour runs with how far that vector leans along the radius. Worked
     * out at the disc's middle and laid down as one sweep — it stays with the
     * light while the disc turns under it.
     */
    private fun DrawScope.rainbow(front: Boolean) {
        disc.eye(camera.distance, eye)
        disc.toWorld(toWorld)
        val ex = eye[0]
        val ey = eye[1]
        val ez = eye[2] - DISC_Z
        val el = sqrt(ex * ex + ey * ey + ez * ez)
        val stops = Array(RAINBOW_STOPS + 1) { 0f to Color.Transparent }
        val sum = FloatArray(3)
        val one = FloatArray(3)
        val gain = if (front) 0.85f else 1.3f
        val hx = FloatArray(LIGHTS.size)
        val hy = FloatArray(LIGHTS.size)
        for ((i, light) in LIGHTS.withIndex()) {
            // The light into the disc's own axes: the transpose of its frame.
            val lx = toWorld[0] * light.x + toWorld[1] * light.y + toWorld[2] * light.z
            val ly = toWorld[3] * light.x + toWorld[4] * light.y + toWorld[5] * light.z
            hx[i] = lx + ex / el
            hy[i] = ly + ey / el
        }
        for (s in 0..RAINBOW_STOPS) {
            val phi = s.toFloat() / RAINBOW_STOPS * 2f * kotlin.math.PI.toFloat()
            val c = cos(phi)
            val sn = sin(phi)
            sum.fill(0f)
            for ((i, light) in LIGHTS.withIndex()) {
                val along = -hx[i] * sn + hy[i] * c
                val u = abs(hx[i] * c + hy[i] * sn)
                val w = exp(-along * along / (light.spread * light.spread))
                if (w < 1e-3f) continue
                grating(u, one)
                for (k in 0 until 3) sum[k] += one[k] * w * light.weight * gain
            }
            stops[s] = s.toFloat() / RAINBOW_STOPS to Color(srgb(sum[0]), srgb(sum[1]), srgb(sum[2]))
        }
        drawPath(
            ring(58.8f, 21.5f),
            Brush.sweepGradient(*stops, center = Offset.Zero),
            blendMode = BlendMode.Plus,
        )
    }

    // --- lid glare -------------------------------------------------------------------

    /**
     * The studio's softboxes, thrown back by the flat of the lid. Across a
     * flat sheet the reflected direction barely changes, so where it meets a
     * softbox is a straight band, and where that band lies follows from the
     * reflection at the middle and how it changes across the lid.
     */
    private fun DrawScope.glare(frame: Pose) {
        val p0 = reflected(frame, 0f, 0f) ?: return
        val pu = reflected(frame, 40f, 0f) ?: return
        val pv = reflected(frame, 0f, 40f) ?: return
        val du = Offset((pu.x - p0.x) / 40f, (pu.y - p0.y) / 40f)
        val dv = Offset((pv.x - p0.x) / 40f, (pv.y - p0.y) / 40f)
        frame.plane(camera, LID_FRONT, matrix, plane)
        withTransform({ transform(matrix) }) {
            for (box in SOFTBOXES) {
                // The band runs along the box's long side, so it is the
                // across coordinate that places it.
                val vertical = box.hw < box.hh
                val a = (if (vertical) p0.x else p0.y) - (if (vertical) box.x else box.y)
                val gx = if (vertical) du.x else du.y
                val gy = if (vertical) dv.x else dv.y
                val g2 = gx * gx + gy * gy
                if (g2 < 1e-12f) continue
                val reach = (if (vertical) box.hw else box.hh) + box.soft
                fun at(s: Float) = Offset((s - a) * gx / g2, (s - a) * gy / g2)
                val start = at(-reach)
                val end = at(reach)
                val edge = (box.soft / reach / 2f).coerceIn(0.01f, 0.49f)
                val white = Color.White.copy(alpha = box.alpha)
                drawPath(
                    roundedSquare(HALF_MM, CORNER_MM),
                    Brush.linearGradient(
                        0f to Color.Transparent,
                        edge to white,
                        1f - edge to white,
                        1f to Color.Transparent,
                        start = start,
                        end = end,
                    ),
                )
            }
        }
    }

    /** Where the view reflected off the lid at local [x], [y] points, as the studio's flat p; null facing the wall. */
    private fun reflected(frame: Pose, x: Float, y: Float): Offset? {
        frame.apply(x, y, LID_FRONT, v)
        var vx = -v[0]
        var vy = -v[1]
        var vz = camera.distance - v[2]
        val l = sqrt(vx * vx + vy * vy + vz * vz)
        vx /= l; vy /= l; vz /= l
        frame.turn(0f, 0f, 1f, n)
        var d = n[0] * vx + n[1] * vy + n[2] * vz
        if (d < 0f) {
            n[0] = -n[0]; n[1] = -n[1]; n[2] = -n[2]
            d = -d
        }
        val rx = -vx + 2f * d * n[0]
        val ry = -vy + 2f * d * n[1]
        val rz = -vz + 2f * d * n[2]
        if (rz <= 0.05f) return null
        val k = 1f / max(rz, 0.08f)
        return Offset(rx * k, ry * k)
    }

    // --- walls, knuckles and shadows ---------------------------------------------------

    /**
     * The case's edge, a quad between each pair of points round its outline,
     * from depth [z0] to [z1]. Seen end on, clear plastic is dark at its two
     * rims and pale between; the walls facing away are seen through the case
     * and drawn fainter, first.
     */
    private fun DrawScope.walls(frame: Pose, z0: Float, z1: Float, front: Boolean) {
        // Drawn in plain colours, and no spark is taken off them.
        if (sparking) return
        val o = wallOutline
        val count = o.size / 4
        for (i in 0 until count) {
            val j = (i + 1) % count
            val x0 = o[i * 4]; val y0 = o[i * 4 + 1]
            val x1 = o[j * 4]; val y1 = o[j * 4 + 1]
            if (x0 == x1 && y0 == y1) continue
            frame.turn(o[i * 4 + 2] + o[j * 4 + 2], o[i * 4 + 3] + o[j * 4 + 3], 0f, n)
            frame.apply((x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f, v)
            val toward = n[0] * -v[0] + n[1] * -v[1] + n[2] * (camera.distance - v[2]) > 0f
            if (toward != front) continue
            frame.apply(x0, y0, z0, world, 0)
            frame.apply(x1, y1, z0, world, 3)
            frame.apply(x1, y1, z1, world, 6)
            frame.apply(x0, y0, z1, world, 9)
            var ok = true
            for (k in 0 until 4) {
                ok = ok && camera.project(world[k * 3], world[k * 3 + 1], world[k * 3 + 2], screen, k * 2)
            }
            if (!ok) continue
            path.reset()
            path.moveTo(screen[0], screen[1])
            path.lineTo(screen[2], screen[3])
            path.lineTo(screen[4], screen[5])
            path.lineTo(screen[6], screen[7])
            path.close()
            val alpha = if (front) 1f else 0.45f
            drawPath(
                path,
                Brush.linearGradient(
                    0f to WALL_DARK.copy(alpha = 0.7f * alpha),
                    0.18f to WALL_MID.copy(alpha = 0.55f * alpha),
                    0.5f to WALL_PALE.copy(alpha = 0.4f * alpha),
                    0.82f to WALL_MID.copy(alpha = 0.55f * alpha),
                    1f to WALL_DARK.copy(alpha = 0.7f * alpha),
                    start = Offset((screen[0] + screen[2]) / 2f, (screen[1] + screen[3]) / 2f),
                    end = Offset((screen[4] + screen[6]) / 2f, (screen[5] + screen[7]) / 2f),
                ),
            )
        }
    }

    /**
     * The hinge's knuckles: round pins, so from any side they show the same
     * band, dark at its two edges, pale down the middle, lit along one side.
     */
    private fun DrawScope.knuckles(frame: Pose, knuckles: List<Knuckle>) {
        if (sparking) return
        for (k in knuckles) {
            frame.apply(HINGE_X, k.from, HINGE_Z, world, 0)
            frame.apply(HINGE_X, k.to, HINGE_Z, world, 3)
            if (!camera.project(world[0], world[1], world[2], screen, 0)) continue
            if (!camera.project(world[3], world[4], world[5], screen, 2)) continue
            val dx = screen[2] - screen[0]
            val dy = screen[3] - screen[1]
            val l = sqrt(dx * dx + dy * dy)
            if (l < 0.5f) continue
            val r = k.radius * frame.s * camera.scaleAt((world[2] + world[5]) / 2f)
            val nx = -dy / l * r
            val ny = dx / l * r
            val a = Offset(screen[0], screen[1])
            val b = Offset(screen[2], screen[3])
            drawLine(
                Brush.linearGradient(
                    0f to WALL_DARK,
                    0.2f to WALL_MID,
                    0.36f to Color.White,
                    0.55f to WALL_PALE,
                    0.85f to WALL_MID,
                    1f to WALL_DARK,
                    start = Offset(a.x - nx, a.y - ny),
                    end = Offset(a.x + nx, a.y + ny),
                ),
                start = a,
                end = b,
                strokeWidth = 2f * r,
                cap = StrokeCap.Butt,
            )
        }
    }

    private fun DrawScope.shadow(frame: Pose, z: Float, map: ImageBitmap, strength: Float, widen: Float = 1f) {
        frame.shadowPlane(camera, z, BACKDROP_Z, matrix, plane)
        withTransform({
            transform(matrix)
            scale(widen, 1f, Offset.Zero)
            translate(SHADOW_LEFT, SHADOW_LEFT)
            scale(SHADOW_MM_PER_PX, SHADOW_MM_PER_PX, Offset.Zero)
        }) {
            drawImage(
                image = map,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(map.width, map.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(map.width, map.height),
                alpha = strength.coerceAtMost(1f),
                filterQuality = FilterQuality.Low,
            )
        }
    }

    // --- hits --------------------------------------------------------------------------

    /** The tray's, the lid's and a loose cover's outlines on screen as they were last drawn, four corners each. */
    private fun rememberHull() {
        var at = 0
        for (frame in arrayOf(tray, lid, loose)) {
            val half = if (frame === loose) COVER_HALF else HALF_MM
            val across = if (frame === loose) half * footprint else half
            for ((cx, cy) in CORNERS) {
                val seen = frame !== loose || coverLoose
                frame.apply(cx * across, cy * half, 0f, v)
                if (!seen || !camera.project(v[0], v[1], v[2], hull, at)) {
                    hull[at] = Float.NaN
                    hull[at + 1] = Float.NaN
                }
                at += 2
            }
        }
    }

    fun hit(x: Float, y: Float): Boolean = inside(x, y, 0) || inside(x, y, 8) || inside(x, y, 16)

    /**
     * Which side of the manual's spine [x], [y] falls on as it was last
     * drawn, -1 left or 1 right; 0 off it, or with no manual out.
     */
    fun bookSide(x: Float, y: Float): Int {
        if (book == null || spineHull[0].isNaN() || !inside(x, y, 16)) return 0
        val ax = spineHull[0]
        val ay = spineHull[1]
        val dx = spineHull[2] - ax
        val dy = spineHull[3] - ay
        val at = dx * (y - ay) - dy * (x - ax)
        val right = dx * (spineHull[5] - ay) - dy * (spineHull[4] - ax)
        return if (at * right > 0f) 1 else -1
    }

    private fun inside(x: Float, y: Float, at: Int): Boolean {
        var sign = 0f
        for (k in 0 until 4) {
            val ax = hull[at + k * 2]
            val ay = hull[at + k * 2 + 1]
            val bx = hull[at + (k + 1) % 4 * 2]
            val by = hull[at + (k + 1) % 4 * 2 + 1]
            if (ax.isNaN() || bx.isNaN()) return false
            val cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            if (cross == 0f) continue
            if (sign == 0f) sign = cross else if (sign * cross < 0f) return false
        }
        return true
    }
}

private val sharedShader: CaseShader? by lazy { caseShader() }

/** The tray's layers in depth order from the back, the disc slotted in after the floor. */
private const val DISC = -1
private val TRAY_ORDER = listOf(0, DISC, 1, 2, 3)

private val CORNERS = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)

// Kept off black: a darker rim drew a hard outline round the whole case.
private val WALL_DARK = Color(0xFF959CA4)
private val WALL_MID = Color(0xFFC2C7CD)
private val WALL_PALE = Color(0xFFF3F5F7)

/** The back of the cover, seen through the lid from behind. */
private val PAPER = Color(0xFFF2EFE9)

/*
 * The sketchbook's riffle blurs from a quarter of its top speed, and three
 * times as much from 0.6 on, the widest a gaussian 14px across a spread
 * 860px wide: a blur radius of 0.054 of half a spread.
 */
private const val RIFFLE_BLUR_FROM = 0.25f
private const val RIFFLE_BLUR_FULL = 0.6f
private const val RIFFLE_BLUR = 0.054f

/** How wide the manual stands at [at], in covers: open, a spread is two. */
private fun BookPose.across(at: Int): Float = if (at in 0 until spreads) 2f else 1f

/**
 * Where the spine stands at [at], in millimetres across the loose cover:
 * shut on its front, down the cover's left edge; open, in the middle; shut
 * on its back, down its right edge.
 */
private fun BookPose.spineAt(at: Int): Float = when {
    at < 0 -> -COVER_HALF
    at >= spreads -> COVER_HALF
    else -> 0f
}

/** A studio light the disc's tracks split: its direction, strength and how tight its streak is. */
private class StudioLight(x: Float, y: Float, z: Float, val weight: Float, val spread: Float) {
    private val d = normalized(x, y, z)
    val x get() = d[0]
    val y get() = d[1]
    val z get() = d[2]
}

private val LIGHTS = listOf(
    StudioLight(-0.625f, 0.218f, 0.75f, 1.6f, 0.05f),
    StudioLight(0.725f, 0.028f, 0.688f, 1.4f, 0.045f),
    StudioLight(0.031f, 0.786f, 0.618f, 0.9f, 0.06f),
    StudioLight(0.069f, -0.722f, 0.688f, 0.35f, 0.16f),
)

private const val RAINBOW_STOPS = 240

/**
 * The reference's softboxes, in the flat p it maps the room's front onto,
 * and how bright each reads off the lid: its glare, at the lid's reflectance
 * head on, comes to the 0.1 the reference caps it at.
 */
private class Softbox(val x: Float, val y: Float, val hw: Float, val hh: Float, val soft: Float, val alpha: Float)

private val SOFTBOXES = listOf(
    Softbox(-0.86f, 0.3f, 0.065f, 0.85f, 0.035f, 0.34f),
    Softbox(1.06f, 0.04f, 0.06f, 1.15f, 0.03f, 0.34f),
    Softbox(0.05f, 1.28f, 1.25f, 0.055f, 0.03f, 0.3f),
)

/**
 * The colour a grating throws at lean [u], into [out]: the first three orders
 * of the spectrum, as the reference sums them.
 */
private fun grating(u: Float, out: FloatArray) {
    out.fill(0f)
    val tmp = FloatArray(3)
    for ((order, weight) in arrayOf(1600f to 1f, 800f to 0.75f, 533.3f to 0.45f)) {
        spectral(order * u, tmp)
        for (k in 0 until 3) out[k] += tmp[k] * weight
    }
}

/** A wavelength in nanometres as linear RGB, the reference's fit to the spectrum. */
private fun spectral(w: Float, out: FloatArray) {
    if (w < 380f || w > 760f) {
        out.fill(0f)
        return
    }
    val x = ((w - 400f) / 300f).clamped(0f, 1f)
    for (k in 0 until 3) {
        val a = C1[k] * (x - X1[k])
        val b = C2[k] * (x - X2[k])
        out[k] = (1f - a * a - Y1[k]).clamped(0f, 1f) + (1f - b * b - Y2[k]).clamped(0f, 1f)
    }
}

private val C1 = floatArrayOf(3.54585104f, 2.93225262f, 2.41593945f)
private val X1 = floatArrayOf(0.69549072f, 0.49228336f, 0.27699880f)
private val Y1 = floatArrayOf(0.02312639f, 0.15225084f, 0.52607955f)
private val C2 = floatArrayOf(3.90307140f, 3.21182957f, 3.96587128f)
private val X2 = floatArrayOf(0.11748627f, 0.86755042f, 0.66077860f)
private val Y2 = floatArrayOf(0.84897130f, 0.88445281f, 0.73949448f)

/** Linear light to sRGB, clamped. */
private fun srgb(c: Float): Float {
    val x = c.clamped(0f, 1f)
    return if (x <= 0.0031308f) x * 12.92f else 1.055f * x.pow(1f / 2.4f) - 0.055f
}
