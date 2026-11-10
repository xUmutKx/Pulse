package com.umutk.pulse.extras.music

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Starts the music when a Bluetooth audio device (headphones, speaker, car kit) connects. Off by default. */
object BluetoothAutoplay {
    private fun sp(c: Context) = c.getSharedPreferences("music_settings", Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("bt_autoplay", false)
    fun setEnabled(c: Context, on: Boolean) = sp(c).edit().putBoolean("bt_autoplay", on).apply()
}

/**
 * Declared in the manifest (the ACL connect broadcast is one of the few implicit ones Android still delivers to a manifest receiver) and also registered by
 * [MusicPlaybackService] while it runs. Needs BLUETOOTH_CONNECT to read the device class.
 */
class BluetoothAutoplayReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED || !BluetoothAutoplay.enabled(ctx)) return
        val device = intent.device() ?: return
        // headphones, speakers and car kits only; keyboards, watches and the like are ignored
        if (runCatching { device.bluetoothClass?.hasService(BluetoothClass.Service.AUDIO) }.getOrNull() != true) return
        // the connection is still being set up at this moment (the phone plays to its speaker until it is done, and the phone can
        // report the connection twice): the check waits a few seconds and only then decides
        val pending = goAsync()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try { startIfQuiet(ctx) } finally { pending.finish() }
        }, 1500L)
    }

    /** Starts the music only when the headphones are the output now, nothing else is playing or in a call, and it was not started a minute ago. */
    private fun startIfQuiet(ctx: Context) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        // the headphones must already be the output (their own Bluetooth route, not the phone's speaker)
        val routed = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
        if (!routed) return
        // a video, another player or a call is on: this stays quiet
        if (am.isMusicActive || am.mode != android.media.AudioManager.MODE_NORMAL) return
        // one start per connection: a second connect event right after the first does not start it again
        val sp = ctx.getSharedPreferences("music_settings", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("bt_last_start", 0L) < 60_000L) return
        sp.edit().putLong("bt_last_start", now).apply()
        // the playback service is started first; the media volume at zero: nothing starts (it would play to nobody, or burst out when the volume is raised)
        runCatching { androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, MusicPlaybackService::class.java)) }
        if (am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0) return
        val engine = MusicPlayerEngine.get(ctx)
        Playlists.restore(ctx, engine) // empty queue: nothing to play, the restore only fills it when a last queue exists
        engine.play()
    }

    @Suppress("DEPRECATION")
    private fun Intent.device(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}
