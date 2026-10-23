package com.umutk.pulse.extras.launcher

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** The launcher's sound effects, synthesized (no sound files): a start-up chime in the look's own mood and a short click. */
object LauncherSounds {
    private const val RATE = 22050

    /** Start-up chime: a rising chord, softer and longer for the newer looks, a plain beep for Windows 95. */
    fun startup(lookId: String) {
        if (!LauncherPrefs.sounds) return
        val notes = when (lookId) {
            "win95" -> listOf(523.3, 659.3, 784.0)
            "xp" -> listOf(392.0, 587.3, 784.0, 987.8)
            "win7" -> listOf(349.2, 440.0, 523.3, 698.5)
            "win10", "win10black" -> listOf(293.7, 440.0, 587.3, 740.0)
            else -> listOf(261.6, 392.0, 523.3, 659.3)
        }
        play(chord(notes, if (lookId == "win95") 1.0 else 2.2))
    }

    /** A short tick when something opens. */
    fun click() {
        if (!LauncherPrefs.sounds) return
        play(chord(listOf(1200.0), .06, 0.0))
    }

    // notes start one after another (stagger) and fade out together
    private fun chord(freqs: List<Double>, seconds: Double, stagger: Double = .12): ShortArray {
        val n = (RATE * seconds).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            var v = 0.0
            freqs.forEachIndexed { k, f ->
                val on = t - k * stagger
                if (on >= 0) v += sin(2 * PI * f * on) * exp(-on * (3.0 / seconds * 1.5)) / freqs.size
            }
            out[i] = (v * 0.5 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun play(pcm: ShortArray) {
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { t.release() }
                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        }
    }
}
