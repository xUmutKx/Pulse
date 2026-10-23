package com.umutk.pulse.extras.buds

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the earbuds last told us. */
data class BudsState(
    val connected: Boolean = false, val name: String = "", val batteryL: Int = -1, val batteryR: Int = -1, val batteryCase: Int = -1,
    val wornL: Boolean = false, val wornR: Boolean = false, val noise: Int = -1, val canNoise: Boolean = false, val message: String = "",
)

/** The settings of the Buds manager. */
object BudsPrefs {
    private fun p(c: Context) = c.getSharedPreferences("buds", Context.MODE_PRIVATE)
    fun address(c: Context): String? = p(c).getString("address", null)
    fun setAddress(c: Context, a: String?) = p(c).edit().putString("address", a).apply()
    /** When both earbuds come out: stop the music. */
    fun pauseOnRemove(c: Context) = p(c).getBoolean("pauseOnRemove", true)
    fun setPauseOnRemove(c: Context, v: Boolean) = p(c).edit().putBoolean("pauseOnRemove", v).apply()
    /** ... and turn the noise control off. */
    fun noiseOffOnRemove(c: Context) = p(c).getBoolean("noiseOff", true)
    fun setNoiseOffOnRemove(c: Context, v: Boolean) = p(c).edit().putBoolean("noiseOff", v).apply()
    /** When one comes back: carry on with the music and the noise control as they were. */
    fun resumeOnWear(c: Context) = p(c).getBoolean("resume", true)
    fun setResumeOnWear(c: Context, v: Boolean) = p(c).edit().putBoolean("resume", v).apply()
    /** Keep the connection in the background (a small notification). */
    fun keepOn(c: Context) = p(c).getBoolean("keepOn", false)
    fun setKeepOn(c: Context, v: Boolean) = p(c).edit().putBoolean("keepOn", v).apply()
}

/** The one link to the earbuds: its state for the screen and for the service. */
object BudsLink {
    private val st = MutableStateFlow(BudsState())
    val state: StateFlow<BudsState> = st.asStateFlow()
    fun set(s: BudsState) { st.value = s }
    fun update(f: (BudsState) -> BudsState) { st.value = f(st.value) }
    /** Set by the service: sends a message to the earbuds. */
    @Volatile var sender: ((ByteArray) -> Unit)? = null

    /** 0 off, 1 noise cancelling, 2 ambient sound, 3 adaptive. */
    fun setNoise(mode: Int) {
        sender?.invoke(BudsProto.encode(BudsProto.NOISE_CONTROLS, byteArrayOf(mode.toByte())))
        update { it.copy(noise = mode) }
    }
}
