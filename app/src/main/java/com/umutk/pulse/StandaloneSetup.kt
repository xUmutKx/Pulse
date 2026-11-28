package com.umutk.pulse

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

/**
 * The first start of one of Pulse's apps on its own (see Standalone.kt): a setup screen of its own that asks only for what THAT app needs, one row for each
 * permission, all optional (nothing is needed to go on; a feature asks again when it is used). Pulse itself keeps its own welcome and setup.
 */
object StandaloneSetup {
    private fun sp(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)
    fun done(c: Context) = sp(c).getBoolean("standalone_setup_done", false)

    /**
     * Called first in an entry activity of the standalone app: on the first start the setup screen opens in its place (and this activity ends), and when it is done the
     * activity opens again as it was asked to. True when the caller must stop (return from onCreate).
     */
    fun gate(a: Activity): Boolean {
        if (!Standalone.on || done(a)) return false
        runCatching { a.startActivity(Intent(a, StandaloneSetupActivity::class.java).putExtra("next", Intent(a.intent))) }
        a.finish()
        return true
    }

    /** What one row asks for: runtime permissions, or a special access whose screen opens in the system settings. */
    class Item(val icon: String, val title: String, val why: String, val perms: List<String> = emptyList(), val granted: ((Context) -> Boolean)? = null, val open: ((Context) -> Unit)? = null)

    private fun media(vararg kinds: String): List<String> = if (Build.VERSION.SDK_INT >= 33) kinds.map { "android.permission.READ_MEDIA_" + it } else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    private fun settings(c: Context, action: String, pkg: Boolean = true) = runCatching {
        c.startActivity(Intent(action).apply { if (pkg) data = Uri.parse("package:" + c.packageName) }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private val notifications = Item("bell", tr("Notifications", "Bildirimler"), tr("Progress, controls and reminders of this app.", "Bu uygulamanın ilerleme, kontrol ve hatırlatmaları."),
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList())
    private fun files() = Item("folder", tr("All files", "Tüm dosyalar"), tr("Browse and change every file on the phone.", "Telefondaki tüm dosyalara bakma ve değiştirme."),
        granted = { if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true },
        open = { c -> if (Build.VERSION.SDK_INT >= 30) settings(c, Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION) })
    private fun overlay(why: String) = Item("window", tr("Show over other apps", "Diğer uygulamaların üzerinde göster"), why,
        granted = { c -> Settings.canDrawOverlays(c) }, open = { c -> settings(c, Settings.ACTION_MANAGE_OVERLAY_PERMISSION) })
    private fun usage(why: String) = Item("speed", tr("Usage access", "Kullanım erişimi"), why,
        granted = { c -> com.umutk.pulse.features.AppLock.usageGranted(c) }, open = { c -> settings(c, Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg = false) })
    private fun listener(why: String) = Item("bell", tr("Notification access", "Bildirim erişimi"), why,
        granted = { c -> androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName) }, open = { c -> settings(c, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, pkg = false) })
    private fun mic(why: String) = Item("mic", tr("Microphone", "Mikrofon"), why, listOf(Manifest.permission.RECORD_AUDIO))

    /** The icon of this app's launcher entry (its own, as the launcher shows it), or null. */
    fun iconOf(c: Context): android.graphics.drawable.Drawable? = runCatching {
        val alias = Standalone.ALIAS ?: return null
        c.packageManager.getActivityIcon(android.content.ComponentName(c, "com.umutk.pulse.$alias"))
    }.getOrNull()

    /** The rows of the app [id] (the id of scripts/standalone_apps.txt). */
    fun items(id: String): List<Item> = when (id) {
        "files" -> listOf(files(), Item("music", tr("Photos, music and video", "Fotoğraf, müzik ve video"), tr("Thumbnails and the type lists.", "Küçük resimler ve tür listeleri."), media("IMAGES", "VIDEO", "AUDIO")), notifications)
        "photos" -> listOf(Item("image", tr("Photos and videos", "Fotoğraf ve videolar"), tr("The pictures of this phone.", "Bu telefonun resimleri."), media("IMAGES", "VIDEO")), notifications)
        "calendar" -> listOf(Item("calendar", tr("Calendar", "Takvim"), tr("Shows and adds your events.", "Etkinliklerini gösterir ve ekler."), listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)), notifications)
        "contacts" -> listOf(Item("users", tr("Contacts", "Kişiler"), tr("Shows and edits your contacts.", "Kişilerini gösterir ve düzenler."), listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)),
            Item("call", tr("Phone calls", "Telefon aramaları"), tr("Call a contact and show the call history.", "Kişiyi arama ve arama geçmişini gösterme."), listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG)))
        "messages" -> listOf(Item("messages", tr("Text messages", "Kısa mesajlar"), tr("Read and send text messages.", "Kısa mesaj okuma ve gönderme."), listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)),
            Item("users", tr("Contacts", "Kişiler"), tr("Names instead of numbers.", "Numara yerine isimler."), listOf(Manifest.permission.READ_CONTACTS)),
            Item("chat", tr("Default messages app", "Varsayılan mesaj uygulaması"), tr("Receive messages here and keep the history.", "Mesajları burada alma ve geçmişi tutma."),
                granted = { c -> runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(c) == c.packageName }.getOrDefault(false) },
                open = { c -> runCatching { if (Build.VERSION.SDK_INT >= 29) (c as Activity).startActivityForResult(c.getSystemService(android.app.role.RoleManager::class.java).createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS), 71) else settings(c, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, pkg = false) } }),
            notifications)
        "music" -> listOf(Item("music", tr("Music", "Müzik"), tr("Your songs.", "Şarkıların."), media("AUDIO")), notifications,
            Item("phone", tr("Bluetooth", "Bluetooth"), tr("Start playing when your earbuds or car connect.", "Kulaklık ya da araba bağlanınca çalmaya başlama."), if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList()))
        "video" -> listOf(Item("video", tr("Videos", "Videolar"), tr("Your videos.", "Videoların."), media("VIDEO")), notifications, overlay(tr("The small window that plays on while you use the phone.", "Telefonu kullanırken oynamaya devam eden küçük pencere.")))
        "ytx" -> listOf(notifications)
        "code" -> listOf(files())
        "terminal" -> listOf(files())
        "ebook" -> listOf(Item("book", tr("Books", "Kitaplar"), tr("Books on the phone.", "Telefondaki kitaplar."), media("IMAGES")), files(), notifications)
        "office" -> listOf(files(), notifications)
        "paint" -> listOf(Item("image", tr("Photos", "Fotoğraflar"), tr("Open and save pictures.", "Resim açma ve kaydetme."), media("IMAGES")))
        "palette" -> listOf(Item("image", tr("Photos", "Fotoğraflar"), tr("Pictures for wallpapers and icons.", "Duvar kağıdı ve simgeler için resimler."), media("IMAGES")))
        "weather" -> listOf(Item("weather", tr("Location", "Konum"), tr("The weather where you are.", "Bulunduğun yerin hava durumu."), listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)), notifications)
        "voice" -> listOf(mic(tr("Record your voice.", "Sesini kaydetme.")), notifications)
        "screenrec" -> listOf(mic(tr("Record the sound with the screen.", "Ekranla birlikte sesi kaydetme.")), notifications, overlay(tr("The small control over the screen while recording.", "Kayıt sırasında ekranın üstündeki küçük kontrol.")))
        "whitenoise" -> listOf(notifications)
        "notes" -> listOf(notifications)
        "clock" -> listOf(notifications, Item("alarm", tr("Exact alarms", "Tam zamanlı alarmlar"), tr("Alarms and timers ring on the minute.", "Alarm ve sayaçlar tam dakikasında çalar."),
            granted = { c -> if (Build.VERSION.SDK_INT >= 31) c.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms() else true },
            open = { c -> if (Build.VERSION.SDK_INT >= 31) settings(c, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }), overlay(tr("The alarm screen over the lock screen.", "Kilit ekranının üstünde alarm ekranı.")))
        "keyboard" -> listOf(
            Item("keyboard", tr("Turn the keyboard on", "Klavyeyi aç"), tr("Switch it on in the phone's keyboard list.", "Telefonun klavye listesinde aç."),
                granted = { c -> c.getSystemService(android.view.inputmethod.InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == c.packageName } },
                open = { c -> settings(c, Settings.ACTION_INPUT_METHOD_SETTINGS, pkg = false) }),
            Item("keyboard", tr("Use it", "Kullan"), tr("Pick it as the keyboard you type with.", "Yazı yazdığın klavye olarak seç."),
                granted = { c -> Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty().startsWith(c.packageName) },
                open = { c -> runCatching { c.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showInputMethodPicker() } }),
            mic(tr("Voice typing.", "Sesle yazma.")), notifications, listener(tr("Offer a one-time code from a notification in the bar.", "Bildirimdeki tek kullanımlık kodu çubukta sunma.")))
        "launcher" -> listOf(
            Item("home", tr("Home app", "Ana ekran uygulaması"), tr("Use this launcher as your home screen.", "Bu başlatıcıyı ana ekranın olarak kullan."),
                granted = { c -> runCatching { if (Build.VERSION.SDK_INT >= 29) c.getSystemService(android.app.role.RoleManager::class.java).isRoleHeld(android.app.role.RoleManager.ROLE_HOME) else false }.getOrDefault(false) },
                open = { c -> runCatching { if (Build.VERSION.SDK_INT >= 29) (c as Activity).startActivityForResult(c.getSystemService(android.app.role.RoleManager::class.java).createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 72) else settings(c, Settings.ACTION_HOME_SETTINGS, pkg = false) } }),
            notifications, files(), usage(tr("Show the apps you used lately.", "Son kullandığın uygulamaları gösterme.")), listener(tr("Notification badges and the notification panel.", "Bildirim rozetleri ve bildirim paneli.")),
            Item("call", tr("Contacts", "Kişiler"), tr("Search people from the launcher.", "Başlatıcıdan kişi arama."), listOf(Manifest.permission.READ_CONTACTS)))
        "island" -> listOf(overlay(tr("The pill over the camera.", "Kameranın üstündeki hap.")), listener(tr("Show what is going on in the pill: calls, music, timers.", "Hapta olanları gösterme: arama, müzik, sayaç.")), notifications)
        "notifhist" -> listOf(listener(tr("Save the notifications you receive.", "Aldığın bildirimleri kaydetme.")), notifications)
        "gamehub" -> listOf(usage(tr("Play time of each game.", "Her oyunun oynama süresi.")), overlay(tr("The menu over a running game.", "Çalışan oyunun üstündeki menü.")), notifications)
        "health" -> listOf(Item("heart", tr("Activity", "Aktivite"), tr("Steps and workouts.", "Adımlar ve antrenmanlar."), if (Build.VERSION.SDK_INT >= 29) listOf(Manifest.permission.ACTIVITY_RECOGNITION) else emptyList()), notifications)
        "localsend" -> listOf(files(), notifications, Item("phone", tr("Nearby devices", "Yakındaki cihazlar"), tr("Find phones and computers on your Wi-Fi.", "Wi-Fi'ındaki telefon ve bilgisayarları bulma."),
            if (Build.VERSION.SDK_INT >= 33) listOf("android.permission.NEARBY_WIFI_DEVICES") else listOf(Manifest.permission.ACCESS_FINE_LOCATION)))
        "ai" -> listOf(mic(tr("Speak instead of typing.", "Yazmak yerine konuşma.")), notifications)
        "browser", "tempmail", "extrastore", "calculator" -> listOf(notifications)
        else -> listOf(notifications)
    }.filter { it.perms.isNotEmpty() || it.granted != null }
}

/** The setup screen of a standalone app. */
class StandaloneSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val next = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("next", Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>("next")
        fun go() {
            getSharedPreferences("p", MODE_PRIVATE).edit().putBoolean("standalone_setup_done", true).putBoolean("welcomed", true).putBoolean("setup_done", true).putString("seen_version", appVersionName(this)).apply()
            next?.let { runCatching { startActivity(it) } }
            finish()
        }
        setContent { LookHost(lookId = "material") { th -> Box(Modifier.fillMaxSize().background(th.bg).statusBarsPadding()) { StandaloneSetupScreen(Standalone.id, ::go) } } }
    }
}

@Composable
fun StandaloneSetupScreen(id: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as? Activity
    var tick by remember { mutableIntStateOf(0) }
    // the rows are read again when the person comes back from a settings screen
    val lc = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lc) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        lc.addObserver(o); onDispose { lc.removeObserver(o) }
    }
    var asked by remember { mutableStateOf<StandaloneSetup.Item?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        tick++
        val it0 = asked; asked = null
        // after two refusals Android no longer asks: the app's own settings page opens instead, so a row never seems dead
        if (it0 != null && res.values.any { !it } && act != null && it0.perms.none { p -> androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(act, p) }) {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
    val name = remember { runCatching { ctx.packageManager.getApplicationLabel(ctx.applicationInfo).toString() }.getOrDefault("Pulse") }
    val logo = remember {
        runCatching { StandaloneSetup.iconOf(ctx)?.toBitmap(192, 192)?.asImageBitmap() }.getOrNull()
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter).padding(top = 32.dp, bottom = 24.dp)) {
        Image(logo ?: rememberLauncherLogo(), null, Modifier.size(72.dp))
        Text(tr("Welcome to $name", "$name uygulamasına hoş geldin"), color = T.text, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
        val items = remember(id) { StandaloneSetup.items(id) }
        Text(if (items.isEmpty()) tr("Nothing to set up.", "Kurulacak bir şey yok.") else tr("Everything here is optional: give what you want now, the rest when a feature needs it.",
            "Buradaki her şey isteğe bağlı: istediğini şimdi ver, kalanını bir özellik gerektirdiğinde."), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        if (items.isNotEmpty()) WelcomeCard(tr("Permissions", "İzinler")) {
            items.forEach { item ->
                val granted = remember(tick, item.title) {
                    item.granted?.let { g -> runCatching { g(ctx) }.getOrDefault(false) } ?: (item.perms.isEmpty() || item.perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED })
                }
                val ask = {
                    if (!granted) { if (item.perms.isNotEmpty()) { asked = item; launcher.launch(item.perms.toTypedArray()) } else item.open?.invoke(ctx) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PCheck(granted, { wanted -> if (wanted) ask() })
                    Box(Modifier.padding(start = 8.dp).size(28.dp), contentAlignment = Alignment.Center) { PIcon(item.icon, Look.accent, 22.dp) }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(item.title, color = T.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(item.why, color = T.sub, fontSize = 12.sp)
                    }
                }
            }
        }
        Text(tr("$name keeps everything on this phone. Nothing is sent to us. The terms and the privacy notice are in its settings.",
            "$name her şeyi bu telefonda tutar. Bize hiçbir şey gönderilmez. Koşullar ve gizlilik bildirimi ayarlarında."), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
        Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) { PButton(tr("Start", "Başla"), onClick = onDone) }
    }
}
