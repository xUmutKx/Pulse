package com.umutk.pulse.extras.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.umutk.pulse.R

/** The launcher's sound effects: each Windows look plays its own start-up sound and its own menu click (res/raw/snd_*). */
object LauncherSounds {
    private var pool: SoundPool? = null
    private val clicks = HashMap<String, Int>()
    private var loadedFor = ""

    private fun key(lookId: String) = when (lookId) { "win95" -> "95"; "xp" -> "xp"; "win7" -> "7"; "win11" -> "11"; "win10", "win10black", "winphone", "winphoneamoled" -> "10"; else -> "" }
    private fun start(k: String) = when (k) { "95" -> R.raw.snd_start_95; "xp" -> R.raw.snd_start_xp; "7" -> R.raw.snd_start_7; "10" -> R.raw.snd_start_10; "11" -> R.raw.snd_start_11; else -> 0 }
    private fun click(k: String) = when (k) { "95" -> R.raw.snd_click_95; "xp" -> R.raw.snd_click_xp; "7" -> R.raw.snd_click_7; "10" -> R.raw.snd_click_10; "11" -> R.raw.snd_click_11; else -> 0 }
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    /** The look's start-up sound. */
    fun startup(c: Context, lookId: String) {
        if (!LauncherPrefs.sounds) return
        val res = start(key(lookId)).takeIf { it != 0 } ?: return
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                c.resources.openRawResourceFd(res).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setOnCompletionListener { it.release() }
                prepare(); start()
            }
        }
    }

    /** Loads the look's click so [click] plays at once. */
    fun prepare(c: Context, lookId: String) {
        if (!LauncherPrefs.sounds || loadedFor == lookId) return
        loadedFor = lookId
        val res = click(key(lookId)).takeIf { it != 0 } ?: return
        if (pool == null) pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build()
        clicks[lookId] = pool!!.load(c, res, 1)
    }

    /** The look's click when something opens. */
    fun click(lookId: String) {
        if (!LauncherPrefs.sounds) return
        clicks[lookId]?.let { pool?.play(it, 1f, 1f, 1, 0, 1f) }
    }
}
