package com.umutk.pulse.features

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Pulse's own ADB client (libadb-android, GPL-3.0-or-later OR Apache-2.0): pairs with a Wear OS watch over wireless debugging and then installs
 * APKs (Pulse Watch first), sends files, lists and removes apps and takes a picture of the watch's screen, with no computer in between.
 * The key (RSA 2048, the one ADB uses) and its certificate are made once and kept in the app's own storage.
 */
class WatchAdb private constructor(ctx: Context) : AbsAdbConnectionManager() {
    private val dir = File(ctx.filesDir, "watch_adb").apply { mkdirs() }
    private val key: PrivateKey
    private val cert: Certificate

    init {
        setApi(android.os.Build.VERSION.SDK_INT)
        val kf = File(dir, "key.pk8"); val cf = File(dir, "cert.der")
        if (!kf.isFile || !cf.isFile) {
            val pair = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val subject = org.bouncycastle.asn1.x500.X500Name("CN=Pulse")
            val now = System.currentTimeMillis()
            val holder = org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(subject, java.math.BigInteger.valueOf(now), java.util.Date(now - 86_400_000L), java.util.Date(now + 30L * 365 * 86_400_000L), subject, pair.public)
                .build(org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
            val c = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(holder)
            kf.writeBytes(pair.private.encoded); cf.writeBytes(c.encoded)
        }
        key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(kf.readBytes()))
        cert = CertificateFactory.getInstance("X.509").generateCertificate(cf.inputStream())
    }

    override fun getPrivateKey(): PrivateKey = key
    override fun getCertificate(): Certificate = cert
    override fun getDeviceName(): String = "Pulse"

    /** Runs [cmd] in the watch's shell and gives back what it printed. */
    fun shell(cmd: String): String = openStream("shell:$cmd").use { s -> s.openInputStream().readBytes().decodeToString() }

    /** Raw bytes from a command (exec: does not touch line ends, so a PNG stays whole). */
    fun execBytes(cmd: String): ByteArray = openStream("exec:$cmd").use { s -> s.openInputStream().readBytes() }

    /** Installs the APK [f] on the watch (streamed to the package manager, nothing left behind on the watch). */
    fun install(f: File): String = openStream("exec:cmd package install -r -S ${f.length()}").use { s ->
        s.openOutputStream().use { o -> f.inputStream().use { it.copyTo(o) } }
        s.openInputStream().readBytes().decodeToString().trim()
    }

    /** Writes the file [f] to the watch's Download folder. */
    fun push(f: File): String {
        val name = f.name.replace("'", "")
        openStream("exec:sh -c 'cat > \"/sdcard/Download/$name\"'").use { s ->
            s.openOutputStream().use { o -> f.inputStream().use { it.copyTo(o) } }
            runCatching { s.openInputStream().readBytes() }
        }
        return "/sdcard/Download/$name"
    }

    companion object {
        @Volatile private var one: WatchAdb? = null
        fun get(ctx: Context): WatchAdb = one ?: synchronized(this) { one ?: WatchAdb(ctx.applicationContext).also { one = it } }
    }
}

/** The watch manager page (Toolkit > Watch): pair, connect, install Pulse Watch or any APK, send a file, the watch's apps and its screen. */
@Composable
fun WatchManagerTool() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sp = remember { ctx.getSharedPreferences("watch_adb", Context.MODE_PRIVATE) }
    var host by remember { mutableStateOf(sp.getString("host", "").orEmpty()) }
    var port by remember { mutableStateOf(sp.getString("port", "").orEmpty()) }
    var pairPort by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(emptyList<String>()) }
    var screen by remember { mutableStateOf<ImageBitmap?>(null) }
    var shellCmd by remember { mutableStateOf("") }
    var shellOut by remember { mutableStateOf("") }

    fun job(what: String, block: suspend (WatchAdb) -> String) = scope.launch {
        busy = true; msg = what
        msg = withContext(Dispatchers.IO) { runCatching { block(WatchAdb.get(ctx)) }.getOrElse { (it.message ?: it.javaClass.simpleName).take(200) } }
        connected = runCatching { WatchAdb.get(ctx).isConnected }.getOrDefault(false)
        busy = false
    }
    fun connect() = job(tr("Connecting…", "Bağlanıyor…")) { a ->
        sp.edit().putString("host", host).putString("port", port).apply()
        if (a.isConnected) a.disconnect()
        a.connect(host.trim(), port.trim().toInt())
        model = a.shell("getprop ro.product.model").trim()
        apps = a.shell("pm list packages -3").lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.sorted()
        tr("Connected", "Bağlandı")
    }
    // what is taken from the watch (an APK, a recording) goes to Download/Pulse/Watch on the phone
    fun saveDir(): File = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Pulse/Watch").apply { mkdirs() }
    // an APK or any file from the phone, copied first so it can be streamed
    fun copyIn(u: Uri): File {
        val name = runCatching { ctx.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull() ?: "file"
        val f = File(ctx.cacheDir, "watch_$name")
        ctx.contentResolver.openInputStream(u)?.use { i -> f.outputStream().use { i.copyTo(it) } }
        return f
    }
    val pickApk = rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.OpenDocument()) { u: Uri? ->
        if (u != null) job(tr("Installing…", "Kuruluyor…")) { a -> val f = copyIn(u); try { a.install(f) } finally { f.delete() } }
    }
    val pickFile = rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.OpenDocument()) { u: Uri? ->
        if (u != null) job(tr("Sending…", "Gönderiliyor…")) { a -> val f = copyIn(u); try { a.push(f) } finally { f.delete() } }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // the connection: the address and port wireless debugging shows on the watch, and the pairing code once
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (connected) com.umutk.pulse.Look.accent else T.sub.copy(alpha = .4f)))
                Text(if (connected) model.ifEmpty { tr("Watch", "Saat") } else tr("Not connected", "Bağlı değil"), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (connected) PButton(tr("Disconnect", "Kes"), compact = true) { job("…") { a -> a.disconnect(); "" } }
            }
            PField(host, { host = it.trim() }, tr("Watch IP address", "Saatin IP adresi"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PField(port, { port = it.filter(Char::isDigit) }, tr("Port", "Port"), Modifier.weight(1f))
                PButton(tr("Connect", "Bağlan"), compact = true) { if (host.isNotBlank() && port.isNotBlank() && !busy) connect() }
            }
            Text(tr("Pairing (first time)", "Eşleştirme (ilk sefer)"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PField(pairPort, { pairPort = it.filter(Char::isDigit) }, tr("Port", "Port"), Modifier.weight(1f))
                PField(code, { code = it.filter(Char::isDigit).take(6) }, tr("Code", "Kod"), Modifier.weight(1f))
                PButton(tr("Pair", "Eşleştir"), compact = true) {
                    if (host.isNotBlank() && pairPort.isNotBlank() && code.length == 6 && !busy) job(tr("Pairing…", "Eşleştiriliyor…")) { a ->
                        if (a.pair(host.trim(), pairPort.toInt(), code)) tr("Paired", "Eşleşti") else tr("Pairing failed", "Eşleşme olmadı")
                    }
                }
            }
            if (msg.isNotEmpty()) Text(msg, color = T.sub, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (connected) {
            PCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PButton(tr("Install APK", "APK kur"), Modifier.weight(1f), compact = true) { pickApk.launch(arrayOf("application/vnd.android.package-archive")) }
                    PButton(tr("Send file", "Dosya gönder"), Modifier.weight(1f), compact = true) { pickFile.launch(arrayOf("*/*")) }
                }
                // Pulse Watch: the newest one from Pulse's releases, straight to the watch
                PButton(tr("Install Pulse Watch", "Pulse Watch'ı kur"), Modifier.fillMaxWidth().padding(top = 6.dp), compact = true) {
                    job(tr("Downloading Pulse Watch…", "Pulse Watch indiriliyor…")) { a ->
                        val f = File(ctx.cacheDir, "PulseWatch.apk")
                        try {
                            java.net.URL("https://github.com/xUmutKx/Pulse/releases/latest/download/PulseWatch.apk").openStream().use { i -> f.outputStream().use { i.copyTo(it) } }
                            a.install(f)
                        } finally { f.delete() }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    PButton(tr("Screen", "Ekran"), Modifier.weight(1f), compact = true) {
                        job("…") { a -> val b = a.execBytes("screencap -p"); screen = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap(); "" }
                    }
                    PButton(tr("Reboot", "Yeniden başlat"), Modifier.weight(1f), compact = true, danger = true) { job("…") { a -> runCatching { a.shell("reboot") }; "" } }
                }
                screen?.let { Image(it, null, Modifier.padding(top = 8.dp).size(200.dp).clip(CircleShape).align(Alignment.CenterHorizontally), contentScale = ContentScale.Crop) }
            }
            // the shell, the log and the screen recording
            PCard {
                Text(tr("Shell and logs", "Kabuk ve kayıtlar"), color = com.umutk.pulse.Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PField(shellCmd, { shellCmd = it }, tr("Command (adb shell …)", "Komut (adb shell …)"), Modifier.weight(1f))
                    PButton(tr("Run", "Çalıştır"), compact = true) { if (shellCmd.isNotBlank() && !busy) job("…") { a -> shellOut = a.shell(shellCmd.trim()).take(6000); "" } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    PButton(tr("Log (logcat)", "Günlük (logcat)"), Modifier.weight(1f), compact = true) { job("…") { a -> shellOut = a.shell("logcat -d -t 300").takeLast(6000); "" } }
                    PButton(tr("Record 30 s", "30 sn kaydet"), Modifier.weight(1f), compact = true) {
                        job(tr("Recording…", "Kaydediliyor…")) { a ->
                            a.shell("screenrecord --time-limit 30 /sdcard/pulse_rec.mp4")
                            val out = File(saveDir(), "watch_${System.currentTimeMillis() / 1000}.mp4")
                            out.writeBytes(a.execBytes("cat /sdcard/pulse_rec.mp4")); a.shell("rm /sdcard/pulse_rec.mp4")
                            tr("Saved: ", "Kaydedildi: ") + out.name
                        }
                    }
                }
                if (shellOut.isNotEmpty()) androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(shellOut, color = T.text, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp))
                }
            }
            PCard {
                Text(tr("Apps on the watch", "Saatteki uygulamalar"), color = com.umutk.pulse.Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                apps.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PIcon("apps", T.sub, 18.dp)
                        Text(p, color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        PButton(tr("Open", "Aç"), compact = true) { job("…") { a -> a.shell("monkey -p $p -c android.intent.category.LAUNCHER 1"); "" } }
                        // the app's APK is copied from the watch to the phone
                        PButton(tr("Pull", "Al"), compact = true) {
                            job("…") { a ->
                                val path = a.shell("pm path $p").lines().firstOrNull { it.startsWith("package:") }?.removePrefix("package:")?.trim().orEmpty()
                                if (path.isEmpty()) tr("Not found", "Bulunamadı") else { val out = File(saveDir(), "$p.apk"); out.writeBytes(a.execBytes("cat $path")); tr("Saved: ", "Kaydedildi: ") + out.name }
                            }
                        }
                        PButton(tr("Disable", "Kapat"), compact = true) { job("…") { a -> a.shell("pm disable-user --user 0 $p").trim() } }
                        PButton(tr("Remove", "Kaldır"), compact = true, danger = true) {
                            job("…") { a -> val r = a.shell("pm uninstall $p").trim(); apps = apps - p; r }
                        }
                    }
                }
                if (apps.isEmpty()) Text(tr("None", "Yok"), color = T.sub, fontSize = 13.sp)
            }
        }
    }
}
