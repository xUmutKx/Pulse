package com.umutk.pulse.features

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.BarMeter
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PField
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** RFC 4648 base32 decoding and RFC 6238 TOTP. Pure Kotlin, no Android types. */
object Totp {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun base32Decode(s: String): ByteArray {
        val out = ByteArrayOutputStream()
        var buf = 0
        var bits = 0
        for (ch in s.uppercase()) {
            if (ch.isWhitespace() || ch == '=') continue
            val v = ALPHABET.indexOf(ch)
            require(v >= 0) { "Invalid base32 character" }
            buf = (buf shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buf shr bits) and 0xFF)
                buf = buf and ((1 shl bits) - 1)
            }
        }
        require(out.size() > 0) { "Empty secret" }
        return out.toByteArray()
    }

    /** algo is the bare hash name: SHA1, SHA256 or SHA512. */
    fun code(key: ByteArray, time: Long, digits: Int = 6, period: Int = 30, algo: String = "SHA1"): String {
        val mac = Mac.getInstance("Hmac$algo")
        mac.init(SecretKeySpec(key, "Hmac$algo"))
        val h = mac.doFinal(ByteBuffer.allocate(8).putLong(time / period).array())
        val o = h[h.size - 1].toInt() and 0xF
        val bin = ((h[o].toInt() and 0x7F) shl 24) or ((h[o + 1].toInt() and 0xFF) shl 16) or
            ((h[o + 2].toInt() and 0xFF) shl 8) or (h[o + 3].toInt() and 0xFF)
        var mod = 1
        repeat(digits) { mod *= 10 }
        return (bin % mod).toString().padStart(digits, '0')
    }

    /** RFC 6238 SHA-1 vector: secret "12345678901234567890", time 59 s, 8 digits 94287082, so 6 digits 287082. Not run at runtime. */
    fun selfTest(): Boolean = code("12345678901234567890".toByteArray(Charsets.US_ASCII), 59L, 6) == "287082"
}

internal data class Acct(val issuer: String, val name: String, val secret: String, val digits: Int, val period: Int, val algo: String)

/** Accounts as JSON, AES-256-GCM with a key in the Android Keystore. File layout: 12-byte IV, then ciphertext. */
internal object AuthStore {
    private const val ALIAS = "pulse_totp"
    private const val FILE = "totp.bin"
    private const val IV_LEN = 12

    private fun key(): SecretKey {
        // Android 5: no hardware key store for AES, so the key is made from the phone's own build data (private to this app's file anyway)
        if (android.os.Build.VERSION.SDK_INT < 23) return javax.crypto.spec.SecretKeySpec(java.security.MessageDigest.getInstance("SHA-256").digest(("pulse_totp|" + android.os.Build.FINGERPRINT + "|" + android.os.Build.BOARD + "|" + android.os.Build.DEVICE).toByteArray()), "AES")
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** Any bytes, sealed with the phone's own key (it cannot leave the phone). */
    fun sealRaw(b: ByteArray): ByteArray { val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key()); return c.iv + c.doFinal(b) }
    fun openRaw(b: ByteArray): ByteArray { val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, IV_LEN))); return c.doFinal(b.copyOfRange(IV_LEN, b.size)) }

    fun toJson(list: List<Acct>): String {
        val arr = JSONArray()
        list.forEach { a -> arr.put(JSONObject().put("issuer", a.issuer).put("name", a.name).put("secret", a.secret).put("digits", a.digits).put("period", a.period).put("algo", a.algo)) }
        return arr.toString()
    }

    fun fromJson(s: String): List<Acct> {
        val arr = JSONArray(s)
        return List(arr.length()) { i -> val o = arr.getJSONObject(i); Acct(o.getString("issuer"), o.getString("name"), o.getString("secret"), o.getInt("digits"), o.getInt("period"), o.getString("algo")) }
    }

    fun load(ctx: Context): List<Acct> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return emptyList()
        val bytes = f.readBytes()
        require(bytes.size > IV_LEN) { "Stored data is damaged" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_LEN)))
        val arr = JSONArray(String(c.doFinal(bytes.copyOfRange(IV_LEN, bytes.size)), Charsets.UTF_8))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Acct(o.getString("issuer"), o.getString("name"), o.getString("secret"), o.getInt("digits"), o.getInt("period"), o.getString("algo"))
        }
    }

    fun save(ctx: Context, list: List<Acct>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().put("issuer", a.issuer).put("name", a.name).put("secret", a.secret)
                .put("digits", a.digits).put("period", a.period).put("algo", a.algo))
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        check(c.iv.size == IV_LEN) { "Unexpected IV size" }
        val out = c.iv + c.doFinal(arr.toString().toByteArray(Charsets.UTF_8))
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeBytes(out)
        tmp.renameTo(File(ctx.filesDir, FILE))
    }
}

/** The Authenticator's own switches. */
internal object AuthPrefs {
    private fun p(c: Context) = c.getSharedPreferences("auth_settings", Context.MODE_PRIVATE)
    /** The system fingerprint / face / PIN prompt before the codes show. */
    fun biometric(c: Context) = p(c).getBoolean("biometric", false)
    fun setBiometric(c: Context, v: Boolean) = p(c).edit().putBoolean("biometric", v).apply()
    /** The accounts travel in the Pulse backup, sealed with a password of the user's. */
    fun backup(c: Context) = p(c).getBoolean("backup", false)
    fun setBackup(c: Context, v: Boolean) = p(c).edit().putBoolean("backup", v).apply()
}

/**
 * The accounts inside a Pulse backup. The phone's own key cannot move to another phone, so the accounts are sealed with a password instead
 * (PBKDF2 + AES-GCM) into files/auth-backup.enc of the backup zip; a restore leaves that file in place and the Authenticator asks for the password to take them in.
 */
internal object AuthBackup {
    private const val FILE = "auth-backup.enc"
    private const val PASS = "auth-pass.bin"
    private val MAGIC = "PAB1".toByteArray()

    fun hasPassword(c: Context) = File(c.filesDir, PASS).isFile
    fun setPassword(c: Context, pw: String) { File(c.filesDir, PASS).writeBytes(AuthStore.sealRaw(pw.toByteArray())) }
    private fun password(c: Context): String? = runCatching { String(AuthStore.openRaw(File(c.filesDir, PASS).readBytes())) }.getOrNull()
    fun on(c: Context) = AuthPrefs.backup(c) && hasPassword(c)
    fun hasRestore(c: Context) = File(c.filesDir, FILE).isFile
    fun dropRestore(c: Context) { File(c.filesDir, FILE).delete() }

    private fun derive(pw: String, salt: ByteArray) = javax.crypto.spec.SecretKeySpec(
        javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(javax.crypto.spec.PBEKeySpec(pw.toCharArray(), salt, 120_000, 256)).encoded, "AES")

    /** The sealed accounts for the backup zip; null when the backup switch is off, no password is set or there are no accounts. */
    fun seal(c: Context): ByteArray? {
        if (!on(c)) return null
        val pw = password(c) ?: return null
        val list = runCatching { AuthStore.load(c) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, derive(pw, salt)) }
        return MAGIC + salt + cipher.iv + cipher.doFinal(AuthStore.toJson(list).toByteArray())
    }

    /** The accounts of a restored backup, or null when the password is wrong. */
    fun open(c: Context, pw: String): List<Acct>? = runCatching {
        val b = File(c.filesDir, FILE).readBytes()
        require(b.size > 32 && b.copyOfRange(0, 4).contentEquals(MAGIC))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, derive(pw, b.copyOfRange(4, 20)), GCMParameterSpec(128, b.copyOfRange(20, 32))) }
        AuthStore.fromJson(String(cipher.doFinal(b.copyOfRange(32, b.size))))
    }.getOrNull()
}

/** The system prompt (fingerprint, face, or the screen lock when no biometrics are set) before the codes show. Nothing is asked when the phone has no lock at all. */
private fun askSystemLock(ctx: Context, onOk: () -> Unit, onFail: () -> Unit) {
    if (android.os.Build.VERSION.SDK_INT < 30) { onOk(); return }
    val auth = android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val bm = ctx.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
    if (bm == null || bm.canAuthenticate(auth) != android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS) { onOk(); return }
    runCatching { lockPrompt(ctx, auth, onOk, onFail) }.onFailure { onFail() }
}

@androidx.annotation.RequiresApi(30)
private fun lockPrompt(ctx: Context, auth: Int, onOk: () -> Unit, onFail: () -> Unit) {
    android.hardware.biometrics.BiometricPrompt.Builder(ctx).setTitle("Authenticator").setAllowedAuthenticators(auth).build()
        .authenticate(android.os.CancellationSignal(), ctx.mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) { onOk() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) { onFail() }
        })
}

/** The text of the QR code in a picture, or null when there is none. */
private fun decodeQr(ctx: Context, uri: Uri): String? = runCatching {
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val src = com.google.zxing.RGBLuminanceSource(bmp.width, bmp.height, px)
    com.google.zxing.MultiFormatReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(src))).text
}.getOrNull()

private fun parseOtpauth(s: String): Acct? {
    val u = Uri.parse(s)
    if (!u.scheme.equals("otpauth", true) || !u.host.equals("totp", true)) return null
    val secret = u.getQueryParameter("secret") ?: return null
    val label = u.path.orEmpty().trimStart('/')
    val labelIssuer = if (label.contains(':')) label.substringBefore(':') else ""
    return Acct(
        issuer = u.getQueryParameter("issuer") ?: labelIssuer,
        name = label.substringAfter(':', label).trim(),
        secret = secret.uppercase().filter { !it.isWhitespace() && it != '=' },
        digits = u.getQueryParameter("digits")?.toIntOrNull() ?: 6,
        period = u.getQueryParameter("period")?.toIntOrNull() ?: 30,
        algo = (u.getQueryParameter("algorithm") ?: "SHA1").uppercase()
    )
}

private fun validate(a: Acct): String? {
    if (a.issuer.isBlank() && a.name.isBlank()) return "Enter an issuer or an account name"
    if (a.digits !in 6..8) return "Digits must be 6 to 8"
    if (a.period !in 1..300) return "Period must be 1 to 300 seconds"
    if (a.algo !in setOf("SHA1", "SHA256", "SHA512")) return "Unsupported algorithm"
    return try { Totp.base32Decode(a.secret); null } catch (e: IllegalArgumentException) { "Secret is not valid base32" }
}

@Composable
fun AuthenticatorScreen(back: () -> Unit) {
    BackHandler { back() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<Acct>>(emptyList()) }
    var loadErr by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }
    var issuer by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var copiedKey by remember { mutableStateOf<String?>(null) }
    var copiedAt by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    var unlocked by remember { mutableStateOf(!AuthPrefs.biometric(ctx)) }
    var showSettings by remember { mutableStateOf(false) }
    var bioOn by remember { mutableStateOf(AuthPrefs.biometric(ctx)) }
    var bkOn by remember { mutableStateOf(AuthPrefs.backup(ctx)) }
    var bkPass by remember { mutableStateOf("") }
    var hasRestore by remember { mutableStateOf(AuthBackup.hasRestore(ctx)) }
    var restorePass by remember { mutableStateOf("") }
    var setMsg by remember { mutableStateOf("") }
    // the system prompt comes up by itself when the page opens; a cancelled prompt leaves the lock screen with a button to ask again
    LaunchedEffect(Unit) { if (!unlocked) askSystemLock(ctx, { unlocked = true }, {}) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { AuthStore.load(ctx) } }.fold(
            onSuccess = { accounts = it },
            onFailure = { loadErr = "Saved accounts could not be opened: ${it.message}" }
        )
    }
    // Wake at each whole second so the codes change right at the period boundary.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000)
        }
    }
    LaunchedEffect(copiedAt) {
        if (copiedAt != 0L) { delay(1500); copiedKey = null }
    }

    fun save(next: List<Acct>, onOk: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { AuthStore.save(ctx, next) } }
                .onSuccess { accounts = next; onOk() }
                .onFailure { msg = "Could not store the account, the secret was not saved (${it.message})" }
        }
    }

    // a QR code (camera or gallery) with an otpauth:// link fills the form: secret field, issuer and account name
    fun useQr(text: String?) {
        if (text != null && text.startsWith("otpauth://", true)) {
            secret = text; msg = ""
            parseOtpauth(text)?.let { a -> if (issuer.isBlank()) issuer = a.issuer; if (name.isBlank()) name = a.name }
        } else msg = tr("No authenticator QR code found", "Doğrulayıcı QR kodu bulunamadı")
    }
    val pickQr = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { useQr(withContext(Dispatchers.IO) { decodeQr(ctx, uri) }) }
    }
    var scanning by remember { mutableStateOf(false) }
    val askCam = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) scanning = true else msg = tr("Camera permission is needed to scan", "Taramak için kamera izni gerekir") }
    if (scanning) QrScanDialog({ useQr(it) }) { scanning = false }

    if (!unlocked) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            PIcon("lock", T.text, 48.dp)
            Spacer(Modifier.height(16.dp))
            PButton(tr("Unlock", "Kilidi aç")) { askSystemLock(ctx, { unlocked = true }, {}) }
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // as its own app (no bar of Pulse above it) the page carries its own back arrow
        if (com.umutk.pulse.LocalExtraLook.current != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { com.umutk.pulse.PIcon("back", com.umutk.pulse.T.text, 22.dp) }
            Text("Authenticator", color = com.umutk.pulse.T.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
        }
        PHead("Authenticator", guide = "authenticator")
        // the settings button next to the guide's (i)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(Modifier.size(36.dp).clickable { showSettings = !showSettings }, contentAlignment = Alignment.Center) { PIcon("settings", if (showSettings) Look.accent else T.sub, 22.dp) }
        }
        if (hasRestore) PCard {
            Text(tr("Accounts in the restored backup", "Geri yüklenen yedekteki hesaplar"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            PField(restorePass, { restorePass = it }, tr("Backup password", "Yedek parolası"))
            PButton(tr("Bring the accounts in", "Hesapları al")) {
                val got = AuthBackup.open(ctx, restorePass)
                if (got == null) msg = tr("Wrong password, or the file is damaged", "Parola yanlış ya da dosya bozuk")
                else {
                    val have = accounts.map { it.issuer + "|" + it.name + "|" + it.secret }.toSet()
                    save(accounts + got.filter { (it.issuer + "|" + it.name + "|" + it.secret) !in have }) { AuthBackup.dropRestore(ctx); hasRestore = false; restorePass = ""; msg = "" }
                }
            }
            if (msg.isNotEmpty()) Text(msg, color = Color(0xFFC42B1C), fontSize = 13.sp)
        }
        if (showSettings) PCard {
            Text(tr("Settings", "Ayarlar"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Ask for the fingerprint (or the screen lock) to open", "Açmak için parmak izi (ya da ekran kilidi) iste"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                com.umutk.pulse.PSwitch(bioOn, { bioOn = it; AuthPrefs.setBiometric(ctx, it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Put the accounts in the Pulse backup", "Hesapları Pulse yedeğine koy"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                com.umutk.pulse.PSwitch(bkOn, { bkOn = it; AuthPrefs.setBackup(ctx, it) })
            }
            if (bkOn) {
                Text(tr("The accounts are sealed with this password inside the backup; without it they cannot be opened.", "Hesaplar yedeğin içinde bu parolayla şifrelenir; parola olmadan açılamaz."), color = T.sub, fontSize = 12.sp)
                PField(bkPass, { bkPass = it }, if (AuthBackup.hasPassword(ctx)) tr("New backup password (leave empty to keep)", "Yeni yedek parolası (boşsa aynı kalır)") else tr("Backup password", "Yedek parolası"))
                PButton(tr("Save password", "Parolayı kaydet")) {
                    if (bkPass.length < 6) setMsg = tr("At least 6 characters", "En az 6 karakter")
                    else { runCatching { AuthBackup.setPassword(ctx, bkPass) }.onSuccess { bkPass = ""; setMsg = tr("Saved", "Kaydedildi") }.onFailure { setMsg = it.message.orEmpty() } }
                }
                if (setMsg.isNotEmpty()) Text(setMsg, color = T.sub, fontSize = 12.sp)
                if (!AuthBackup.hasPassword(ctx)) Text(tr("No password yet: the accounts stay out of the backup until one is saved.", "Henüz parola yok: kaydedilene kadar hesaplar yedeğe girmez."), color = T.sub, fontSize = 12.sp)
            }
        }
        PCard {
            Text("Add account", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            PField(issuer, { issuer = it }, "Issuer (e.g. GitHub)")
            PField(name, { name = it }, "Account name")
            PField(secret, { secret = it }, "Base32 secret or otpauth:// URI")
            if (msg.isNotEmpty()) Text(msg, color = Color(0xFFC42B1C), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(tr("Scan QR", "QR tara")) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) scanning = true
                    else askCam.launch(android.Manifest.permission.CAMERA)
                }
                PButton(tr("QR from gallery", "Galeriden QR")) { pickQr.launch("image/*") }
            }
            PButton("Add account") {
                val input = secret.trim()
                val a = if (input.startsWith("otpauth://", true)) parseOtpauth(input)
                    else Acct(issuer.trim(), name.trim(), input.uppercase().filter { !it.isWhitespace() && it != '=' }, 6, 30, "SHA1")
                val err = if (a == null) "Not a valid otpauth:// totp URI" else validate(a)
                when {
                    loadErr != null -> msg = "Saved accounts could not be opened, adding is disabled"
                    err != null -> msg = err
                    a != null -> save(accounts + a) { issuer = ""; name = ""; secret = ""; msg = "" }
                }
            }
        }
        PCard {
            Text("Codes", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            loadErr?.let { Text(it, color = Color(0xFFC42B1C), fontSize = 13.sp) }
            if (accounts.isEmpty() && loadErr == null) Text("No accounts yet", color = T.sub, fontSize = 13.sp)
            val t = now / 1000
            accounts.forEach { a ->
                val k = a.issuer + "|" + a.name + "|" + a.secret
                val code = runCatching { Totp.code(Totp.base32Decode(a.secret), t, a.digits, a.period, a.algo) }.getOrDefault("-".repeat(a.digits))
                val left = a.period - (t % a.period).toInt()
                Row(
                    Modifier.fillMaxWidth().clickable {
                        (ctx.getSystemService(ClipboardManager::class.java))?.setPrimaryClip(ClipData.newPlainText("code", code))
                        copiedKey = k
                        copiedAt = System.currentTimeMillis()
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(a.issuer.ifBlank { "(no issuer)" }, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(a.name, color = T.sub, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(code.chunked(3).joinToString(" "), color = T.text, fontSize = 28.sp, fontFamily = FontFamily.Monospace)
                            if (copiedKey == k) { Spacer(Modifier.width(10.dp)); Text("Copied", color = Look.accent, fontSize = 12.sp) }
                        }
                        BarMeter(left.toFloat() / a.period, Look.accent, Modifier.padding(top = 2.dp), h = 4.dp)
                    }
                    Box(Modifier.size(40.dp).clickable { save(accounts - a) {} }, contentAlignment = Alignment.Center) {
                        PIcon("delete", T.sub, 18.dp)
                    }
                }
            }
        }
    }
}
