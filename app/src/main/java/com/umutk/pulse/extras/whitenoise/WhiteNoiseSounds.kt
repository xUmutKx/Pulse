package com.umutk.pulse.extras.whitenoise

import android.content.Context
import com.umutk.pulse.R
import com.umutk.pulse.extras.ambient.AmbientTrack
import java.io.File

/** One built-in loop: [res] is the raw WAV in res/raw. */
class WhiteNoiseSound(val id: String, val en: String, val tr: String, val res: Int)

/** Empty: every sound ships in the ogg packs (assets/ambient, assets/birds); the old raw WAV loops were poor and are gone. */
val WhiteNoiseSounds = emptyList<WhiteNoiseSound>()

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

/**
 * Unpacks the bundled packs (assets/ambient, assets/birds) to filesDir/extras/bundled/<name> because AmbientEngine plays files.
 * Re-copied after every app update (stamp = lastUpdateTime). Call on Dispatchers.IO. Returns the dirs that hold a pack.json.
 */
fun installBundledPacks(context: Context): List<Pair<String, File>> {
    val stamp = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString() }.getOrDefault("0")
    return listOf("ambient", "birds").mapNotNull { name ->
        val dir = File(context.filesDir, "extras/bundled/$name")
        val mark = File(dir, ".stamp")
        runCatching {
            if (!(mark.isFile && mark.readText() == stamp && File(dir, "pack.json").isFile)) {
                dir.mkdirs()
                for (f in context.assets.list(name).orEmpty()) {
                    val out = File(dir, f)
                    context.assets.open("$name/$f").use { i -> out.outputStream().use { i.copyTo(it) } }
                }
                mark.writeText(stamp)
            }
        }
        (name to dir).takeIf { File(dir, "pack.json").isFile }
    }
}
