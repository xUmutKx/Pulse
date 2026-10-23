package com.umutk.pulse.extras.music.notif

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import com.umutk.pulse.R
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.stats.MusicStats
import com.umutk.pulse.tr

/** Every button the media notification can show. [builtin] ones map to a player command, the rest to a session command. */
enum class NotifBtn(val id: String, val icon: Int, val en: String, val tur: String, val builtin: Boolean = false) {
    PREV("prev", R.drawable.ic_notif_prev, "Previous", "Önceki", true),
    PLAY("play", R.drawable.ic_notif_play, "Play / pause", "Çal / duraklat", true),
    NEXT("next", R.drawable.ic_notif_next, "Next", "Sonraki", true),
    SHUFFLE("shuffle", R.drawable.ic_notif_shuffle, "Shuffle", "Karıştır"),
    REPEAT("repeat", R.drawable.ic_notif_repeat, "Repeat", "Tekrar"),
    FAV("fav", R.drawable.ic_notif_heart, "Favourite", "Favori"),
    CLOSE("close", R.drawable.ic_notif_close, "Close", "Kapat"),
    OUTPUT("output", R.drawable.ic_notif_speaker, "Audio output", "Ses çıkışı"),
    SLEEP("sleep", R.drawable.ic_notif_sleep, "Sleep timer", "Uyku zamanlayıcı"),
    SPEED("speed", R.drawable.ic_notif_speed, "Speed", "Hız"),
    LYRICS("lyrics", R.drawable.ic_notif_lyrics, "Lyrics", "Sözler"),
    NOISE("noise", R.drawable.ic_notif_noise, "Noise mixer", "Gürültü karıştırıcı"),
    REW("rew", R.drawable.ic_notif_rew, "Back 10 s", "10 sn geri"),
    FWD("fwd", R.drawable.ic_notif_fwd, "Forward 30 s", "30 sn ileri"),
    EQ("eq", R.drawable.ic_notif_eq, "Equalizer", "Ekolayzır");

    val label get() = tr(en, tur)

    companion object {
        /** Android's media notification has room for five actions. */
        const val MAX = 5
        val DEFAULT = listOf(PREV, PLAY, NEXT, SHUFFLE, REPEAT, CLOSE)
        fun of(id: String) = values().firstOrNull { it.id == id }
        fun action(b: NotifBtn) = "pulse.notif." + b.id
    }
}

/** Builds the buttons from the current player state. */
object NotifLayout {
    private val SPEEDS = floatArrayOf(1f, 1.25f, 1.5f, 2f, .75f)

    fun custom(ctx: Context, b: NotifBtn, p: Player): CommandButton {
        val e = MusicPlayerEngine.get(ctx)
        val (icon, name) = when (b) {
            NotifBtn.SHUFFLE -> (if (p.shuffleModeEnabled) R.drawable.ic_notif_shuffle else R.drawable.ic_notif_shuffle_off) to b.label
            NotifBtn.REPEAT -> when (p.repeatMode) {
                Player.REPEAT_MODE_ALL -> R.drawable.ic_notif_repeat
                Player.REPEAT_MODE_ONE -> R.drawable.ic_notif_repeat_one
                else -> R.drawable.ic_notif_repeat_off
            } to b.label
            NotifBtn.FAV -> (if (p.currentMediaItem?.mediaId?.let { MusicStats.isFavourite(ctx, it) } == true) R.drawable.ic_notif_heart_on else R.drawable.ic_notif_heart) to b.label
            NotifBtn.OUTPUT -> NotifOutput.icon(ctx) to b.label
            NotifBtn.SLEEP -> b.icon to (if (e.sleepLeftMs.value != 0L) tr("Sleep timer on", "Uyku zamanlayıcı açık") else b.label)
            NotifBtn.SPEED -> b.icon to "${"%.2f".format(p.playbackParameters.speed)}x"
            else -> b.icon to b.label
        }
        return CommandButton.Builder().setSessionCommand(SessionCommand(NotifBtn.action(b), Bundle.EMPTY))
            .setIconResId(icon).setDisplayName(name).build()
    }

    /** All enabled non-built-in buttons: this is the session's custom layout. */
    fun customLayout(ctx: Context, ids: List<String>, p: Player): ImmutableList<CommandButton> =
        ImmutableList.copyOf(ids.mapNotNull { NotifBtn.of(it) }.filter { !it.builtin }.map { custom(ctx, it, p) })

    fun builtin(b: NotifBtn, showPause: Boolean, compact: Int): CommandButton {
        val bb = CommandButton.Builder()
        when (b) {
            NotifBtn.PREV -> bb.setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS).setIconResId(b.icon)
            NotifBtn.NEXT -> bb.setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT).setIconResId(b.icon)
            else -> bb.setPlayerCommand(Player.COMMAND_PLAY_PAUSE).setIconResId(if (showPause) R.drawable.ic_notif_pause else R.drawable.ic_notif_play)
        }
        if (compact >= 0) bb.setExtras(Bundle().apply { putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, compact) })
        return bb.setDisplayName(b.label).build()
    }

    /** Next playback speed in the cycle. */
    fun nextSpeed(cur: Float): Float {
        val i = SPEEDS.indexOfFirst { Math.abs(it - cur) < .01f }
        return SPEEDS[(i + 1) % SPEEDS.size]
    }
}

/** Speaker / wired headset / Bluetooth switch through ExoPlayer's preferred audio device (API 23+, no call-routing side effects). */
object NotifOutput {
    private const val SPEAKER = 0
    private const val HEADSET = 1
    private const val BT = 2
    private var chosen: AudioDeviceInfo? = null

    private fun kind(d: AudioDeviceInfo) = if (android.os.Build.VERSION.SDK_INT < 23) -1 else when (d.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET -> HEADSET
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> BT
        else -> -1
    }

    private fun available(c: Context): List<AudioDeviceInfo> =
        if (android.os.Build.VERSION.SDK_INT < 23) emptyList() else (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { kind(it) >= 0 }.distinctBy { kind(it) }

    /** The user's pick while still connected, otherwise the system's choice: wired, then Bluetooth, then speaker. */
    private fun current(c: Context): Int {
        val av = available(c).map { kind(it) }
        chosen?.let { ch -> if (available(c).any { it.id == ch.id }) return kind(ch) }
        return when { HEADSET in av -> HEADSET; BT in av -> BT; else -> SPEAKER }
    }

    fun icon(c: Context) = when (current(c)) {
        HEADSET -> R.drawable.ic_notif_headset
        BT -> R.drawable.ic_notif_bluetooth
        else -> R.drawable.ic_notif_speaker
    }

    /** Moves to the next connected output; does nothing with only one. */
    fun cycle(c: Context) {
        val av = available(c).sortedBy { kind(it) }
        if (av.size < 2) return
        val i = av.indexOfFirst { kind(it) == current(c) }
        chosen = av[(i + 1) % av.size]
        if (android.os.Build.VERSION.SDK_INT >= 23) MusicPlayerEngine.get(c).player.setPreferredAudioDevice(chosen)
    }
}
