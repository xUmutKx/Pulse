package com.umutk.pulse.extras.keyboard

import android.content.Context
import android.content.Intent
import com.umutk.pulse.tr

/** The phone's own things from the keyboard (from WM Keyboard's tools, MIT): the flashlight, a web search, an alarm. */
object KbPhone {
    @Volatile private var torchOn = false

    fun torch(c: Context) {
        val cm = c.getSystemService(android.hardware.camera2.CameraManager::class.java) ?: return
        val id = runCatching { cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }.getOrNull() ?: return
        if (runCatching { cm.setTorchMode(id, !torchOn) }.isSuccess) torchOn = !torchOn
    }

    /** Searches [q] on the web (an address opens as it is); nothing typed opens the search page. */
    fun search(c: Context, q: String) {
        val url = if (Regex("^(https?://)?[\\w-]+(\\.[\\w-]+)+(/\\S*)?$").matches(q)) (if (q.startsWith("http")) q else "https://$q")
            else "https://www.google.com/search?q=" + java.net.URLEncoder.encode(q, "UTF-8")
        runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * An alarm from the time in [text]: "7:30", "07.30", "19:45", "7 pm", "in 10 min" / "10 dk sonra", "in 2 hours" / "2 saat sonra"; with no time the clock app's alarm page opens.
     * The clock app sets it (Android's alarm intent), so it rings like any other alarm.
     */
    fun alarm(c: Context, text: String) {
        val t = text.lowercase()
        val now = java.util.Calendar.getInstance()
        var h = -1; var m = 0
        Regex("(\\d{1,2})[:.](\\d{2})\\s*(am|pm)?").findAll(t).lastOrNull()?.let { r ->
            h = r.groupValues[1].toInt(); m = r.groupValues[2].toInt()
            if (r.groupValues[3] == "pm" && h < 12) h += 12; if (r.groupValues[3] == "am" && h == 12) h = 0
        } ?: Regex("(\\d{1,2})\\s*(am|pm)").findAll(t).lastOrNull()?.let { r ->
            h = r.groupValues[1].toInt() % 12 + if (r.groupValues[2] == "pm") 12 else 0
        } ?: Regex("(?:in\\s+)?(\\d{1,3})\\s*(min|minutes|dk|dakika|h|hour|hours|saat)").findAll(t).lastOrNull()?.let { r ->
            val n = r.groupValues[1].toInt(); val isHour = r.groupValues[2].startsWith("h") || r.groupValues[2] == "saat"
            now.add(if (isHour) java.util.Calendar.HOUR_OF_DAY else java.util.Calendar.MINUTE, n)
            h = now.get(java.util.Calendar.HOUR_OF_DAY); m = now.get(java.util.Calendar.MINUTE)
        }
        val i = if (h in 0..23 && m in 0..59) Intent(android.provider.AlarmClock.ACTION_SET_ALARM).putExtra(android.provider.AlarmClock.EXTRA_HOUR, h).putExtra(android.provider.AlarmClock.EXTRA_MINUTES, m)
            .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, tr("Pulse Keyboard", "Pulse Klavye")).putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            else Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
        runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onSuccess {
            if (h >= 0) android.widget.Toast.makeText(c, tr("Alarm %02d:%02d", "Alarm %02d:%02d").format(h, m), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
