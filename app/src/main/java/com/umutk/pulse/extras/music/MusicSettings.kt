package com.umutk.pulse.extras.music

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** All music player options in one prefs file. Players read them through the engine ([MusicPlayerEngine.reloadSettings]). */
object MusicSettings {
    private fun sp(c: Context) = c.getSharedPreferences("music_settings", Context.MODE_PRIVATE)

    /** Look up album art on the internet for tracks that have none (on by default). */
    fun onlineCovers(c: Context) = sp(c).getBoolean("online_covers", false)
    fun setOnlineCovers(c: Context, on: Boolean) = sp(c).edit().putBoolean("online_covers", on).apply()
    /** Fills the artist, album, genre and year a song is missing from the internet (OnlineTags). */
    fun onlineTags(c: Context) = sp(c).getBoolean("online_tags", true)
    fun setOnlineTags(c: Context, on: Boolean) = sp(c).edit().putBoolean("online_tags", on).apply()

    /** Fade length at each track change in seconds, 0 = off. */
    fun crossfade(c: Context) = sp(c).getInt("xfade", 0)
    fun setCrossfade(c: Context, s: Int) = sp(c).edit().putInt("xfade", s.coerceIn(0, 12)).apply()

    /** 0 off, 1 track gain, 2 album gain. */
    fun replayGain(c: Context) = sp(c).getInt("rg", 0)
    fun setReplayGain(c: Context, m: Int) = sp(c).edit().putInt("rg", m).apply()

    /** Gain for files without ReplayGain tags, dB. */
    fun rgFallback(c: Context) = sp(c).getInt("rgfb", -6)
    fun setRgFallback(c: Context, db: Int) = sp(c).edit().putInt("rgfb", db.coerceIn(-15, 0)).apply()

    /** Pre-amp in dB, -12..12. */
    fun preamp(c: Context) = sp(c).getInt("preamp", 0)
    fun setPreamp(c: Context, db: Int) = sp(c).edit().putInt("preamp", db.coerceIn(-12, 12)).apply()

    fun skipSilence(c: Context) = sp(c).getBoolean("skipSil", false)
    fun setSkipSilence(c: Context, on: Boolean) = sp(c).edit().putBoolean("skipSil", on).apply()

    /** Playback speed 0.5..2.0 and whether pitch follows. */
    fun speed(c: Context) = sp(c).getFloat("speed", 1f)
    fun setSpeed(c: Context, v: Float) = sp(c).edit().putFloat("speed", v.coerceIn(.5f, 2f)).apply()
    fun pitchFollows(c: Context) = sp(c).getBoolean("pitchFollow", false)
    fun setPitchFollows(c: Context, on: Boolean) = sp(c).edit().putBoolean("pitchFollow", on).apply()

    /** Shortest track kept in the library, seconds. */
    fun minSeconds(c: Context) = sp(c).getInt("minSec", 10)
    fun setMinSeconds(c: Context, s: Int) = sp(c).edit().putInt("minSec", s.coerceIn(0, 120)).apply()

    fun showHidden(c: Context) = sp(c).getBoolean("hidden", false)
    fun setShowHidden(c: Context, on: Boolean) = sp(c).edit().putBoolean("hidden", on).apply()

    /** Folders left out of the library (path prefixes). */
    fun excluded(c: Context): Set<String> = sp(c).getStringSet("excl", emptySet()) ?: emptySet()
    fun setExcluded(c: Context, s: Set<String>) = sp(c).edit().putStringSet("excl", s).apply()

    /** Cover in the notification and on the lock screen. */
    fun notificationArt(c: Context) = sp(c).getBoolean("notifArt", true)
    fun setNotificationArt(c: Context, on: Boolean) = sp(c).edit().putBoolean("notifArt", on).apply()

    /** Notification buttons in display order (ids from notif.NotifBtn); Android shows at most 5. */
    fun notificationButtonIds(c: Context): List<String> =
        sp(c).getString("notifBtn", null)?.split(',')?.filter { it.isNotBlank() } ?: com.umutk.pulse.extras.music.notif.NotifBtn.DEFAULT.map { it.id }
    fun setNotificationButtonIds(c: Context, ids: List<String>) = sp(c).edit().putString("notifBtn", ids.joinToString(",")).apply()

    /** Pause when headphones are unplugged. */
    fun pauseOnUnplug(c: Context) = sp(c).getBoolean("unplug", true)
    fun setPauseOnUnplug(c: Context, on: Boolean) = sp(c).edit().putBoolean("unplug", on).apply()

    fun resumeWired(c: Context) = sp(c).getBoolean("resumeWired", false)
    fun setResumeWired(c: Context, on: Boolean) = sp(c).edit().putBoolean("resumeWired", on).apply()

    fun pauseUsbDac(c: Context) = sp(c).getBoolean("pauseUsb", false)
    /** The song stops when the media volume is turned down to zero. */
    fun pauseAtZeroVolume(c: Context) = sp(c).getBoolean("pauseZero", false)
    fun setPauseAtZeroVolume(c: Context, v: Boolean) = sp(c).edit().putBoolean("pauseZero", v).apply()
    fun setPauseUsbDac(c: Context, on: Boolean) = sp(c).edit().putBoolean("pauseUsb", on).apply()

    /** The equalizer preset follows the genre of the song that plays. */
    fun autoEq(c: Context) = sp(c).getBoolean("autoEq", false)
    fun setAutoEq(c: Context, v: Boolean) = sp(c).edit().putBoolean("autoEq", v).apply()

    fun respondButtons(c: Context) = sp(c).getBoolean("buttons", true)
    fun setRespondButtons(c: Context, on: Boolean) = sp(c).edit().putBoolean("buttons", on).apply()

    fun audioFocus(c: Context) = sp(c).getBoolean("focus", true)
    fun setAudioFocus(c: Context, on: Boolean) = sp(c).edit().putBoolean("focus", on).apply()

    fun countStats(c: Context) = sp(c).getBoolean("stats", true)
    fun setCountStats(c: Context, on: Boolean) = sp(c).edit().putBoolean("stats", on).apply()

    /** Resume where playback stopped when the player opens. */
    fun resume(c: Context) = sp(c).getBoolean("resume", true)
    fun setResume(c: Context, on: Boolean) = sp(c).edit().putBoolean("resume", on).apply()

    /** The shuffle follows mood, energy and ratings (on) or is plain chance (off). */
    fun smartShuffle(c: Context) = sp(c).getBoolean("smartShuffle", true)
    fun setSmartShuffle(c: Context, on: Boolean) = sp(c).edit().putBoolean("smartShuffle", on).apply()
    /** 0 = follows the taste closely, 100 = nearly plain chance. */
    fun shuffleRandomness(c: Context) = sp(c).getInt("shuffleRandom", 35)
    fun setShuffleRandomness(c: Context, v: Int) = sp(c).edit().putInt("shuffleRandom", v.coerceIn(0, 100)).apply()

    fun keepScreenOn(c: Context) = sp(c).getBoolean("screenOn", false)
    fun setKeepScreenOn(c: Context, on: Boolean) = sp(c).edit().putBoolean("screenOn", on).apply()

    /** Poweramp: art as a full-width square. */
    /** Lyrics over the whole player page (cover, title and artist across the top) instead of only over the cover square. */
    fun lyricsFull(c: Context) = sp(c).getBoolean("lyricsFull", false)
    fun setLyricsFull(c: Context, on: Boolean) = sp(c).edit().putBoolean("lyricsFull", on).apply()
    fun lyricsFont(c: Context) = sp(c).getInt("lyricsFont", 18)
    fun setLyricsFont(c: Context, v: Int) = sp(c).edit().putInt("lyricsFont", v.coerceIn(12, 30)).apply()
}

/**
 * One gain stage in the audio pipeline: pre-amp, ReplayGain, track fade and sleep fade multiplied together.
 * PCM 16-bit and float are handled; the result is clipped, never wrapped.
 */
object AudioGain {
    @Volatile var gain = 1f
    @Volatile var fade = 1f
    @Volatile var sleepFade = 1f
    val processor = Proc()

    class Proc : BaseAudioProcessor() {
        @Throws(UnhandledAudioFormatException::class)
        override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
            if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) throw UnhandledAudioFormatException(inputAudioFormat)
            return inputAudioFormat
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val size = inputBuffer.remaining()
            if (size == 0) return
            val out = replaceOutputBuffer(size)
            val g = gain * fade * sleepFade
            val saved = inputBuffer.order()
            inputBuffer.order(ByteOrder.nativeOrder())
            if (g > .999f && g < 1.001f) out.put(inputBuffer)
            else if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
                while (inputBuffer.remaining() >= 4) out.putFloat((inputBuffer.getFloat() * g).coerceIn(-1f, 1f))
            } else {
                while (inputBuffer.remaining() >= 2) out.putShort((inputBuffer.getShort() * g).toInt().coerceIn(-32768, 32767).toShort())
            }
            inputBuffer.order(saved)
            out.flip()
        }
    }
}
