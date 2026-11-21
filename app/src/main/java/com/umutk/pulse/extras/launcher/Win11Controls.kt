package com.umutk.pulse.extras.launcher

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PSwitch
import com.umutk.pulse.Shell
import com.umutk.pulse.T
import com.umutk.pulse.extras.lyrics.LiveLyrics
import com.umutk.pulse.tr

/** True when Pulse can run system commands (Shizuku granted, or root). */
private fun priv(): Boolean = Shell.shizukuOk() || Shell.root == true

/** Runs a system command off the main thread. */
private fun sh(cmd: String) { Thread { runCatching { Shell.run(cmd) } }.start() }

@Composable
private fun Tog(label: String, initial: Boolean, enabled: Boolean = true, onSet: (Boolean) -> Unit) {
    var on by remember(label) { mutableStateOf(initial) }
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        PSwitch(on, { on = it; onSet(it) }, enabled)
    }
}

@Composable
private fun <V> Pick(label: String, options: List<Pair<String, V>>, current: () -> V, onPick: (V) -> Unit) {
    var cur by remember(label) { mutableStateOf(current()) }
    Text(label, color = T.sub, fontSize = 12.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (n, v) -> PChip(cur == v, n) { cur = v; onPick(v) } }
    }
}

/** Asks for the power Pulse needs to change system switches itself (Shizuku or root), when it has none yet. */
@Composable
private fun NeedPriv(ctx: Context) {
    if (priv()) return
    Text(tr("Pulse changes this switch itself once it has Shizuku or root.", "Pulse bu anahtarı, Shizuku veya root verilince kendisi değiştirir."), color = T.sub, fontSize = 12.sp)
    PButton(tr("Allow", "İzin ver")) { Shell.askShizuku(ctx); Thread { Shell.detectRoot() }.start() }
}

@Composable
private fun NeedWrite(ctx: Context) {
    if (Settings.System.canWrite(ctx)) return
    Text(tr("Pulse may change these once it is allowed to modify system settings.", "Pulse, sistem ayarlarını değiştirme izni verilince bunları değiştirebilir."), color = T.sub, fontSize = 12.sp)
    PButton(tr("Allow", "İzin ver")) { runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

private fun gint(ctx: Context, k: String, d: Int = 0) = runCatching { Settings.Global.getInt(ctx.contentResolver, k) }.getOrDefault(d)
private fun sint(ctx: Context, k: String, d: Int = 0) = runCatching { Settings.System.getInt(ctx.contentResolver, k) }.getOrDefault(d)
private fun secint(ctx: Context, k: String, d: Int = 0) = runCatching { Settings.Secure.getInt(ctx.contentResolver, k) }.getOrDefault(d)
private fun putSys(ctx: Context, k: String, v: Int) { if (Settings.System.canWrite(ctx)) runCatching { Settings.System.putInt(ctx.contentResolver, k, v) } }

/** What Pulse can really change for a phone setting, picked by the setting's own action. No hand-off to the phone's Settings app. */
@Composable
internal fun Win11Controls(ctx: Context, action: String) {
    val a = action
    when {
        a == Settings.ACTION_WIFI_SETTINGS || a == Settings.ACTION_WIRELESS_SETTINGS -> Section("Wi-Fi") {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            Tog("Wi-Fi", runCatching { wm.isWifiEnabled }.getOrDefault(false), priv()) { sh("svc wifi " + if (it) "enable" else "disable") }
            NeedPriv(ctx)
        }
        a == Settings.ACTION_BLUETOOTH_SETTINGS -> Section("Bluetooth") {
            val on = runCatching { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter?.isEnabled == true }.getOrDefault(false)
            Tog("Bluetooth", on, priv()) { sh("svc bluetooth " + if (it) "enable" else "disable") }
            NeedPriv(ctx)
        }
        a == Settings.ACTION_AIRPLANE_MODE_SETTINGS -> Section(tr("Airplane mode", "Uçak modu")) {
            Tog(tr("Airplane mode", "Uçak modu"), gint(ctx, Settings.Global.AIRPLANE_MODE_ON) == 1, priv()) { sh("cmd connectivity airplane-mode " + if (it) "enable" else "disable") }
            NeedPriv(ctx)
        }
        a == Settings.ACTION_DATA_ROAMING_SETTINGS -> Section(tr("Mobile data", "Mobil veri")) {
            Tog(tr("Mobile data", "Mobil veri"), gint(ctx, "mobile_data", 1) == 1, priv()) { sh("svc data " + if (it) "enable" else "disable") }
            NeedPriv(ctx)
        }
        a == Settings.ACTION_NFC_SETTINGS -> Section("NFC") {
            Tog("NFC", runCatching { android.nfc.NfcAdapter.getDefaultAdapter(ctx)?.isEnabled == true }.getOrDefault(false), priv()) { sh("svc nfc " + if (it) "enable" else "disable") }
            NeedPriv(ctx)
        }
        a == Settings.ACTION_LOCATION_SOURCE_SETTINGS -> Section(tr("Location", "Konum")) {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            Tog(tr("Location", "Konum"), runCatching { lm.isLocationEnabled }.getOrDefault(false), priv()) { sh("cmd location set-location-enabled " + if (it) "true" else "false") }
            NeedPriv(ctx)
        }
        a == "android.settings.ZEN_MODE_SETTINGS" || a == Settings.ACTION_APP_NOTIFICATION_SETTINGS -> Section(tr("Do not disturb", "Rahatsız etme")) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                Text(tr("Pulse may switch Do not disturb once it has this access.", "Pulse, bu erişim verilince Rahatsız etme'yi açıp kapatabilir."), color = T.sub, fontSize = 12.sp)
                PButton(tr("Allow", "İzin ver")) { runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            } else {
                Pick(tr("Interrupt", "Kesinti"), listOf(tr("All", "Hepsi") to NotificationManager.INTERRUPTION_FILTER_ALL, tr("Priority", "Öncelikli") to NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    tr("Alarms", "Alarmlar") to NotificationManager.INTERRUPTION_FILTER_ALARMS, tr("None", "Hiçbiri") to NotificationManager.INTERRUPTION_FILTER_NONE),
                    { nm.currentInterruptionFilter }) { runCatching { nm.setInterruptionFilter(it) } }
            }
        }
        a == Settings.ACTION_SOUND_SETTINGS -> Section(tr("Ringer", "Zil")) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            Pick(tr("Mode", "Mod"), listOf(tr("Sound", "Ses") to android.media.AudioManager.RINGER_MODE_NORMAL, tr("Vibrate", "Titreşim") to android.media.AudioManager.RINGER_MODE_VIBRATE),
                { am.ringerMode }) { runCatching { am.ringerMode = it } }
        }
        a == Settings.ACTION_DISPLAY_SETTINGS -> Section(tr("Screen", "Ekran")) {
            val ui = ctx.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
            Tog(tr("Dark mode", "Koyu mod"), ui.nightMode == android.app.UiModeManager.MODE_NIGHT_YES, priv()) { sh("cmd uimode night " + if (it) "yes" else "no") }
            Tog(tr("Auto-rotate", "Otomatik döndür"), sint(ctx, Settings.System.ACCELEROMETER_ROTATION) == 1, Settings.System.canWrite(ctx)) { putSys(ctx, Settings.System.ACCELEROMETER_ROTATION, if (it) 1 else 0) }
            Pick(tr("Screen off after", "Ekran kapanma"), listOf("15 s" to 15000, "30 s" to 30000, "1 min" to 60000, "2 min" to 120000, "5 min" to 300000, "10 min" to 600000),
                { sint(ctx, Settings.System.SCREEN_OFF_TIMEOUT, 60000) }) { putSys(ctx, Settings.System.SCREEN_OFF_TIMEOUT, it) }
            Pick(tr("Text size", "Yazı boyutu"), listOf("85%" to 85, "100%" to 100, "115%" to 115, "130%" to 130, "150%" to 150),
                { (ctx.resources.configuration.fontScale * 100).toInt().let { v -> listOf(85, 100, 115, 130, 150).minByOrNull { kotlin.math.abs(it - v) } ?: 100 } }) {
                if (Settings.System.canWrite(ctx)) runCatching { Settings.System.putFloat(ctx.contentResolver, Settings.System.FONT_SCALE, it / 100f) }
            }
            NeedWrite(ctx); NeedPriv(ctx)
        }
        a.endsWith("POWER_USAGE_SUMMARY") -> Section(tr("Power", "Güç")) {
            Tog(tr("Battery saver", "Pil tasarrufu"), gint(ctx, "low_power") == 1, priv()) { sh("settings put global low_power " + if (it) "1" else "0") }
            Pick(tr("Screen off after", "Ekran kapanma"), listOf("15 s" to 15000, "30 s" to 30000, "1 min" to 60000, "2 min" to 120000, "5 min" to 300000, "10 min" to 600000),
                { sint(ctx, Settings.System.SCREEN_OFF_TIMEOUT, 60000) }) { putSys(ctx, Settings.System.SCREEN_OFF_TIMEOUT, it) }
            NeedWrite(ctx); NeedPriv(ctx)
        }
        a == Settings.ACTION_DATE_SETTINGS -> Section(tr("Date & time", "Tarih ve saat")) {
            Tog(tr("Set time automatically", "Saati otomatik ayarla"), gint(ctx, Settings.Global.AUTO_TIME) == 1, priv()) { sh("settings put global auto_time " + if (it) "1" else "0") }
            Tog(tr("Set time zone automatically", "Saat dilimini otomatik ayarla"), gint(ctx, Settings.Global.AUTO_TIME_ZONE) == 1, priv()) { sh("settings put global auto_time_zone " + if (it) "1" else "0") }
            Pick(tr("Clock", "Saat"), listOf("12 h" to "12", "24 h" to "24"), { Settings.System.getString(ctx.contentResolver, Settings.System.TIME_12_24) ?: "24" }) {
                if (Settings.System.canWrite(ctx)) runCatching { Settings.System.putString(ctx.contentResolver, Settings.System.TIME_12_24, it) }
            }
            NeedWrite(ctx); NeedPriv(ctx)
        }
        a == Settings.ACTION_INPUT_METHOD_SETTINGS -> Section(tr("Keyboard", "Klavye")) {
            Text(tr("Pulse keyboard is picked from the list of keyboards.", "Pulse klavyesi klavye listesinden seçilir."), color = T.sub, fontSize = 12.sp)
            PButton(tr("Choose keyboard", "Klavye seç")) { runCatching { (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() } }
        }
        a == Settings.ACTION_SECURITY_SETTINGS -> Section(tr("Lock screen", "Kilit ekranı")) {
            PButton(tr("Lock now", "Şimdi kilitle")) { powerAction(ctx, "lock") }
            Tog(tr("Lyrics on the lock screen", "Kilit ekranında sözler"), LiveLyrics.lockScreen(ctx)) { LiveLyrics.set(ctx, "lock_screen", it) }
            Pick(tr("Lyrics text size", "Söz yazı boyutu"), listOf(tr("Like the player", "Oynatıcı gibi") to 0, "20" to 20, "24" to 24, "28" to 28, "32" to 32, "38" to 38),
                { LiveLyrics.lockFont(ctx) }) { LiveLyrics.setLockFont(ctx, it) }
            Pick(tr("Least lines", "En az satır"), listOf("1" to 1, "2" to 2, "3" to 3), { LiveLyrics.minLines(ctx) }) { LiveLyrics.setMinLines(ctx, it) }
        }
        a == Settings.ACTION_ACCESSIBILITY_SETTINGS -> Section(tr("Vision and hearing", "Görme ve işitme")) {
            Tog(tr("Grayscale", "Gri tonlama"), secint(ctx, "accessibility_display_daltonizer_enabled") == 1 && secint(ctx, "accessibility_display_daltonizer") == 0, priv()) {
                sh(if (it) "settings put secure accessibility_display_daltonizer 0; settings put secure accessibility_display_daltonizer_enabled 1" else "settings put secure accessibility_display_daltonizer_enabled 0") }
            Tog(tr("Invert colors", "Renkleri ters çevir"), secint(ctx, "accessibility_display_inversion_enabled") == 1, priv()) { sh("settings put secure accessibility_display_inversion_enabled " + if (it) "1" else "0") }
            Tog(tr("Mono audio", "Mono ses"), sint(ctx, "master_mono") == 1, priv()) { sh("settings put system master_mono " + if (it) "1" else "0") }
            Tog(tr("Remove animations", "Animasyonları kaldır"), runCatching { Settings.Global.getFloat(ctx.contentResolver, "animator_duration_scale") }.getOrDefault(1f) == 0f, priv()) {
                val v = if (it) "0" else "1"; sh("settings put global animator_duration_scale $v; settings put global transition_animation_scale $v; settings put global window_animation_scale $v") }
            NeedPriv(ctx)
        }
        else -> Section(tr("This phone", "Bu telefon")) {
            listOf(tr("Device", "Cihaz") to "${Build.MANUFACTURER} ${Build.MODEL}", "Android" to "${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})", tr("Build", "Derleme") to Build.DISPLAY).forEach { (k, v) ->
                Row(Modifier.padding(vertical = 2.dp)) { Text(k, color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text(v, color = T.sub, fontSize = 13.sp) }
            }
        }
    }
}
