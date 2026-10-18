package com.umutk.pulse.extras.whitenoise

import android.content.Context
import com.umutk.pulse.R
import com.umutk.pulse.extras.ambient.AmbientTrack
import java.io.File

/** One built-in loop: [res] is the raw WAV in res/raw. */
class WhiteNoiseSound(val id: String, val en: String, val tr: String, val res: Int)

val WhiteNoiseSounds = listOf(
    WhiteNoiseSound("white", "White noise", "Beyaz gürültü", R.raw.wn_white),
    WhiteNoiseSound("coffee", "Coffee", "Kahve", R.raw.wn_coffee),
    WhiteNoiseSound("bell", "Bell", "Çan", R.raw.wn_bell),
    WhiteNoiseSound("rain", "Rain", "Yağmur", R.raw.wn_rain),
    WhiteNoiseSound("fire", "Fire", "Ateş", R.raw.wn_fire),
    WhiteNoiseSound("wind", "Wind", "Rüzgar", R.raw.wn_wind),
    WhiteNoiseSound("waves", "Waves", "Dalga", R.raw.wn_waves),
)

/**
 * Copies the raw loops to filesDir/extras/whitenoise (AmbientEngine plays files, not resources) and returns one track per sound.
 * Call on Dispatchers.IO. A file that already exists is kept.
 * shortcut: an existing copy is never refreshed, so a regenerated WAV needs a new file name or a cleared app data.
 */
fun installWhiteNoiseSounds(context: Context): List<AmbientTrack> {
    val dir = File(context.filesDir, "extras/whitenoise").apply { mkdirs() }
    return WhiteNoiseSounds.map { s ->
        val f = File(dir, "wn_${s.id}.wav")
        if (!f.isFile) {
            val tmp = File(dir, f.name + ".tmp")
            context.resources.openRawResource(s.res).use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (!tmp.renameTo(f)) tmp.delete()
        }
        AmbientTrack(s.id, f.path)
    }
}
