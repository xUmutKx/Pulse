package com.umutk.pulse.extras.palette.core.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.umutk.pulse.extras.palette.core.color.toLab
import com.umutk.pulse.extras.palette.core.palette.ColorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Curated, key-free photo suggestions for wallpaper/widget backgrounds.
 *
 * Not scraped from Pinterest: Pinterest has no keyless/no-auth photo endpoint,
 * and scraping its pages would violate its terms and break the moment its
 * markup changes. LoremFlickr is a real, currently-running, no-account no-key
 * service that serves actual Creative-Commons Flickr photos matching plain-word
 * tags over a single HTTPS GET — closest available match to "no API hassle".
 * Tags are picked per palette mood to lean toward the moody/pastel/floral
 * "aesthetic home screen" look. Trade-off: LoremFlickr overlays a small CC
 * attribution mark in a corner of each photo (that's the free/keyless price).
 */
object SamplePhotos {

    private enum class Mood(val tagSets: List<List<String>>) {
        LAVENDER(listOf(listOf("lavender", "flowers"), listOf("purple", "aesthetic"), listOf("violet", "moody"), listOf("wisteria", "dreamy"))),
        DUSTY_PINK(listOf(listOf("sakura", "pink"), listOf("blossom", "aesthetic"), listOf("pink", "moody"), listOf("rose", "vintage"))),
        WARM(listOf(listOf("sunset", "aesthetic"), listOf("golden", "warm"), listOf("amber", "moody"), listOf("autumn", "dreamy"))),
        COOL(listOf(listOf("blue", "moody"), listOf("ocean", "aesthetic"), listOf("teal", "dreamy"), listOf("rain", "window"))),
        NIGHT(listOf(listOf("night", "moody"), listOf("dark", "aesthetic"), listOf("midnight", "fog"), listOf("stars", "dreamy"))),
        NATURE(listOf(listOf("forest", "moody"), listOf("moss", "nature"), listOf("green", "aesthetic"), listOf("fern", "misty"))),
        MONO(listOf(listOf("blackandwhite", "aesthetic"), listOf("minimal", "monochrome"), listOf("grey", "moody"), listOf("smoke", "abstract"))),
        SAKURA(listOf(listOf("cherryblossom", "spring"), listOf("sakura", "japan"), listOf("pink", "petals"), listOf("blossom", "dreamy"))),
        AUTUMN(listOf(listOf("autumn", "leaves"), listOf("fall", "cozy"), listOf("maple", "aesthetic"), listOf("orange", "forest"))),
        OCEAN(listOf(listOf("ocean", "waves"), listOf("beach", "aesthetic"), listOf("sea", "moody"), listOf("coast", "dreamy"))),
        URBAN(listOf(listOf("city", "aesthetic"), listOf("street", "moody"), listOf("architecture", "minimal"), listOf("neon", "night"))),
        DESERT(listOf(listOf("desert", "dunes"), listOf("sand", "warm"), listOf("cactus", "aesthetic"), listOf("canyon", "moody"))),
        MINIMAL_BEIGE(listOf(listOf("minimal", "beige"), listOf("cream", "aesthetic"), listOf("linen", "cozy"), listOf("neutral", "calm")))
    }

    data class Photo(val tags: List<String>, val lock: Int) {
        private val tagPath get() = tags.joinToString(",")
        fun thumbUrl() = "https://loremflickr.com/300/300/$tagPath?lock=$lock"
        fun fullUrl(w: Int = 1080, h: Int = 2340) = "https://loremflickr.com/$w/$h/$tagPath?lock=$lock"
    }

    /** Orders photos so the mood closest to [palette]'s vibe comes first. */
    fun suggestionsFor(palette: ColorPalette): List<Photo> {
        val accent = palette.accent.toLab()
        val hueDeg = (Math.toDegrees(accent.hue.toDouble()).toFloat() + 360f) % 360f
        val dark = palette.darkest.toLab().l < 0.28f
        val mono = accent.chroma < 0.04f

        val primary = when {
            mono -> Mood.MONO
            dark -> Mood.NIGHT
            hueDeg in 260f..300f -> Mood.LAVENDER
            hueDeg in 300f..345f -> Mood.DUSTY_PINK
            hueDeg in 150f..260f -> Mood.COOL
            hueDeg in 60f..150f -> Mood.NATURE
            else -> Mood.WARM
        }
        val rest = Mood.entries.filter { it != primary }
        return (listOf(primary) + rest).flatMap { mood ->
            mood.tagSets.mapIndexed { i, tags -> Photo(tags, lock = i + 1) }
        }
    }

    suspend fun fetchBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }
}
