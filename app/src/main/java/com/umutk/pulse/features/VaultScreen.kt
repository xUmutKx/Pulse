package com.umutk.pulse.features

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.PulsePick
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val KINDS = listOf(1, 2, 3, 4, 5)
private fun kindName(t: Int) = when (t) { 1 -> tr("Login", "Giriş"); 2 -> tr("Secure note", "Güvenli not"); 3 -> tr("Card", "Kart"); 4 -> tr("Identity", "Kimlik"); else -> tr("SSH key", "SSH anahtarı") }
private fun kindIcon(t: Int) = when (t) { 1 -> "key"; 2 -> "notes"; 3 -> "coin"; 4 -> "person"; else -> "code" }

/** Names of the stored fields, as shown in an entry. */
private fun fieldLabel(k: String): String = when (k) {
    "holder" -> tr("Card holder", "Kart sahibi"); "brand" -> tr("Brand", "Marka"); "number" -> tr("Number", "Numara"); "expMonth" -> tr("Expiry month", "Son kullanma ayı")
    "expYear" -> tr("Expiry year", "Son kullanma yılı"); "code" -> tr("Security code", "Güvenlik kodu"); "publicKey" -> tr("Public key", "Açık anahtar")
    "privateKey" -> tr("Private key", "Özel anahtar"); "fingerprint" -> tr("Fingerprint", "Parmak izi")
    else -> if (k.startsWith("f:")) k.removePrefix("f:") else k.replaceFirstChar { it.uppercase() }
}
private fun secretField(k: String) = k in setOf("number", "code", "privateKey", "ssn", "passportNumber", "licenseNumber")

/** Copies [text]; the clipboard is emptied again after half a minute. */
private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Pulse", text))
    Handler(Looper.getMainLooper()).postDelayed({ runCatching { if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", "")) } }, 30_000L)
}

/**
 * The password vault: logins, cards, identities, secure notes and SSH keys, the Bitwarden JSON import, and the switch that makes Pulse the phone's
 * autofill service. Opens behind the same screen-lock prompt as the Authenticator when that is on.
 */
@Composable
fun VaultScreen(back: () -> Unit) {
    BackHandler { back() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(!AuthPrefs.biometric(ctx)) }
    LaunchedEffect(Unit) { if (!unlocked) askSystemLock(ctx, { unlocked = true }, {}) }
    if (!unlocked) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            PIcon("lock", T.sub, 40.dp)
            PButton(tr("Unlock", "Kilidi aç"), Modifier.padding(top = 16.dp)) { askSystemLock(ctx, { unlocked = true }, {}) }
        }
        return
    }
    var entries by remember { mutableStateOf<List<VItem>>(emptyList()) }
    var kind by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<VItem?>(null) }
    var editing by remember { mutableStateOf<VItem?>(null) }
    var msg by remember { mutableStateOf("") }
    var rev by remember { mutableStateOf(0) }
    LaunchedEffect(rev) { entries = withContext(Dispatchers.IO) { VaultStore.all(ctx) } }
    val autofillOn = remember(rev) {
        Build.VERSION.SDK_INT >= 26 && runCatching { ctx.getSystemService(android.view.autofill.AutofillManager::class.java)?.hasEnabledAutofillServices() == true }.getOrDefault(false)
    }
    val pick = rememberLauncherForActivityResult(PulsePick.OpenDocument()) { u ->
        if (u != null) scope.launch {
            msg = tr("Importing…", "İçe aktarılıyor…")
            val r = withContext(Dispatchers.IO) {
                runCatching { VaultStore.importBitwarden(ctx, ctx.contentResolver.openInputStream(u)!!.bufferedReader().use { it.readText() }) }
                    .getOrElse { ImportResult(false, "The file could not be read.") }
            }
            msg = if (!r.ok) r.message else tr(
                "Added: ${r.logins} logins, ${r.cards} cards, ${r.identities} identities, ${r.notes} notes, ${r.ssh} SSH keys" + if (r.skipped > 0) " (${r.skipped} skipped)" else "",
                "Eklendi: ${r.logins} giriş, ${r.cards} kart, ${r.identities} kimlik, ${r.notes} not, ${r.ssh} SSH anahtarı" + if (r.skipped > 0) " (${r.skipped} atlandı)" else "",
            )
            rev++
        }
    }
    val counts = KINDS.associateWith { k -> entries.count { it.type == k } }
    val shown = entries.filter { (kind == 0 || it.type == kind) && (query.isBlank() || it.name.contains(query, true) || it.user.contains(query, true) || it.uris.any { u -> u.contains(query, true) }) }
        .sortedBy { it.name.lowercase() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            PCard {
                Row(verticalAlignment = Alignment.CenterVertically) { PIcon("shieldOk", T.accent, 22.dp, Modifier.padding(end = 10.dp)); Text(tr("Autofill", "Otomatik doldurma"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                Text(
                    if (Build.VERSION.SDK_INT < 26) tr("Autofill needs Android 8 or newer.", "Otomatik doldurma Android 8 ve üstünü ister.")
                    else if (autofillOn) tr("An autofill service is on. If it is not Pulse, pick Pulse in the settings: only one can be on at a time.", "Bir otomatik doldurma servisi açık. Pulse değilse ayarlardan Pulse'u seç: aynı anda yalnızca biri açık olur.")
                    else tr("Pulse offers your logins in other apps and in browsers, and offers to save new ones.", "Pulse girişlerini diğer uygulamalarda ve tarayıcılarda önerir, yenilerini kaydetmeyi sorar."),
                    color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp),
                )
                if (Build.VERSION.SDK_INT >= 26) PButton(tr("Use Pulse for autofill", "Pulse ile otomatik doldur")) {
                    val i = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${ctx.packageName}"))
                    runCatching { ctx.startActivity(i) }.onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).clickable { pick.launch(arrayOf("application/json", "text/plain", "*/*")) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("upload", T.accent, 22.dp, Modifier.padding(end = 8.dp)); Text("Bitwarden JSON", color = T.text, fontSize = 14.sp)
                }
                Row(Modifier.weight(1f).clickable { editing = VItem(type = 1) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("add", T.accent, 22.dp, Modifier.padding(end = 8.dp)); Text(tr("New login", "Yeni giriş"), color = T.text, fontSize = 14.sp)
                }
            }
            if (msg.isNotEmpty()) Text(msg, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PChip(kind == 0, tr("All", "Hepsi") + " ${entries.size}") { kind = 0 }
                KINDS.forEach { k -> PChip(kind == k, kindName(k) + " ${counts[k] ?: 0}") { kind = k } }
            }
        }
        item { PField(query, { query = it }, tr("Search", "Ara"), Modifier.fillMaxWidth()) }
        items(shown, key = { it.id }) { it2 ->
            Row(Modifier.fillMaxWidth().clickable { open = it2 }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon(kindIcon(it2.type), T.sub, 22.dp, Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(it2.name.ifBlank { "-" }, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = when (it2.type) { 1 -> it2.user; 3 -> it2.fields["number"]?.takeLast(4)?.let { n -> "•••• $n" }.orEmpty(); else -> it2.folder }
                    if (sub.isNotBlank()) Text(sub, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp)) }
    }

    open?.let { v -> ItemDialog(v, onClose = { open = null }, onEdit = { editing = v; open = null }, onDelete = { VaultStore.delete(ctx, v.id); open = null; rev++ }) }
    editing?.let { v -> EditDialog(v, onClose = { editing = null }) { VaultStore.upsert(ctx, it); editing = null; rev++ } }
}

@Composable
private fun ItemDialog(v: VItem, onClose: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf(setOf<String>()) }
    var sure by remember { mutableStateOf(false) }
    @Composable fun line(label: String, value: String, secret: Boolean, key: String) {
        if (value.isBlank()) return
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = T.sub, fontSize = 11.sp)
                val hide = secret && key !in shown
                Text(if (hide) "••••••••" else value, color = T.text, fontSize = 14.sp, fontFamily = if (secret) FontFamily.Monospace else FontFamily.Default, maxLines = if (key == "privateKey") 6 else 3, overflow = TextOverflow.Ellipsis)
            }
            if (secret) PIcon(if (key in shown) "eyeOff" else "eyeOn", T.accent, 20.dp, Modifier.clickable { shown = if (key in shown) shown - key else shown + key }.padding(8.dp))
            PIcon("copy", T.accent, 20.dp, Modifier.clickable { copy(ctx, value) }.padding(8.dp))
        }
    }
    WinAlert(
        onDismissRequest = onClose,
        title = { Text(v.name.ifBlank { kindName(v.type) }, color = T.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (v.type == 1) { line(tr("User name", "Kullanıcı adı"), v.user, false, "user"); line(tr("Password", "Şifre"), v.pass, true, "pass") }
                v.uris.forEachIndexed { i, u -> line(if (i == 0) tr("Address", "Adres") else "", u, false, "uri$i") }
                v.fields.forEach { (k, f) -> line(fieldLabel(k), f, secretField(k), k) }
                line(tr("Authenticator key", "Doğrulayıcı anahtarı"), v.totp, true, "totp")
                line(tr("Notes", "Notlar"), v.notes, false, "notes")
                if (v.folder.isNotBlank()) line(tr("Folder", "Klasör"), v.folder, false, "folder")
                if (sure) Text(tr("Delete this entry?", "Bu kayıt silinsin mi?"), color = T.text, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { PButton(if (sure) tr("Delete", "Sil") else tr("Edit", "Düzenle"), danger = sure) { if (sure) onDelete() else onEdit() } },
        dismissButton = { Row { PButton(if (sure) tr("Keep", "Kalsın") else tr("Delete", "Sil"), compact = true) { sure = !sure }; PButton(tr("Close", "Kapat"), compact = true, modifier = Modifier.padding(start = 8.dp)) { onClose() } } },
    )
}

@Composable
private fun EditDialog(v: VItem, onClose: () -> Unit, onSave: (VItem) -> Unit) {
    var name by remember { mutableStateOf(v.name) }
    var user by remember { mutableStateOf(v.user) }
    var pass by remember { mutableStateOf(v.pass) }
    var uri by remember { mutableStateOf(v.uris.firstOrNull().orEmpty()) }
    var notes by remember { mutableStateOf(v.notes) }
    WinAlert(
        onDismissRequest = onClose,
        title = { Text(if (v.name.isBlank() && v.pass.isBlank()) tr("New login", "Yeni giriş") else tr("Edit", "Düzenle"), color = T.text) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PField(name, { name = it }, tr("Name", "Ad"), Modifier.fillMaxWidth())
                if (v.type == 1) {
                    PField(user, { user = it }, tr("User name", "Kullanıcı adı"), Modifier.fillMaxWidth())
                    PField(pass, { pass = it }, tr("Password", "Şifre"), Modifier.fillMaxWidth())
                    PField(uri, { uri = it }, tr("Address or app (https://… or androidapp://package)", "Adres ya da uygulama (https://… ya da androidapp://paket)"), Modifier.fillMaxWidth())
                }
                PField(notes, { notes = it }, tr("Notes", "Notlar"), Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            PButton(tr("Save", "Kaydet")) {
                v.name = name.trim(); v.notes = notes
                if (v.type == 1) { v.user = user.trim(); v.pass = pass; v.uris = if (uri.isBlank()) emptyList() else listOf(uri.trim()) }
                onSave(v)
            }
        },
        dismissButton = { PButton(tr("Cancel", "İptal"), onClick = onClose) },
    )
}
