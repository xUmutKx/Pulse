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

private data class Acct(val issuer: String, val name: String, val secret: String, val digits: Int, val period: Int, val algo: String)

/** Accounts as JSON, AES-256-GCM with a key in the Android Keystore. File layout: 12-byte IV, then ciphertext. */
private object AuthStore {
    private const val ALIAS = "pulse_totp"
    private const val FILE = "totp.bin"
    private const val IV_LEN = 12

    private fun key(): SecretKey {
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
        Files.move(tmp.toPath(), File(ctx.filesDir, FILE).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
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

    // a picture from the gallery: its QR code, if it holds an otpauth:// link, goes into the secret field
    val pickQr = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { decodeQr(ctx, uri) }
            if (text != null && text.startsWith("otpauth://", true)) { secret = text; msg = "" }
            else msg = "No authenticator QR code found in that picture"
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PHead("Authenticator", guide = "authenticator")
        PCard {
            Text("Add account", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            PField(issuer, { issuer = it }, "Issuer (e.g. GitHub)")
            PField(name, { name = it }, "Account name")
            PField(secret, { secret = it }, "Base32 secret or otpauth:// URI")
            if (msg.isNotEmpty()) Text(msg, color = Color(0xFFC42B1C), fontSize = 13.sp)
            PButton("Scan QR from gallery") { pickQr.launch("image/*") }
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
