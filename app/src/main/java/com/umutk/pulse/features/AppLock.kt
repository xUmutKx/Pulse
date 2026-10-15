package com.umutk.pulse.features

import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.toArgb
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Base64
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Pattern
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PSwitch
import com.umutk.pulse.PTheme
import com.umutk.pulse.T
import com.umutk.pulse.Themes
import com.umutk.pulse.applock.LockService
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An installed app as the App Lock list shows it. */
class AppRow(val pkg: String, val label: String)

/**
 * App Lock state, rules and storage. Plain SharedPreferences: androidx.security (EncryptedSharedPreferences)
 * is not a dependency of this project, so the lock list is stored unencrypted. The secret itself is only a salted PBKDF2 hash.
 * The state is in memory as well so Compose redraws and the accessibility service read the same values.
 */
object AppLock {
    private const val PREFS = "applock"
    private const val ITER = 100_000   // shortcut: fixed cost, verification runs on the main thread (~0.1-0.3 s)

    /** Looks the overlay can use: the Material default and the Windows looks, by PTheme id. */
    val looks = listOf("material", "amoled", "win7", "xp", "win95", "win10", "win11")

    var locked by mutableStateOf<Set<String>>(emptySet())
    var graceMin by mutableIntStateOf(0)          // 0 = ask every time
    var look by mutableStateOf("material")        // lock screen look: "material" (Material You, default), "follow" (current theme), "win11blur", or a theme id
    var blurDp by mutableIntStateOf(25)           // Windows 11 blur: strength 0..40
    var tintA by mutableIntStateOf(89)            // Windows 11 blur: dark tint 0..255
    @Volatile var rev = 0                         // bumped on every save: the service rebuilds its ready lock screens
    var hasSecret by mutableStateOf(false)
    var secretKind by mutableStateOf("pin")       // "pin", "pass" or "pattern"
    var useBio by mutableStateOf(true)            // fingerprint first, the secret as fallback
    var fast by mutableStateOf(true)              // fast mode: root/Shizuku event-log watcher (used only when one of them is available)
    @Volatile var fails = 0                       // wrong tries in a row
    @Volatile var lockedUntil = 0L                // no tries before this time (every fifth wrong try)
    private var secret: String? = null            // "iterations:saltB64:hashB64"

    private val unlockedAt = HashMap<String, Long>()   // in memory only: relocked on screen off

    private fun sp(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = sp(c)
        locked = p.getStringSet("locked", emptySet())?.toSet() ?: emptySet()
        graceMin = p.getInt("grace", 0)
        look = p.getString("look", "material") ?: "material"
        blurDp = p.getInt("blur", 25); tintA = p.getInt("tint", 89)
        secretKind = p.getString("kind", "pin") ?: "pin"
        useBio = p.getBoolean("bio", true)
        fast = p.getBoolean("fast", true)
        secret = p.getString("secret", null)
        hasSecret = secret != null
    }

    fun save(c: Context) {
        sp(c).edit().putStringSet("locked", HashSet(locked)).putInt("grace", graceMin).putString("look", look)
            .putInt("blur", blurDp).putInt("tint", tintA).putString("kind", secretKind).putString("secret", secret).putBoolean("bio", useBio).putBoolean("fast", fast).apply()
        rev++
    }

    fun setLocked(c: Context, pkg: String, on: Boolean) {
        locked = if (on) locked + pkg else locked - pkg
        save(c)
    }

    fun setGrace(c: Context, min: Int) { graceMin = min; save(c) }

    fun setLook(c: Context, id: String) { look = id; save(c) }

    fun setBio(c: Context, on: Boolean) { useBio = on; save(c) }

    fun setFast(c: Context, on: Boolean) { fast = on; save(c) }

    /** "follow": the theme selected in Pulse; otherwise the look picked in App lock. */
    fun lookTheme(look: String = this.look): PTheme = when (look) {
        "follow" -> com.umutk.pulse.Look.theme
        "win11blur" -> Themes.first { it.id == "win11" }
        else -> Themes.firstOrNull { it.id == look } ?: Themes.first { it.id == "material" }
    }

    /** Stores a new PIN, password or pattern (kind "pin", "pass" or "pattern") as a salted PBKDF2-HMAC-SHA256 hash. */
    fun setSecret(c: Context, kind: String, s: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        secret = "$ITER:${b64(salt)}:${b64(pbkdf2(s, salt, ITER))}"
        secretKind = kind
        hasSecret = true
        save(c)
    }

    fun checkSecret(s: String): Boolean {
        val parts = secret?.split(":") ?: return false
        return runCatching {
            MessageDigest.isEqual(Base64.decode(parts[2], Base64.NO_WRAP),
                pbkdf2(s, Base64.decode(parts[1], Base64.NO_WRAP), parts[0].toInt()))
        }.getOrDefault(false)
    }

    private fun pbkdf2(s: String, salt: ByteArray, iter: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(s.toCharArray(), salt, iter, 256)).encoded

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    fun markUnlocked(pkg: String) { unlockedAt[pkg] = System.currentTimeMillis(); fails = 0 }

    /** The app left the front: with "ask every time" its unlock ends here. */
    fun leave(pkg: String) { if (graceMin == 0) unlockedAt.remove(pkg) }

    /** Screen off: everything is locked again. */
    fun relockAll() { unlockedAt.clear() }

    /** The one place that decides whether opening [pkg] must show the lock. Chat bubbles never get here (LockService.isBubble). */
    fun needsAsk(pkg: String, now: Long): Boolean {
        if (pkg !in locked) return false
        val at = unlockedAt[pkg] ?: return true
        return graceMin > 0 && now - at >= graceMin * 60_000L
    }

    fun label(c: Context, pkg: String): String = try {
        c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    fun installedApps(c: Context): List<AppRow> {
        val pm = c.packageManager
        val all = HashMap<String, AppRow>()
        // installed packages plus everything with a launcher entry (some come only from the latter, e.g. other profiles / hidden-from-list)
        try { pm.getInstalledApplications(android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS).forEach { all[it.packageName] = AppRow(it.packageName, it.loadLabel(pm).toString()) } } catch (_: Exception) {}
        try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).forEach {
                val ai = it.activityInfo.applicationInfo
                all.getOrPut(ai.packageName) { AppRow(ai.packageName, it.loadLabel(pm).toString()) }
            }
        } catch (_: Exception) {}
        all.remove(c.packageName)
        return all.values.sortedBy { fold(it.label) }
    }

    /** Search folding: case, Turkish dotted/dotless i and accents all compare equal. */
    fun fold(s: String): String = java.text.Normalizer.normalize(s.replace('İ', 'i').replace('ı', 'i'), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT)

    /** Usage access (second detection path) and "display over other apps" (overlay fallback). */
    fun usageGranted(c: Context): Boolean = (c.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager)
        .unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName) == android.app.AppOpsManager.MODE_ALLOWED

    fun canDraw(c: Context) = Settings.canDrawOverlays(c)

    /** Whether the accessibility service is switched on in Settings. */
    fun serviceEnabled(c: Context): Boolean {
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(c, LockService::class.java).flattenToString()
        return s.split(':').any { it.equals(me, ignoreCase = true) }
    }
}

/** App Lock settings page: permissions, fallback PIN/password, grace time, look, and the app switches. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppLockPage() {
    val ctx = LocalContext.current
    val t = T
    LaunchedEffect(Unit) { AppLock.load(ctx) }

    // Re-read the permission states when the user comes back from Settings.
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val a11y = remember(tick) { AppLock.serviceEnabled(ctx) }
    val usage = remember(tick) { AppLock.usageGranted(ctx) }
    val draw = remember(tick) { AppLock.canDraw(ctx) }

    val apps by produceState(initialValue = emptyList<AppRow>()) {
        value = withContext(Dispatchers.IO) { AppLock.installedApps(ctx) }
    }
    var query by remember { mutableStateOf("") }
    var pinMode by remember { mutableStateOf("") }   // "" closed, "pin" or "pass"
    var s1 by remember { mutableStateOf("") }
    var s2 by remember { mutableStateOf("") }
    var patternNote by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    var appCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(pageGutter),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("Permissions", "İzinler"), color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StatusRow(tr("Accessibility service", "Erişilebilirlik servisi"), a11y && LockService.instance != null,
                        if (a11y && LockService.instance == null) tr("Listed but not running: switch it off and on", "Listede ama çalışmıyor: kapatıp tekrar açın") else null) {
                        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    StatusRow(tr("Usage access (backup detection)", "Kullanım erişimi (yedek algılama)"), usage) {
                        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    StatusRow(tr("Display over other apps", "Diğer uygulamaların üzerinde göster"), draw) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
        item {
            PCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Lock type", "Kilit türü"), color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val sel = if (pinMode.isNotEmpty()) pinMode else if (AppLock.hasSecret) AppLock.secretKind else ""
                        listOf(Triple("pattern", Icons.Outlined.Pattern, tr("Pattern", "Desen")), Triple("pin", Icons.Outlined.Dialpad, "PIN"),
                            Triple("pass", Icons.Outlined.Password, tr("Password", "Şifre"))).forEach { (k, icon, name) ->
                            LockTile(icon, name, sel == k, Modifier.weight(1f)) { pinMode = k; s1 = ""; s2 = ""; patternNote = "" }
                        }
                        LockTile(Icons.Outlined.Fingerprint, tr("Fingerprint", "Parmak izi"), AppLock.useBio, Modifier.weight(1f)) { AppLock.setBio(ctx, !AppLock.useBio) }
                    }
                    if (pinMode == "pattern") {
                        Text(if (patternNote.isNotEmpty()) patternNote else if (s1.isEmpty()) tr("Draw", "Çiz") else tr("Repeat", "Tekrar"), color = t.sub, fontSize = 12.sp, maxLines = 1)
                        // the second drawing confirms the first: the same one saves at once, a different one starts over
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { c -> com.umutk.pulse.applock.PatternView(c, t.text.toArgb(), com.umutk.pulse.Look.accent.toArgb()) { p ->
                                when {
                                    s1.isEmpty() -> { s1 = p; patternNote = "" }
                                    p == s1 -> { AppLock.setSecret(ctx, "pattern", s1); pinMode = ""; s1 = ""; s2 = ""; patternNote = "" }
                                    else -> { s1 = ""; s2 = ""; patternNote = tr("Not the same", "Aynı değil") }
                                }
                            } },
                            modifier = Modifier.size(220.dp))
                        PButton(tr("Redo", "Yeniden")) { s1 = ""; s2 = ""; patternNote = "" }
                    } else if (pinMode.isNotEmpty()) {
                        val numeric = pinMode == "pin"
                        val kb = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password)
                        OutlinedTextField(value = s1, onValueChange = { s1 = it }, singleLine = true, keyboardOptions = kb,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                            label = { Text(tr("New", "Yeni")) })
                        OutlinedTextField(value = s2, onValueChange = { s2 = it }, singleLine = true, keyboardOptions = kb,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                            label = { Text(tr("Repeat", "Tekrar")) })
                        val valid = s1.length >= 4 && s1 == s2 && (!numeric || s1.all { it.isDigit() })
                        PButton(tr("Save", "Kaydet")) {
                            if (valid) { AppLock.setSecret(ctx, pinMode, s1); pinMode = ""; s1 = ""; s2 = "" }
                        }
                    }
                }
            }
        }
        if (com.umutk.pulse.Shell.root == true || com.umutk.pulse.Shell.shizuku == 2) item {
            PCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Fast mode (root/Shizuku)", "Hızlı mod (root/Shizuku)"), color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    PSwitch(AppLock.fast, { AppLock.setFast(ctx, it) })
                }
            }
        }
        item {
            PCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Ask again after", "Tekrar sor"), color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ChoiceRow(
                        listOf("0" to tr("Every time", "Her seferinde"), "1" to tr("1 min", "1 dk"), "5" to tr("5 min", "5 dk"),
                            "15" to tr("15 min", "15 dk"), "30" to tr("30 min", "30 dk"), "60" to tr("1 h", "1 sa")),
                        selected = AppLock.graceMin.toString(),
                    ) { AppLock.setGrace(ctx, it.toInt()) }
                }
            }
        }
        item {
            PCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Lock screen look", "Kilit ekranı görünümü"), color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // one live preview per look, three to a row so every look is in view; the name sits under each
                    val looks = listOf("material" to "Material You", "follow" to tr("Current theme", "Mevcut tema"), "amoled" to "AMOLED", "win95" to "Windows 95", "xp" to "Windows XP",
                        "win7" to "Windows 7", "win10" to "Windows 10", "win11" to "Windows 11", "win11blur" to "Windows 11 blur")
                    looks.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (id, name) ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    androidx.compose.runtime.key(id, AppLock.blurDp, AppLock.tintA, AppLock.secretKind) {
                                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(10.dp))
                                            .border(if (AppLock.look == id) 3.dp else 1.dp, if (AppLock.look == id) com.umutk.pulse.Look.accent else t.line, RoundedCornerShape(10.dp))) {
                                            androidx.compose.ui.viewinterop.AndroidView(factory = { c -> lockPreview(c, id) }, modifier = Modifier.fillMaxSize())
                                            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().clickable { AppLock.setLook(ctx, id) })
                                        }
                                    }
                                    Text(name, color = if (AppLock.look == id) com.umutk.pulse.Look.accent else t.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (AppLock.look == "win11blur") {
                        Slider(AppLock.blurDp.toFloat(), { AppLock.blurDp = it.toInt() }, valueRange = 0f..40f, onValueChangeFinished = { AppLock.save(ctx) })
                        Slider(AppLock.tintA.toFloat(), { AppLock.tintA = it.toInt() }, valueRange = 0f..255f, onValueChangeFinished = { AppLock.save(ctx) })
                    }
                }
            }
        }
        // the search stays pinned at the top while the apps scroll under it; tapping it brings it to the top at once
        stickyHeader(key = "search") {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().background(t.bg).padding(vertical = 4.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { f ->
                        if (f.isFocused) scope.launch { list.animateScrollToItem((list.layoutInfo.totalItemsCount - appCount - 1).coerceAtLeast(0)) }
                    },
                    label = { Text(tr("Search apps", "Uygulama ara"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = if (query.isNotEmpty()) ({
                        Icon(Icons.Outlined.Close, null, tint = t.sub, modifier = Modifier.clickable { query = "" })
                    }) else null)
            }
        }
        val q = AppLock.fold(query.trim())
        val shown = if (q.isEmpty()) apps else apps.filter { AppLock.fold(it.label).contains(q) || it.pkg.contains(q, ignoreCase = true) }
        appCount = shown.size
        items(shown, key = { it.pkg }) { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                com.umutk.pulse.AppIcon(a.pkg, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.pkg, color = t.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PSwitch(on = a.pkg in AppLock.locked, enabled = AppLock.hasSecret,
                    onChange = { AppLock.setLocked(ctx, a.pkg, it) })
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, note: String? = null, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = T.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(note ?: if (ok) tr("On", "Açık") else tr("Off", "Kapalı"), color = if (ok) T.accent else T.sub, fontSize = 12.sp)
        }
        PButton(if (ok) tr("Settings", "Ayarlar") else tr("Turn on", "Aç"), onClick = onOpen)
    }
}

@Composable
private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, label) ->
            FilterChip(selected = selected == key, onClick = { onPick(key) }, label = { Text(label, maxLines = 1) })
        }
    }
}

/** A lock type choice: its icon over a one-word name, outlined in the accent when picked. */
@Composable
private fun LockTile(icon: ImageVector, name: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(T.radius)
    Column(
        modifier.clip(shape).border(if (on) 2.dp else 1.dp, if (on) com.umutk.pulse.Look.accent else T.line, shape).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, name, tint = if (on) com.umutk.pulse.Look.accent else T.text, modifier = Modifier.size(26.dp))
        Text(name, color = if (on) com.umutk.pulse.Look.accent else T.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A real lock screen of look [id], drawn at phone size and scaled down into the preview box. */
private fun lockPreview(c: Context, id: String): android.view.View {
    val dm = c.resources.displayMetrics
    val w = dm.widthPixels; val h = dm.heightPixels
    val box = android.widget.FrameLayout(c)
    val v = com.umutk.pulse.applock.lockScreen(c, c.packageName, { false }, null, {}, id)
    box.addView(v, android.widget.FrameLayout.LayoutParams(w, h))
    box.addOnLayoutChangeListener { _, l, tp, r, b, _, _, _, _ ->
        v.pivotX = 0f; v.pivotY = 0f
        val sc = minOf((r - l) / w.toFloat(), (b - tp) / h.toFloat()); v.scaleX = sc; v.scaleY = sc
    }
    return box
}
