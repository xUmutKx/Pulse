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
            // the ambient loops themselves are not inside the app (8 MB, and they were kept twice: in the app and copied here): each is fetched once, from this
            // project's own repository at a fixed commit, checked by its SHA-256, and kept here
            // (offline: the first failure ends the round, the rest is tried the next time the sounds are listed)
            var online = true
            if (name == "ambient") for ((f, sha) in AMBIENT_FILES) {
                val out = File(dir, f)
                if (!online || (out.isFile && out.length() > 0)) continue
                online = runCatching {
                    val tmp = File(dir, "$f.part")
                    val c = java.net.URL(AMBIENT_BASE + f).openConnection() as java.net.HttpURLConnection
                    c.connectTimeout = 10_000; c.readTimeout = 20_000
                    try { if (c.responseCode in 200..299) tmp.outputStream().use { o -> c.inputStream.use { it.copyTo(o) } } } finally { c.disconnect() }
                    val got = java.security.MessageDigest.getInstance("SHA-256").digest(tmp.readBytes()).joinToString("") { "%02x".format(it) }
                    if (got == sha) tmp.renameTo(out) else tmp.delete()
                }.isSuccess
            }
        }
        (name to dir).takeIf { File(dir, "pack.json").isFile }
    }
}

/** Where the ambient loops are fetched from: this project's repository at the commit they were last in the app (it never changes). */
private const val AMBIENT_BASE = "https://raw.githubusercontent.com/xUmutKx/Pulse/0804cb1f71e2ee08941e81e107120e3cc97383a5/app/src/main/assets/ambient/"

/** Each ambient loop and its SHA-256: a file that does not match is not kept. */
private val AMBIENT_FILES = listOf(
    "beach.ogg" to "963e74d27f35145497009ce61bd745c6fd76201fde76c49590a65906edf9b90f",
    "bell.ogg" to "c983f7dc0185de17392f03aa91c51cd8c2b5fea0a3e84031e8ffc743725483bb",
    "bowl.ogg" to "f8d5845777488ff2e081523556186e3c2cbe18be32e7c3fa603cdfce051a0004",
    "brown.ogg" to "fc056fef84f09276fd24caab09f20addce7e7a6891b680986064f71cfe4c2551",
    "cafe_jazz.ogg" to "b69eca158b4e5d3d78f6449bd6f213ee4dd827c92b6bde9f3ed9ea22a5b4108e",
    "coffee.ogg" to "a0fab0e573718b593bc71041b1c391cbdae200a85abe42bebb955f1ab2eb5879",
    "crickets.ogg" to "9c5f4ce9e4b1be7993e77b2533d57c144f8b0d07f5f754b7dee0ec74d19bc7d6",
    "damlama.ogg" to "615d19d4ebd4ecf56a34155c1ca3a9a2689adbfde183c8426385b410c06afbf0",
    "fire.ogg" to "55cbf035cc7ffe86d221d98e6863ded17c4adaedec9a7430b44a2ef7cdcf9271",
    "gok_gultusu.ogg" to "29e2c3146328e7d3b888cf9cd361690b1e10eab8fc0d122f50c3971fe943db77",
    "heartbeat.ogg" to "c41b6ab5df55cba1ad2f68e0131b1407db36f2d8ab0b488c819809b4f6760397",
    "rain.ogg" to "7c13b91128181122f0a15ddb936b2ffc70a6b6b33be62b0e6f260582ddd5a623",
    "rain_roof.ogg" to "380fc7343fced98f7d40c1c759de16a570786fcd540312be100ab1686eb742c2",
    "selale.ogg" to "b6ada51e5eba1c6bdc8857e873c8b1388cb9bd2e10f70d5afd3c304c68ec9b99",
    "steam.ogg" to "3a7c9b56effc21c6091b51b3b2c1e3984b7ac2353972e388a60b5215f3c98a26",
    "traffic.ogg" to "ac280190a2cc9d758917aa64bf54846667d74b74fb538fa9dd0a31722edc5afb",
    "tren.ogg" to "c38b72e99a501795f84ad40071447d6f132361525289c836bee0bc6d3308b51a",
    "vinyl.ogg" to "521c439f01a34232d267cb7b4ffd0384f5a1ec1c1bc2387b2a1ac588c1140966",
    "waves.ogg" to "eead1f6a0241920e62338f1226fbaacf944199416796c4eaae26e11faddd88d0",
    "wind.ogg" to "8fc0deef2d04fd2ffab3ad60ff6f370f832883bfabc6c9fc66444de0cb435433",
)
