package com.umutk.pulse.extras.music

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.media.audiofx.Equalizer as PlatformEqualizer

/**
 * Wraps the platform equalizer. Works with any audio session id and reapplies the saved band levels
 * when the session changes (ExoPlayer reports a new session id when its audio track is recreated).
 * Every call is safe on devices without EQ support: [isSupported] is false and the setters do nothing.
 */
class Equalizer {

    private var eq: PlatformEqualizer? = null
    private var savedLevels: ShortArray? = null
    private var savedEnabled = true

    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var bassStrength = 0
    private var virtStrength = 0
    var currentPreset = -1
        private set

    val bassSupported: Boolean get() = bass?.strengthSupported == true
    val virtualizerSupported: Boolean get() = virt?.strengthSupported == true

    /** Current band levels in millibels (empty when unsupported). */
    val bandLevels: List<Int>
        get() = eq?.let { e -> (0 until e.numberOfBands.toInt()).map { e.getBandLevel(it.toShort()).toInt() } }.orEmpty()

    val enabled: Boolean get() = savedEnabled

    /** 0..1000 */
    fun setBassBoost(strength: Int) {
        bassStrength = strength.coerceIn(0, 1000)
        runCatching { bass?.takeIf { it.strengthSupported }?.setStrength(bassStrength.toShort()) }
    }

    /** 0..1000 */
    fun setVirtualizer(strength: Int) {
        virtStrength = strength.coerceIn(0, 1000)
        runCatching { virt?.takeIf { it.strengthSupported }?.setStrength(virtStrength.toShort()) }
    }

    val bassBoost: Int get() = bassStrength
    val virtualizer: Int get() = virtStrength

    /** Saves/restores EQ state between runs. */
    fun save(c: Context) {
        c.getSharedPreferences("music_eq", Context.MODE_PRIVATE).edit()
            .putString("levels", savedLevels?.joinToString(",").orEmpty()).putBoolean("on", savedEnabled)
            .putInt("bass", bassStrength).putInt("virt", virtStrength).apply()
    }

    fun restore(c: Context) {
        val p = c.getSharedPreferences("music_eq", Context.MODE_PRIVATE)
        savedLevels = p.getString("levels", "")?.split(',')?.mapNotNull { it.toShortOrNull() }?.takeIf { it.isNotEmpty() }?.toShortArray()
        savedEnabled = p.getBoolean("on", true)
        bassStrength = p.getInt("bass", 0)
        virtStrength = p.getInt("virt", 0)
    }

    val isSupported: Boolean get() = eq != null

    /** Band centre frequencies in Hz, empty when unsupported. */
    val centerFrequenciesHz: List<Int>
        get() = eq?.let { e -> (0 until e.numberOfBands.toInt()).map { e.getCenterFreq(it.toShort()) / 1000 } }.orEmpty()

    /** Band level range in millibels, (min, max). (0, 0) when unsupported. */
    val levelRange: Pair<Int, Int>
        get() = eq?.bandLevelRange?.let { it[0].toInt() to it[1].toInt() } ?: (0 to 0)

    val presetNames: List<String>
        get() = eq?.let { e -> (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) } }.orEmpty()

    /** Binds to the audio session. Call again with the new id after the player is recreated. */
    fun attach(audioSessionId: Int) {
        release()
        eq = try {
            PlatformEqualizer(0, audioSessionId).also { e ->
                // Saved levels may come from a device with a different band count.
                savedLevels?.take(e.numberOfBands.toInt())?.forEachIndexed { band, level ->
                    e.setBandLevel(band.toShort(), level)
                }
                e.enabled = savedEnabled
            }
        } catch (e: RuntimeException) {
            // Unsupported device or invalid session: stay in the no-op state.
            null
        }
        bass = runCatching { BassBoost(0, audioSessionId).also { it.enabled = true } }.getOrNull()
        virt = runCatching { Virtualizer(0, audioSessionId).also { it.enabled = true } }.getOrNull()
        setBassBoost(bassStrength)
        setVirtualizer(virtStrength)
    }

    fun setEnabled(enabled: Boolean) {
        savedEnabled = enabled
        eq?.enabled = enabled
        runCatching { bass?.enabled = enabled; virt?.enabled = enabled }
    }

    fun setBandLevel(band: Int, levelMb: Int) {
        val e = eq ?: return
        if (band !in 0 until e.numberOfBands.toInt()) return
        val (min, max) = levelRange
        val clamped = levelMb.coerceIn(min, max).toShort()
        val levels = savedLevels ?: ShortArray(e.numberOfBands.toInt())
        levels[band] = clamped
        savedLevels = levels
        currentPreset = -1
        e.setBandLevel(band.toShort(), clamped)
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        if (index !in 0 until e.numberOfPresets.toInt()) return
        e.usePreset(index.toShort())
        currentPreset = index
        // Keep the saved levels in sync with what the preset set.
        savedLevels = ShortArray(e.numberOfBands.toInt()) { e.getBandLevel(it.toShort()) }
    }

    fun release() {
        eq?.release()
        eq = null
        bass?.release(); bass = null
        virt?.release(); virt = null
    }
}
