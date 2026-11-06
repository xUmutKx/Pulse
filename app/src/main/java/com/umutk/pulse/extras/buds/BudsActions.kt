package com.umutk.pulse.extras.buds

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import com.umutk.pulse.tr

/**
 * What a touch and hold on an earbud can do on the phone. With the earbud's touch option set to "Custom" (the option values 5 for the left and 6 for the right earbud,
 * as in the GalaxyBudsClient notes) the earbud does nothing itself and reports the hold with message 147; the action chosen here runs then.
 */
object BudsActions {
    /** The id and the name of each action, in the order they are listed. */
    fun all(): List<Pair<String, String>> = listOf(
        "none" to tr("Nothing", "Hiçbir şey"),
        "playPause" to tr("Play / pause", "Oynat / duraklat"),
        "next" to tr("Next track", "Sonraki parça"),
        "prev" to tr("Previous track", "Önceki parça"),
        "volUp" to tr("Volume up", "Sesi artır"),
        "volDown" to tr("Volume down", "Sesi azalt"),
        "ancToggle" to tr("ANC on / off", "ANC aç / kapat"),
        "ambientToggle" to tr("Ambient on / off", "Ortam sesi aç / kapat"),
        "noiseCycle" to tr("Cycle noise control", "Gürültü denetimini değiştir"),
        "ancLevel" to tr("ANC strength", "ANC gücü"),
        "eq" to tr("Next equalizer", "Sonraki ekolayzır"),
        "voice" to tr("Voice detect on / off", "Konuşma algılama aç / kapat"),
        "game" to tr("Game mode on / off", "Oyun modu aç / kapat"),
        "torch" to tr("Flashlight", "Fener"),
        "battery" to tr("Show battery", "Pili göster"),
    )

    private var torchOn = false

    fun run(c: Context, id: String) {
        val ctx = c.applicationContext
        val s = BudsLink.state.value
        fun flip(key: String, def: Int, msg: Int) { val n = if (BudsPrefs.saved(ctx, key, def) == 1) 0 else 1; BudsPrefs.save(ctx, key, n); BudsLink.send(msg, n) }
        when (id) {
            "playPause" -> key(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "next" -> key(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
            "prev" -> key(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "volUp" -> ctx.getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "volDown" -> ctx.getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "ancToggle" -> if (s.canNoise) BudsLink.setNoise(if (s.noise == 1) 0 else 1)
            "ambientToggle" -> if (s.canNoise) BudsLink.setNoise(if (s.noise == 2) 0 else 2)
            "noiseCycle" -> if (s.canNoise) BudsLink.setNoise(when (s.noise) { 0 -> 1; 1 -> 2; else -> 0 })
            "ancLevel" -> flip("ancHigh", 0, BudsProto.NOISE_REDUCTION_LEVEL)
            "eq" -> { val n = (BudsPrefs.saved(ctx, "eq", 0) + 1) % 6; BudsPrefs.save(ctx, "eq", n); BudsLink.send(BudsProto.EQUALIZER, n) }
            "voice" -> flip("voice", 0, BudsProto.SET_DETECT_CONVERSATIONS)
            "game" -> flip("game", 0, BudsProto.GAME_MODE)
            "torch" -> runCatching {
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cam = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                if (cam != null) { torchOn = !torchOn; cm.setTorchMode(cam, torchOn) }
            }
            "battery" -> Handler(Looper.getMainLooper()).post {
                val t = listOfNotNull(s.batteryL.takeIf { it >= 0 }?.let { "L $it%" }, s.batteryR.takeIf { it >= 0 }?.let { "R $it%" }, s.batteryCase.takeIf { it in 0..100 }?.let { tr("Case", "Kutu") + " $it%" })
                Toast.makeText(ctx, t.joinToString("  ").ifBlank { "—" }, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun key(ctx: Context, code: Int) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }
}
