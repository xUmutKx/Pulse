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
 * Registered by [MusicPlaybackService] while it runs. Implicit ACL broadcasts are not delivered to manifest receivers
 * on API 26+, so this is not declared in the manifest. Needs BLUETOOTH_CONNECT to read the device class.
 */
class BluetoothAutoplayReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED || !BluetoothAutoplay.enabled(ctx)) return
        val device = intent.device() ?: return
        // headphones, speakers and car kits only; keyboards, watches and the like are ignored
        if (runCatching { device.bluetoothClass?.hasService(BluetoothClass.Service.AUDIO) }.getOrNull() != true) return
        val engine = MusicPlayerEngine.get(ctx)
        Playlists.restore(ctx, engine) // empty queue: nothing to play, the restore only fills it when a last queue exists
        engine.play()
    }

    @Suppress("DEPRECATION")
    private fun Intent.device(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}
