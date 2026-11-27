package com.umutk.pulse.extras.keyboard

import androidx.core.content.ContextCompat
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.umutk.pulse.features.StandByOwner
import kotlinx.coroutines.CoroutineScope
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The Pulse keyboard. It types into the field with a composing word (so a typo can be corrected when the word ends and the correction taken back with backspace),
 * keeps the suggestions of the bar up to date, and lets the settings page (KeyboardSettings) change how it looks and behaves.
 */
class PulseKeyboardService : InputMethodService(), KbActions {
    private val st = KbState()
    private var owner: StandByOwner? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val word = StringBuilder()
    /** The text before the open word, read with every key, for the neural model. */
    @Volatile private var lmCtx = ""
    /** The last word a held delete key took away, and when: the next word typed in its place teaches that it was a slip. */
    private var deletedWord: String? = null
    private var deletedAt = 0L
    private fun slipDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length]
    }
    private fun learnFromDelete(typed: String) {
        val gone = deletedWord ?: return
        deletedWord = null
        if (SystemClock.uptimeMillis() - deletedAt > 30_000L || !KeyboardPrefs.learn || st.plain || st.incognito) return
        val now = typed.lowercase(lang.locale)
        if (now == gone || now.length < 3) return
        if (slipDistance(gone, now) <= maxOf(2, now.length / 3)) KeyboardDictionary.learnTypo(this, gone, now)
    }

    /** The last key was a sentence end (full stop behind a known word, ! or ?): the next letter gets a space and a capital. */
    private var dotEnd = false
    private var prevWord: String? = null
    private var prevWord2: String? = null
    private val touchPts = ArrayList<androidx.compose.ui.geometry.Offset?>()
    private var emailField = false
    private var nameField = false
    /** An address bar (Chrome's is one): search words get suggestions and corrections, an address (dots, slashes) is left alone. */
    private var uriField = false
    private fun urlNow(): Boolean {
        // (the text before the cursor already holds the open word: adding it again made "GünaydınGünaydın", whose "nG" looked like code, so a capitalised first word of a message got no suggestions at all)
        val before = currentInputConnection?.getTextBeforeCursor(60, 0)?.toString().orEmpty()
        val b = if (word.isNotEmpty() && before.endsWith(word)) before else before + word
        if (!uriField) return linkOrCode(b.takeLastWhile { !it.isWhitespace() })
        return b.any { it == '.' || it == '/' || it == ':' || it == '@' } && !b.contains(' ')
    }

    // what is being typed is a link, a file name, a handle or a bit of code: nothing in it is mended, split or suggested, in any field
    private val reDomain = Regex("^[\\p{L}\\d_-]{2,}(\\.[\\p{L}\\d_-]{2,})+")
    private val reCodeBits = Regex("[\\p{L}\\d]_[\\p{L}\\d]|[\\p{L}\\d]/[\\p{L}\\d]|\\p{Ll}\\p{Lu}|[\\p{L}\\d][=(\\[{<>\\\\]|\\p{L}\\d|\\d\\p{L}")
    private fun linkOrCode(token: String): Boolean {
        // the sentence's own marks at the end ("see example.com.") are not part of it
        val t = token.trimEnd('.', ',', '!', '?', ';', ':')
        if (t.length < 3) return false
        return t.contains("://") || t.startsWith("www.", true) || t.startsWith("@") || t.startsWith("#") || t.startsWith("$") || t.startsWith("--") ||
            reDomain.containsMatchIn(t) || reCodeBits.containsMatchIn(t)
    }
    private var noAuto = false
    // the last text copied: the right slot of the bar offers it for two pastes
    private var quickClip: String? = null
    /** Words corrected in the last minutes (what was typed, when): typed again, they are learned instead of corrected twice. */
    private val recentFixed = LinkedHashMap<String, Long>()
    /** The rhythm of the word being typed (WM Keyboard's KeystrokeTiming): slow, careful letters are a word meant as it is. */
    private val rhythm = KeystrokeTiming()

    /**
     * WM Keyboard's adaptive confidence, simply: corrections made and corrections undone are counted (halved past 200, so old habits fade);
     * when more than one in ten is undone, the hybrid correction makes only one-letter fixes until the rate falls again.
     */
    private fun hybridCount(made: Boolean) {
        val sp = getSharedPreferences("pulse_kb_hybrid", MODE_PRIVATE)
        var m = sp.getInt("made", 0); var u = sp.getInt("undone", 0)
        if (made) m++ else u++
        if (m > 200) { m /= 2; u /= 2 }
        sp.edit().putInt("made", m).putInt("undone", u).apply()
    }
    private fun hybridStrict(): Boolean { val sp = getSharedPreferences("pulse_kb_hybrid", MODE_PRIVATE); val m = sp.getInt("made", 0); return m >= 20 && sp.getInt("undone", 0) * 10 > m }
    private fun editDistance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) { var prev = d[0]; d[0] = i; for (j in 1..b.length) { val tmp = d[j]; d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1); prev = tmp } }
        return d[b.length]
    }
    private var clipRetry = false
    private var quickAt = 0L
    private var quickLeft = 0
    private var quickPinned = false
    // the copied text leaves the bar after two minutes even when nothing is typed: the bar is refreshed then (it only refreshed on a key before)
    private val quickExpiry = android.os.Handler(android.os.Looper.getMainLooper())
    private val quickExpire = Runnable { if (quickClip != null && !quickPinned && SystemClock.uptimeMillis() - quickAt >= 120_000L) { quickClip = null; updateSuggestions() } }
    private var lastEmail: String? = null
    private class Correction(val original: String, val corrected: String, val sep: String)
    private var lastCorrection: Correction? = null
    /**
     * A suggestion the person tapped for a typo ([original]), which they may still be correcting: a pick that was wrong is replaced by a nearer word, then by the right one.
     * The typo means the last word of that chain, and the picks on the way were not right for it.
     */
    private class Pick(val original: String, val chosen: String, val at: Long)
    private var pick: Pick? = null

    /** [from], a word that came from a tapped suggestion, was just replaced by [to]: the first typo of the chain means [to] now, and [from] is no answer to it. */
    private fun learnChain(from: String, to: String) {
        val pk = pick ?: return
        if (pk.chosen != from || SystemClock.uptimeMillis() - pk.at > 60_000L || pk.original == to || to.length < 3) return
        if (!KeyboardPrefs.learn || st.plain || st.incognito) return
        KeyboardDictionary.reject(this, pk.original, from)
        KeyboardDictionary.learnTypo(this, pk.original, to, force = true)
    }
    // the space that came by itself behind a comma: Backspace takes it away with the comma
    private var commaSpace = false
    /** The word as it stood before the user started deleting letters of it: if the corrected form ends as a real word, the first form was a typo. */
    private var peak: String? = null
    // a letter was typed right behind "word." with no space: the space and a capital are put in when the word ends, unless it is an address
    private var dotFix = false
    private val siteNames = setOf("google", "youtube", "facebook", "instagram", "twitter", "github", "gmail", "wikipedia", "amazon", "netflix", "whatsapp", "reddit", "linkedin", "yahoo", "bing", "spotify", "twitch", "tiktok", "pinterest", "microsoft", "apple", "discord", "telegram", "hepsiburada", "trendyol", "sahibinden", "yemeksepeti", "ciceksepeti", "gittigidiyor", "mynet", "hurriyet", "milliyet", "sabah", "haberturk", "sozcu", "cnn", "bbc", "ebay", "paypal", "dropbox", "medium", "stackoverflow", "gitlab", "openai", "anthropic", "claude", "chatgpt", "yandex", "outlook", "hotmail", "icloud", "zoom", "steam", "epicgames", "roblox", "minecraft", "xbox", "playstation", "nintendo", "samsung", "huawei", "xiaomi", "turkcell", "vodafone", "turktelekom", "ptt", "edevlet", "meb", "osym", "ais", "ziraat", "isbank", "garanti", "akbank", "yapikredi", "enpara", "papara", "booking", "airbnb", "tripadvisor", "uber", "getir", "n11")
    private val tlds = setOf("com", "net", "org", "edu", "gov", "io", "co", "me", "app", "dev", "info", "biz", "tr", "uk", "de", "fr", "es", "it", "ru", "us", "tv", "xyz", "online", "site", "tech", "store", "ai", "gg", "ly", "to", "cc", "eu", "nl", "pl", "jp", "cn", "br", "in", "au", "ca", "ch", "se", "no", "fi", "dk", "gen", "web", "html", "php", "py", "kt", "js", "ts", "txt", "md", "json", "xml", "css", "java", "cpp", "sh", "yml", "csv", "pdf", "png", "jpg", "zip", "apk", "exe", "mp3", "mp4", "doc", "docx", "xls", "xlsx", "gz", "tar", "rs", "go", "rb", "sql", "log", "ini", "cfg", "bat", "svg", "gif", "webp", "ttf", "iso")
    private var corrAt = 0L
    private var selStart = 0
    private var selEnd = 0
    private var spaceTap = 0L
    private var lastWasSpace = false
    private var punctTyped = false
    private var slotWords = arrayOf("", "", "")
    private var target: String? = null
    /** The typed word [target] was worked out for: a correction is only made when the word is still that one. */
    private var targetFor = ""
    private var anchor = 0
    private var lastGlide: String? = null
    private var glideAlts: List<String> = emptyList()
    private var clipboard: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { takeClip(false) }
    /** The clip last taken (its time and text), so the check on opening does not take the same one twice. */
    private var lastClipSig = ""

    /**
     * The newest copy into the keyboard's clipboard and the bar. The system does not always tell the keyboard about a copy (Circle to Search, Google Lens and
     * some overlays copy while no field is open), so the clipboard is also looked at whenever the keyboard opens: a copy of the last two minutes that was missed is taken then.
     */
    private fun takeClip(onOpen: Boolean) {
        // everything copied is kept (a password field's copy and a sensitive one are not), texts and pictures
        val clip = runCatching { clipboard?.primaryClip }.getOrNull()?.takeIf { it.itemCount > 0 }
            // a very long copy can fail to come over at the first try: once more half a second later
            ?: run { if (!clipRetry) { clipRetry = true; android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ clipRetry = false; takeClip(onOpen) }, 500L) }; return }
        if (st.incognito || st.plain) return
        if (android.os.Build.VERSION.SDK_INT >= 24 && clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return
        val stamp = if (android.os.Build.VERSION.SDK_INT >= 26) clip.description.timestamp else 0L
        val sig = "$stamp|" + (clip.getItemAt(0).text?.toString()?.take(200) ?: clip.getItemAt(0).uri?.toString().orEmpty())
        if (sig == lastClipSig) return
        if (onOpen && (stamp == 0L || android.os.SystemClock.elapsedRealtime() - stamp > 120_000)) {
            lastClipSig = sig
            // an older copy the system never told the keyboard about still goes into the clipboard list (not the bar), unless it is there already
            val old = clip.getItemAt(0).takeIf { it.uri == null }?.text?.toString()?.trim().orEmpty()
            if (old.isNotEmpty()) scope.launch(Dispatchers.IO) {
                if (ClipStore.load(this@PulseKeyboardService).none { it.image == null && it.text == old }) {
                    val l = ClipStore.addText(this@PulseKeyboardService, old)
                    withContext(Dispatchers.Main) { st.clipItems = l }
                }
            }
            return
        }
        lastClipSig = sig
        val item = clip.getItemAt(0)
        val uri = item.uri
        // an e-mail address that was copied is remembered, so its first letters offer it in any field
        runCatching {
            val copied = item.text?.toString()?.trim().orEmpty()
            if (copied.length in 6..80 && emailRe.matches(copied)) {
                val low = copied.lowercase()
                getSharedPreferences("pulse_kb_emails", MODE_PRIVATE).let { sp -> sp.edit().putString("list", (listOf(low) + emailsSaved().filter { it != low }).take(8).joinToString("\n")).apply() }
            }
        }
        if (uri != null && clip.description.hasMimeType("image/*")) {
            scope.launch(Dispatchers.IO) {
                val l = ClipStore.addImage(this@PulseKeyboardService, uri)
                // a screenshot or a picture just copied takes the bar's right slot from an older copied text (a phone that puts its screenshot on the clipboard showed the old text)
                val thumb = runCatching { contentResolver.openInputStream(uri)?.use { ins -> android.graphics.BitmapFactory.decodeStream(ins, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap() } }.getOrNull()
                withContext(Dispatchers.Main) {
                    st.clipItems = l
                    val newest = l.firstOrNull { it.image != null }
                    if (newest != null && thumb != null) {
                        quickClip = null; shotVideo = null; st.shotVideo = false; st.shotFile = ""; shotItem = newest
                        shotAt = SystemClock.uptimeMillis(); shotPinned = false; st.quickPinned = false; st.shotThumb = thumb
                        shotExpiry.removeCallbacks(shotExpire); shotExpiry.postDelayed(shotExpire, 15_000L)
                        updateSuggestions()
                    }
                }
            }
        } else if (uri != null && !clip.description.hasMimeType("text/*")) {
            // a copied file (music, video, an APK, a document): it waits in the bar's right slot to be pasted into a chat, from a copy in the app's cache (not saved anywhere else)
            val mime = clip.description.getMimeType(0) ?: "application/octet-stream"
            scope.launch(Dispatchers.IO) {
                val f = runCatching {
                    val name = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "file"
                    val dir = java.io.File(cacheDir, "clipfile").apply { deleteRecursively(); mkdirs() }
                    java.io.File(dir, name.replace(Regex("[\\\\/:*?\"<>|]"), "_")).also { out -> contentResolver.openInputStream(uri)?.use { ins -> out.outputStream().use { ins.copyTo(it) } } }
                }.getOrNull()?.takeIf { it.isFile && it.length() > 0 }
                if (f != null) withContext(Dispatchers.Main) {
                    quickClip = null; shotItem = null; shotVideo = f; shotVideoMime = mime
                    shotAt = SystemClock.uptimeMillis(); shotPinned = false; st.quickPinned = false
                    st.shotThumb = null; st.shotVideo = false; st.shotFile = f.extension.uppercase().take(4).ifEmpty { "FILE" }; st.shotFileName = f.name
                    shotExpiry.removeCallbacks(shotExpire); shotExpiry.postDelayed(shotExpire, 60_000L)
                    updateSuggestions()
                }
            }
        } else {
            // a very long copy can come as a text file behind an address instead of inline: it is read from there (up to 2 MB)
            val t0 = (runCatching { item.coerceToText(this)?.toString() }.getOrNull()?.takeIf { uri == null || it != uri.toString() }
                ?: uri?.let { u -> runCatching { contentResolver.openInputStream(u)?.use { ins -> String(ins.readBytes().let { b -> if (b.size > 2_000_000) b.copyOf(2_000_000) else b }) } }.getOrNull() })?.trim()
            // a long copy used to skip the quick-paste slot entirely (nothing showed); it belongs there too, the bar label is cut short regardless of the length
            // the newest copy wins the slot: a copied text takes it from a screenshot that is still shown
            // a link copied with trackers (utm_…, fbclid, gclid, si, igshid …): the bar offers it clean, and the clipboard gets the clean one too
            val t = t0?.let { KbLinks.clean(it) }?.also { c -> if (c != t0) { lastClipSig = "clean|" + c.take(200); runCatching { clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("link", c)) } } } ?: t0
            if (!t.isNullOrEmpty()) { shotItem = null; shotVideo = null; st.shotVideo = false; st.shotFile = ""; st.shotThumb = null; quickClip = t; quickAt = SystemClock.uptimeMillis(); quickLeft = 1; quickPinned = false; st.quickPinned = false; quickExpiry.removeCallbacks(quickExpire); quickExpiry.postDelayed(quickExpire, 120_000L); updateSuggestions()
                // the clipboard list is written off the main thread: a long copy (a page, a log) took seconds to save there, or failed, and the bar's quick paste never showed
                scope.launch(Dispatchers.IO) { runCatching { ClipStore.addText(this@PulseKeyboardService, t) }.getOrNull()?.let { l -> withContext(Dispatchers.Main) { st.clipItems = l } } }
                if (t.length <= 600) st.clips = (listOf(t) + st.clips.filter { it != t }).take(20) }
        }
    }

    override fun clipShortcut(id: Long) { st.clipNameFor = id; st.clipName = "" }
    override fun clipShortcutCancel() { st.clipName = null }
    override fun clipShortcutSave() {
        val name = st.clipName?.trim()?.lowercase(lang.locale).orEmpty()
        val item = st.clipItems.firstOrNull { it.id == st.clipNameFor }
        st.clipName = null
        if (name.isEmpty() || item == null || item.text.isBlank()) return
        KeyShortcuts.put(this, name, item.text)
        android.widget.Toast.makeText(this, com.umutk.pulse.tr("Shortcut saved: $name", "Kısayol kaydedildi: $name"), android.widget.Toast.LENGTH_SHORT).show()
    }
    /** The pin of the bar's right slot: the item shown there is pinned in the clipboard and stays in the bar until a newer copy comes. */
    override fun quickPin() {
        if (shotVideo != null) { shotPinned = true; st.quickPinned = true; return }
        val s = shotItem
        if (s != null) { shotPinned = true; st.clipItems = ClipStore.togglePin(this, s.id); st.quickPinned = true; return }
        val q = quickClip ?: return
        quickPinned = true
        val it = ClipStore.load(this).firstOrNull { it.image == null && it.text == q }
        if (it != null && !it.pinned) st.clipItems = ClipStore.togglePin(this, it.id)
        st.quickPinned = true
    }
    override fun clipPin(id: Long) { st.clipItems = ClipStore.togglePin(this, id) }
    override fun clipDelete(ids: Set<Long>) {
        val gone = st.clipItems.filter { it.id in ids }
        st.clipItems = ClipStore.delete(this, ids)
        // an item deleted from the clipboard leaves the right slot of the bar at once
        if (shotItem?.id in ids) { shotItem = null; st.shotThumb = null }
        val q = quickClip
        if (q != null && gone.any { it.image == null && it.text == q }) quickClip = null
        updateSuggestions()
    }
    override fun clipClear() {
        val before = st.clipItems.filter { !it.pinned }
        st.clipItems = ClipStore.clear(this)
        if (shotItem != null && before.any { it.id == shotItem?.id }) { shotItem = null; st.shotThumb = null }
        val q = quickClip
        if (q != null && before.any { it.image == null && it.text == q }) quickClip = null
        updateSuggestions()
    }

    /** A kept item into the field: a text is typed, a picture is handed over as content when the field takes pictures. */
    override fun gif(f: java.io.File) { pasteVideo(f, "image/gif") }
    override fun sticker(f: java.io.File) { pasteVideo(f, java.net.URLConnection.guessContentTypeFromName(f.name) ?: "image/png") }

    override fun authPick(code: String) {
        currentInputConnection?.commitText(code, 1)
        st.panel = "keys"
        afterText()
    }

    /** Quick auth: the fingerprint first when the setting asks for it, then the codes of the accounts (computed now). */
    private fun openAuth(then: () -> Unit = { showAuth() }) {
        if (!KeyboardPrefs.authLock || android.os.Build.VERSION.SDK_INT < 28) { then(); return }
        val cancel = android.os.CancellationSignal()
        val prompt = android.hardware.biometrics.BiometricPrompt.Builder(this)
            .setTitle(com.umutk.pulse.tr("Quick auth", "Hızlı kimlik doğrulama"))
            .setDescription(com.umutk.pulse.tr("Confirm it is you to see the codes", "Kodları görmek için kimliğini doğrula"))
            .setNegativeButton(com.umutk.pulse.tr("Cancel", "İptal"), mainExecutor) { _, _ -> cancel.cancel() }
            .build()
        prompt.authenticate(cancel, mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) { then() }
        })
    }

    private fun showAuth() {
        refreshAuth()
        st.panel = "auth"
    }

    /** The codes of every account for the current time, with the period of each (a code changes when its period ends). */
    private fun refreshAuth() {
        val now = System.currentTimeMillis() / 1000L
        val accounts = runCatching { com.umutk.pulse.features.AuthStore.load(this) }.getOrDefault(emptyList())
        st.authCodes = accounts.map { a ->
            val name = if (a.issuer.isBlank()) a.name else "${a.issuer} · ${a.name}"
            name to runCatching { com.umutk.pulse.features.Totp.code(com.umutk.pulse.features.Totp.base32Decode(a.secret), now, a.digits, a.period, a.algo) }.getOrDefault("------")
        }
        st.authPeriods = accounts.map { it.period }
    }

    private var vaultShown: List<com.umutk.pulse.features.VItem> = emptyList()
    private fun showVault() {
        st.vaultQuery = ""
        refreshVault()
        st.panel = "vault"
    }
    /** The logins of the vault that fit the search; the ones that fit the app in front come first. */
    private fun refreshVault() {
        val pkg = currentInputEditorInfo?.packageName
        val fit = runCatching { com.umutk.pulse.features.VaultStore.matches(this, pkg, null).map { it.id }.toSet() }.getOrDefault(emptySet())
        val q = st.vaultQuery.trim().lowercase()
        vaultShown = runCatching { com.umutk.pulse.features.VaultStore.all(this) }.getOrDefault(emptyList())
            .filter { it.type == 1 && (q.isEmpty() || it.name.lowercase().contains(q) || it.user.lowercase().contains(q) || it.uris.any { u -> u.lowercase().contains(q) }) }
            .sortedWith(compareBy({ it.id !in fit }, { it.name.lowercase() }))
        st.vaultRows = vaultShown.map { Triple(it.name.ifBlank { it.user }, it.user, it.id in fit) }
    }
    override fun vaultPick(i: Int, other: Boolean) {
        val item = vaultShown.getOrNull(i) ?: return
        val text = if (passwordField != other) item.pass else item.user
        currentInputConnection?.commitText(text, 1)
        st.panel = "keys"; st.vaultQuery = ""
        afterText()
    }

    override fun authTick() { if (st.panel == "auth") refreshAuth() }

    /** A field that does not say it takes pictures may still accept the system's paste: the file goes on the phone's clipboard as an address and the field is asked to paste it. */
    private fun pasteViaClipboard(f: java.io.File, mime: String): Boolean = runCatching {
        val u = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", f)
        clipboard?.setPrimaryClip(android.content.ClipData.newUri(contentResolver, f.name, u).also { lastClipSig = "paste|" + f.name })
        currentInputConnection?.performContextMenuAction(android.R.id.paste) == true
    }.getOrDefault(false)

    override fun pasteClip(item: ClipItem) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        val path = item.image
        // a text goes in and is also the copied text again (the phone's clipboard), so it can be pasted from anywhere
        if (path == null) {
            c.commitText(item.text, 1); clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("", item.text))
            // the paste put the text on the phone's clipboard again: it is marked as seen and leaves the bar, so it is not offered a second time
            runCatching {
                val cp = clipboard?.primaryClip
                if (cp != null && cp.itemCount > 0) lastClipSig = "${if (android.os.Build.VERSION.SDK_INT >= 26) cp.description.timestamp else 0L}|" + (cp.getItemAt(0).text?.toString()?.take(200) ?: cp.getItemAt(0).uri?.toString().orEmpty())
            }
            quickClip = null; st.panel = "keys"; afterText(); updateSuggestions(); return
        }
        val info = currentInputEditorInfo
        val types = if (info != null) androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(info) else emptyArray()
        val ok = types.any { it.startsWith("image/") || it == "*/*" }
        if (!ok) {
            if (pasteViaClipboard(java.io.File(path), "image/*")) { st.panel = "keys"; return }
            android.widget.Toast.makeText(this, com.umutk.pulse.tr("This field takes no pictures", "Bu alan resim kabul etmiyor"), android.widget.Toast.LENGTH_SHORT).show(); return
        }
        runCatching {
            val f = java.io.File(path)
            val u = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", f)
            val mime = if (f.inputStream().use { it.read() } == 0x89) "image/png" else "image/jpeg"
            val content = androidx.core.view.inputmethod.InputContentInfoCompat(u, android.content.ClipDescription("clip", arrayOf(mime)), null)
            androidx.core.view.inputmethod.InputConnectionCompat.commitContent(c, info!!, content, androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
            st.panel = "keys"
        }
    }

    private val lang get() = KeyboardLayouts.lang(st.lang)
    private var seenPrimary = ""

    /** The word lists used together: the layout's own, then those of the other languages in use (a Turkish keyboard that also corrects English). */
    private fun dictCodes(code: String): List<String> {
        val all = (listOf(KeyboardLayouts.dict(code)) + KeyboardPrefs.langList().map { KeyboardLayouts.dict(it) }).distinct()
        // the language of the sentence under way goes first (see detectLang)
        val c = ctxLang
        return if (c != null && c in all) listOf(c) + all.filter { it != c } else all
    }

    /** The language of the sentence being written, from its last words; null when it is mixed or too short to tell (then every language of the keyboard counts alike). */
    @Volatile private var ctxLang: String? = null

    /**
     * One English word does not turn a Turkish sentence English: the words of the sentence so far vote, each word that belongs to exactly one of the keyboard's
     * languages counts for it, and a language leads only with at least two such words and twice the votes of the other. The word being typed is not counted.
     */
    private fun detectLang() {
        val langs = KeyboardPrefs.langList().map { KeyboardLayouts.dict(it) }.distinct()
        if (!KeyboardPrefs.autoLang || langs.size < 2) { ctxLang = null; return }
        val tb = currentInputConnection?.getTextBeforeCursor(140, 0)?.toString().orEmpty()
        val sentence = tb.takeLastWhile { it !in ".!?\n" }
        val ws = sentence.split(Regex("[^\\p{L}']+")).filter { it.length >= 3 }.let { if (word.isNotEmpty()) it.dropLast(1) else it }.takeLast(6)
        val votes = HashMap<String, Int>()
        ws.forEach { w -> val low = w.lowercase(lang.locale); val hits = langs.filter { KeyboardDictionary.isWord(it, low) }; if (hits.size == 1) votes[hits[0]] = (votes[hits[0]] ?: 0) + 1 }
        val sorted = votes.byValueDesc()
        val top = sorted.firstOrNull(); val second = sorted.getOrNull(1)?.value ?: 0
        ctxLang = if (top != null && top.value >= 2 && top.value >= 2 * second) top.key else null
    }

    companion object {
        /** The keyboard running now, for the quick settings tile (null while none is). */
        @Volatile var live: PulseKeyboardService? = null
    }

    /** The floating look was switched on or off from outside (the tile): the window takes the new flags and touch area, the keyboard is drawn again. */
    fun floatChanged() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            applyFloatWindow()
            if (!KeyboardPrefs.floatNow) floatWasUp = isInputViewShown
            st.refreshInsets?.invoke()
            kbRoot?.requestLayout()
        }
    }

    /** The floating keyboard's tall window must not swallow touches outside its keys: the app behind stays usable. */
    private fun applyFloatWindow() {
        window.window?.let { w ->
            val f = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            if (KeyboardPrefs.floatNow) w.addFlags(f) else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        }
    }

    // Works before unlock (as WM Keyboard does): until the first unlock the phone's own storage is locked, so the keyboard reads a copy of its settings kept in the
    // device storage; once unlocked it uses its normal data and refreshes that copy.
    private fun locked() = android.os.Build.VERSION.SDK_INT >= 24 && runCatching { !(getSystemService(Context.USER_SERVICE) as android.os.UserManager).isUserUnlocked }.getOrDefault(false)
    private val deCtx by lazy { if (android.os.Build.VERSION.SDK_INT >= 24) createDeviceProtectedStorageContext() else this }
    override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences =
        if (locked()) deCtx.getSharedPreferences(name, mode) else super.getSharedPreferences(name, mode)
    override fun getFilesDir(): java.io.File = if (locked()) deCtx.filesDir else super.getFilesDir()
    private fun mirrorPrefs() {
        if (android.os.Build.VERSION.SDK_INT < 24 || locked()) return
        runCatching {
            for (name in listOf(KeyboardPrefs.FILE, "pulse_keyboard")) {
                val ed = deCtx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                for ((k, v) in super.getSharedPreferences(name, Context.MODE_PRIVATE).all) when (v) {
                    is Boolean -> ed.putBoolean(k, v); is Int -> ed.putInt(k, v); is Long -> ed.putLong(k, v); is Float -> ed.putFloat(k, v)
                    is String -> ed.putString(k, v); is Set<*> -> ed.putStringSet(k, v.map { it.toString() }.toSet())
                }
                ed.apply()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // the person's own touch sound and key font, read once
        runCatching { KeyboardEffects.loadOwn(this); KbFont.load(this) }
        live = this
        mirrorPrefs()
        // started before the unlock: at the unlock the learned words and the normal settings are read again
        if (locked()) runCatching {
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    runCatching { unregisterReceiver(this) }
                    KeyboardPrefs.loaded = false; KeyboardPrefs.load(this@PulseKeyboardService)
                    runCatching { KeyboardDictionary.reloadUser(this@PulseKeyboardService) }
                    mirrorPrefs()
                }
            }, android.content.IntentFilter(Intent.ACTION_USER_UNLOCKED))
        }
        runCatching { (getSystemService(Context.INPUT_SERVICE) as android.hardware.input.InputManager).registerInputDeviceListener(deviceListener, null) }
        KeyboardPrefs.load(this)
        // the tile's floating keyboard lasts across restarts of the keyboard until the tile is tapped again
        KeyboardPrefs.floatSession = floatTileOn(this)
        owner = StandByOwner().also { it.start() }
        st.lang = KeyboardPrefs.langList().first()
        scope.launch(Dispatchers.IO) { runCatching { EmojiData.index(this@PulseKeyboardService) } }
        // the vault is read once off the main thread, so the first login box has its suggestions at once
        scope.launch(Dispatchers.IO) { runCatching { com.umutk.pulse.features.VaultStore.all(this@PulseKeyboardService) } }
        st.recents = getSharedPreferences("pulse_keyboard", Context.MODE_PRIVATE).getString("recents", "")?.let { r -> if ('\n' in r) r.split('\n') else r.split(' ') }?.filter { it.isNotEmpty() && !isArt(it) } ?: emptyList()
        scope.launch(Dispatchers.IO) {
            KeyboardPrefs.langList().forEach { code ->
                // a language without a word list fetches its own (when there is a connection)
                if (!KeyboardDictionary.hasList(this@PulseKeyboardService, code)) KeyboardDictionary.download(this@PulseKeyboardService, code)
                KeyboardDictionary.load(this@PulseKeyboardService, code)
                // the small bundled list is replaced once by the full one (inflected forms, names), when there is a connection
                runCatching { KeyboardDictionary.upgradeOnce(this@PulseKeyboardService, code) }
            }
            withContext(Dispatchers.Main) { updateSuggestions() }
        }
        st.clipItems = ClipStore.load(this)
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.addPrimaryClipChangedListener(clipListener)
        // (the file observers are not set here: they are on only while the keyboard is on the screen, see startShotWatch)
    }

    /**
     * The observers of new pictures, videos, downloads and files (a screenshot, a camera photo or a file just made waits in the bar). They are on only while the keyboard is shown:
     * the keyboard service lives as long as it is the chosen keyboard, and an observer of every file of the phone woke it all day and all night (each change started a set of four looks).
     * When the keyboard opens, what is new is looked at once (onStartInputView), so nothing is missed.
     */
    private var shotWatching = false
    private fun startShotWatch() {
        if (shotWatching) return
        shotWatching = true
        runCatching { contentResolver.registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, shotObserver) }
        runCatching { contentResolver.registerContentObserver(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, shotObserver) }
        if (android.os.Build.VERSION.SDK_INT >= 29) runCatching { contentResolver.registerContentObserver(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, true, shotObserver) }
        // every other file made anywhere (a zip made in a folder of its own, a document) is looked at the same way
        runCatching { contentResolver.registerContentObserver(android.provider.MediaStore.Files.getContentUri("external"), true, shotObserver) }
    }
    private fun stopShotWatch() {
        if (!shotWatching) return
        shotWatching = false
        runCatching { contentResolver.unregisterContentObserver(shotObserver) }
    }

    // ---- a screenshot or a camera photo just taken waits in the right slot of the bar, to be pasted from there --------------------------------------------------
    private var shotItem: ClipItem? = null
    // a photo or screenshot waits 30 seconds in the bar, then it goes (the bar is refreshed then too)
    private val shotExpiry = android.os.Handler(android.os.Looper.getMainLooper())
    /** When the keyboard was last shown: a picture waiting in the bar is shown for 15 seconds from then (for a minute from the shot while the keyboard is closed). */
    private var kbOpenAt = 0L
    private fun shotLeftMs(): Long {
        val now = SystemClock.uptimeMillis()
        return if (isInputViewShown) 15_000L - (now - maxOf(shotAt, kbOpenAt)) else 60_000L - (now - shotAt)
    }
    private val shotExpire: Runnable = Runnable {
        if ((shotItem != null || shotVideo != null) && !shotPinned) {
            val left = shotLeftMs()
            if (left > 500L) shotExpiry.postDelayed(shotExpire, left)
            else { shotItem = null; shotVideo = null; shotGroup = emptyList(); st.shotCount = 0; st.shotVideo = false; st.shotFile = ""; st.shotThumb = null; updateSuggestions() }
        }
    }
    /** a video just downloaded, waiting in the same slot (its picture with a play mark): it is not kept in the clipboard, it is pasted from where it is */
    private var shotVideo: java.io.File? = null
    /** The pictures of one kind taken together (the newest first): the chip of the bar is a stack and one tap sends them all. */
    private var shotGroup: List<java.io.File> = emptyList()
    private var shotVideoMime = "video/mp4"
    private val videosTaken = LinkedHashSet<Long>()
    private var shotPinned = false
    private var shotAt = 0L
    private val shotObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        // looked at four times, the first one quick (a downloaded file or a screenshot shows in the bar as soon as it is finished); a file that is still being written is taken by a later look
        // one look at a time: a change that comes while the looks of the last one are still going (a zip unpacked sends hundreds of them) starts them over instead of piling up another set, which had made the keyboard heavy
        private var job: kotlinx.coroutines.Job? = null
        override fun onChange(selfChange: Boolean) {
            job?.cancel()
            job = scope.launch { delay(250); checkShot(); checkVideo(); checkFile(); delay(900); checkShot(); checkVideo(); checkFile(); delay(2500); checkShot(); checkVideo(); checkFile(); delay(5000); checkShot(); checkVideo(); checkFile() }
        }
    }
    /** The pictures already taken in (their numbers in the phone's picture list), so none is taken twice and none is skipped when two come close together. */
    private val shotsTaken = LinkedHashSet<Long>()
    private val shotLock = kotlinx.coroutines.sync.Mutex()

    /**
     * The new screenshots, camera photos and downloaded pictures: only those made in the last moments, each of them once. The newest waits in the right slot of the bar and is
     * pasted from where it is, like a video: nothing of it goes into the clipboard (its history stays for what the person copied).
     */
    private suspend fun checkShot(fromStart: Boolean = false) {
        if (st.incognito) return
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        if (!shotLock.tryLock()) return
        var newest: Triple<Long, Long, String>? = null
        val batchPaths = ArrayList<String>()
        try {
            val sp = getSharedPreferences("pulse_kb_shots", MODE_PRIVATE)
            if (shotsTaken.isEmpty()) sp.getString("ids", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.forEach { shotsTaken += it }
            val found = withContext(Dispatchers.IO) {
                runCatching {
                    val cols = arrayOf(android.provider.MediaStore.Images.Media._ID, android.provider.MediaStore.Images.Media.DISPLAY_NAME, android.provider.MediaStore.Images.Media.DATE_ADDED, android.provider.MediaStore.Images.Media.DATA)
                    // a "LIMIT" in the sort order is refused from Android 11 on (the whole lookup threw and nothing was ever found): the limit goes in a Bundle
                    val q = android.os.Bundle().apply {
                        putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(android.provider.MediaStore.Images.Media.DATE_ADDED))
                        putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 8)
                    }
                    val out = ArrayList<Triple<Long, Long, String>>()
                    val plainIds = HashSet<Long>()
                    (if (android.os.Build.VERSION.SDK_INT >= 26) contentResolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, q, null)
                    else contentResolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, null, null, android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 8"))?.use { c ->
                        while (c.moveToNext()) {
                            val name = c.getString(1).orEmpty(); val path = c.getString(3).orEmpty()
                            val age = System.currentTimeMillis() / 1000 - c.getLong(2)
                            // a screenshot by its name or folder in any spelling the phones use (Screenshot, Screen_shot, Ekran görüntüsü, Screen capture),
                            // or a photo just taken with the camera, HEIC too (the camera app finishes the file a little later)
                            val both = (name + "/" + path).lowercase(Locale.ROOT)
                            val shot = listOf("screenshot", "screen_shot", "screen shot", "screen-shot", "screencap", "screen_cap", "ekran g", "ekran_g", "ekrang", "capture", "smartselect", "scrnshot").any { it in both }
                            val cam = path.contains("/dcim/", true) || path.contains("/camera/", true)
                            // a picture just downloaded (the Download folder) is offered the same way
                            // (directly in the Download folder: what a zip is unpacked into is a folder of its own, those pictures are not offered)
                            val down = java.io.File(path).parentFile?.name.equals("Download", true) == true
                            // only what is new: a picture made in the last moments, never the older ones of the gallery
                            // (when the keyboard comes up, e.g. back from the camera or from the recent apps, a wider look: a photo made in the last quarter of an hour)
                            if (shot && age <= (if (fromStart) 180 else 45) || cam && age <= (if (fromStart) 900 else 30) || down && age <= (if (fromStart) 180 else 45)) {
                                out += Triple(c.getLong(0), age, path)
                                if (!shot && (down || cam)) plainIds += c.getLong(0)
                            }
                        }
                    }
                    // several pictures made at once that are not screenshots (a zip unpacked, a folder copied in) are not a picture the person just took: none of them is offered
                    if (plainIds.size > 2) out.removeAll { it.first in plainIds }
                    out
                }.getOrNull()
            }.orEmpty().filter { it.first !in shotsTaken }
            if (found.isEmpty()) return
            // each is marked as seen; a file that cannot be read yet (still being written) is not, so the next look takes it
            for (t in found) {
                val f = java.io.File(t.third)
                if (!f.isFile || f.length() <= 0) continue
                shotsTaken += t.first
                batchPaths += t.third
                if (newest == null || t.second < newest!!.second) newest = t
            }
            while (shotsTaken.size > 40) shotsTaken.remove(shotsTaken.first())
            sp.edit().putString("ids", shotsTaken.joinToString(",")).apply()
        } finally { shotLock.unlock() }
        val hit = newest ?: return
        if (hit.second > (if (fromStart) 900 else 40)) return
        val file = java.io.File(hit.third)
        // pictures of the same kind taken together wait as one stack: one tap sends them all
        fun kind(p: String) = if (p.contains("/dcim/", true) || p.contains("/camera/", true)) 1 else if (p.contains("/download", true)) 2 else 0
        val same = batchPaths.filter { kind(it) == kind(hit.third) }.map { java.io.File(it) }.filter { it.isFile && it.length() > 0 }
        // many pictures at once that are not from the camera or a screenshot (a zip opened, a folder copied) are not a thing to paste: nothing shows in the bar
        if (kind(hit.third) == 2 && same.size > 5) return
        // only a stack made in the last ten seconds is added to; an older one (already pasted, or left behind) is not carried into the new picture's stack
        val older = if (SystemClock.uptimeMillis() - shotAt < 10_000L) shotGroup else emptyList()
        shotGroup = (same + older.filter { f -> f.isFile && kind(f.path) == kind(hit.third) && same.none { it.path == f.path } }).take(12)
        st.shotCount = shotGroup.size
        val mime = when (file.extension.lowercase()) { "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"; "heic", "heif" -> "image/heic"; else -> "image/jpeg" }
        // the newest copy wins the slot: a screenshot takes it from a copied text
        quickClip = null; shotItem = null; shotVideo = file; shotVideoMime = mime; st.shotVideo = false; shotAt = SystemClock.uptimeMillis(); shotPinned = false; st.quickPinned = false
        // only a screenshot is kept in the clipboard history too; a camera photo or a downloaded picture is offered in the bar for a moment and nothing more
        val isShot = (file.name + "/" + file.path).lowercase(Locale.ROOT).let { both -> listOf("screenshot", "screen_shot", "screen shot", "screen-shot", "screencap", "screen_cap", "ekran g", "ekran_g", "ekrang", "capture", "smartselect", "scrnshot").any { it in both } }
        if (!st.incognito && isShot) scope.launch(Dispatchers.IO) { runCatching { val l = ClipStore.addImage(this@PulseKeyboardService, android.net.Uri.fromFile(file)); withContext(Dispatchers.Main) { st.clipItems = l } } }
        st.shotThumb = withContext(Dispatchers.IO) { runCatching {
            val bmp = android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }) ?: return@runCatching null
            // the camera stores a portrait photo sideways with an orientation tag: it is turned upright here
            val deg = when (android.media.ExifInterface(file.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
            (if (deg == 0f) bmp else android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(deg) }, true)).asImageBitmap()
        }.getOrNull() }
        shotExpiry.removeCallbacks(shotExpire); shotExpiry.postDelayed(shotExpire, 15_000L)
        updateSuggestions()
    }

    /** A video that has just landed in the Download folder (finished in the last minute) goes to the right slot of the bar, as a picture with a play mark. */
    private suspend fun checkVideo() {
        if (st.incognito) return
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_VIDEO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val V = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val hit = withContext(Dispatchers.IO) {
            runCatching {
                val cols = arrayOf(android.provider.MediaStore.Video.Media._ID, android.provider.MediaStore.Video.Media.DATA, android.provider.MediaStore.Video.Media.DATE_MODIFIED, android.provider.MediaStore.Video.Media.MIME_TYPE)
                val q = android.os.Bundle().apply {
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(android.provider.MediaStore.Video.Media.DATE_MODIFIED))
                    putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 4)
                }
                var out: Triple<Long, String, String>? = null
                (if (android.os.Build.VERSION.SDK_INT >= 26) contentResolver.query(V, cols, q, null)
                else contentResolver.query(V, cols, null, null, android.provider.MediaStore.Video.Media.DATE_MODIFIED + " DESC LIMIT 4"))?.use { c ->
                    while (c.moveToNext() && out == null) {
                        val id = c.getLong(0); val path = c.getString(1).orEmpty()
                        // finished in the last minute (a download is written for a long time: its last change is when it was done), in a Download folder, not offered before
                        val age = System.currentTimeMillis() / 1000 - c.getLong(2)
                        if (id !in videosTaken && age in 0..60 && (path.contains("/download", true) || path.contains("/dcim/", true) || path.contains("/camera/", true)) && java.io.File(path).length() > 0) out = Triple(id, path, c.getString(3) ?: "video/mp4")
                    }
                }
                out
            }.getOrNull()
        } ?: return
        val (id, path, mime) = hit
        videosTaken += id
        while (videosTaken.size > 20) videosTaken.remove(videosTaken.first())
        val thumb = withContext(Dispatchers.IO) { runCatching {
            val uri = android.content.ContentUris.withAppendedId(V, id)
            @Suppress("DEPRECATION")
            val b = if (android.os.Build.VERSION.SDK_INT >= 29) contentResolver.loadThumbnail(uri, android.util.Size(240, 240), null)
                else android.media.ThumbnailUtils.createVideoThumbnail(path, android.provider.MediaStore.Images.Thumbnails.MINI_KIND)
            b?.asImageBitmap()
        }.getOrNull() }
        quickClip = null; shotItem = null; shotVideo = java.io.File(path); shotVideoMime = mime
        shotAt = SystemClock.uptimeMillis(); shotPinned = false; st.quickPinned = false
        st.shotThumb = thumb; st.shotVideo = true; st.shotFile = ""
        shotExpiry.removeCallbacks(shotExpire); shotExpiry.postDelayed(shotExpire, 15_000L)
        updateSuggestions()
    }

    private val filesTaken = LinkedHashSet<Long>()

    /** Any other file just downloaded (a PDF, an archive, a document, an APK) waits in the right slot too, for a chat that takes files. */
    private suspend fun checkFile() {
        if (st.incognito || android.os.Build.VERSION.SDK_INT < 29) return
        val hit = withContext(Dispatchers.IO) {
            // the whole file list first (a file made in any folder), the Downloads list when the phone does not show that one
            listOf(android.provider.MediaStore.Files.getContentUri("external"), android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI).firstNotNullOfOrNull { D -> runCatching {
                val cols = arrayOf(android.provider.MediaStore.Downloads._ID, android.provider.MediaStore.Downloads.DISPLAY_NAME, android.provider.MediaStore.Downloads.DATE_MODIFIED, android.provider.MediaStore.Downloads.MIME_TYPE, android.provider.MediaStore.Downloads.DATA, android.provider.MediaStore.Downloads.SIZE)
                val q = android.os.Bundle().apply {
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(android.provider.MediaStore.Downloads.DATE_MODIFIED))
                    putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 4)
                }
                var out: Triple<Long, String, String>? = null
                contentResolver.query(D, cols, q, null)?.use { c ->
                    while (c.moveToNext() && out == null) {
                        val id = c.getLong(0); val name = c.getString(1).orEmpty(); val mime = c.getString(3) ?: "application/octet-stream"; val path = c.getString(4).orEmpty()
                        val age = System.currentTimeMillis() / 1000 - c.getLong(2)
                        // pictures and videos have their own lookups; half-written downloads and Pulse's own temporary files are left alone
                        val partial = name.startsWith(".") || name.startsWith("temp_") || listOf(".part", ".crdownload", ".tmp", ".pending").any { name.endsWith(it, true) }
                        if (id !in filesTaken && age in 0..60 && !partial && !mime.startsWith("image/") && !mime.startsWith("video/") && c.getLong(5) > 0 && java.io.File(path).isFile) out = Triple(id, path, mime)
                    }
                }
                out
            }.getOrNull() }
        } ?: return
        val (id, path, mime) = hit
        filesTaken += id
        while (filesTaken.size > 20) filesTaken.remove(filesTaken.first())
        quickClip = null; shotItem = null; shotVideo = java.io.File(path); shotVideoMime = mime
        shotAt = SystemClock.uptimeMillis(); shotPinned = false; st.quickPinned = false
        st.shotThumb = null; st.shotVideo = false; st.shotFile = java.io.File(path).extension.uppercase().take(4).ifEmpty { "FILE" }; st.shotFileName = java.io.File(path).name
        shotExpiry.removeCallbacks(shotExpire); shotExpiry.postDelayed(shotExpire, 15_000L)
        updateSuggestions()
    }

    /** Pastes the video or picture [f] into the field when the field takes that kind (a chat that sends them), straight from where it is, through Pulse's file provider. */
    private fun pasteVideo(f: java.io.File, mime: String) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        val info = currentInputEditorInfo
        val types = if (info != null) androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(info) else emptyArray()
        val kind = mime.substringBefore('/')
        if (types.none { it.startsWith("$kind/") || it == "*/*" }) {
            if (pasteViaClipboard(f, mime)) { st.panel = "keys"; return }
            android.widget.Toast.makeText(this, if (kind == "image") com.umutk.pulse.tr("This field takes no pictures", "Bu alan resim kabul etmiyor") else if (kind == "video") com.umutk.pulse.tr("This field takes no videos", "Bu alan video kabul etmiyor") else com.umutk.pulse.tr("This field takes no files", "Bu alan dosya kabul etmiyor"), android.widget.Toast.LENGTH_SHORT).show(); return
        }
        runCatching {
            val u = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", f)
            val content = androidx.core.view.inputmethod.InputContentInfoCompat(u, android.content.ClipDescription(kind, arrayOf(mime)), null)
            androidx.core.view.inputmethod.InputConnectionCompat.commitContent(c, info!!, content, androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
            st.panel = "keys"
        }
    }

    override fun onDestroy() {
        runCatching { recognizer?.destroy() }; recognizer = null
        clipboard?.removePrimaryClipChangedListener(clipListener)
        stopShotWatch()
        KeyboardDictionary.saveUser(this)
        runCatching { (getSystemService(Context.INPUT_SERVICE) as android.hardware.input.InputManager).unregisterInputDeviceListener(deviceListener) }
        owner?.stop(); scope.cancel()
        if (live === this) live = null
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    /**
     * A request for the cursor's place that asks the field for as little text as it can: a plain request made the field hand over its WHOLE text across the process
     * border (in a very long document that took seconds and a lot of memory, and the keyboard stopped answering).
     */
    private fun smallExtract() = android.view.inputmethod.ExtractedTextRequest().apply { hintMaxChars = 1; hintMaxLines = 1 }

    private var kbRoot: View? = null
    /** The floating keyboard's tall window was laid out (until a normal keyboard is shown again). */
    private var floatWasUp = false

    /**
     * The floating keyboard: the window is tall (the keyboard sits at the bottom of it and moves up inside it), so only the keyboard's own part takes touches and
     * the app does not make room for it; everything else in the window is the app's. The normal keyboard keeps the system's own insets.
     */
    override fun onComputeInsets(outInsets: android.inputmethodservice.InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        val r = st.floatRect
        val root = kbRoot
        // the keyboard is going away (or gone) after the floating one was up: its tall window takes no touch at all while it leaves, so the app behind is usable at once
        // (when the floating session ended first, the system's default made the whole tall window take touches for a moment)
        if (!isInputViewShown && (KeyboardPrefs.floatNow || floatWasUp)) {
            val h0 = window.window?.decorView?.height ?: 0
            outInsets.contentTopInsets = h0; outInsets.visibleTopInsets = h0
            outInsets.touchableInsets = android.inputmethodservice.InputMethodService.Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.setEmpty()
            return
        }
        if (!KeyboardPrefs.floatNow) {
            floatWasUp = false
            // the app is told where the visible keyboard really starts (the top of what is drawn), not where the window starts: with the screen turned the other way round
            // the camera's cut-out and the system bars shift the keyboard, and the bar then lay over the text box
            if (root != null && root.isShown) { val l = IntArray(2); root.getLocationInWindow(l); outInsets.contentTopInsets = l[1]; outInsets.visibleTopInsets = l[1] }
            return
        }
        floatWasUp = true
        // until the keyboard has been measured, nothing in the tall window takes touches: otherwise the empty window would block the screen behind it
        if (r == null || root == null) {
            outInsets.contentTopInsets = window.window?.decorView?.height ?: 0
            outInsets.visibleTopInsets = outInsets.contentTopInsets
            outInsets.touchableInsets = android.inputmethodservice.InputMethodService.Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.setEmpty()
            return
        }
        val h = window.window?.decorView?.height ?: return
        val loc = IntArray(2); root.getLocationInWindow(loc)
        outInsets.contentTopInsets = h
        outInsets.visibleTopInsets = h
        outInsets.touchableInsets = android.inputmethodservice.InputMethodService.Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(r[0] + loc[0], r[1] + loc[1], r[2] + loc[0], r[3] + loc[1])
    }

    override fun onCreateInputView(): View {
        // the keyboard view is made with the knowledge of a connected physical keyboard
        st.hardware = hardwareAttached()
        val o = owner ?: StandByOwner().also { it.start(); owner = it }
        // Compose finds its lifecycle on the root of the window, which for a keyboard is the window's decor view
        window.window?.decorView?.let { it.setViewTreeLifecycleOwner(o); it.setViewTreeSavedStateRegistryOwner(o) }
        return ComposeView(this).apply {
            kbRoot = this
            // the floating keyboard moved: the system is asked again which part of the window takes touches
            st.refreshInsets = { post { this@PulseKeyboardService.window.window?.decorView?.requestLayout() } }
            // the camera's cut-out (at the bottom when the screen is turned upside down) and the sides: the keys keep clear of it, so they are not hidden under it
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val c = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout())
                v.setPadding(c.left, 0, c.right, c.bottom)
                insets
            }
            setViewTreeLifecycleOwner(o); setViewTreeSavedStateRegistryOwner(o)
            setContent { KeyboardView(st, this@PulseKeyboardService) }
            // the bottom keys sit where the system's home swipe starts: the whole keyboard keeps its touches (Android 10+)
            if (android.os.Build.VERSION.SDK_INT >= 29) addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ -> v.systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, r - l, b - t)) }
        }
    }

    override fun onEvaluateInputViewShown(): Boolean = true

    /**
     * Some apps (web pages, Flutter and React Native fields) give the field's real kind a moment after the keyboard came up, or restart the input without the keyboard
     * being started again: the keyboard opened as the text keys in a number field and turned into the number pad only with the first key. The kind is looked at again
     * at those moments (a new input, a moment after the start, the first selection change) and the number pad follows without a key.
     */
    private fun syncKind(info: EditorInfo?) {
        info ?: return
        if (st.forceLayout != null || st.panel != "keys") return
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val num = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        // the kind of the text field itself (a password box without suggestions, an address box, a plain one) is read again too: a field that first said "password"
        // or "no input" and then became a normal one kept the plain keys, with no suggestion bar, until the first key
        val kindChanged = applyKind(info)
        val numChanged = num != st.numpad
        if (!kindChanged && !numChanged) return
        if (numChanged) { st.numpad = num; st.page = if (num) 1 else 0 }
        updateSuggestions()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        st.refreshInsets?.invoke()
        kbRoot?.requestLayout()
    }

    /**
     * A web page's box (a WebView of Pulse itself: the sign-in of YTXTube, Game Hub) lives in THIS process: its answers to "what is before the cursor" are
     * worked out on the main thread, which is the one asking, so every question waited out its timeout and the screen froze. Such a box only gets typed into
     * (commit / delete are not waited for); every question is answered at once with "nothing".
     */
    private var blindWeb = false
    private fun webBox(info: EditorInfo?): Boolean {
        if (info == null || info.packageName != packageName) return false
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        return cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT || v == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
    }
    private var blindIc: android.view.inputmethod.InputConnection? = null
    private var blindOf: android.view.inputmethod.InputConnection? = null
    override fun getCurrentInputConnection(): android.view.inputmethod.InputConnection? {
        val ic = super.getCurrentInputConnection() ?: return null
        if (!blindWeb) return ic
        if (blindOf !== ic) {
            blindOf = ic
            blindIc = object : android.view.inputmethod.InputConnectionWrapper(ic, true) {
                // the open word is what the keyboard itself typed there: it is answered from that, so no question is ever put to the page (that froze the screen)
                override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = word.toString().takeLast(n)
                override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
                override fun getSelectedText(flags: Int): CharSequence? = null
                override fun getCursorCapsMode(reqModes: Int): Int = 0
                override fun getExtractedText(request: android.view.inputmethod.ExtractedTextRequest?, flags: Int): android.view.inputmethod.ExtractedText? = null
            }
        }
        return blindIc
    }

    /** Takes the kind of the field from [info] (plain keys, address, web address, name, no automatic correction); true when any of them changed. */
    private fun applyKind(info: EditorInfo): Boolean {
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        val nAuto = (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        blindWeb = webBox(info)
        val nPlain = info.inputType == 0 || (cls != InputType.TYPE_CLASS_TEXT && cls != 0) ||
            v in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        val nEmail = cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || v == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        val nUri = cls == InputType.TYPE_CLASS_TEXT && v == InputType.TYPE_TEXT_VARIATION_URI
        val nName = cls == InputType.TYPE_CLASS_TEXT && v == InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        // a terminal app asks for no input type at all: its keys stay plain (no open word), the bar of suggestions stays on
        // (an editor that declares no input type at all, a custom text box of an app, is treated the same way: its keys are plain, but the bar opens with the usual words)
        val nTerm = info.inputType == 0
        st.terminal = nTerm
        // the command words (ls, cd, git ...) only in a real terminal (Termux and the like) or Pulse's own code editor; any other app's box with no input type gets the usual words
        termApp = nTerm && info.packageName.orEmpty().let { p -> p.startsWith("com.termux") || p == packageName || "terminal" in p.lowercase() || "ssh" in p.lowercase() || p.startsWith("jackpal") }
        val changed = nAuto != noAuto || nPlain != st.plain || nEmail != emailField || nUri != uriField || nName != nameField
        noAuto = nAuto; st.plain = nPlain; emailField = nEmail; uriField = nUri; nameField = nName
        passwordField = (cls == InputType.TYPE_CLASS_TEXT && v in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        return changed
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        editEpoch++
        // is a physical keyboard connected? asked before the keyboard is drawn, so a field opens as the suggestion bar at once (not the full keys first and the bar a moment later);
        // the full keys come only when the keyboard button of the bar is pressed, and every new field starts as the bar again
        if (!restarting) { st.hardware = hardwareAttached(); st.hwOpen = false }
        // a restart of the input with the keyboard already up: the new kind is taken at once
        if (restarting) syncKind(attribute)
    }

    /** When the keyboard came up: a key hit in the first moments is the finger that swiped it up (or the one still on the screen), not typing. */
    private var shownAt = 0L

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        if (!restarting) shownAt = android.os.SystemClock.uptimeMillis()
        runCatching { KeyboardPrefs.saverOn = (getSystemService(POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode }
        if (!restarting) runCatching { takeClip(true) }
        // a screenshot or download made a moment before the keyboard came up is offered too
        scope.launch { runCatching { checkShot(fromStart = true) } }
        super.onStartInputView(info, restarting)
        // a hardware keyboard: the screen keyboard still comes up, as its suggestion bar only
        st.hardware = hardwareAttached()
        st.hwOpen = false
        st.inline = emptyList(); st.emojiQuery = null
        st.forceLayout = null; st.panel = "keys"
        st.clipItems = ClipStore.load(this)
        // the settings may have changed the language since the last field
        KeyboardPrefs.load(this)
        val primary = KeyboardPrefs.langList().first()
        if (primary != seenPrimary) { seenPrimary = primary; st.lang = primary }
        if (st.lang !in KeyboardPrefs.langList()) st.lang = primary
        // per-app language (a setting): the language last used in this app comes back
        if (KeyboardPrefs.perAppLang) info.packageName?.let { pk -> getSharedPreferences("pulse_kb_perapp", MODE_PRIVATE).getString(pk, null)?.takeIf { it in KeyboardPrefs.langList() }?.let { saved -> if (saved != st.lang) { st.lang = saved; scope.launch(Dispatchers.IO) { KeyboardDictionary.load(this@PulseKeyboardService, saved) } } } }
        st.dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        // a field that asks for no suggestions (many web inputs in Chrome do) still gets the bar; only the automatic correction stays off there
        applyKind(info); lastEmail = null
        st.emailBar = false
        st.numpad = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        st.page = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME) 1 else 0
        st.panel = "keys"; st.toolbar = false
        // a new field: the keyboard is laid out again (a number field and a text field differ in size), so its touch area and page follow the field at once
        st.refreshInsets?.invoke()
        st.enter = when {
            (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 -> "newline"
            else -> when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_SEND -> "send"; EditorInfo.IME_ACTION_SEARCH -> "search"; EditorInfo.IME_ACTION_GO -> "go"
                EditorInfo.IME_ACTION_DONE -> "done"; EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS -> "next"; else -> "newline"
            }
        }
        word.clear(); touchPts.clear(); lastCorrection = null; prevWord = null; prevWord2 = null; lastWasSpace = false; punctTyped = false
        st.shift = 0
        updateCaps(); updateSuggestions()
        // the new field's layout (number pad, bar only for a hardware keyboard) is handed to the window now, not with the next key: state changes are applied at once and the window is measured again
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        kbRoot?.let { v -> v.post { st.refreshInsets?.invoke(); v.requestLayout() } }
        // the app may still change the field's kind a moment after the start: it is looked at twice more
        kbRoot?.let { v -> v.postDelayed({ syncKind(currentInputEditorInfo) }, 120); v.postDelayed({ syncKind(currentInputEditorInfo) }, 450) }
        // the bar never opens empty: when nothing was put on it (the field answered late), the usual words of the language stand there
        kbRoot?.let { v -> v.postDelayed({ fillEmptyBar() }, 250); v.postDelayed({ fillEmptyBar() }, 700) }
    }

    private fun fillEmptyBar() {
        if (!KeyboardPrefs.suggestions || word.isNotEmpty() || st.panel != "keys" || st.slots.any { it.isNotBlank() }) return
        if (currentInputConnection == null) return
        val fb = commonNext(st.lang, st.shift > 0)
        slotWords = fb.toTypedArray(); st.slots = fb; target = null
    }

    /**
     * Is a physical keyboard attached? The configuration alone is not enough: a Bluetooth or USB keyboard connected after the service started may not show in it yet,
     * so the input devices are asked too (a real keyboard with letter keys, not the phone's own virtual one).
     */
    private fun hardwareAttached(): Boolean {
        // only a real keyboard with letter keys counts; a game controller (which also says "keyboard" for its buttons, and can change the configuration) does not:
        // with one connected, the screen keyboard came up as its suggestion bar only, in Game Hub and everywhere
        return runCatching {
            android.view.InputDevice.getDeviceIds().any { id ->
                val d = android.view.InputDevice.getDevice(id) ?: return@any false
                val pad = (d.sources and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD ||
                    (d.sources and android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK
                // (a keyboard cover of a tablet, joined by contacts, can be reported as "virtual" with an id above zero: only the phone's own virtual keyboard, id below zero, is left out)
                (!d.isVirtual || d.id > 0) && !pad && (d.sources and android.view.InputDevice.SOURCE_KEYBOARD) == android.view.InputDevice.SOURCE_KEYBOARD &&
                    d.keyboardType == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC
            } ||
                // the system itself says a keyboard with letter keys is out (not hidden)
                (resources.configuration.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY && resources.configuration.hardKeyboardHidden == android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO)
        }.getOrDefault(resources.configuration.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY)
    }

    // a keyboard plugged in or paired while a field is open: the screen keyboard follows at once (no key press needed)
    private val deviceListener = object : android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) { st.hardware = hardwareAttached() }
        override fun onInputDeviceRemoved(deviceId: Int) { st.hardware = hardwareAttached(); if (!st.hardware) st.hwOpen = false }
        override fun onInputDeviceChanged(deviceId: Int) { st.hardware = hardwareAttached() }
    }

    // a keyboard that is closed with the emoji (or clipboard, or tool) page up comes back as the plain keys, whichever way it is shown again
    private fun backToKeys() { st.panel = "keys"; st.emojiQuery = null; st.toolbar = false; releaseMods() }

    /** The keyboard closed or opened: the keys of the extra row (Ctrl, Alt, Win, Shift, Fn) are let go, so none of them stays on for the next field. */
    private fun releaseMods() {
        st.ctrl = 0; st.alt = 0; st.win = 0; st.shiftMod = 0; st.fnOpen = false
        heldMods.clear(); heldPrev.clear(); heldUsed.clear()
    }
    override fun onFinishInputView(finishingInput: Boolean) { super.onFinishInputView(finishingInput); backToKeys(); stopShotWatch() }
    override fun onWindowHidden() {
        stopShotWatch()
        super.onWindowHidden(); backToKeys(); st.floatArmed = false
        mirrorPrefs()
        // the touch area is asked again at once: a hidden keyboard takes no touches
        runCatching { window.window?.decorView?.requestLayout() }
    }
    /** With the setting on, the keyboard's window is secure: a screen capture (and so Circle to Search, started by a long press on the home bar) cannot read the screen while the keyboard is up. */
    private fun applyCtsBlock() {
        runCatching {
            val w = window.window ?: return
            if (KeyboardPrefs.blockCts) w.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onWindowShown() {
        applyCtsBlock()
        startShotWatch()
        kbOpenAt = SystemClock.uptimeMillis()
        // a picture still waiting is looked at again from the moment the keyboard is up
        if ((shotItem != null || shotVideo != null) && !shotPinned) { shotExpiry.removeCallbacks(shotExpire); shotExpiry.postDelayed(shotExpire, 15_000L) }
        super.onWindowShown(); backToKeys()
        st.floatArmed = false
        // shown again with the same field (back from a screenshot or another window): the bar is made again, it is not left empty
        runCatching { updateSuggestions() }
        kbRoot?.let { v -> v.postDelayed({ fillEmptyBar() }, 250) }
        applyFloatWindow()
        st.refreshInsets?.invoke()
    }

    /**
     * The login the keyboard sees being typed: a user name box (e-mail, a label that says user / login, a number box) is remembered, and when a password box is left or sent
     * the keyboard offers to save the pair (a notification, so it still shows when the keyboard closes with the page). Independent of the phone's autofill.
     */
    private fun trackLogin() {
        if (st.incognito) return
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        val pass = (cls == InputType.TYPE_CLASS_TEXT && v in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val label = (info.hintText?.toString().orEmpty() + " " + info.label?.toString().orEmpty()).lowercase()
        if (listOf("otp", "one-time", "verification", "doğrulama", "dogrulama", "sms", "tek kullanımlık", "tek kullanimlik", "kod", "code").any { label.contains(it) }) return
        val text = (ic.getTextBeforeCursor(200, 0)?.toString().orEmpty() + ic.getTextAfterCursor(200, 0)?.toString().orEmpty()).trim()
        if (text.isEmpty()) return
        if (pass) { LoginSaver.offer(this, info.packageName, text); return }
        val userLike = (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || v == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)) ||
            cls == InputType.TYPE_CLASS_NUMBER || listOf("user", "kullanıcı", "kullanici", "login", "e-posta", "email", "mail", "öğrenci", "ogrenci", "numara").any { label.contains(it) }
        if (userLike) LoginSaver.rememberUser(info.packageName, text)
    }

    /** A name field left with "Umut Kıvanç Şahin" in it: the whole name is kept, so its first letters offer it in any field. */
    private fun learnFullName() {
        if (st.incognito) return
        val hint = (currentInputEditorInfo?.hintText?.toString().orEmpty() + " " + currentInputEditorInfo?.label?.toString().orEmpty()).lowercase()
        if (!nameField && listOf("ad soyad", "adınız", "adiniz", "full name", "your name", "isim soyisim", "ad-soyad").none { hint.contains(it) }) return
        val ic = currentInputConnection ?: return
        val t = (ic.getTextBeforeCursor(80, 0)?.toString().orEmpty() + ic.getTextAfterCursor(80, 0)?.toString().orEmpty()).trim()
        val parts = t.split(' ').filter { it.isNotBlank() }
        if (parts.size !in 2..4 || !parts.all { p -> p.all { it.isLetter() || it == '\'' || it == '-' } }) return
        getSharedPreferences("pulse_kb_fullnames", MODE_PRIVATE).let { p -> p.edit().putString("list", (listOf(t) + p.getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() && it != t }).take(5).joinToString("\n")).apply() }
    }

    override fun onFinishInput() {
        editEpoch++
        runCatching { commitNumber() }
        runCatching { trackLogin() }
        super.onFinishInput()
        st.inline = emptyList()
        lastEmail?.let { e -> getSharedPreferences("pulse_kb_emails", MODE_PRIVATE).let { p -> p.edit().putString("list", (listOf(e) + p.getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() && it != e }).take(5).joinToString("\n")).apply() } }
        lastEmail = null
        word.clear(); lastCorrection = null
        runCatching { learnFullName() }
        KeyboardDictionary.saveUser(this)
        Thread { runCatching { com.umutk.pulse.AutoBackup.maybeRun(applicationContext) } }.start()
    }

    private var editAt = 0L
    private var shiftDropAt = 0L
    private val corrExec = java.util.concurrent.Executors.newSingleThreadExecutor()

    // a text field in this same process (Pulse's own fields) answers an edit with a selection report at once, on the same thread: finishing the open word from inside the report
    // reported again, and again, until the stack was full. One report is worked on at a time; a report that arrives meanwhile is only noted.
    private var inSelection = false

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        if (inSelection) { selStart = newSelStart; selEnd = newSelEnd; return }
        inSelection = true
        try { runCatching { handleSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd) } } finally { inSelection = false }
    }

    private fun handleSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        if (st.numpad != (currentInputEditorInfo?.inputType?.and(InputType.TYPE_MASK_CLASS).let { it == InputType.TYPE_CLASS_NUMBER || it == InputType.TYPE_CLASS_PHONE || it == InputType.TYPE_CLASS_DATETIME })) syncKind(currentInputEditorInfo)
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart; selEnd = newSelEnd
        // the field was emptied (a message was sent: the app clears it after the Enter) or the cursor jumped: the capital letter is worked out again from what is there now
        if (word.isEmpty() && newSelStart == newSelEnd && (newSelStart == 0 || Math.abs(newSelStart - oldSelStart) > 1)) updateCaps()
        // the cursor went somewhere else after a correction: backspace must not undo that correction any more
        if (lastCorrection != null && word.isEmpty() && SystemClock.uptimeMillis() - corrAt > 300 && newSelStart != oldSelStart) lastCorrection = null
        // the cursor left the word under way (the user tapped elsewhere): the word is finished as it stands
        // (the report may be late: a report right after our own edit is not the user's tap, and the open word is closed, never dropped)
        if (word.isNotEmpty() && SystemClock.uptimeMillis() - editAt > 250 && (candidatesStart < 0 || newSelStart < candidatesStart || newSelStart > candidatesEnd || newSelStart < candidatesEnd)) {
            // (a tap inside the word closes it too, so Backspace deletes at the cursor and not at the end of the word)
            // the open word is dropped first: closing it reports the selection again, and a report must never find the word still open
            val ic = currentInputConnection
            word.clear(); peak = null; lastCorrection = null
            ic?.finishComposingText()
            updateSuggestions()
        }
        // the cursor came back to the end of a word (a tap behind it, or a delete that stopped there): the word opens again, so it stands in the middle of the bar
        // with its own suggestions and the next letter goes on with it, instead of the bar moving on to a new word
        reopenWordAtCursor(oldSelStart, newSelStart, newSelEnd)
    }

    private fun reopenWordAtCursor(oldStart: Int, newStart: Int, newEnd: Int) {
        if (word.isNotEmpty() || newStart != newEnd || newStart <= 0 || newStart == oldStart) return
        if (!KeyboardPrefs.suggestions || st.plain || emailField || urlNow() || st.panel != "keys") return
        if (SystemClock.uptimeMillis() - editAt < 250) return
        val c = currentInputConnection ?: return
        val before = c.getTextBeforeCursor(40, 0)?.toString().orEmpty()
        val after = c.getTextAfterCursor(1, 0)?.toString().orEmpty()
        if (before.isEmpty() || !before.last().isLetter() || (after.isNotEmpty() && after[0].isLetter())) return
        val w = before.takeLastWhile { it.isLetter() || it == '\'' }
        if (w.isEmpty() || w.length > 30 || (before.length > w.length && before[before.length - w.length - 1].isLetterOrDigit())) return
        editAt = SystemClock.uptimeMillis()
        c.setComposingRegion(newStart - w.length, newStart)
        word.append(w); touchPts.clear(); target = null; peak = null; lastCorrection = null
        prevWord = before.dropLast(w.length).trimEnd().takeLastWhile { it.isLetter() || it == '\'' }.let { KeyboardLayouts.lower(it, st.lang) }.ifEmpty { null }
        updateSuggestions()
    }

    // ---- typing ------------------------------------------------------------------------------------------------------------------------------------

    // ---- the physical keyboard, Turkish: with an English (US) keyboard layout, the Turkish letters sit on the Turkish Q positions
    // (the same as a Turkish Q keyboard). Only when Turkish is the keyboard's language and the device layout is plain ASCII.
    private val turkishDown = HashSet<Int>()

    /** [shift] is Shift xor Caps Lock (the letters); [real] is the Shift key alone (punctuation does not follow Caps Lock). */
    private fun turkishQ(keyCode: Int, shift: Boolean, real: Boolean = shift): String? = when (keyCode) {
        // a Turkish Q keyboard under an English layout: the key right of ç is "." and ":", the one beside the Enter key "," and ";", and Shift+7 is "/"
        KeyEvent.KEYCODE_SLASH -> if (real) ":" else "."
        KeyEvent.KEYCODE_BACKSLASH -> if (real) ";" else ","
        KeyEvent.KEYCODE_7 -> if (real) "/" else null
        KeyEvent.KEYCODE_LEFT_BRACKET -> if (shift) "Ğ" else "ğ"
        KeyEvent.KEYCODE_RIGHT_BRACKET -> if (shift) "Ü" else "ü"
        KeyEvent.KEYCODE_SEMICOLON -> if (shift) "Ş" else "ş"
        KeyEvent.KEYCODE_APOSTROPHE -> if (shift) "İ" else "i"
        KeyEvent.KEYCODE_I -> if (shift) null else "ı"
        KeyEvent.KEYCODE_COMMA -> if (shift) "Ö" else "ö"
        KeyEvent.KEYCODE_PERIOD -> if (shift) "Ç" else "ç"
        else -> null
    }

    /**
     * A hardware keyboard is attached: its letters go through the same typing as the screen keys, so the suggestion bar follows the word, the automatic correction works
     * (a Turkish word typed with plain letters is fixed), and the function keys F1, F2 and F3 pick the left, the middle and the right suggestion. The Turkish letters sit on the Turkish Q positions.
     * Shortcuts (Ctrl, Alt, Win) and keys that are not text are left to the system.
     */
    private fun hardwareKey(keyCode: Int, e: KeyEvent): Boolean {
        if (e.isCtrlPressed || e.isAltPressed || e.isMetaPressed) return false
        if (currentInputConnection == null) return false
        val fn = keyCode - KeyEvent.KEYCODE_F1
        if (fn in 0..2) { turkishDown.add(keyCode); slot(fn); return true }
        // the translation bar is open: Tab puts the translation in, Enter puts it in and sends
        if (st.trans && !st.plain && st.panel == "keys") when (keyCode) {
            KeyEvent.KEYCODE_TAB -> { turkishDown.add(keyCode); transOp("apply"); return true }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { turkishDown.add(keyCode); val had = st.transOut.isNotBlank(); transOp("apply"); if (had) enter(); return true }
        }
        if (st.plain || st.panel != "keys") return false
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> { turkishDown.add(keyCode); space(); return true }
            KeyEvent.KEYCODE_DEL -> { turkishDown.add(keyCode); backspace(); return true }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { turkishDown.add(keyCode); enter(); return true }
        }
        val asciiDevice = e.getUnicodeChar(0) in 1..127
        val tr = if (KeyboardPrefs.langList().firstOrNull() == "tr" && asciiDevice) turkishQ(keyCode, e.isShiftPressed != e.isCapsLockOn, e.isShiftPressed) else null
        val ch = tr ?: e.getUnicodeChar(e.metaState).takeIf { it >= 32 }?.let { String(Character.toChars(it)) } ?: return false
        turkishDown.add(keyCode)
        type(ch)
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val e = event ?: return super.onKeyDown(keyCode, event)
        // Ctrl + Shift + Esc (a real keyboard): the Task Manager of Pulse, as on Windows
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && e.isCtrlPressed && e.isShiftPressed) { openTaskManager(); return true }
        // a key from a real keyboard: one is attached, whatever the configuration said (its letters then go through the keyboard's own typing: suggestions and automatic correction)
        if (!st.hardware && e.device?.let { d -> !d.isVirtual && d.keyboardType == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC } == true) st.hardware = true
        if (st.hardware && hardwareKey(keyCode, e)) return true
        // the arrows, Home and End of a real keyboard move the cursor here, through the text field: a key event that reaches the app makes Android leave touch mode and draw its
        // keyboard-navigation highlight (a layer over the screen); a key the keyboard takes itself never reaches the app
        if (!e.isCtrlPressed && !e.isAltPressed && !e.isMetaPressed && !e.isShiftPressed && (currentInputEditorInfo?.inputType ?: 0) != 0 && currentInputConnection != null) {
            val handled = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END -> { arrow(keyCode); true }
                KeyEvent.KEYCODE_DPAD_UP -> { moveLine(-1); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { moveLine(1); true }
                else -> false
            }
            if (handled) { turkishDown.add(keyCode); return true }
        }
        if (e.isCtrlPressed || e.isAltPressed || e.isMetaPressed || e.repeatCount > 0) return super.onKeyDown(keyCode, event)
        if (KeyboardPrefs.langList().firstOrNull() != "tr") return super.onKeyDown(keyCode, event)
        // the device layout already types Turkish (not plain ASCII): the system's own letter is right, nothing to change
        val device = e.getUnicodeChar(0)
        if (device !in 1..127) return super.onKeyDown(keyCode, event)
        val ch = turkishQ(keyCode, e.isShiftPressed != e.isCapsLockOn, e.isShiftPressed) ?: return super.onKeyDown(keyCode, event)
        val c = currentInputConnection ?: return super.onKeyDown(keyCode, event)
        turkishDown.add(keyCode)
        c.commitText(ch, 1)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // the key's release goes with its press: the field does not see a stray key up
        if (turkishDown.remove(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun type(s: String) {
        // the keyboard has only just come up: a key under the finger that swiped it up (a number key's corner, say) is not typed
        if (android.os.SystemClock.uptimeMillis() - shownAt < 350L && st.panel == "keys") return
        endFlash()
        st.keyTick++
        val c = currentInputConnection ?: return
        if (s.isEmpty()) return
        // the emoji search is open: the keys type into its box
        st.emojiQuery?.let { if (st.panel == "emoji") { st.emojiQuery = it + s; return } }
        st.clipName?.let { if (st.panel == "clip") { if (s.all { ch -> ch.isLetterOrDigit() }) st.clipName = it + s; return } }
        if (st.panel == "vault") { if (s.all { ch -> ch.isLetterOrDigit() || ch in "@.-_ " }) { st.vaultQuery += s; refreshVault() }; return }
        if (st.trans) { st.transIn += s; scheduleTrans(); return }
        if (st.artOn) { st.artIn += s; return }
        // another digit set (a setting): the digit keys type its digits
        if (s.length == 1 && s[0] in '0'..'9' && KeyboardPrefs.numerals != "latin" && !st.plain && !st.numpad) { finishWord("", false); c.commitText(KbNumerals.map(s, KeyboardPrefs.numerals), 1); afterText(); return }
        if (s.length == 1) termKey(s[0])
        if (s.codePointCount(0, s.length) > 1 || Character.isSurrogate(s[0])) { finishWord("", false); c.commitText(s, 1); afterText(); return }
        lastGlide = null
        commaSpace = false
        // the calculator (a setting, off by default): "12*5=" is answered right after the sign
        if (s == "=" && KeyboardPrefs.calcEquals && !st.plain && word.isEmpty()) {
            val before = c.getTextBeforeCursor(60, 0)?.toString().orEmpty()
            // written the way the sum is: "12*5" -> "12*5=60", "12 * 5" -> "12 * 5 = 60"; small raised digits are powers ("2⁶=" -> "=64")
            val ans = KbCalc.answerFor(before)
            if (ans != null) { c.commitText(ans, 1); punctTyped = true; afterText(); return }
            // units: "10 km to mi=" -> "= 6.2137 mi"
            KbUnits.tail(before)?.let { u -> c.commitText("= $u", 1); punctTyped = true; afterText(); return }
        }
        if (s.length == 1 && (st.ctrl > 0 || st.alt > 0 || st.win > 0 || st.shiftMod > 0) && sendChord(s[0])) return
        var ch = s[0]
        val letter = ch.isLetter() || (ch == '\'' && word.isNotEmpty())
        // fast typing: the keys are redrawn a moment after the automatic capital is used, so the next letters can still arrive as capitals; inside the word they stay small
        if (letter && ch.isUpperCase() && st.shift == 0 && word.isNotEmpty() && !st.plain && SystemClock.uptimeMillis() - shiftDropAt < 700) ch = ch.toString().lowercase(lang.locale).first()
        if (letter && st.plain) {
            // a field that wants plain keys (a terminal such as Termux, a password, "no input"): the letter goes in at once as a committed character, with no open word.
            // A word kept open (setComposingText) and closed again made a terminal write the key twice
            c.commitText(ch.toString(), 1)
            word.clear(); target = null; lastCorrection = null; punctTyped = false; lastWasSpace = false
            if (st.shift == 1) { st.shift = 0; shiftDropAt = SystemClock.uptimeMillis() }
            return
        }
        if (letter) {
            // a comma right before a letter, behind a word: a slip of the finger; the comma goes and the word is reopened
            if (punctTyped && ch.isLetter() && KeyboardPrefs.commaSlip && !st.plain && word.isEmpty()) {
                val before = c.getTextBeforeCursor(40, 0)?.toString().orEmpty()
                if (before.length >= 3 && before.last() == ',' && before[before.length - 2].isLetter()) {
                    var n = 1
                    while (n < before.length && before[before.length - 1 - n].isLetter()) n++
                    val stem = before.substring(before.length - n, before.length - 1)
                    c.deleteSurroundingText(n, 0); word.append(stem); punctTyped = false
                }
            }
            if (punctTyped && KeyboardPrefs.autoSpace && !st.plain && ch.isLetter() && !spaceAfterCursor(c)) {
                c.commitText(" ", 1)
            }
            // behind a full stop, ! or ? the next sentence starts with a capital (the space is already there)
            if (dotEnd && KeyboardPrefs.autoCap && word.isEmpty() && ch.isLetter() && !st.plain) ch = ch.toString().uppercase(lang.locale).first()
            dotEnd = false
            dotFix = false
            if (word.isEmpty() && !punctTyped && ch.isLetter() && !st.plain && !emailField && !urlNow() && KeyboardPrefs.autoSpace) {
                val bt = c.getTextBeforeCursor(60, 0)?.toString().orEmpty()
                if (bt.length >= 4 && bt.last() == '.') {
                    val token = bt.takeLastWhile { !it.isWhitespace() }
                    val stem = token.dropLast(1)
                    dotFix = stem.length >= 3 && stem.all { it.isLetter() } && !stem.startsWith("www", true)
                }
            }
            punctTyped = false; lastWasSpace = false
            // the field must still hold the open word right before the cursor; if the app changed it or moved the cursor, the word is closed as it is, and a new one starts
            if (word.isNotEmpty()) {
                val tb = c.getTextBeforeCursor(word.length, 0)?.toString()
                if (tb != word.toString()) { c.finishComposingText(); word.clear(); peak = null; touchPts.clear(); target = null }
            }
            editAt = SystemClock.uptimeMillis()
            if (word.isEmpty()) rhythm.reset()
            rhythm.onKeystroke(SystemClock.uptimeMillis())
            word.append(ch)
            target = null
            // the touch model: where this letter was touched, and the finger's drift from the key's centre is learned
            val tp = st.touch; st.touch = null
            touchPts.add(tp); if (touchPts.size > 48) touchPts.removeAt(0)
            if (tp != null) st.letterRects[KeyboardLayouts.lower(ch.toString(), st.lang)]?.let { r -> KeyboardDictionary.learnTouch(KeyboardLayouts.lower(ch.toString(), st.lang).first(), tp, r.center, r.width) }
            if (lang.code == "tr") apostropheAfterName(c)
            c.setComposingText(word, 1)
            lastCorrection = null
            if (st.shift == 1) { st.shift = 0; shiftDropAt = SystemClock.uptimeMillis() }
            updateSuggestions()
            // the words just before this one are looked at again as soon as its second letter is typed (not only when it ends): one left wrong is mended while the typing goes on
            if (word.length == 2 && KeyboardPrefs.autocorrect && KeyboardPrefs.suggestions) sweepBack()
        } else {
            // a second comma right behind a comma (or the comma and its space) is dropped: one comma stays
            if (ch == ',' && word.isEmpty() && c.getTextBeforeCursor(2, 0)?.toString()?.let { it.endsWith(",") || it == ", " } == true) return
            // a word is only mended when the space key ends it: a period, comma or bracket leaves what was typed as it is
            // a full stop behind a word of the dictionary ends a sentence (not in addresses, passwords or numbers): the space and the capital come with the next letter
            val before = word.toString().lowercase(lang.locale)
            // the names of well-known sites are not sentence ends ("youtube." goes on as "youtube.com")
            val dotOk = ch == '.' && before.length >= 3 && before !in siteNames && KeyboardDictionary.isKnown(before) && !st.plain && !emailField && !urlNow() && KeyboardPrefs.autoSpace
            if (!finishWord(s, false, s)) c.commitText(s, 1)
            punctTyped = ch in ",;!?:" || dotOk
            dotEnd = ch in "!?" || dotOk
            // a mark behind a word (, ; : ! ? or a full stop after a known word) is followed by a space at once (not in numbers, addresses, passwords or web addresses)
            // a space is added only when it is certain: a ':' or ';' (emoticons, times, code) only behind a word of the dictionary
            val sure = ch !in ";:" || KeyboardDictionary.isKnown(before)
            if ((ch == ',' || ch in ";:!?" || dotOk) && sure && !st.plain && !emailField && !urlNow() && c.getTextBeforeCursor(2, 0)?.getOrNull(0)?.isLetter() == true && !spaceAfterCursor(c)) {
                c.commitText(" ", 1); commaSpace = true; punctTyped = false
            }
            lastWasSpace = false
            if (ch in ".!?") lastCorrection = null
            prevWord = null; prevWord2 = null
            afterText()
        }
    }

    /** Ends the word under way: commits it, corrected when the correction applies. [sep] is what the user typed after it. */
    private fun finishWord(sep: String, correct: Boolean, tail: String = ""): Boolean {
        val c = currentInputConnection ?: return false
        if (word.isEmpty()) return false
        val typed = word.toString()
        // the field no longer holds the word before the cursor (moved by the app or the user): nothing is replaced, so nothing is lost
        if (c.getTextBeforeCursor(typed.length, 0)?.toString() != typed) {
            c.finishComposingText(); word.clear(); target = null; peak = null; lastCorrection = null; return false
        }
        // a web address typed as "site.com": the full stop behind a known word (google) had started a new sentence (a space and a capital: "google. Com"); when the ending of an address
        // (com, net, org, com.tr ...) then comes, the space goes and the ending stays small
        if (!st.plain && !emailField && !st.incognito && typed.lowercase(lang.locale) in tlds) {
            val bt = c.getTextBeforeCursor(typed.length + 60, 0)?.toString().orEmpty()
            if (bt.endsWith(". $typed")) {
                val stem = bt.dropLast(typed.length + 2).takeLastWhile { !it.isWhitespace() }
                if (stem.length >= 2 && stem.all { it.isLetterOrDigit() || it == '-' || it == '.' }) {
                    c.beginBatchEdit(); c.finishComposingText(); c.deleteSurroundingText(typed.length + 1, 0); c.commitText(typed.lowercase(lang.locale), 1)
                    if (tail.isNotEmpty()) c.commitText(tail, 1)
                    c.endBatchEdit()
                    word.clear(); target = null; peak = null; lastCorrection = null; punctTyped = false; dotEnd = false; dotFix = false
                    return true
                }
            }
        }
        var out = typed
        // a word that is part of an address (after an "@") is never corrected: "gmail" and "com" stay as typed
        val inAddr = c.getTextBeforeCursor(80, 0)?.toString().orEmpty().takeLastWhile { !it.isWhitespace() }.contains('@')
        // a correction is only made to the word the bar shows in the middle for exactly this typed word; an old suggestion (the bar had not caught up with the last letters) is never used
        // a word with an apostrophe (Adana'ya) is kept as it is: the dictionary's suggestion for it has no apostrophe
        var timedOut = false
        var pending: java.util.concurrent.Future<String?>? = null
        val pwNow = prevWord; val pw2Now = prevWord2; val upNow = st.shift > 0; val codeNow = st.lang; val locNow = lang.locale; val ctxNow = lmCtx
        var t = if (inAddr || typed.contains('\'')) null else target?.takeIf { targetFor.equals(typed, true) && slotWords.getOrNull(1).equals(it, true) }
        // typing fast: the bar had no time to catch up with the word (every new letter restarts its work), so the correction is worked out right now, here, for exactly this word
        if (t == null && correct && !inAddr && typed.length >= 2 && !typed.contains('\'') && KeyboardPrefs.autocorrect && KeyboardPrefs.suggestions && !st.plain && !urlNow() && !targetFor.equals(typed, true))
            t = runCatching {
                // never longer than a few frames: the work runs on its own thread and is waited for only briefly, so typing cannot hang here. When it is not
                // done in time it is NOT thrown away: the same work goes on and mends the word the moment it is ready (lateFix), so the word is not left to be mended later
                val code = st.lang; val loc = lang.locale; val pw = prevWord; val pw2 = prevWord2; val up = st.shift > 0
                // (the work is not stopped by the next key: it is this word's correction, wanted whatever is typed after it)
                val f = corrExec.submit<String?> { KeyboardDictionary.uncancelled { compute(code, loc, typed, pw, up, pw2, ctxText = ctxNow).third } }
                // (Enter sends the text: the correction of the last word is waited for longer, so it is mended before the message goes)
                try { f.get(if (sep == "\n") 500L else 70L, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (e: java.util.concurrent.TimeoutException) { pending = f; timedOut = true; null }
            }.getOrNull()
        // "word.next" typed together: a space (and a capital) after the period, unless the next word is a domain ending, ends with a dot itself, or is no word at all
        var spaceBefore = false
        if (dotFix) {
            dotFix = false
            val lw = typed.lowercase(lang.locale)
            if (!inAddr && sep != "." && typed.length >= 2 && lw !in tlds && KeyboardDictionary.isKnown(lw)) spaceBefore = true
        }
        // the hybrid correction (a setting, off by default): WM Keyboard's rules on top of Pulse's own
        if (correct && KeyboardPrefs.hybridFix && KeyboardPrefs.autocorrect && !inAddr && !st.plain && !urlNow()) {
            val lw = typed.lowercase(lang.locale)
            val known = KeyboardDictionary.isWord(lang.code, lw) || KeyboardDictionary.isKnown(lw)
            // typed slowly and carefully (a name, a foreign word): not rewritten silently, it stays in the bar to be picked
            if (rhythm.multiplier(1f) >= 1.3) t = null
            // WM Keyboard's own engine (its fuzzy search over the same word list): when both engines name the same word, the fix is sure;
            // when Pulse found nothing, WM's clear one-letter fix is taken. When they disagree Pulse's stays (it reads the sentence around the word).
            val dl = KeyboardLayouts.dict(st.lang); WmFix.warm(this, dl)
            var agree = false
            if (!known && typed.length >= 3) {
                val wm = WmFix.search(dl, lw, 3)
                val top = wm.firstOrNull()?.takeIf { it.word != lw }
                if (top != null) {
                    if (t?.lowercase(lang.locale) == top.word) agree = true
                    else if (t == null && top.edits <= 1 && top.word.length >= 3 && (wm.getOrNull(1)?.let { top.score - it.score >= 1.5 } ?: true)) {
                        t = if (typed[0].isUpperCase()) top.word.replaceFirstChar { it.titlecase(lang.locale) } else top.word
                    }
                }
            }
            // the confidence gate (fitted for Pulse in the way of WM Keyboard's): a fix it is not sure of stays in the bar instead of replacing the word
            if (!agree) t?.let { cand ->
                if (' ' !in cand) {
                    val lc = cand.lowercase(lang.locale)
                    val run = slotWords.firstOrNull { it.isNotBlank() && !it.equals(cand, true) && !it.equals(typed, true) && it.none { ch -> ch.isWhitespace() } }?.lowercase(lang.locale)
                    // a Turkish word with endings is not in the list itself (only its base): it is scored as an ordinary word of the list
                    fun sc(x: String) = KeyboardDictionary.lexScore(lang.code, x).let { v -> if (v <= 0f && KeyboardDictionary.isWord(lang.code, x)) 8f else v }
                    val p = CorrectionGate.probability(lw, lc, sc(lc), run, run?.let { sc(it) } ?: 0f)
                    if (p < CorrectionGate.THRESHOLD) t = null
                }
            }
            // many corrections were undone lately: only a one-letter fix is made silently
            if (t != null && hybridStrict() && editDistance(typed.lowercase(lang.locale), t!!.lowercase(lang.locale)) > 1) t = null
            // never rewritten: short words, all-capital words (acronyms), words with two or more digits
            if (typed.length < 3 || (typed.length >= 2 && typed.all { !it.isLetter() || it.isUpperCase() }) || typed.count { it.isDigit() } >= 2) t = null
            else if (!known && t == null) {
                // a letter held down too long: "helllo" -> "hello"
                val two = lw.replace(Regex("(.)\\1{2,}"), "$1$1"); val one = lw.replace(Regex("(.)\\1{2,}"), "$1")
                t = when { two != lw && KeyboardDictionary.isKnown(two) -> two; one != lw && KeyboardDictionary.isKnown(one) -> one; else -> null }
                // a missed space: two known words run together ("nasılsınbugün" -> "nasılsın bugün"), the split nearest the middle wins
                if (t == null && lw.length >= 5) {
                    t = (2..lw.length - 2).sortedBy { kotlin.math.abs(it - lw.length / 2) }
                        .firstOrNull { i -> KeyboardDictionary.isKnown(lw.substring(0, i)) && KeyboardDictionary.isKnown(lw.substring(i)) && lw.substring(0, i).length >= 2 && lw.substring(i).length >= 2 }
                        ?.let { i -> lw.substring(0, i) + " " + lw.substring(i) }
                }
                if (t != null && typed[0].isUpperCase()) t = t!!.replaceFirstChar { it.titlecase(lang.locale) }
            }
        }
        // the same word typed again soon after it was corrected: the person means it, so it is learned as a word and left alone from now on
        if (correct && t != null && !t.equals(typed, true) && typed.length >= 2 && typed.none { it.isDigit() }) {
            val lk = typed.lowercase(lang.locale); val at = recentFixed[lk]
            if (at != null && SystemClock.uptimeMillis() - at < 300_000L) {
                recentFixed.remove(lk); t = null
                if (KeyboardPrefs.learn && !st.incognito) KeyboardDictionary.learnWord(typed, 4)
            }
        }
        if (correct && KeyboardPrefs.autocorrect && !KeyboardPrefs.practicing && !st.plain && !urlNow() && (!noAuto || uriField) && t != null && !t.equals(typed, true) && !KeyboardDictionary.isKept(typed) && !KeyboardDictionary.rejectedAtLeast(typed.lowercase(lang.locale), KeyboardPrefs.undoToKeep)) out = t
        // a name typed in lower case takes its capital when the word is ended: adana becomes Adana (words that are also ordinary words do not)
        if (lang.code == "tr" && out == typed && typed.none { it.isUpperCase() } && typed.lowercase(lang.locale).let { it in KeyboardDictionary.properNouns && it !in KeyboardDictionary.commonNames })
            out = typed.replaceFirstChar { it.titlecase(lang.locale) }
        // English grammar on the word just ended: a lone "i" and its contractions, and the common contractions typed without the apostrophe
        if (lang.code == "en" && out == typed) EN_GRAMMAR[typed.lowercase(Locale.ENGLISH)]?.let { out = it }
        // an English first name or place typed in lower case is written with its capital (wendy -> Wendy, london -> London)
        if (lang.code == "en" && out == typed && typed.none { it.isUpperCase() } && typed.lowercase(Locale.ENGLISH) in KeyboardDictionary.properNamesEn && typed.lowercase(Locale.ENGLISH) !in KeyboardDictionary.commonNames)
            out = typed.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
        // a brand, an app or a creator: written its own way (starbucks -> Starbucks, mcdonalds -> McDonald's, youtube -> YouTube, a letter off too);
        // in Turkish an ending after it takes its apostrophe (starbucksta -> Starbucks'ta)
        if (correct && !inAddr && !urlNow() && !st.plain && KeyboardPrefs.autocorrect && !KeyboardDictionary.isKept(typed)) {
            val lw = typed.lowercase(lang.locale)
            val known = KeyboardDictionary.isWord(lang.code, lw)
            val b = KeyboardDictionary.brandFor(typed, known) ?: KeyboardDictionary.brandFor(out, KeyboardDictionary.isWord(lang.code, out.lowercase(lang.locale)))
                ?.takeIf { KeyboardDictionary.brandKey(it) == KeyboardDictionary.brandKey(out) }
            if (b != null) out = b
            else if (lang.code == "tr" && !known) {
                // the longest brand the word starts with, and a Turkish ending after it
                val k = KeyboardDictionary.brandKey(typed)
                for (n in k.length - 1 downTo 3) {
                    val form = KeyboardDictionary.brands[k.substring(0, n)] ?: continue
                    val rest = k.substring(n)
                    if (rest in TR_ENDINGS) { out = "$form'$rest"; break }
                }
            }
        }
        if (correct && out != typed && !out.equals(typed, true)) { hybridCount(true); recentFixed[typed.lowercase(lang.locale)] = SystemClock.uptimeMillis(); if (recentFixed.size > 40) recentFixed.remove(recentFixed.keys.first()) }
        // abbreviations and formulas typed in small letters get their capitals back (tbmm -> TBMM, tubitak -> TÜBİTAK, h2o -> H2O)
        if (correct && !inAddr && !st.plain && !st.incognito && !urlNow()) KeyboardDictionary.abbrFix(typed)?.let { out = it }
        // easter eggs: a few made-up words make the keyboard do a trick (the word itself is typed as it is)
        when (typed.lowercase(java.util.Locale.ROOT)) { "dobarrelroll", "barrelroll" -> "roll"; "pulserainbow" -> "rainbow"; "pulseflip" -> "flip"; "pulseparty" -> "party"; else -> null }?.let { st.egg = it; st.eggN++; out = typed }
        if (spaceBefore) {
            c.finishComposingText(); c.deleteSurroundingText(typed.length, 0); c.commitText(" ", 1)
            out = out.replaceFirstChar { it.titlecase(lang.locale) }
        }
        // the word and what follows it (the space or the sign) go in as one batch. The word is first closed where it stands, and only a corrected word is replaced by
        // deleting exactly what was typed: a commit that "replaces the composing text" depends on the app agreeing where that text is, and some fields (web pages,
        // editors that move the region) then wrote the sign over the word, deleted it, or put a second copy beside it
        if (spaceBefore) c.commitText(out + tail, 1)
        else {
            c.beginBatchEdit()
            c.finishComposingText()
            if (out != typed) { c.deleteSurroundingText(typed.length, 0); c.commitText(out, 1) }
            if (tail.isNotEmpty()) c.commitText(tail, 1)
            c.endBatchEdit()
        }
        // safety net: some fields drop or swallow the word in this edit. When the text before the cursor no longer holds the word, it is written back
        // (once, only when it is really missing), so a space never leaves the word gone
        runCatching {
            val got = c.getTextBeforeCursor(out.length + tail.length, 0)?.toString()
            if (got != null && got != out + tail && !got.contains(out)) {
                if (tail.isNotEmpty() && got.endsWith(tail)) c.deleteSurroundingText(tail.length, 0)
                c.commitText(out + tail, 1)
            }
        }
        if (!out.equals(typed, true)) { flashFix(c, typed, out, tail.length); flashLater(out, tail) }
        peak?.let { pk ->
            val lc = lang.locale
            val a = pk.lowercase(lc); val b = typed.lowercase(lc)
            if (out == typed && KeyboardPrefs.learn && !st.plain && !st.incognito && a != b && a.length >= 3 && b.length >= 3 && Math.abs(a.length - b.length) <= 2 &&
                (KeyboardDictionary.similar(a, b) || KeyboardDictionary.similarLoose(a, b)) && KeyboardDictionary.isWord(st.lang, b)) { KeyboardDictionary.learnTypo(this, a, b); learnChain(a, b) }
        }
        peak = null
        if (nameField && out.length >= 2 && !st.incognito && out.all { it.isLetter() || it == '\'' || it == '-' }) {
            val nm = out.replaceFirstChar { it.titlecase(lang.locale) }
            getSharedPreferences("pulse_kb_names", MODE_PRIVATE).let { p -> p.edit().putString("list", (listOf(nm) + p.getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() && it != nm }).take(30).joinToString("\n")).apply() }
        }
        lastCorrection = if (out != typed) Correction(typed, out, sep) else null
        if (!st.incognito) { KbStats.add(this, "words"); if (out != typed) KbStats.add(this, "fixes") }
        corrAt = SystemClock.uptimeMillis()
        learnFromDelete(out)
        if (out == typed && KeyboardPrefs.learn && !st.plain && !st.incognito) KeyboardDictionary.learn(this, prevWord2, prevWord, typed)
        prevWord2 = prevWord; prevWord = out
        if (!st.incognito && !st.plain && out.length >= 2 && out.all { it.isLetter() || it == '\'' }) { recentWords.remove(out); recentWords.addFirst(out); while (recentWords.size > 6) recentWords.removeLast() }
        word.clear(); target = null
        // typing fast left no time to mend the word: it is mended a moment later, in its place, when nothing new has been started behind it
        if (timedOut && out == typed) lateFix(typed, sep, tail, codeNow, locNow, pwNow, pw2Now, upNow, ctxNow, pending)
        // writing a long message: a word a little further back that was left wrong and has a clear correction is mended now, in its place
        if (correct && (sep == " " || sep == "") && KeyboardPrefs.autocorrect && KeyboardPrefs.suggestions) sweepBack()
        return true
    }

    private val swept = LinkedHashMap<String, Int>()

    /**
     * Looks at the words just before the one that was ended (the last three): a word that is no word of the list, nothing like a name or a code, and has a clear correction is mended in
     * place with the same highlight as any other correction. A word is looked at once (a word the person put back is never touched again).
     */
    private fun sweepBack() {
        val c = currentInputConnection ?: return
        if (st.plain || urlNow() || emailField || KeyboardPrefs.practicing || st.incognito) return
        val before = c.getTextBeforeCursor(140, 0)?.toString() ?: return
        val ms = Regex("\\p{L}+").findAll(before.dropLast(1)).toList()
        // the word just ended is the last one; the three before it are the candidates
        val cands = ms.dropLast(1).takeLast(4)
        val codeNow = st.lang; val locNow = lang.locale
        for (m in cands) {
            val w = m.value
            val lw = w.lowercase(locNow)
            if (w.length < 3 || w.any { it.isUpperCase() } || KeyboardDictionary.isKept(w)) continue
            val end = m.range.last + 1
            // a word followed by a single space, which is what lateFix looks for
            if (before.getOrNull(end) != ' ') continue
            if (dictCodes(codeNow).any { KeyboardDictionary.isWord(it, lw) || KeyboardDictionary.inLexicon(it, lw) } || KeyboardDictionary.userCount(lw) > 0) continue
            val key = lw + "|" + before.substring(maxOf(0, m.range.first - 10), m.range.first)
            // a word is looked at up to three times: while the next word starts, when it ends, and a few words later (more of the sentence to go on)
            val seen = swept[key] ?: 0
            if (seen >= 3) continue
            swept[key] = seen + 1
            if (swept.size > 300) swept.remove(swept.keys.first())
            val ctx = before.substring(0, m.range.first)
            val words = Regex("\\p{L}+").findAll(ctx).map { it.value }.toList()
            lateFix(w, " ", " ", codeNow, locNow, words.lastOrNull(), words.getOrNull(words.size - 2), false, ctx)
        }
    }

    /** Works the correction out for a word that was left uncorrected (typing fast) and puts it in its place, only when the text before the cursor still ends with it and no word is open. */
    /** Counts the fields and the sends: a late correction belongs to the field and the moment it was started in, never to a later one (a sent message, a new field). */
    private var editEpoch = 0

    private fun lateFix(typed: String, sep: String, tail: String, code: String, loc: Locale, pw: String?, pw2: String?, up: Boolean, ctx: String = lmCtx, pending: java.util.concurrent.Future<String?>? = null) {
        if (!KeyboardPrefs.autocorrect || !KeyboardPrefs.suggestions) return
        val epoch = editEpoch
        scope.launch {
            // the work that was already running goes on (nothing is started again); a new run only when there was none
            val fix = withContext(Dispatchers.Default) {
                runCatching { if (pending != null) pending.get(3, java.util.concurrent.TimeUnit.SECONDS) else KeyboardDictionary.uncancelled { compute(code, loc, typed, pw, up, pw2, ctxText = ctx).third } }.getOrNull()
            } ?: return@launch
            if (epoch != editEpoch) return@launch
            val lo = typed.lowercase(loc); val fl = fix.lowercase(loc)
            if (fl == lo || KeyboardDictionary.isKept(typed) || KeyboardDictionary.rejectedAtLeast(lo, KeyboardPrefs.undoToKeep)) return@launch
            val key = typed + tail
            // the word is mended where it stands, at once: while the next word is being typed too (its letters, still open, are put back behind the mended word
            // as the open word), or when the next word is closed. Only when something else is going on (a selection, another edit) does it wait a moment.
            var tries = 0
            while (tries++ < 30) {
                // a send or a new field since the word was typed: the fix is dropped, the text stays as it was sent
                if (epoch != editEpoch) return@launch
                val c = currentInputConnection ?: return@launch
                if (st.plain) return@launch
                val before = c.getTextBeforeCursor(160, 0)?.toString() ?: return@launch
                val at = before.lastIndexOf(key)
                if (at < 0) return@launch
                val rest = before.substring(at + key.length)
                if (rest.length > 60) return@launch
                val open = word.toString()
                if (open.isEmpty() || rest == open) {
                    c.beginBatchEdit(); c.deleteSurroundingText(key.length + rest.length, 0); c.commitText(fix + tail, 1)
                    if (rest.isNotEmpty()) { if (open.isEmpty()) c.commitText(rest, 1) else c.setComposingText(open, 1) }
                    c.endBatchEdit()
                    flashFix(c, typed, fix, tail.length + rest.length)
                    if (rest.isEmpty()) flashLater(fix, tail)
                    if (!st.incognito) KbStats.add(this@PulseKeyboardService, "fixes")
                    if (rest.isEmpty()) { lastCorrection = Correction(typed, fix, sep); corrAt = SystemClock.uptimeMillis() }
                    return@launch
                }
                kotlinx.coroutines.delay(60)
            }
        }
    }

    /**
     * The mended word lights up for a moment in the text field (the field's own highlight of a correction), so a word mended while the typing goes on is seen.
     * [after] is how many characters stand between the end of the word and the cursor.
     */
    private fun flashFix(c: android.view.inputmethod.InputConnection, old: String, new: String, after: Int) {
        // the grey behind the word takes the place of the phone's blue flash (both at once was a long blue band)
        if (!KeyboardPrefs.fixFlash || KeyboardPrefs.fixSelect) return
        runCatching {
            val end = (if (android.os.Build.VERSION.SDK_INT >= 31) c.getSurroundingText(1, 0, 0)?.let { it.offset + it.selectionStart } else null)
                ?: c.getExtractedText(smallExtract(), 0)?.let { it.startOffset + it.selectionStart } ?: return
            val at = end - after - new.length
            if (at >= 0) c.commitCorrection(android.view.inputmethod.CorrectionInfo(at, old, new))
        }
    }

    // ---- the mended word is selected for a moment (it shows highlighted, as if picked), then the cursor goes back behind it -------------------------------------------
    private var flash: IntArray? = null   // start, end of the word and where the cursor goes back to
    private var flashWord: String? = null  // the word shown with the grey behind it (put back plain when the moment ends)
    private val flashHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val flashEnd = Runnable { endFlash() }
    private val pauseHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Puts the cursor back where it was (any key does this first, so a typed letter never replaces the highlighted word). */
    private fun endFlash() {
        pauseHandler.removeCallbacksAndMessages(null)   // every key cancels the correction that waits for a pause in the typing
        val f = flash ?: return
        flash = null; flashHandler.removeCallbacks(flashEnd)
        runCatching {
            val c = currentInputConnection ?: return@runCatching
            // the soft grey behind the word goes: the same word again without it, closed where it is, and the cursor back where it was
            val w = flashWord
            if (w != null) { c.beginBatchEdit(); c.setComposingRegion(f[0], f[1]); c.setComposingText(w, 1); c.finishComposingText(); c.setSelection(f[2], f[2]); c.endBatchEdit() }
            else c.setSelection(f[2], f[2])
            selStart = f[2]; selEnd = f[2]
        }
        flashWord = null
    }

    /** After the key's work is done: the corrected [word] (with [tail] behind it, at the cursor) lights up for most of a second. */
    private fun flashLater(word: String, tail: String) {
        if (!KeyboardPrefs.fixSelect || st.plain) return
        flashHandler.post {
            runCatching {
                val c = currentInputConnection ?: return@post
                endFlash()
                if (c.getSelectedText(0)?.isNotEmpty() == true) return@post
                // the text before the cursor is still exactly the word and what followed it: nothing new was typed meanwhile
                val before = c.getTextBeforeCursor(word.length + tail.length, 0)?.toString() ?: return@post
                if (before != word + tail) return@post
                val end = (if (android.os.Build.VERSION.SDK_INT >= 31) c.getSurroundingText(1, 0, 0)?.let { it.offset + it.selectionStart } else null)
                    ?: c.getExtractedText(smallExtract(), 0)?.let { it.startOffset + it.selectionStart } ?: return@post
                val ws = end - tail.length - word.length
                if (ws < 0) return@post
                // a short, soft grey behind the word (the field's selection was a long, bright blue): the word is put back with a light background for a moment
                val tint = android.text.SpannableString(word).apply { setSpan(android.text.style.BackgroundColorSpan(0x40909090), 0, word.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                c.beginBatchEdit()
                val ok = c.setComposingRegion(ws, ws + word.length) && c.setComposingText(tint, 1)
                c.setSelection(end, end)
                c.endBatchEdit()
                if (ok) {
                    flash = intArrayOf(ws, ws + word.length, end); flashWord = word
                    flashHandler.postDelayed(flashEnd, 450L)
                }
            }
        }
    }

    /**
     * The typing stops with a word that can only be a slip (nothing is spelled with its letters, and the middle suggestion is a clear correction): a third of a second later it is
     * mended without a space, so a message sent with the app's own button (not the keyboard's Enter) goes out corrected too. A word that is still the start of a real word is left.
     */
    private fun schedulePauseFix(typed: String, fix: String?, code: String, loc: Locale) {
        pauseHandler.removeCallbacksAndMessages(null)
        // a word is mended only when the space key ends it: a pause in the typing (or anything else) never changes what was written
        if (true) return
        if (fix == null || !KeyboardPrefs.pauseFix || !KeyboardPrefs.autocorrect || typed.length < 4 || st.plain || urlNow() || emailField || KeyboardPrefs.practicing) return
        if (KeyboardDictionary.complete(dictCodes(code).first(), typed.lowercase(loc), 1).isNotEmpty()) return
        pauseHandler.postDelayed({
            if (word.toString() == typed && target != null && targetFor.equals(typed, true)) { finishWord("", true); afterText() }
        }, 350L)
    }

    private fun afterText() { updateCaps(); updateSuggestions() }

    override fun space() {
        endFlash()
        val c = currentInputConnection ?: return
        termKey(' ')
        captureEmail(c); commitNumber()
        st.emojiQuery?.let { if (st.panel == "emoji") { if (it.isNotEmpty() && !it.endsWith(" ")) st.emojiQuery = "$it "; return } }
        if (st.clipName != null && st.panel == "clip") return
        if (st.panel == "vault") return
        if (st.trans) { if (st.transIn.isNotEmpty() && !st.transIn.endsWith(" ")) st.transIn += " "; return }
        if (st.artOn) { st.artIn += " "; return }
        val now = SystemClock.uptimeMillis()
        if (word.isEmpty() && lastWasSpace && KeyboardPrefs.quickPeriod && !st.plain && now - spaceTap < 600) {
            val before = c.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                c.deleteSurroundingText(1, 0); c.commitText(". ", 1)
                lastWasSpace = false; punctTyped = false; afterText(); return
            }
        }
        // the predicted word is only added when the field really ends in the word we remember (not after a sign, a deleted word, a paste or a moved cursor):
        // otherwise Space would write words the user never asked for
        if (word.isEmpty() && KeyboardPrefs.quickPrediction && !st.plain && slotWords[1].isNotEmpty() && prevWord != null && c.getSelectedText(0).isNullOrEmpty()) {
            val pwQ = prevWord ?: ""
            val tail = c.getTextBeforeCursor(pwQ.length + 1, 0)?.toString().orEmpty()
            val ends = pwQ.isNotEmpty() && tail.length == pwQ.length + 1 && tail.last() == ' ' && tail.dropLast(1).equals(pwQ, true)
            if (ends && !punctTyped) {
                c.commitText(slotWords[1] + " ", 1); prevWord = slotWords[1]; lastWasSpace = true; spaceTap = now; afterText(); return
            }
        }
        // the correction and the space go in as one edit, so the field never shows the word gone for a moment
        if (!finishWord(" ", true, " ")) c.commitText(" ", 1)
        lastWasSpace = true; spaceTap = now; punctTyped = false
        afterText()
    }

    override fun enter() {
        endFlash()
        val c = currentInputConnection ?: return
        termKey('\n')
        if (st.clipName != null && st.panel == "clip") { clipShortcutSave(); return }
        if (st.panel == "vault") return
        if (st.trans) { transOp("apply"); return }
        captureEmail(c); commitNumber()
        runCatching { trackLogin() }
        // Enter (send) ends the word as it stands: only the space key mends a word; a correction still being worked out for an earlier word is dropped
        editEpoch++
        finishWord("\n", false)
        if (st.enter != "newline") {
            val info = currentInputEditorInfo
            c.performEditorAction(info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_DONE)
        } else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        lastWasSpace = false; punctTyped = false; prevWord = null
        afterText()
    }

    override fun backspace() {
        endFlash()
        val c = currentInputConnection ?: return
        termKey('\b')
        st.emojiQuery?.let { if (st.panel == "emoji") { st.emojiQuery = it.dropLast(1); return } }
        st.clipName?.let { if (st.panel == "clip") { st.clipName = it.dropLast(1); return } }
        if (st.panel == "vault") { st.vaultQuery = st.vaultQuery.dropLast(1); refreshVault(); return }
        if (st.trans && st.transIn.isNotEmpty()) { st.transIn = st.transIn.dropLast(1); scheduleTrans(); return }
        if (st.artOn) { if (st.artIn.isNotEmpty()) st.artIn = st.artIn.dropLast(1); return }
        lastWasSpace = false
        numPending = null
        lastGlide?.let { g ->
            val before = c.getTextBeforeCursor(g.length + 1, 0)?.toString()
            lastGlide = null
            if (before == "$g ") { c.deleteSurroundingText(g.length + 1, 0); afterText(); return }
        }
        // right after an automatic correction, Backspace takes it back: the word as it was typed opens again, and that correction is remembered as unwanted,
        // so the same word is not corrected the same way the next time
        val lc = lastCorrection
        // only a correction made by the space key is taken back: a comma or full stop is deleted by itself
        if (lc != null && lc.sep == " " && word.isEmpty() && c.getSelectedText(0).isNullOrEmpty()) {
            val n = lc.corrected.length + lc.sep.length
            if (c.getTextBeforeCursor(n, 0)?.toString() == lc.corrected + lc.sep) {
                lastCorrection = null
                if (!st.incognito) KeyboardDictionary.reject(this, lc.original, lc.corrected)
                if (!st.incognito) KbStats.add(this, "undone"); hybridCount(false)
                c.beginBatchEdit(); c.deleteSurroundingText(n, 0); c.setComposingText(lc.original, 1); c.endBatchEdit()
                editAt = SystemClock.uptimeMillis()
                word.append(lc.original); touchPts.clear(); target = null; peak = null
                updateSuggestions(); return
            }
        }
        if (word.isNotEmpty()) {
            editAt = SystemClock.uptimeMillis()
            if (peak == null && word.length >= 3) peak = word.toString()
            word.deleteCharAt(word.length - 1)
            if (touchPts.isNotEmpty()) touchPts.removeAt(touchPts.size - 1)
            if (word.isEmpty()) c.commitText("", 1) else c.setComposingText(word, 1)
            lastCorrection = null; target = null
            updateCaps(); updateSuggestions(); return
        }
        // the space right behind a word goes: the word is open again, with its suggestions
        val tb = c.getTextBeforeCursor(60, 0)?.toString().orEmpty()
        if (tb.length >= 2 && tb.last() == ' ' && tb[tb.length - 2].isLetter() && c.getSelectedText(0).isNullOrEmpty() && !st.plain && KeyboardPrefs.suggestions) {
            val w = tb.dropLast(1).takeLastWhile { it.isLetter() || it == '\'' }
            if (w.isNotEmpty() && w.length < 40) {
                val before = tb.dropLast(1 + w.length).trimEnd().takeLastWhile { it.isLetter() || it == '\'' }
                c.beginBatchEdit(); c.deleteSurroundingText(w.length + 1, 0); c.setComposingText(w, 1); c.endBatchEdit()
                word.setLength(0); word.append(w); touchPts.clear(); target = null; lastCorrection = null
                prevWord = before.ifEmpty { null }?.let { KeyboardLayouts.lower(it, st.lang) }
                updateCaps(); updateSuggestions(); return
            }
        }
        if (commaSpace && tb.length >= 2 && tb.last() == ' ' && tb[tb.length - 2] in ",;:!?." && c.getSelectedText(0).isNullOrEmpty()) { commaSpace = false; c.deleteSurroundingText(1, 0); dotEnd = false; dotFix = false; punctTyped = false }
        else if (c.getSelectedText(0)?.isNotEmpty() == true) c.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        // text was deleted that the remembered words may have belonged to: they are forgotten, so no old prediction is written back
        prevWord = null; prevWord2 = null; lastCorrection = null; lastGlide = null
        updateCaps(); updateSuggestions()
    }

    // ---- keys of the keyboard ----------------------------------------------------------------------------------------------------------------------

    private var lastShiftTap = 0L
    /** the shift is on only because a sentence began (not pressed); the tap that turns it off then counts as the first of a caps-lock double tap */
    private var autoShift = false
    private var autoOffTap = false

    override fun shift() {
        // one tap: the next letter is capital; a tap again turns it off. Two quick taps (the second within 400 ms of the first) turn on caps lock; a tap turns it off again.
        // When the capital came by itself at a sentence start, the first tap turns it off and a second quick tap makes caps lock, as if the user had pressed shift twice
        val now = android.os.SystemClock.uptimeMillis()
        val quick = now - lastShiftTap < 400
        val wasAuto = st.shift == 1 && autoShift
        st.shift = when {
            st.shift == 2 -> 0
            st.shift == 1 && quick -> 2
            st.shift == 0 && quick && autoOffTap -> 2
            st.shift == 0 -> 1
            else -> 0
        }
        autoOffTap = wasAuto && st.shift == 0
        autoShift = false
        lastShiftTap = now
        // the bar follows the shift key at once: an open word shows (or drops) its capital-letter form, and with no word open the next words (portakal) turn to capitals (Portakal)
        updateSuggestions()
    }

    override fun page(p: Int) { finishWord("", false); st.page = p; st.panel = "keys"; if (p == 0) updateCaps() }

    override fun emojiPanel() { finishWord("", false); st.emojiQuery = null; if (st.panel != "emoji") st.emojiTab = if (st.recents.isEmpty()) 1 else 0; st.panel = if (st.panel == "emoji") "keys" else "emoji"; st.toolbar = false }

    override fun lang() {
        val l = KeyboardPrefs.langList()
        finishWord("", false)
        st.lang = l[(l.indexOf(st.lang) + 1).mod(l.size)]
        scope.launch(Dispatchers.IO) { KeyboardDictionary.load(this@PulseKeyboardService, st.lang); withContext(Dispatchers.Main) { updateSuggestions() } }
        if (KeyboardPrefs.perAppLang) currentInputEditorInfo?.packageName?.let { pk -> getSharedPreferences("pulse_kb_perapp", MODE_PRIVATE).edit().putString(pk, st.lang).apply() }
    }

    // ---- autofill inside the bar (Android 11+): the password manager's suggestions, drawn by the system -----------------------------------------------

    @androidx.annotation.RequiresApi(30)
    override fun onCreateInlineSuggestionsRequest(uiExtras: android.os.Bundle): android.view.inputmethod.InlineSuggestionsRequest? {
        if (!KeyboardPrefs.inlineAutofill) return null
        val d = resources.displayMetrics.density
        // white text on a transparent chip: the keyboard draws the grey rounded back behind it
        val chip = androidx.autofill.inline.common.ViewStyle.Builder().setBackgroundColor(android.graphics.Color.TRANSPARENT).build()
        val title = androidx.autofill.inline.common.TextViewStyle.Builder().setTextColor(android.graphics.Color.WHITE).build()
        val sub = androidx.autofill.inline.common.TextViewStyle.Builder().setTextColor(0xFFBBBBBB.toInt()).build()
        val styles = androidx.autofill.inline.UiVersions.newStylesBuilder().addStyle(androidx.autofill.inline.v1.InlineSuggestionUi.newStyleBuilder()
            .setChipStyle(chip).setTitleStyle(title).setSubtitleStyle(sub).build()).build()
        val spec = android.widget.inline.InlinePresentationSpec.Builder(android.util.Size((100 * d).toInt(), (38 * d).toInt()), android.util.Size((260 * d).toInt(), (42 * d).toInt())).setStyle(styles).build()
        return android.view.inputmethod.InlineSuggestionsRequest.Builder(listOf(spec)).setMaxSuggestionCount(4).build()
    }

    @androidx.annotation.RequiresApi(30)
    override fun onInlineSuggestionsResponse(response: android.view.inputmethod.InlineSuggestionsResponse): Boolean = try { inlineResponse(response) } catch (e: Throwable) { st.inline = emptyList(); false }

    @androidx.annotation.RequiresApi(30)
    private fun inlineResponse(response: android.view.inputmethod.InlineSuggestionsResponse): Boolean {
        val list = response.inlineSuggestions
        if (list.isEmpty()) { st.inline = emptyList(); return false }
        val d = resources.displayMetrics.density
        val size = android.util.Size((260 * d).toInt(), (42 * d).toInt())
        val views = arrayOfNulls<android.view.View>(list.size)
        var left = list.size
        val main = ContextCompat.getMainExecutor(this)
        list.forEachIndexed { i, sug ->
            sug.inflate(this, size, main) { v ->
                views[i] = v
                if (--left == 0) st.inline = views.filterNotNull()
            }
        }
        return true
    }

    override fun longFeedback() {
        if (!KeyboardPrefs.haptic) return
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
        if (!vib.hasVibrator()) return
        if (android.os.Build.VERSION.SDK_INT >= 26) vib.vibrate(android.os.VibrationEffect.createOneShot(28, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vib.vibrate(28)
    }

    override fun slotLong(i: Int) {
        val w = slotWords.getOrNull(i).orEmpty()
        // the copied item in the right slot: held, a preview opens above the bar (the text in full, the picture bigger, the video playing)
        val sl = st.slots.getOrNull(i).orEmpty()
        if (sl.startsWith("\u0002")) { st.previewShot = true; return }
        if (sl.startsWith("\u0001") && !sl.startsWith("\u0001🔑")) { quickClip?.let { st.previewText = it; return } }
        // the address in the left slot of an empty box: held, all the saved addresses come up on the bar to pick from
        if (i == 0 && word.isEmpty() && '@' in w && emailRe.matches(w)) {
            val all = emailsSaved().filter { !KeyboardDictionary.isBanned(it) }.take(8)
            if (all.size > 1) { slotWords = (all + listOf("", "", "")).take(maxOf(3, all.size)).toTypedArray(); st.slots = slotWords.toList(); st.emailBar = true; return }
        }
        // the quoted word is what was typed: held, it is saved as a right word (never "corrected" again)
        if (w.startsWith("\"") || (w.isEmpty() && i == 0 && st.slots.getOrNull(0)?.startsWith("\"") == true)) {
            val typed = if (word.isNotEmpty()) word.toString() else lastCorrection?.original.orEmpty()
            if (typed.length >= 2 && typed.none { it.isDigit() }) {
                KeyboardDictionary.learnWord(typed, 4)
                lastCorrection?.let { KeyboardDictionary.reject(this, it.original, it.corrected); KbStats.add(this, "undone"); hybridCount(false) }
                android.widget.Toast.makeText(this, com.umutk.pulse.tr("Saved \"$typed\" as a right word", "\"$typed\" doğru kelime olarak kaydedildi"), android.widget.Toast.LENGTH_SHORT).show()
                updateSuggestions()
            }
            return
        }
        // an e-mail address or a domain ("@gmail.com") on the e-mail bar can be blocked too
        if (w.isEmpty() || (!w[0].isLetter() && !(st.emailBar && w[0] == '@')) || st.slots.getOrNull(i)?.startsWith("\u0001") == true) return
        st.banAsk = i
        scope.launch { delay(3000); if (st.banAsk == i) st.banAsk = -1 }
    }

    override fun banSlot(i: Int) {
        val w = slotWords.getOrNull(i).orEmpty()
        st.banAsk = -1
        if (w.isEmpty()) return
        KeyboardDictionary.ban(this, w)
        // the words written last are a source of the bar too: the blocked one leaves them
        recentWords.removeIf { it.equals(w, ignoreCase = true) }
        android.widget.Toast.makeText(this, com.umutk.pulse.tr("Will not suggest \"$w\" again", "\"$w\" bir daha önerilmeyecek"), android.widget.Toast.LENGTH_SHORT).show()
        updateSuggestions()
    }

    /** The answer of the sum before the cursor, offered in the middle slot (written as it stands when tapped). */
    private var ratesAsked = 0L
    /** "35$", "20 eur", "£8": the amount in lira (and "500 tl" in dollars), offered in the middle of the bar; a tap writes it after the amount. The rates are the calculator's (kept on the phone, refreshed every few hours). */
    private fun currencyAnswer(before: String): String? {
        val m = Regex("""(?:^|[\s(])([0-9]+(?:[.,][0-9]{1,2})?)\s?(\$|€|£|usd|eur|gbp|dolar|euro|tl|₺|try)$""", RegexOption.IGNORE_CASE).find(before)
        val (num, sym) = if (m != null) m.groupValues[1] to m.groupValues[2] else {
            val m2 = Regex("""(?:^|[\s(])(\$|€|£)\s?([0-9]+(?:[.,][0-9]{1,2})?)$""").find(before) ?: return null
            m2.groupValues[2] to m2.groupValues[1]
        }
        val v = num.replace(',', '.').toDoubleOrNull() ?: return null
        val from = when (sym.lowercase()) { "$", "usd", "dolar" -> "USD"; "€", "eur", "euro" -> "EUR"; "£", "gbp" -> "GBP"; else -> "TRY" }
        val to = if (from == "TRY") "USD" else "TRY"
        if (com.umutk.pulse.extras.calculator.Rates.updated == 0L) com.umutk.pulse.extras.calculator.Rates.load(this)
        if (System.currentTimeMillis() - com.umutk.pulse.extras.calculator.Rates.updated > 6 * 3600_000L && System.currentTimeMillis() - ratesAsked > 600_000L) { ratesAsked = System.currentTimeMillis(); scope.launch { if (com.umutk.pulse.extras.calculator.Rates.refresh(this@PulseKeyboardService)) updateSuggestions() } }
        val r = com.umutk.pulse.extras.calculator.Rates.rates; val a = r[from] ?: return null; val b = r[to] ?: return null
        val out = v / a * b
        val txt = java.math.BigDecimal(out).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return " ≈ $txt " + if (to == "TRY") "TL" else to
    }

    private var calcText: String? = null

    override fun slot(i: Int) {
        st.banAsk = -1
        calcText?.let { a -> if (i == 1 && st.slots.getOrNull(1) == a.trim()) { finishWord("", false); currentInputConnection?.commitText(a, 1); calcText = null; afterText(); return } }
        // a terminal: the word in the slot takes the place of the letters typed so far (the terminal gets them as ordinary keys) and a space follows
        if (st.terminal && st.plain && KeyboardPrefs.suggestions) {
            val w = slotWords.getOrNull(i).orEmpty()
            val ic = currentInputConnection
            if (w.isEmpty() || ic == null) return
            if (termWord.isNotEmpty()) ic.deleteSurroundingText(termWord.length, 0)
            ic.commitText("$w ", 1)
            termWord.setLength(0); termNote(w); termLineStart = false
            updateSuggestions(); return
        }
        // a login of the vault: its user name or password is written as it is
        if (vaultTexts.isNotEmpty()) { vaultTexts.getOrNull(i)?.let { fillField(it) }; return }
        val qc = quickClip
        // a file found by the letters being typed: a tap sends it (and the letters typed for it are taken away)
        if (i == 2 && st.slots.getOrNull(2)?.startsWith("\u0003") == true) {
            val f = fileHit
            if (f != null) {
                val typedNow = word.length
                if (typedNow > 0) { currentInputConnection?.deleteSurroundingText(typedNow, 0); word.setLength(0) }
                pasteVideo(f, java.net.URLConnection.guessContentTypeFromName(f.name) ?: "application/octet-stream")
            }
            fileHit = null; updateSuggestions(); return
        }
        // the screenshot just taken: a tap pastes it (when the field takes pictures)
        if (i == 2 && word.isEmpty() && st.slots.getOrNull(2)?.startsWith("\u0002") == true) {
            val v = shotVideo
            val grp = shotGroup
            if (v != null && grp.size > 1) {
                // a stack: every picture goes into the field, one after the other
                val items = grp.toList()
                scope.launch {
                    for (f in items) {
                        pasteVideo(f, java.net.URLConnection.guessContentTypeFromName(f.name) ?: "image/jpeg")
                        delay(450)
                    }
                }
                shotGroup = emptyList(); st.shotCount = 0
            } else if (v != null) pasteVideo(v, shotVideoMime) else shotItem?.let { pasteClip(it) }
            // what was pasted is not offered again: the stack is emptied also when the chip was held (pinned), or the next picture would carry it along
            shotGroup = emptyList(); st.shotCount = 0
            if (!shotPinned) { shotItem = null; shotVideo = null; st.shotVideo = false; st.shotFile = ""; st.shotThumb = null }; updateSuggestions(); return
        }
        // the one-time code of a notification: typed as it is (no space after it), then gone
        if (i == 2 && st.slots.getOrNull(2)?.startsWith("\u0001🔑") == true) {
            val otp = slotWords.getOrNull(2).orEmpty()
            if (otp.isNotEmpty()) { finishWord("", false); currentInputConnection?.commitText(otp, 1); KbOtp.code = null; afterText() }
            return
        }
        if (i == 2 && qc != null && st.slots.getOrNull(2)?.startsWith("\u0001") == true) {
            // a word being typed is kept as it is, then the copied text follows it
            if (word.isNotEmpty()) finishWord("", false)
            currentInputConnection?.commitText(qc, 1)
            // pasted once, it leaves the bar (a pinned one stays); it stays in the clipboard list either way
            quickLeft--
            if (quickLeft <= 0 && !quickPinned) quickClip = null
            afterText(); return
        }
        val c = currentInputConnection ?: return
        // the word the typing was just corrected from stands quoted at the left: a tap puts it back
        val lcb = lastCorrection
        if (word.isEmpty() && lcb != null && st.slots.getOrNull(i)?.startsWith("\"") == true) {
            // (the quoted word can stand in any of the three places of the bar; what stands before the cursor is looked at with a little room, so a space more or less does not stop the tap)
            val tb = c.getTextBeforeCursor(lcb.corrected.length + lcb.sep.length + 3, 0)?.toString().orEmpty()
            val n = when {
                tb.endsWith(lcb.corrected + lcb.sep) -> lcb.corrected.length + lcb.sep.length
                tb.endsWith(lcb.corrected) -> lcb.corrected.length
                tb.trimEnd().endsWith(lcb.corrected) -> lcb.corrected.length + (tb.length - tb.trimEnd().length)
                else -> -1
            }
            lastCorrection = null
            if (n > 0) {
                if (KeyboardPrefs.learn && !st.incognito) KeyboardDictionary.reject(this, lcb.original, lcb.corrected)
                if (!st.incognito) KbStats.add(this, "undone"); hybridCount(false)
                // the word as it was typed goes back for good and a space follows it (the tap ends the word, like any other pick); it was rejected above, so it is not mended again
                val sep = lcb.sep.ifEmpty { " " }
                c.beginBatchEdit(); c.deleteSurroundingText(n, 0); c.commitText(lcb.original + sep, 1); c.endBatchEdit()
                word.clear(); target = null; prevWord = lcb.original; lastWasSpace = sep.endsWith(" ")
            }
            updateSuggestions(); return
        }
        val w = slotWords.getOrNull(i).orEmpty()
        if (w.isEmpty()) return
        // a blocked word is not written from the bar (the typed word itself is, it is the person's)
        if (!w.startsWith("\"") && w != word.toString() && KeyboardDictionary.isBanned(w)) { updateSuggestions(); return }
        if (numOffered.isNotEmpty() && w in numOffered) {
            val d = digitsBefore(c)
            if (d.isNotEmpty() && w.startsWith(d)) { c.deleteSurroundingText(d.length, 0); c.commitText(w, 1); numPending = w; numOffered = emptyList(); st.emailBar = false; afterText(); return }
        }
        if (emailField || atTokenNow()) {
            val before = c.getTextBeforeCursor(100, 0)?.toString().orEmpty()
            val tok = before.takeLastWhile { !it.isWhitespace() }
            // a domain ("@outlook.com") replaces what stands after the typed "@"; a whole saved address replaces the typed beginning: the name is never written twice
            if (w.startsWith("@") && '@' in tok) c.deleteSurroundingText(tok.length - tok.lastIndexOf('@'), 0)
            else if ('@' in w && tok.isNotEmpty()) c.deleteSurroundingText(tok.length, 0)
            c.commitText(w, 1); afterText(); return
        }
        val isEmoji = !w[0].isLetterOrDigit()
        val g = lastGlide
        if (word.isEmpty() && g != null && w in glideAlts) {
            c.deleteSurroundingText(g.length + 1, 0); c.commitText("$w ", 1); lastGlide = w; prevWord = w; return
        }
        if (word.isNotEmpty()) {
            val typed = word.toString()
            // the quoted word at the left is what was typed: a tap keeps it as it is, and it is remembered so it is not mended again
            if (st.slots.getOrNull(i)?.startsWith("\"") == true && w == typed && KeyboardPrefs.learn && !st.incognito && !st.plain && typed.length >= 2 && typed.none { it.isDigit() }) {
                KeyboardDictionary.learnWord(typed, 3)
            }
            word.clear(); target = null
            // a tapped suggestion that is not a completion: this typo means that word from now on
            if (!isEmoji && KeyboardPrefs.learn && !st.plain && !st.incognito) {
                val lt = typed.lowercase(lang.locale); val lw = w.lowercase(lang.locale)
                // a pick that replaces an earlier pick: the typo at the start of the chain means this word, and the earlier pick was not right for it
                learnChain(lt, lw)
                KeyboardDictionary.learnTypo(this, lt, lw)
                val now = SystemClock.uptimeMillis()
                pick = if (lt != lw && !lw.startsWith(lt) && lt.length >= 3) Pick(pick?.takeIf { it.chosen == lt && now - it.at < 60_000L }?.original ?: lt, lw, now) else null
            }
            if (isEmoji) { c.commitText("$w ", 1) } else {
                c.commitText("$w ", 1)
                if (KeyboardPrefs.learn && !st.plain && !st.incognito) KeyboardDictionary.learn(this, prevWord, w)
                prevWord = w
            }
        } else {
            c.commitText(if (isEmoji) "$w " else "$w ", 1)
            if (!isEmoji) prevWord = w
        }
        lastCorrection = null; lastWasSpace = true; spaceTap = SystemClock.uptimeMillis(); punctTyped = false
        afterText()
    }

    override fun arrow(code: Int) {
        endFlash()
        finishWord("", false); lastWasSpace = false
        val c = currentInputConnection
        // left, right, start and end of the line move the cursor through the text field itself: a key event makes Android show its keyboard-navigation highlight over the app
        if (c != null && code in setOf(21, 22, 122, 123)) {
            val a = minOf(selStart, selEnd); val b = maxOf(selStart, selEnd)
            val pos = when (code) {
                21 -> if (a != b) a else (a - 1).coerceAtLeast(0)
                22 -> if (a != b) b else b + 1
                122 -> { val before = c.getTextBeforeCursor(2000, 0)?.toString().orEmpty(); a - (before.length - (before.lastIndexOf('\n') + 1)) }
                else -> { val after = c.getTextAfterCursor(2000, 0)?.toString().orEmpty(); val nl = after.indexOf('\n'); b + (if (nl < 0) after.length else nl) }
            }
            if (c.setSelection(pos, pos)) { selStart = pos; selEnd = pos; return }
        }
        sendDownUpKeyEvents(code)
    }

    override fun edit(id: String) {
        endFlash()
        val c = currentInputConnection ?: return
        finishWord("", false)
        when (id) {
            "selectAll" -> c.performContextMenuAction(android.R.id.selectAll)
            "copy", "cut" -> {
                // the selected text goes into the clipboard history right here too, so a cut or a copy is never missing from it (the system listener can be late or skipped)
                val sel = c.getSelectedText(0)?.toString()?.trim()
                c.performContextMenuAction(if (id == "cut") android.R.id.cut else android.R.id.copy)
                if (!sel.isNullOrEmpty() && !st.incognito && !st.plain) st.clipItems = ClipStore.addText(this, sel)
            }
            "paste" -> c.performContextMenuAction(android.R.id.paste)
            "undo" -> c.performContextMenuAction(android.R.id.undo)
            "redo" -> c.performContextMenuAction(android.R.id.redo)
            "capslock" -> st.shift = if (st.shift == 2) 0 else 2
        }
    }

    override fun moveCursor(delta: Int) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        val pos = (selStart + delta).coerceAtLeast(0)
        c.setSelection(pos, pos); selStart = pos; selEnd = pos
    }

    /**
     * The cursor moves [delta] lines up (negative) or down. In a field with several lines the editor itself is asked (the up and down keys), so the lines of wrapped
     * text count as lines too, not only the ones ended by a line break; in a one-line field up goes to the start and down to the end.
     */
    override fun moveLine(delta: Int) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        val before = c.getTextBeforeCursor(4000, 0)?.toString().orEmpty()
        val after = c.getTextAfterCursor(4000, 0)?.toString().orEmpty()
        val multi = (currentInputEditorInfo?.inputType ?: 0) and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0 || (before + after).contains('\n')
        // one line only, or already on the first / last line: the cursor stays where it is (it never jumps to the start or the end of the text)
        if (!multi) return
        val room = if (delta < 0) before.count { it == '\n' } else after.count { it == '\n' }
        val steps = minOf(Math.abs(delta), room)
        if (steps == 0) return
        val code = if (delta < 0) android.view.KeyEvent.KEYCODE_DPAD_UP else android.view.KeyEvent.KEYCODE_DPAD_DOWN
        repeat(steps) {
            c.sendKeyEvent(softKey(SystemClock.uptimeMillis(), android.view.KeyEvent.ACTION_DOWN, code, 0))
            c.sendKeyEvent(softKey(SystemClock.uptimeMillis(), android.view.KeyEvent.ACTION_UP, code, 0))
        }
    }

    override fun deleteWord() {
        val c = currentInputConnection ?: return
        finishWord("", false); lastGlide = null; lastCorrection = null
        feedback()
        // a terminal gives no text before the cursor, so the word is taken back by key events: the letters just typed one by one, else Ctrl+W (the shell's word delete)
        if (st.terminal) {
            val n = termWord.length
            if (n > 0) {
                repeat(n) {
                    c.sendKeyEvent(softKey(SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0))
                    c.sendKeyEvent(softKey(SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0))
                }
                termWord.setLength(0)
            } else {
                val m = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
                c.sendKeyEvent(softKey(SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_W, m))
                c.sendKeyEvent(softKey(SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_W, m))
            }
            updateSuggestions()
            return
        }
        val before = c.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        if (before.isEmpty()) return
        var n = 0
        while (n < before.length && before[before.length - 1 - n].isWhitespace()) n++
        val wordStart = n
        while (n < before.length && (before[before.length - 1 - n].isLetterOrDigit() || before[before.length - 1 - n] == '\'')) n++
        if (n == wordStart) n++   // punctuation: one mark
        // the word that was deleted is remembered: if a different but alike word is typed in its place, the first was a slip of the second
        before.takeLast(n).trim().takeIf { it.length >= 3 && it.all { ch -> ch.isLetter() || ch == '\'' } }?.let { deletedWord = it.lowercase(lang.locale); deletedAt = SystemClock.uptimeMillis() }
        c.deleteSurroundingText(n, 0)
        afterText()
    }

    override fun quickDeleteStart() { finishWord("", false); anchor = selEnd; st.selecting = true }

    override fun quickDelete(chars: Int) {
        val c = currentInputConnection ?: return
        val from = (anchor - chars).coerceAtLeast(0)
        c.setSelection(from, anchor)
    }

    override fun quickDeleteEnd(apply: Boolean) {
        val c = currentInputConnection ?: return
        if (apply) {
            // a whole word swiped away: it counts like a word deleted letter by letter, so the next word typed in its place can teach the slip
            c.getSelectedText(0)?.toString()?.takeIf { it.length in 3..30 && it.all { ch -> ch.isLetter() || ch == '\'' } }?.let { if (peak == null) peak = it }
            c.commitText("", 1)
        }
        st.selecting = false; afterText()
    }

    /**
     * AI fix: the selected text (or the whole field) goes to Pulse AI on the phone, which mends spelling, punctuation, capitals and the Turkish letters,
     * and the answer replaces it. Nothing leaves the phone; without Pulse AI (or a model in it) a note says so.
     */
    private fun aiFix() {
        val c = currentInputConnection ?: return
        if (st.incognito) return
        val sel = c.getSelectedText(0)?.toString().orEmpty()
        val whole = if (sel.isEmpty()) (c.getTextBeforeCursor(4000, 0)?.toString().orEmpty() + c.getTextAfterCursor(4000, 0)?.toString().orEmpty()) else sel
        if (whole.isBlank()) return
        android.widget.Toast.makeText(this, com.umutk.pulse.tr("AI is fixing the text…", "Yapay zekâ metni düzeltiyor…"), android.widget.Toast.LENGTH_SHORT).show()
        scope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching { com.umutk.pulse.extras.ai.AiEngine.fix(this@PulseKeyboardService, whole) }.getOrNull()
            }
            val ic = currentInputConnection ?: return@launch
            if (out.isNullOrBlank()) {
                android.widget.Toast.makeText(this@PulseKeyboardService, com.umutk.pulse.tr("Pulse AI needs a chat model (Extra apps > Pulse AI > Models)", "Pulse AI için bir sohbet modeli gerekli (Ek uygulamalar > Pulse AI > Modeller)"), android.widget.Toast.LENGTH_LONG).show(); return@launch
            }
            ic.beginBatchEdit()
            if (sel.isEmpty()) { ic.performContextMenuAction(android.R.id.selectAll) }
            ic.commitText(out, 1)
            ic.endBatchEdit()
            word.clear(); afterText()
        }
    }

    override fun menu(id: String) {
        // a note picked in the Notes panel: its text goes in at the cursor
        if (id.startsWith("noteIn:")) {
            val n = runCatching { com.umutk.pulse.extras.notes.NotesStore.load(this).firstOrNull { it.id == id.removePrefix("noteIn:") } }.getOrNull() ?: return
            finishWord("", false)
            currentInputConnection?.commitText(n.body, 1)
            st.panel = "keys"
            return
        }
        when (id) {
            "menu" -> st.panel = if (st.panel == "menu") "keys" else "menu"
            "clip" -> st.panel = if (st.panel == "clip") "keys" else "clip"
            "auth" -> if (st.panel == "auth") st.panel = "keys" else openAuth()
            "vault" -> if (st.panel == "vault") st.panel = "keys" else openAuth { showVault() }
            // the Dictionary: the selected word, else the word before the cursor; its meanings and Wikipedia summary come from the net
            "dict" -> {
                val c = currentInputConnection
                val sel = c?.getSelectedText(0)?.toString()?.trim().orEmpty()
                val before = c?.getTextBeforeCursor(40, 0)?.toString().orEmpty()
                val term = sel.ifEmpty { (before.trimEnd().takeLastWhile { it.isLetter() || it == '\'' || it == '-' }) }
                st.panel = "dict"; st.dictQuery = term; st.dictText = ""
                if (term.length >= 2) { st.dictBusy = true; scope.launch { val r = withContext(Dispatchers.IO) {
                    // a word the dictionary does not know (a typo, "uygulams"): its right form is looked up instead
                    val fix = if (KeyboardDictionary.isKnown(term.lowercase())) null else runCatching { KeyboardDictionary.correct(lang.code, term.lowercase(), 3f).firstOrNull()?.word }.getOrNull()
                    (if (fix != null) KbDict.lookup(fix, lang.code).also { if (it.isNotEmpty()) st.dictQuery = fix } else "").ifEmpty { KbDict.lookup(term, lang.code) }
                }; st.dictText = r.ifEmpty { tr("Nothing found for \"$term\"", "\"$term\" için bir şey bulunamadı") }; st.dictBusy = false } }
            }
            // holding the emoji key: the GIF page of the emoji panel
            "gif" -> { finishWord("", false); st.emojiQuery = null; st.gifSearch = false; st.emojiTab = 12; st.panel = "emoji" }
            // Notes opens in the keyboard itself (a list to pick from, and the text of the field saved as a note); the app is one tap away inside it
            "notes" -> st.panel = if (st.panel == "notes") "keys" else "notes"
            "notesApp" -> { st.panel = "keys"; runCatching { startActivity(android.content.Intent().setClassName(this, "com.umutk.pulse.ExtraToolActivity").putExtra("route", "notes").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            "notesSave" -> {
                val c = currentInputConnection ?: return
                finishWord("", false)
                val sel = c.getSelectedText(0)?.toString().orEmpty()
                val txt = sel.ifEmpty { c.getTextBeforeCursor(3000, 0)?.toString().orEmpty() + c.getTextAfterCursor(3000, 0)?.toString().orEmpty() }.trim()
                if (txt.isEmpty()) android.widget.Toast.makeText(this, tr("Nothing to save", "Kaydedilecek metin yok"), android.widget.Toast.LENGTH_SHORT).show()
                else runCatching {
                    val now = System.currentTimeMillis()
                    val n = com.umutk.pulse.extras.notes.Note(java.util.UUID.randomUUID().toString(), txt.lineSequence().first().take(60), txt, now)
                    com.umutk.pulse.extras.notes.NotesStore.save(this, listOf(n) + com.umutk.pulse.extras.notes.NotesStore.load(this))
                    st.noteRev++
                    android.widget.Toast.makeText(this, tr("Saved to Notes", "Notlara kaydedildi"), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            "stickers" -> st.panel = if (st.panel == "stickers") "keys" else "stickers"
            // the grammar check: the selected text, else the paragraph before the cursor, put right (spaces, repeated words, capitals, apostrophes)
            "grammar" -> {
                st.panel = "keys"
                val c = currentInputConnection ?: return
                finishWord("", false)
                val sel = c.getSelectedText(0)?.toString().orEmpty()
                val before = if (sel.isEmpty()) c.getTextBeforeCursor(400, 0)?.toString().orEmpty().substringAfterLast('\n') else ""
                val src = sel.ifEmpty { before }
                val fixed = KbGrammar.fix(src, lang.code)
                if (fixed == src) android.widget.Toast.makeText(this, tr("No problems found", "Sorun bulunamadı"), android.widget.Toast.LENGTH_SHORT).show()
                else { if (sel.isEmpty()) c.deleteSurroundingText(src.length, 0); c.commitText(fixed, 1); android.widget.Toast.makeText(this, tr("Fixed", "Düzeltildi"), android.widget.Toast.LENGTH_SHORT).show() }
            }
            // one-tap text tools (offline): a random password, the case of the selection or last word, the start / end of the line
            "passgen" -> {
                st.panel = "keys"
                finishWord("", false)
                val r = java.security.SecureRandom()
                val sets = listOf("abcdefghijkmnopqrstuvwxyz", "ABCDEFGHJKLMNPQRSTUVWXYZ", "23456789", "!@#$%*-_?")
                val all = sets.joinToString("")
                val chars = (sets.map { it[r.nextInt(it.length)] } + (1..12).map { all[r.nextInt(all.length)] }).shuffled(r)
                currentInputConnection?.commitText(chars.joinToString(""), 1)
            }
            "case" -> {
                st.panel = "keys"
                val c = currentInputConnection ?: return
                finishWord("", false)
                val sel = c.getSelectedText(0)?.toString().orEmpty()
                val src = sel.ifEmpty { c.getTextBeforeCursor(60, 0)?.toString().orEmpty().takeLastWhile { !it.isWhitespace() } }
                if (src.isEmpty()) return
                val tr = KeyboardPrefs.langList().firstOrNull() == "tr"
                val loc = if (tr) java.util.Locale("tr") else java.util.Locale.ROOT
                val out = when {
                    src == src.lowercase(loc) -> src.uppercase(loc)
                    src == src.uppercase(loc) -> src.lowercase(loc).replaceFirstChar { it.titlecase(loc) }
                    else -> src.lowercase(loc)
                }
                if (sel.isEmpty()) c.deleteSurroundingText(src.length, 0)
                c.commitText(out, 1)
            }
            "lineStart" -> { st.panel = "keys"; arrow(android.view.KeyEvent.KEYCODE_MOVE_HOME) }
            "lineEnd" -> { st.panel = "keys"; arrow(android.view.KeyEvent.KEYCODE_MOVE_END) }
            // from WM Keyboard (MIT): open an app, the flashlight, a web search of the selection or the word, an alarm from a time typed
            "apps" -> {
                val before = currentInputConnection?.getTextBeforeCursor(30, 0)?.toString().orEmpty()
                st.appsQuery = before.takeLastWhile { it.isLetterOrDigit() }
                st.panel = if (st.panel == "apps") "keys" else "apps"
            }
            "torch" -> { st.panel = "keys"; KbPhone.torch(this) }
            // emoji mix (WM Keyboard's idea): the two emoji before the cursor become one picture (Google's Emoji Kitchen, through emojik.vercel.app)
            "emojimix" -> {
                st.panel = "keys"
                val before = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString().orEmpty()
                val pair = KbEmojiMix.lastTwo(before)
                if (pair == null) { android.widget.Toast.makeText(this, tr("Type two emoji first", "Önce iki emoji yaz"), android.widget.Toast.LENGTH_SHORT).show(); return }
                scope.launch {
                    val f = withContext(Dispatchers.IO) { KbEmojiMix.fetch(this@PulseKeyboardService, pair.first, pair.second) }
                    if (f == null) { android.widget.Toast.makeText(this@PulseKeyboardService, tr("No mix for these two", "Bu ikisinin karışımı yok"), android.widget.Toast.LENGTH_SHORT).show(); return@launch }
                    currentInputConnection?.deleteSurroundingText(pair.first.length + pair.second.length + pair.third, 0)
                    pasteVideo(f, "image/png")
                }
            }
            "websearch" -> {
                st.panel = "keys"
                val c = currentInputConnection
                val q = c?.getSelectedText(0)?.toString()?.trim().orEmpty().ifEmpty { c?.getTextBeforeCursor(200, 0)?.toString()?.substringAfterLast('\n')?.trim().orEmpty() }
                KbPhone.search(this, q)
            }
            "alarm" -> {
                st.panel = "keys"
                val before = currentInputConnection?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
                KbPhone.alarm(this, before)
            }
            "dictInsert" -> { currentInputConnection?.commitText(st.dictText.lineSequence().firstOrNull { it.trimStart().startsWith("1.") }?.trim()?.removePrefix("1.")?.trim() ?: st.dictText.lineSequence().drop(1).firstOrNull().orEmpty(), 1); st.panel = "keys" }
            // the calculator and the converter: the selected expression, or the one right before the cursor; the answer goes in after it ("12*5" -> "12*5 = 60")
            "calc", "convert" -> {
                st.panel = "keys"
                val c = currentInputConnection ?: return
                finishWord("", false)
                val before = c.getTextBeforeCursor(60, 0)?.toString().orEmpty()
                val ans = if (id == "calc") (KbCalc.tail(before)?.let { KbCalc.eval(it) }?.let { KbCalc.format(it) }) else KbUnits.tail(before)
                if (ans != null) c.commitText(" = $ans", 1)
                // nothing to work out yet: the numbers and signs page opens, and the answer of what is typed there comes up on the bar by itself
                else if (id == "calc") st.page = 1
                else android.widget.Toast.makeText(this, if (id == "calc") tr("Type a sum first, such as 12*5", "Önce bir işlem yaz, örn. 12*5") else tr("Type a unit first, such as 10 km to mi", "Önce bir birim yaz, örn. 10 km to mi"), android.widget.Toast.LENGTH_SHORT).show()
            }
            // the camera's text: a small window takes the photo, reads it on the phone and puts the text on the clipboard (the bar offers it)
            "scan" -> {
                st.panel = "keys"
                runCatching { startActivity(android.content.Intent(this, ScanTextActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            "gamepad" -> st.panel = if (st.panel == "gamepad") "keys" else "gamepad"
            "lang" -> lang()
            // the e-mail key: "@" and the chosen domain go in at the cursor, nothing before it is touched
            "email" -> { st.panel = "keys"; insertEmailDomain() }
            "voice" -> { st.panel = "keys"; voice() }
            "translate" -> {
                st.panel = "keys"
                // a selection is translated at once; otherwise the translation bar opens (or closes)
                if (currentInputConnection?.getSelectedText(0)?.isNotEmpty() == true) translate() else transOp(if (st.trans) "close" else "open")
            }
            "back" -> st.panel = "keys"
            "emoji" -> { st.emojiTab = if (st.recents.isEmpty()) 1 else 0; st.panel = "emoji" }
            "resize" -> st.panel = "resize"
            "settings", "languages" -> {
                st.panel = "keys"
                startActivity(Intent().setClassName(this, "com.umutk.pulse.ExtraToolActivity").putExtra("route", "keyboard").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "theme" -> { val l = KeyboardThemes.ids; KeyboardPrefs.theme = l[(l.indexOf(KeyboardPrefs.theme) + 1).mod(l.size)]; KeyboardPrefs.save(this) }
            "incognito" -> st.incognito = !st.incognito
            "autocorrect" -> { KeyboardPrefs.autocorrect = !KeyboardPrefs.autocorrect; KeyboardPrefs.save(this) }
            "aifix" -> { st.panel = "keys"; aiFix() }
            // media controls (WM Keyboard's idea): the music or video playing in any app
            "media" -> { val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager; listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP).forEach { a -> am.dispatchMediaKeyEvent(android.view.KeyEvent(a, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) } }
            "mediaNext" -> { val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager; listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP).forEach { a -> am.dispatchMediaKeyEvent(android.view.KeyEvent(a, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)) } }
            "onehand" -> { KeyboardPrefs.oneHand = when (KeyboardPrefs.oneHand) { "off" -> "right"; "right" -> "left"; else -> "off" }; KeyboardPrefs.save(this) }
            "arrows" -> { KeyboardPrefs.arrows = !KeyboardPrefs.arrows; KeyboardPrefs.save(this) }
            "keyset" -> st.panel = if (st.panel == "keyset") "keys" else "keyset"
            "layout" -> st.panel = if (st.panel == "layout") "keys" else "layout"
            // (turned sideways the keyboard has its own two switches)
            "numbers" -> { if (st.land) KeyboardPrefs.landNumberRow = !KeyboardPrefs.landNumberRow else KeyboardPrefs.numberRow = !KeyboardPrefs.numberRow; KeyboardPrefs.save(this) }
            "proRow" -> { if (KeyboardPrefs.theme == "samsung") KeyboardPrefs.samProRow = !KeyboardPrefs.samProRow else if (st.land) KeyboardPrefs.landProRow = !KeyboardPrefs.landProRow else KeyboardPrefs.proRow = !KeyboardPrefs.proRow; KeyboardPrefs.save(this) }
            "glide" -> { KeyboardPrefs.glide = !KeyboardPrefs.glide; KeyboardPrefs.save(this) }
        }
        if (id == "resize") return
        if (st.panel == "keys") KeyboardPrefs.save(this)
    }

    /**
     * Translates the selected text, or the text before the cursor, inside the keyboard (the MyMemory service, no key needed) and puts the translation in its place.
     * From the language of the keyboard to the next one of the keyboard's languages (Turkish and English by default).
     */
    /** Free translation services, no key: MyMemory first, then two Lingva servers. Null when none answers. */
    private fun translateText(text: String, from: String, to: String): String? {
        fun get(u: String): String? = runCatching {
            val conn = (java.net.URL(u).openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 12000; setRequestProperty("User-Agent", "Pulse-Keyboard") }
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull()
        val q = java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        // the best machine translation first: Google's own translate service (the one the other keyboards use), then its mirrors, and MyMemory last
        get("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t&q=$q")?.let { b ->
            runCatching {
                val parts = org.json.JSONArray(b).getJSONArray(0)
                (0 until parts.length()).joinToString("") { parts.getJSONArray(it).optString(0, "") }
            }.getOrNull()?.takeIf { it.isNotBlank() && !it.equals(text, true) }?.let { return it }
        }
        get("https://api.mymemory.translated.net/get?q=$q&langpair=$from%7C$to")?.let { b ->
            runCatching { org.json.JSONObject(b).getJSONObject("responseData").getString("translatedText") }.getOrNull()
                ?.takeIf { it.isNotBlank() && !it.contains("MYMEMORY WARNING", true) && !it.equals(text, true) }?.let { return it }
        }
        for (host in listOf("lingva.ml", "lingva.thedaviddelta.com")) {
            get("https://$host/api/v1/$from/$to/$q")?.let { b ->
                runCatching { org.json.JSONObject(b).getString("translation") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return null
    }

    private var transJob: Job? = null
    private val transLangs = listOf("en", "tr", "de", "fr", "es", "it", "pt", "ru", "ar", "nl", "pl", "ja", "ko", "zh")

    override fun transOp(op: String) {
        when (op) {
            "open" -> {
                val langs = KeyboardPrefs.langList().map { KeyboardLayouts.dict(it) }.distinct()
                val from = KeyboardLayouts.dict(st.lang)
                st.transFrom = from; st.transTo = langs.firstOrNull { it != from } ?: if (from == "tr") "en" else "tr"
                st.transOut = ""; st.transIn = ""; st.trans = true; st.toolbar = false; scheduleTrans()
            }
            "close" -> { st.trans = false; transJob?.cancel(); st.transOut = ""; st.transIn = ""; st.transBusy = false }
            "swap" -> { val f = st.transFrom; st.transFrom = st.transTo; st.transTo = f; st.transOut = ""; scheduleTrans() }
            "from" -> { st.transFrom = transLangs[(transLangs.indexOf(st.transFrom) + 1) % transLangs.size]; st.transOut = ""; scheduleTrans() }
            "to" -> { st.transTo = transLangs[(transLangs.indexOf(st.transTo) + 1) % transLangs.size]; st.transOut = ""; scheduleTrans() }
            "apply" -> {
                val out = st.transOut
                val ic = currentInputConnection ?: return
                if (out.isBlank()) return
                finishWord("", false)
                // what was typed in the bar: the translation goes into the text box at the cursor; nothing typed: the text of the box itself is replaced
                if (st.transIn.isEmpty()) { val raw = ic.getTextBeforeCursor(400, 0)?.toString().orEmpty(); ic.deleteSurroundingText(raw.length, 0) }
                ic.commitText(out, 1)
                transOp("close"); afterText()
            }
        }
    }

    /** Translates the text before the cursor a moment after the last key, for the bar. */
    private fun scheduleTrans() {
        transJob?.cancel()
        if (!st.trans) return
        val text = st.transIn.trim().ifEmpty { currentInputConnection?.getTextBeforeCursor(400, 0)?.toString().orEmpty().trim() }
        if (text.isEmpty() || st.transFrom == st.transTo) { st.transOut = ""; st.transBusy = false; return }
        val from = st.transFrom; val to = st.transTo
        transJob = scope.launch {
            delay(600); st.transBusy = true
            val out = withContext(Dispatchers.IO) { translateText(text, from, to) }
            if (st.trans && from == st.transFrom && to == st.transTo) { st.transOut = out.orEmpty(); st.transBusy = false }
        }
    }

    private fun translate() {
        val c = currentInputConnection ?: return
        val sel = c.getSelectedText(0)?.toString()?.takeIf { it.isNotBlank() }
        val rawBefore = c.getTextBeforeCursor(400, 0)?.toString().orEmpty()
        val typed = (sel ?: rawBefore).trim()
        // nothing typed or selected: the copied text is translated and put in
        val clip = if (typed.isEmpty()) (getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty() else ""
        val text = typed.ifEmpty { clip }
        if (text.isEmpty()) { android.widget.Toast.makeText(this, com.umutk.pulse.tr("Type or select the text to translate", "Çevrilecek yazıyı yazın ya da seçin"), android.widget.Toast.LENGTH_SHORT).show(); return }
        val langs = KeyboardPrefs.langList().map { KeyboardLayouts.dict(it) }.distinct()
        val from = KeyboardLayouts.dict(st.lang)
        val to = langs.firstOrNull { it != from } ?: if (from == "tr") "en" else "tr"
        android.widget.Toast.makeText(this, com.umutk.pulse.tr("Translating…", "Çevriliyor…"), android.widget.Toast.LENGTH_SHORT).show()
        scope.launch(Dispatchers.IO) {
            val out = translateText(text.take(480), from, to)
            withContext(Dispatchers.Main) {
                val ic = currentInputConnection ?: return@withContext
                if (out == null) { android.widget.Toast.makeText(this@PulseKeyboardService, com.umutk.pulse.tr("Could not translate", "Çevrilemedi"), android.widget.Toast.LENGTH_SHORT).show(); return@withContext }
                finishWord("", false)
                // a selection is replaced by committing over it; the text before the cursor is deleted first
                if (sel == null && typed.isNotEmpty()) ic.deleteSurroundingText(rawBefore.length, 0)
                ic.commitText(out, 1)
                afterText()
            }
        }
    }

    override fun hide() {
        finishWord("", false); requestHideSelf(0)
        // an old floating window of the tile (from before the tile stopped opening one) goes too
        runCatching { KeyboardFloatActivity.current?.finish() }
    }

    private var recognizer: android.speech.SpeechRecognizer? = null
    private var voiceFallbackDone = false

    /**
     * Speech typing: listens right here, in the keyboard, and writes what was said into the field; the keyboard stays up. Needs the microphone
     * permission of Pulse. Without it (or without a recognizer on the phone) the device's own voice keyboard is opened as before.
     */
    override fun voice() {
        finishWord("", false)
        if (st.panel == "voice") { stopListening(); st.panel = "keys"; return }
        val granted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted && KeyboardPrefs.voiceWhisper && com.umutk.pulse.extras.ai.Transcriber.ready(this)) { whisperDictation(); return }
        if (!granted || !android.speech.SpeechRecognizer.isRecognitionAvailable(this)) { switchToVoiceKeyboard(); return }
        val tag = if (lang.code == "tr") "tr-TR" else "en-US"
        val rec = recognizer ?: android.speech.SpeechRecognizer.createSpeechRecognizer(this).also { recognizer = it }
        // the keyboard's own listening panel covers the keys; the words are written into the field as they are recognised
        st.voiceText = ""
        st.panel = "voice"
        rec.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { st.listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { st.listening = false }
            override fun onError(error: Int) {
                st.listening = false
                st.panel = "keys"
                if (!voiceFallbackDone && error == android.speech.SpeechRecognizer.ERROR_CLIENT) { voiceFallbackDone = true; switchToVoiceKeyboard() }
            }
            override fun onResults(results: android.os.Bundle?) {
                st.listening = false
                st.panel = "keys"
                val text = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) { currentInputConnection?.commitText(text.trim() + " ", 1); afterText() }
            }
            override fun onPartialResults(partial: android.os.Bundle?) {
                st.voiceText = partial?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        runCatching { rec.startListening(i) }.onFailure { st.listening = false; st.panel = "keys"; switchToVoiceKeyboard() }
    }

    @Volatile private var whisperRec: android.media.AudioRecord? = null
    @Volatile private var whisperStop = false

    /**
     * Speech typing with the Whisper model of Pulse AI, on the phone (nothing is sent anywhere): it records until the voice key is pressed again (or 30 seconds), then listens to
     * the recording and writes the words. Used when the setting is on and a speech model is there.
     */
    private fun whisperDictation() {
        if (whisperRec != null) { whisperStop = true; return }
        val rate = 16000
        val min = android.media.AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val rec = runCatching { android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC, rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, min * 4) }.getOrNull()
        if (rec == null || rec.state != android.media.AudioRecord.STATE_INITIALIZED) { rec?.release(); switchToVoiceKeyboard(); return }
        whisperRec = rec; whisperStop = false
        st.voiceText = ""; st.panel = "voice"; st.listening = true
        val code = if (lang.code == "tr") "tr" else lang.code.take(2)
        Thread {
            val pcm = java.io.ByteArrayOutputStream()
            val buf = ShortArray(min)
            val t0 = android.os.SystemClock.uptimeMillis()
            runCatching {
                rec.startRecording()
                while (!whisperStop && android.os.SystemClock.uptimeMillis() - t0 < 30_000L) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) for (i in 0 until n) { pcm.write(buf[i].toInt() and 0xFF); pcm.write((buf[i].toInt() shr 8) and 0xFF) }
                }
            }
            runCatching { rec.stop() }; rec.release(); whisperRec = null
            val bytes = pcm.toByteArray()
            val samples = FloatArray(bytes.size / 2) { i -> ((bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)).toShort() / 32768f }
            android.os.Handler(mainLooper).post { st.listening = false; st.voiceText = com.umutk.pulse.tr("Listening to what you said…", "Söylediğin dinleniyor…") }
            val text = if (samples.size < 8000) "" else runCatching {
                var pfd: android.os.ParcelFileDescriptor? = null
                try {
                    val path = com.umutk.pulse.extras.ai.Transcriber.modelPath(this) { pfd = it } ?: return@runCatching ""
                    com.umutk.pulse.ai.stt.Whisper.open(path).use { w -> w.transcribe(samples, code, null).joinToString(" ") { it.text.trim() }.trim() }
                } finally { runCatching { pfd?.close() } }
            }.getOrDefault("")
            android.os.Handler(mainLooper).post {
                st.panel = "keys"; st.voiceText = ""
                if (text.isNotBlank()) { currentInputConnection?.commitText("$text ", 1); afterText() }
            }
        }.start()
    }

    private fun stopListening() {
        if (whisperRec != null) { whisperStop = true; return }
        st.listening = false
        runCatching { recognizer?.stopListening() }
    }

    /** The device's own voice keyboard, when speech typing in Pulse is not possible. */
    private fun switchToVoiceKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val voice = imm.enabledInputMethodList.firstOrNull { ime ->
            ime.packageName != packageName && (0 until ime.subtypeCount).any { ime.getSubtypeAt(it).mode == "voice" }
        } ?: imm.enabledInputMethodList.firstOrNull { it.id.contains("voicesearch", true) || it.id.contains("voice", true) && it.packageName != packageName }
        if (voice == null) { android.widget.Toast.makeText(this, if (java.util.Locale.getDefault().language == "tr") "Sesle yazma açık değil" else "Voice typing is off", android.widget.Toast.LENGTH_SHORT).show(); return }
        // the finger may still be down (it is a long press): the switch waits a moment so the voice keyboard does not get the lifted finger as its own tap and close
        val sub = (0 until voice.subtypeCount).map { voice.getSubtypeAt(it) }.firstOrNull { it.mode == "voice" }
        scope.launch {
            delay(260)
            if (android.os.Build.VERSION.SDK_INT >= 28 && sub != null) runCatching { switchInputMethod(voice.id, sub) }.onFailure { runCatching { switchInputMethod(voice.id) } }
            else runCatching { switchInputMethod(voice.id) }
        }
    }

    override fun feedback() {
        if (KeyboardPrefs.haptic) {
            // the strength slider: a short, soft pulse of its own instead of the system's key tap, which is strong on many phones
            val ms = KeyboardPrefs.hapticMs.coerceIn(1, 40)
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (vib != null && vib.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= 26) vib.vibrate(android.os.VibrationEffect.createOneShot((4 + ms / 3).toLong(), if (vib.hasAmplitudeControl()) (12 + ms * 5).coerceAtMost(255) else android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") vib.vibrate((4 + ms / 3).toLong())
            } else window.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (KeyboardPrefs.sound) { if (KeyboardPrefs.soundKind == 0) (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f) else KeyboardEffects.play(KeyboardPrefs.soundKind) }
    }

    /** Turkish endings on a name take the apostrophe as the word is typed: Adana + da is Adana'da, Pelin + den is Pelin'den. */
    private fun apostropheAfterName(c: android.view.inputmethod.InputConnection) {
        if (word.indexOf('\'') >= 0) return
        val w = word.toString().lowercase(lang.locale)
        // a place with its head word, after a space: Ağrı Dağı'na, Van Gölü'ne, Ege Denizi'nden (the apostrophe goes after the head word)
        val before = c.getTextBeforeCursor(word.length + 1, 0)?.toString().orEmpty()
        if (before.length == word.length + 1 && before.first() == ' ') {
            for (h in TR_GEO_HEADS) {
                if (!w.startsWith(h)) continue
                val rest = w.substring(h.length)
                if (rest.isNotEmpty() && rest.length <= 4 && rest in TR_ENDINGS) {
                    word.setLength(0); word.append(h.substring(0, 1).uppercase(lang.locale)).append(h.substring(1)).append('\'').append(rest)
                    return
                }
            }
        }
        for (cut in w.length - 1 downTo 3) {
            val rest = w.substring(cut)
            if (rest.length > 4) break
            val stem = w.substring(0, cut)
            if (stem in KeyboardDictionary.properNouns && stem !in KeyboardDictionary.commonNames && rest in TR_ENDINGS) {
                val cap = stem.substring(0, 1).uppercase(lang.locale) + stem.substring(1)
                word.setLength(0); word.append(cap).append('\'').append(rest)
                return
            }
        }
    }

    override fun emoji(e: String) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        c.commitText(e, 1)
        // pictures (block shading, multi-line art) are not kept in the recent emoji: they show as broken dots there
        if (!isArt(e)) {
            st.recents = (listOf(e) + st.recents.filter { it != e }).take(30)
            getSharedPreferences("pulse_keyboard", Context.MODE_PRIVATE).edit().putString("recents", st.recents.joinToString("\n")).apply()
        }
        lastWasSpace = false
    }

    override fun paste(s: String) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        c.commitText(s, 1); st.panel = "keys"; afterText()
    }

    // ---- swipe typing, extra keys -------------------------------------------------------------------------------------------------------------------

    override fun glide(letters: String) {
        val c = currentInputConnection ?: return
        val found = dictCodes(st.lang).flatMap { KeyboardDictionary.glide(it, letters, 4) }.distinct().take(4)
        if (found.isEmpty()) return
        finishWord(" ", true)
        // a word swiped right after another is a new word
        val before = c.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        if (before.isNotEmpty() && !before[0].isWhitespace() && before[0] !in "([{\"'") c.commitText(" ", 1)
        val up = st.shift > 0
        fun fix(w: String) = if (up) w.replaceFirstChar { it.titlecase(lang.locale) } else w
        val words = found.map { fix(it) }
        c.commitText(words[0] + " ", 1)
        if (st.shift == 1) st.shift = 0
        if (KeyboardPrefs.learn && !st.plain && !st.incognito) KeyboardDictionary.learn(this, prevWord, words[0])
        prevWord = words[0]; lastGlide = words[0]; glideAlts = words
        lastWasSpace = true; spaceTap = SystemClock.uptimeMillis(); punctTyped = false
        slotWords = arrayOf(words.getOrElse(1) { "" }, words[0], words.getOrElse(2) { "" })
        st.slots = slotWords.toList()
    }

    private fun metaNow(): Int {
        var m = 0
        if (st.ctrl > 0) m = m or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (st.alt > 0) m = m or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (st.win > 0) m = m or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        if (st.shiftMod > 0) m = m or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        return m
    }

    /** A one-shot modifier is used up by the key it was waiting for. */
    private fun useModifiers() {
        // a modifier that is held down stays for the next key too (and is let go with the finger)
        heldUsed.addAll(heldMods.keys)
        if (st.ctrl == 1 && "ctrl" !in heldMods) st.ctrl = 0
        if (st.alt == 1 && "alt" !in heldMods) st.alt = 0
        if (st.win == 1 && "win" !in heldMods) st.win = 0
        if (st.shiftMod == 1 && "shiftm" !in heldMods) st.shiftMod = 0
    }

    private val heldMods = HashMap<String, Long>()
    private val heldPrev = HashMap<String, Int>()
    private val heldUsed = HashSet<String>()

    /**
     * Ctrl, Alt, Win and Shift of the extra row: a finger held on one works as on a real keyboard (the keys pressed meanwhile get the modifier, which goes when the finger lifts);
     * a quick tap without another key still switches it on for the next key (and twice to lock it), as before.
     */
    override fun modHold(id: String, down: Boolean) {
        fun get() = when (id) { "ctrl" -> st.ctrl; "alt" -> st.alt; "win" -> st.win; else -> st.shiftMod }
        fun set(v: Int) { when (id) { "ctrl" -> st.ctrl = v; "alt" -> st.alt = v; "win" -> st.win = v; else -> st.shiftMod = v } }
        if (down) {
            finishWord("", false)
            heldPrev[id] = get(); heldMods[id] = SystemClock.uptimeMillis(); heldUsed.remove(id)
            if (get() == 0) set(1)
        } else {
            val prev = heldPrev.remove(id) ?: 0; val t0 = heldMods.remove(id) ?: return
            val used = heldUsed.remove(id)
            set(if (!used && SystemClock.uptimeMillis() - t0 < 450L) (prev + 1) % 3 else prev)
        }
    }

    /**
     * A key of the screen keyboard, sent to the app. Without these flags Android takes an arrow, Tab, Esc... for a person using a keyboard to move about: the app leaves
     * "touch mode" and draws its keyboard-navigation highlight (a layer over the screen that stays). A soft keyboard's key is marked as such, and as one that keeps touch mode
     * (what the system's own sendDownUpKeyEvents does too).
     */
    private fun softKey(t: Long, action: Int, code: Int, meta: Int) =
        KeyEvent(t, t, action, code, 0, meta, android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE)

    private fun sendKey(code: Int, extraMeta: Int = 0) {
        endFlash()
        val c = currentInputConnection ?: return
        val t = SystemClock.uptimeMillis(); val meta = metaNow() or extraMeta
        c.sendKeyEvent(softKey(t, KeyEvent.ACTION_DOWN, code, meta))
        c.sendKeyEvent(softKey(t, KeyEvent.ACTION_UP, code, meta))
        useModifiers()
    }

    /** A letter typed while Ctrl, Alt, Win or Shift waits: the key press with those modifiers (Ctrl+C and so on), not the letter. */
    private fun sendChord(ch: Char): Boolean {
        finishWord("", false)
        // Win + D / L / A / N: what the phone has for them (home, lock, quick settings, notifications), through the accessibility helper
        if (st.win > 0 && st.ctrl == 0) {
            val g = when (ch.lowercaseChar()) {
                'd' -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                'l' -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                'a' -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                'n' -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                else -> 0
            }
            if (g != 0) {
                if (g == android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) runCatching { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                else com.umutk.pulse.extras.launcher.globalAction(this, g)
                useModifiers(); return true
            }
        }
        // Ctrl with A, C, X, V, Z, Y: the editor's own actions (a raw key chord is not understood by every text field, and some typed the letter after it)
        if (st.ctrl > 0 && st.alt == 0 && st.win == 0) {
            val act = when (ch.lowercaseChar()) {
                'a' -> "selectAll"; 'c' -> "copy"; 'x' -> "cut"; 'v' -> "paste"; 'y' -> "redo"
                'z' -> if (ch.isUpperCase() || st.shiftMod > 0) "redo" else "undo"
                else -> null
            }
            if (act != null) { edit(act); useModifiers(); if (st.shift == 1) st.shift = 0; return true }
        }
        val evs = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(charArrayOf(ch.lowercaseChar())) ?: return false
        val shifted = ch.isUpperCase() || st.shift > 0
        // the key once: the map gives a DOWN and an UP event per key, and sending the key for each of them typed it twice (Alt+S wrote the ß of the Alt and then a plain s,
        // because the one-shot Alt was already used up by the first press)
        evs.filter { it.action == KeyEvent.ACTION_DOWN && it.keyCode !in setOf(59, 60, 57, 58, 113, 114, 117, 118) }.map { it.keyCode }.distinct()
            .forEach { code -> sendKey(code, if (shifted) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0) }
        if (st.shift == 1) st.shift = 0
        return true
    }

    override fun padKey(code: Int, down: Boolean) {
        val c = currentInputConnection ?: return
        val t = SystemClock.uptimeMillis()
        val src = if (code in 19..22) android.view.InputDevice.SOURCE_DPAD or android.view.InputDevice.SOURCE_GAMEPAD else android.view.InputDevice.SOURCE_GAMEPAD
        c.sendKeyEvent(KeyEvent(t, t, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code, 0, 0, -1, 0, 0, src))
    }

    /** "@gmail.com" (or the domain chosen in the settings) at the cursor. */
    private fun insertEmailDomain() {
        val c = currentInputConnection ?: return
        finishWord("", false)
        val d = KeyboardPrefs.emailDomain.trim().removePrefix("@").ifEmpty { "gmail.com" }
        val before = c.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        // when an "@" is already there only the domain is added
        c.commitText(if (before == "@") d else "@$d", 1)
        afterText()
    }

    /** Pulse's Task Manager (the Processes page), over whatever is on the screen. */
    private fun openTaskManager() {
        runCatching { startActivity(Intent(this, com.umutk.pulse.MainActivity::class.java).putExtra("start", "processes").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
    }

    override fun extraKey(id: String) {
        val d = ExtraKeys.byId(id) ?: return
        finishWord("", false); lastGlide = null; lastWasSpace = false
        when (id) {
            // Ctrl + Shift + Esc on the key bar: the Task Manager (the keyboard used to close at that chord)
            "esc" -> if (st.ctrl > 0 && st.shiftMod > 0) { openTaskManager(); useModifiers(); st.ctrl = 0; st.shiftMod = 0 } else if (d.code != 0) sendKey(d.code)
            "ctrl" -> st.ctrl = (st.ctrl + 1) % 3
            "alt" -> st.alt = (st.alt + 1) % 3
            "win" -> st.win = (st.win + 1) % 3
            "shiftm" -> st.shiftMod = (st.shiftMod + 1) % 3
            "fn" -> st.fnOpen = !st.fnOpen
            "email" -> insertEmailDomain()
            "selectAll", "copy", "cut", "paste", "undo", "redo" -> edit(id)
            // Alt + Tab: the phone's recent apps (the accessibility helper), as the editor cannot switch windows
            "tab" -> if (st.alt > 0) { if (com.umutk.pulse.features.CoverAccessibilityService.instance?.altTabFromKeyboard() != true) com.umutk.pulse.extras.launcher.globalAction(this, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS); useModifiers() } else sendKey(d.code)
            else -> if (d.code != 0) sendKey(d.code)
        }
    }

    // ---- shift, suggestions ------------------------------------------------------------------------------------------------------------------------

    /** The sentence start puts the shift on, as the field asks. */
    private fun updateCaps() {
        if (!KeyboardPrefs.autoCap || st.plain || st.shift == 2) return
        // automatic capitals belong to the first letter of a sentence only: while a word is under way, the rest of it stays small
        if (word.isNotEmpty()) return
        val c = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return
        // the field's own flag first; many fields (browsers, chat boxes) set none, so the sentence start is read from the text itself
        val before = c.getTextBeforeCursor(8, 0)?.toString().orEmpty()
        val own = before.trimEnd(' ').let { t -> before.isEmpty() || before.endsWith("\n") || (before.endsWith(" ") && (t.endsWith(".") || t.endsWith("!") || t.endsWith("?") || t.endsWith("…"))) }
        // a field with nothing in it always starts with a capital (search boxes and address bars that ask for no autocorrect included), except passwords, e-mail and web addresses
        val untouched = before.isEmpty() && c.getTextAfterCursor(1, 0).isNullOrEmpty() && !emailField && !urlNow() &&
            info.inputType and InputType.TYPE_MASK_VARIATION !in listOf(InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        st.shift = if (c.getCursorCapsMode(info.inputType) != 0 || (own && info.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD == 0 && info.inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD == 0 && (!noAuto || untouched))) 1 else 0
        autoShift = st.shift == 1
        // a name goes on in capitals: after "Umut Kıvanç" the next word (şahin) starts with a capital, as the saved full names and the names of the list say
        if (st.shift == 0 && (lang.code == "tr" || lang.code == "en")) {
            val b2 = c.getTextBeforeCursor(60, 0)?.toString().orEmpty()
            val toks = if (b2.endsWith(" ")) b2.split(' ').filter { t -> t.isNotBlank() } else emptyList()
            if (toks.size >= 2) {
                val a = toks[toks.size - 2]; val b = toks[toks.size - 1]
                fun cap(t: String) = t.length >= 2 && t[0].isUpperCase() && t.drop(1).all { it.isLowerCase() || it == '\'' }
                if (cap(a) && cap(b)) {
                    val lc = lang.locale
                    val named = a.lowercase(lc) in KeyboardDictionary.properNouns || b.lowercase(lc) in KeyboardDictionary.properNouns ||
                        getSharedPreferences("pulse_kb_fullnames", MODE_PRIVATE).getString("list", "").orEmpty().split('\n').any { it.startsWith("$a $b", true) } ||
                        getSharedPreferences("pulse_kb_names", MODE_PRIVATE).getString("list", "").orEmpty().split('\n').any { it.equals(a, true) }
                    if (named) { st.shift = 1; autoShift = true }
                }
            }
        }
    }

    private val EN_GRAMMAR: Map<String, String> = mapOf(
        "i" to "I", "im" to "I'm", "ive" to "I've", "ill" to "I'll", "id" to "I'd",
        "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't", "cant" to "can't", "wont" to "won't",
        "wouldnt" to "wouldn't", "couldnt" to "couldn't", "shouldnt" to "shouldn't", "isnt" to "isn't", "arent" to "aren't",
        "wasnt" to "wasn't", "werent" to "weren't", "hasnt" to "hasn't", "havent" to "haven't", "hadnt" to "hadn't",
        "thats" to "that's", "whats" to "what's", "theres" to "there's", "heres" to "here's", "youre" to "you're",
        "theyre" to "they're", "weve" to "we've", "youve" to "you've", "lets" to "let's", "hes" to "he's", "shes" to "she's",
    )

    private fun cased(w: String, typed: String): String {
        // a word that is English and not Turkish is capitalised the English way (if -> If, not İf)
        val loc = if (lang.locale.language == "tr" && w.none { it in "çğıöşü" } && KeyboardDictionary.isWord("en", w) && !KeyboardDictionary.isWord("tr", w)) Locale.ENGLISH else lang.locale
        return when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> w.uppercase(loc)
            typed.isNotEmpty() && typed[0].isUpperCase() -> w.replaceFirstChar { it.titlecase(loc) }
            else -> KeyboardDictionary.form(w)
        }
    }

    private val domains = listOf("@gmail.com", "@outlook.com", "@hotmail.com", "@yahoo.com", "@icloud.com", "@yandex.com", "@live.com", "@proton.me", "@msn.com", "@me.com")
    /** The domains of the addresses typed before, the most used first, then the usual ones: a short list that reads on the bar. */
    private fun rankedDomains(): List<String> {
        val counts = getSharedPreferences("pulse_kb_domains", MODE_PRIVATE).all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }
        val mine = counts.sortedByDescending { it.second }.map { "@" + it.first }
        return (mine + domains).distinct().take(8)
    }
    private val emailRe = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    /** An e-mail field: the addresses typed here before when it is empty, the usual domains once there is a name or an "@". */
    private fun emailsSaved(): List<String> = getSharedPreferences("pulse_kb_emails", MODE_PRIVATE).getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() }

    /** An e-mail address typed before the cursor is remembered, so its first letters offer it later in any field. */
    private fun captureEmail(c: android.view.inputmethod.InputConnection) {
        if (st.incognito || st.plain) return
        val tok = c.getTextBeforeCursor(80, 0)?.toString().orEmpty().trimEnd().takeLastWhile { !it.isWhitespace() }
        if (emailRe.matches(tok)) {
            val low = tok.lowercase()
            getSharedPreferences("pulse_kb_emails", MODE_PRIVATE).let { sp -> sp.edit().putString("list", (listOf(low) + emailsSaved().filter { it != low }).take(8).joinToString("\n")).apply() }
            // the domains typed most come first on the bar (counted here)
            val dom = low.substringAfter('@')
            getSharedPreferences("pulse_kb_domains", MODE_PRIVATE).let { sp -> sp.edit().putInt(dom, sp.getInt(dom, 0) + 1).apply() }
        }
    }

    /** The word right before the cursor (no spaces) holds an "@": the e-mail suggestions apply here, in any field. */
    private fun atTokenNow(): Boolean {
        val tok = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty().takeLastWhile { !it.isWhitespace() }
        return '@' in tok
    }

    private fun emailSuggestions() {
        val before = currentInputConnection?.getTextBeforeCursor(100, 0)?.toString().orEmpty()
        if (emailRe.matches(before)) lastEmail = before
        // the text being typed: the last word without spaces, so "@" in an earlier word does not count
        val tok = before.takeLastWhile { !it.isWhitespace() }
        val saved = getSharedPreferences("pulse_kb_emails", MODE_PRIVATE).getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() && !KeyboardDictionary.isBanned(it) }
        val list0 = when {
            // the addresses used most come first (the list is kept most recent first); all of them scroll on the bar
            tok.isEmpty() -> saved.take(2)
            // the continuation of an address: saved addresses first, then the domains after the "@"
            '@' in tok -> {
                val local = tok.substringBefore('@')
                val domainPart = tok.substringAfterLast('@')
                // (no whole saved address once an "@" is typed: only the domains, short, so more of them fit on the bar)
                val mine = emptyList<String>()
                // the domains are offered as "@gmail.com" alone: that is what is missing, it is short enough to read on the bar, and a tap puts only it after the typed name
                val dom = rankedDomains().filter { it.startsWith("@" + domainPart, true) && it.length > domainPart.length + 1 }
                // an address you saved that fits what is typed comes first (a tap writes it whole), then the domains, the ones used most first: four show, four more are a swipe away
                (mine.take(2) + dom.take(8)).distinct()
            }
            // the first letters of an address typed before ("umut"): in an e-mail field its saved addresses that begin so are offered whole, a tap writes one
            emailField -> saved.filter { it.startsWith(tok, true) && !it.equals(tok, true) }.take(2)
            // the domains (@gmail.com ...) only after an "@" is typed; otherwise they would replace words that have nothing to do with e-mail
            else -> emptyList()
        }
        val list = list0.filter { !KeyboardDictionary.isBanned(it) }
        // the system's autofill chips do not follow what is typed (they kept showing other addresses after "umut"): once there is text, only the bar's own suggestions stay
        if (tok.isNotEmpty() && st.inline.isNotEmpty()) st.inline = emptyList()
        // at least three slots, so the code that reads the first three never meets a short list
        slotWords = (list + listOf("", "", "")).take(maxOf(3, list.size)).toTypedArray()
        st.slots = slotWords.toList()
        // the chips of the e-mail bar read in full (no cut-off words) and scroll, in an e-mail field and anywhere an address is typed
        st.emailBar = list.isNotEmpty()
    }

    /** With nothing being typed, the right slot of the bar holds the text copied last (two taps paste it, then it goes). */
    private fun showClipSlot() {
        // a screenshot that was just taken takes the right slot for two minutes
        if ((shotItem != null || shotVideo != null) && !shotPinned && shotLeftMs() <= 0L) { shotItem = null; shotVideo = null; st.shotVideo = false; st.shotFile = ""; st.shotThumb = null }
        // it stays at the right while a word is typed too (no space needed): until it is pasted once or its time runs out
        if ((shotItem != null || shotVideo != null) && !emailField && !st.plain) {
            slotWords = arrayOf(slotWords[0], slotWords[1], "")
            st.slots = listOf(st.slots.getOrElse(0) { "" }, st.slots.getOrElse(1) { "" }, "\u0002")
            return
        }
        // a one-time code that just came in a notification (a setting): it waits at the right, a tap types it
        if (KeyboardPrefs.otpOn && word.isEmpty()) KbOtp.fresh()?.let { otp ->
            slotWords = arrayOf(slotWords[0], slotWords[1], otp)
            st.slots = listOf(st.slots.getOrElse(0) { "" }, st.slots.getOrElse(1) { "" }, "\u0001🔑 $otp")
            return
        }
        val q = quickClip ?: return
        // the copied text stays in the bar for two minutes; after that it is only in the clipboard (a newer copy replaces it)
        if (!quickPinned && SystemClock.uptimeMillis() - quickAt > 120_000L) { quickClip = null; return }
        // it stays at the right while a word is being typed too (until it is pasted twice, a newer copy comes, or the two minutes pass)
        if (emailField || st.plain) return
        val label = "\u0001" + q.replace('\n', ' ').take(14) + if (q.length > 14) "…" else ""
        slotWords = arrayOf(slotWords[0], slotWords[1], q)
        st.slots = listOf(st.slots.getOrElse(0) { "" }, st.slots.getOrElse(1) { "" }, label)
    }

    // a run that is still working is not cancelled by each new letter: typing faster than it finishes would then never get an answer at all.
    // It finishes (its result is dropped when the word has moved on) and one more run for the newest text follows at once.
    private var rerunNeeded = false

    /** The texts the vault chips in the bar write, in the order of the chips (empty when the bar shows words). */
    private var vaultTexts: List<String> = emptyList()

    /**
     * The logins of the Pulse password vault in the bar of a user-name or password box, read inside the app (no system autofill service). One chip for each
     * different login: the same user name with the same password shows once. Only for the app the login belongs to; not offered while the vault is behind the screen lock.
     */
    /** The logins the bar may offer: those of the app; in a browser the page's address is unknown to a keyboard, so every login that belongs to a web address is offered (the chip shows its name). */
    private fun loginPool(pkg: String?): List<com.umutk.pulse.features.VItem> {
        val own = com.umutk.pulse.features.VaultStore.matches(this, pkg, null)
        val browsers = setOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "org.mozilla.firefox_beta", "com.brave.browser", "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native", "com.vivaldi.browser", "com.duckduckgo.mobile.android", "org.chromium.chrome", "com.kiwibrowser.browser", "com.google.android.apps.chrome")
        if (pkg !in browsers) return own
        val web = com.umutk.pulse.features.VaultStore.all(this).filter { v -> v.type == 1 && v.pass.isNotEmpty() && v.uris.any { u -> com.umutk.pulse.features.VaultStore.packageOf(u) == null } }.sortedBy { it.name.lowercase() }
        return own + web
    }

    /**
     * A login chip was tapped: the box gets exactly that text. What was typed in the box before (the first letters of the user name, a half-written password) is taken
     * out first, so the chip no longer lands behind it ("na" + "nam..."), and the state of the word and of the last correction is cleared so the delete key does not bring an old word back.
     */
    private fun fillField(text: String) {
        val c = currentInputConnection ?: return
        runCatching {
            finishWord("", false); lastGlide = null; lastCorrection = null
            val before = c.getTextBeforeCursor(500, 0)?.length ?: 0
            val after = c.getTextAfterCursor(500, 0)?.length ?: 0
            c.beginBatchEdit()
            if (before > 0 || after > 0) c.deleteSurroundingText(before, after)
            c.commitText(text, 1)
            c.endBatchEdit()
            selStart = text.length; selEnd = text.length
        }
        vaultTexts = emptyList()
        afterText()
    }

    private fun loginSuggestions(): Boolean {
        vaultTexts = emptyList()
        val info = currentInputEditorInfo ?: return false
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        val pass = (cls == InputType.TYPE_CLASS_TEXT && v in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        // what the app says about the box: its hint, its label, its private options (a web page's field often says its name there)
        val label = (info.hintText?.toString().orEmpty() + " " + info.label?.toString().orEmpty() + " " + info.privateImeOptions.orEmpty()).lowercase()
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
        // a box that says it is something else (a school, a department, a name, an amount) is no login box, whatever else it looks like
        val notLogin = listOf("okul", "school", "bölüm", "bolum", "department", "faculty", "fakülte", "fakulte", "sınıf", "sinif", "class", "ad soyad", "full name", "first name", "last name", "soyad", "adres", "address", "city", "şehir", "sehir", "amount", "tutar", "adet", "quantity", "miktar", "yaş", "age", "search", "ara", "note", "not ")
            .any { label.contains(it) } && listOf("user", "kullanıcı", "kullanici", "login", "mail", "şifre", "sifre", "pass").none { label.contains(it) }
        if (notLogin) return false
        val passLabel = listOf("password", "passwd", "şifre", "sifre", "parola", "pass word", "pin kodu").any { label.contains(it) }
        val userLabel = listOf("user", "kullanıcı", "kullanici", "login", "e-posta", "eposta", "email", "e-mail", "mail", "hesap", "account", "tc kimlik", "kimlik no", "öğrenci", "ogrenci", "student").any { label.contains(it) }
        val passBox = pass || (cls == InputType.TYPE_CLASS_TEXT && passLabel)
        val user = !passBox && ((cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || v == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS || userLabel)) ||
            // a number box is a login box only when it says so (a student or ID number, a phone), or says nothing
            (numeric && (userLabel || label.isBlank())))
        if (!passBox && !user) return false
        // the vault's chips come only in number boxes (a student or ID number, a phone, a PIN)
        if (!numeric) return false
        // a verification code box (SMS, e-mail, authenticator) is no login: nothing from the vault is offered there
        if (listOf("otp", "one-time", "verification", "doğrulama", "dogrulama", "sms", "tek kullanımlık", "tek kullanimlik", "kod", "code").any { label.contains(it) }) return false
        if (com.umutk.pulse.features.AuthPrefs.biometric(this)) return false
        val typed = if (user) currentInputConnection?.getTextBeforeCursor(60, 0)?.toString().orEmpty() else ""
        // a password box that already has text is being typed in: the vault does not step in
        if (passBox && currentInputConnection?.getTextBeforeCursor(1, 0)?.isNotEmpty() == true) return false
        val seen = HashSet<String>()
        val list = loginPool(info.packageName)
            // a number box offers only the logins whose user name is a number (a student or ID number, a phone number)
            .filter { !numeric || passBox || it.user.isNotEmpty() && it.user.all { c -> c.isDigit() || c in "+ -()" } }
            .filter { typed.isEmpty() || it.user.startsWith(typed, true) && !it.user.equals(typed, true) }
            .filter { seen.add(it.user + "\u0000" + it.pass) }.take(6)
        if (list.isEmpty()) return false
        vaultTexts = list.map { if (passBox) it.pass else it.user }
        val shown = list.map { if (passBox) "•••••• " + it.name.ifBlank { it.user } else it.user }
        val n = maxOf(3, shown.size)
        slotWords = Array(n) { "" }
        st.slots = (shown + listOf("", "", "")).take(n)
        st.emailBar = true
        return true
    }

    private fun updateSuggestions() {
        // no text field to write in (a game, a screen with no field): the bar has nothing to suggest, and nothing looks like typing
        if (currentInputConnection == null || (currentInputEditorInfo?.inputType == 0 && !st.terminal)) { st.slots = listOf("", "", ""); slotWords = arrayOf("", "", ""); target = null; return }
        if (st.trans) scheduleTrans()
        // a terminal (Termux): no word is open and nothing is mended, but the bar offers the commands and the words typed there before
        if (st.terminal && termApp && st.plain && KeyboardPrefs.suggestions) { termSuggestions(); return }
        if (runCatching { loginSuggestions() }.getOrDefault(false)) return
        if (emailField || atTokenNow()) { emailSuggestions(); return }
        // a name field: the names typed in such fields before
        if (nameField && !st.incognito) {
            val saved = getSharedPreferences("pulse_kb_names", MODE_PRIVATE).getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() }
            val hit = if (word.isEmpty()) saved.take(3) else saved.filter { it.startsWith(word.toString(), true) && !it.equals(word.toString(), true) }.take(3)
            if (hit.isNotEmpty()) {
                slotWords = arrayOf(hit.getOrElse(0) { "" }, hit.getOrElse(1) { "" }, hit.getOrElse(2) { "" }); st.slots = slotWords.toList(); return
            }
        }
        // numbers typed before: from the first two digits the bar offers them whole (a tap writes the rest); works in number fields too
        noteNumber()
        numOffered = numberOffers()
        if (numOffered.isNotEmpty()) {
            slotWords = (numOffered + listOf("", "", "")).take(maxOf(3, numOffered.size)).toTypedArray(); st.slots = slotWords.toList(); st.emailBar = true; return
        } else if (st.emailBar && !emailField && !atTokenNow()) st.emailBar = false
        if (!KeyboardPrefs.suggestions || (st.plain && !st.terminal) || urlNow()) { st.slots = listOf("", "", ""); slotWords = arrayOf("", "", ""); target = null; return }
        // a sum before the cursor ("12*5", "2⁶+1"): its answer is offered in the middle of the bar at once, no "=" needed; a tap writes it the way the sum is written
        calcText = null
        if (!st.plain) runCatching {
            val bt = currentInputConnection?.getTextBeforeCursor(60, 0)?.toString().orEmpty()
            val ans = KbCalc.answerFor(bt) ?: currencyAnswer(bt)
            if (ans != null) { calcText = ans; slotWords = arrayOf("", "", ""); st.slots = listOf("", ans.trim(), ""); target = null; return }
        }
        detectLang()
        val typed = word.toString(); val code = st.lang; val l = lang; val prev = prevWord; val prev2 = prevWord2; val up = st.shift > 0
        // the text before the word, for the neural model (the word itself is the open one at the end)
        lmCtx = runCatching { currentInputConnection?.getTextBeforeCursor(160, 0)?.toString().orEmpty().let { b -> if (typed.isNotEmpty() && b.endsWith(typed)) b.dropLast(typed.length) else b } }.getOrDefault("")
        // the touch model needs the touch of every letter and the key positions; a word that was not touched letter by letter is judged without it
        val tc: KeyboardDictionary.TouchCtx? = if (typed.isNotEmpty() && touchPts.size >= typed.length && st.letterRects.isNotEmpty()) {
            val centers = HashMap<Char, androidx.compose.ui.geometry.Offset>()
            var w = 0f
            st.letterRects.entries.toList().forEach { (k, r) -> if (k.length == 1) { centers[k[0]] = r.center; w = r.width } }
            if (w > 0f) KeyboardDictionary.TouchCtx(touchPts.takeLast(typed.length), centers, w) else null
        } else null
        // right after an automatic correction the bar shows what was typed, quoted at the left, so it can be put back with a tap or kept with a long press
        val shown = lastCorrection
        if (typed.isEmpty() && shown != null) {
            // the emoji of the word that was just written stays on offer at the right: a tap writes it after the word, which stays
            val em = if (KeyboardPrefs.emojiPredictions) (KeyboardDictionary.emoji(shown.corrected) ?: EmojiData.forWord(shown.corrected)).orEmpty() else ""
            slotWords = arrayOf("", "", em); target = null
            st.slots = listOf("\"${shown.original}\"", shown.corrected, em)
            return
        }
        // the start of the field or of a sentence: the words a sentence begins with, before anything has been typed
        if (typed.isEmpty()) {
            val tb = currentInputConnection?.getTextBeforeCursor(3, 0)?.toString().orEmpty()
            if (tb.isEmpty() || tb.trimEnd(' ').let { it.isEmpty() || it.last() in ".!?\n" }) {
                val starts = (if (KeyboardLayouts.dict(code) == "tr") listOf("Merhaba", "Ben", "Evet", "Bir", "Ve") else listOf("I", "The", "Hello", "A", "And")).filter { !KeyboardDictionary.isBanned(it) }.let { all -> all.take(3).let { s -> s + List(3 - s.size) { "" } } to all.drop(3).take(slotCount() - 3) }.let { (s, more) -> startsMore = more; s }
                // an empty box of any kind: the left slot holds the address used most (a tap writes it, a long press shows the others), the middle and right the sentence words
                val mail = if (tb.isEmpty() && !st.plain) getSharedPreferences("pulse_kb_emails", MODE_PRIVATE).getString("list", "").orEmpty().split('\n').firstOrNull { it.isNotBlank() && '@' in it && !KeyboardDictionary.isBanned(it) } else null
                if (mail != null) { slotWords = arrayOf(mail, starts[0], starts[1]); st.slots = listOf(mail, starts[0], starts[1]); target = null; showClipSlot(); return }
                slotWords = (starts + startsMore).toTypedArray(); st.slots = starts + startsMore; target = null; showClipSlot(); return
            }
        }
        // every keystroke: the older correction work stops, and the new word is looked at at once (the completions); the corrections follow only when typing pauses
        KeyboardDictionary.touch()
        job?.cancel()
        job = scope.launch {
            // first the completions alone (a prefix look-up, no waiting), then the same with the corrections
            if (typed.length >= 2) {
                val q = withContext(Dispatchers.Default) { compute(code, l.locale, typed, prev, up, prev2, tc, fast = true) }
                if (word.toString() == typed) { target = q.third; targetFor = typed; val wd = widenBar(q.second, q.first, typed, code, prev, up); slotWords = wd.second; st.slots = wd.first; withPersonal(typed); withFiles(typed); showClipSlot() }
            }
            // the full pass (the corrections) waits for a short pause in the typing: while the letters come fast nothing heavy runs behind them
            kotlinx.coroutines.delay(55)
            if (word.toString() != typed) return@launch
            val res = withContext(Dispatchers.Default) { compute(code, l.locale, typed, prev, up, prev2, tc) }
            // nothing to suggest (an unknown word before, no completion): the bar is never left empty, it offers the words that most often come next
            if (word.toString() == typed && typed.isEmpty() && res.first.all { it.isBlank() }) {
                val fb = commonNext(code, up)
                slotWords = fb.toTypedArray(); st.slots = fb; target = null
                showClipSlot()
                return@launch
            }
            if (word.toString() == typed) {
                // a shortcut of the clipboard: the name typed offers the whole text in the middle
                // saved e-mail addresses are offered only in e-mail fields (above) or after an "@", never while ordinary words are typed
                val sc = if (typed.length >= 2) KeyShortcuts.find(this@PulseKeyboardService, typed.lowercase(l.locale)) else null
                if (sc == null) schedulePauseFix(typed, res.third, code, l.locale)
                if (sc != null) { target = null; slotWords = arrayOf(typed, sc, res.second[2]); st.slots = listOf("\"$typed\"", sc, res.first[2]) }
                else { target = res.third; targetFor = typed; val wd = widenBar(res.second, res.first, typed, code, prev, up); slotWords = wd.second; st.slots = wd.first; withPersonal(typed); withFiles(typed) }
                // nothing found for what is being typed (a first word, a name, a word no list knows): the bar is not left empty, it shows the word as typed (a tap keeps it)
                // and the same word with a capital (a name), so something is always there to tap
                if (typed.isNotEmpty() && st.slots.all { it.isBlank() }) {
                    val cap = typed.replaceFirstChar { it.uppercase() }.takeIf { it != typed }.orEmpty()
                    slotWords = arrayOf(typed, cap, "")
                    st.slots = listOf("\"$typed\"", cap, "")
                    target = null
                }
                showClipSlot()
            }
        }
    }

    /**
     * What is yours, offered in any field while the first letters are typed: your full name and your e-mail addresses (learned from what you typed, and from the vault's identity).
     * "umut" can be "Umut Kıvanç Şahin" or "umutsahin1950@gmail.com": both are put on the bar, the name first, beside the usual word.
     */
    private fun personalItems(typed: String): List<String> {
        if (st.incognito || typed.length < 2 || typed.any { it.isDigit() }) return emptyList()
        val key = com.umutk.pulse.OfFind.fold(typed, false)
        val names = LinkedHashSet<String>()
        names.addAll(getSharedPreferences("pulse_kb_fullnames", MODE_PRIVATE).getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() })
        val mails = LinkedHashSet<String>()
        mails.addAll(emailsSaved())
        if (!com.umutk.pulse.features.AuthPrefs.biometric(this)) runCatching {
            com.umutk.pulse.features.VaultStore.all(this).filter { it.type == 4 }.forEach { v ->
                val n = listOf("firstName", "middleName", "lastName").mapNotNull { v.fields[it]?.takeIf { s -> s.isNotBlank() } }.joinToString(" ")
                if (n.contains(' ')) names.add(n)
                v.fields["email"]?.takeIf { it.contains('@') }?.let { mails.add(it.lowercase()) }
            }
        }
        fun hit(s: String) = !s.equals(typed, true) && (com.umutk.pulse.OfFind.fold(s, false).startsWith(key) || s.split(' ', '.', '@').any { com.umutk.pulse.OfFind.fold(it, false).startsWith(key) && key.length >= 3 })
        return (names.filter { hit(it) && !KeyboardDictionary.isBanned(it) }.take(1) + mails.filter { hit(it) && !KeyboardDictionary.isBanned(it) }.take(1)).distinct()
    }

    /** Puts [personalItems] on the bar beside the usual words: the first on the right, the second on the left (unless that place holds the word as typed). */
    // ---- a terminal (Termux and the like): commands and words typed there before, offered on the bar; kept in memory only (a terminal sees passwords and tokens typed at prompts) ----
    private val termWord = StringBuilder()
    private val termSeen = LinkedHashMap<String, Int>()
    private var termLineStart = true

    /** What a key did in a terminal: letters and path signs build the word, a space or Enter ends it, delete takes one back. */
    private fun termKey(ch: Char) {
        if (!st.terminal || !st.plain) return
        when {
            ch == '\b' -> if (termWord.isNotEmpty()) termWord.setLength(termWord.length - 1)
            ch == ' ' || ch == '\n' -> { val had = termWord.isNotEmpty(); termNote(termWord.toString()); termWord.setLength(0); termLineStart = ch == '\n' || (termLineStart && !had) }
            ch.isLetterOrDigit() || ch in "-_./~" -> if (termWord.length < 40) termWord.append(ch)
            else -> { termNote(termWord.toString()); termWord.setLength(0) }
        }
        updateSuggestions()
    }

    /** A finished word is counted (plain words of 2 to 24 characters; long mixed strings that look like a key or a token are left out). */
    private fun termNote(w: String) {
        if (st.incognito || w.length !in 2..24 || !w.all { it.isLetterOrDigit() || it in "-_.~" }) return
        if (w.length >= 20 || (w.length >= 12 && w.any { it.isDigit() } && w.any { it.isUpperCase() } && w.any { it.isLowerCase() })) return
        termSeen.merge(w, 1, Int::plus)
        if (termSeen.size > 400) termSeen.remove(termSeen.keys.first())
    }

    /** The field is a terminal (or the code editor): the bar offers commands. */
    private var termApp = false

    private fun termSuggestions() {
        val t = termWord.toString()
        val hist = termSeen.byValueDesc().map { it.key }
        val pool = (hist + TERM_CMDS).distinct()
        val out = if (t.isEmpty()) (if (termLineStart) (hist.filter { it in TERM_CMDS } + listOf("ls", "cd", "git", "pkg")).distinct().take(3) else hist.take(3))
            else pool.filter { it.startsWith(t) && it != t }.take(3)
        slotWords = arrayOf(out.getOrElse(0) { "" }, out.getOrElse(1) { "" }, out.getOrElse(2) { "" })
        st.slots = slotWords.toList()
        target = null
    }

    // ---- a file named by the letters being typed (five or more): offered at the right of the bar ----
    private var fileHit: java.io.File? = null
    private var fileJob: kotlinx.coroutines.Job? = null

    private fun withFiles(typed: String) {
        fileJob?.cancel()
        if (!KeyboardPrefs.fileSuggest || typed.length < 5 || typed.any { it.isWhitespace() } || st.incognito || st.plain || passwordField) { fileHit = null; return }
        fileJob = scope.launch {
            delay(250)
            if (word.toString() != typed) return@launch
            val hit = withContext(Dispatchers.IO) {
                runCatching {
                    val root = android.os.Environment.getExternalStorageDirectory()
                    val dirs = listOf("Download", "Download/Pulse", "DCIM/Camera", "Pictures", "Pictures/Screenshots", "Documents", "Movies", "Music", "DCIM/Screenshots")
                    dirs.flatMap { d -> java.io.File(root, d).listFiles()?.toList().orEmpty() }
                        .filter { it.isFile && !it.name.startsWith(".") && !it.name.startsWith("temp_") && it.name.contains(typed, ignoreCase = true) }
                        .maxByOrNull { it.lastModified() }
                }.getOrNull()
            }
            if (word.toString() != typed || hit == null) { if (hit == null) fileHit = null; return@launch }
            fileHit = hit
            st.fileThumb = null
            val s = st.slots.toMutableList(); if (s.size > 2) { s[2] = "\u0003" + hit.name; st.slots = s }
            val w = slotWords.copyOf(); if (w.size > 2) { w[2] = ""; slotWords = w }
            // its picture follows a moment later (a photo, the first page of a document, a song's cover, a frame of a video)
            val th = runCatching { fileThumbOf(hit) }.getOrNull()
            if (fileHit == hit) st.fileThumb = th
        }
    }

    /** A small picture of [f] for the bar: the photo itself, or what Files shows for it (a page, a cover, a frame). Null when there is none. */
    private suspend fun fileThumbOf(f: java.io.File): androidx.compose.ui.graphics.ImageBitmap? = withContext(Dispatchers.IO) {
        val ext = f.extension.lowercase()
        val bmp = if (ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")) {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.path, o)
            var k = 1; while (o.outWidth / (k * 2) >= 160 && o.outHeight / (k * 2) >= 160) k *= 2
            android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = k })
        } else if (com.umutk.pulse.features.FilePreviews.supports(ext)) com.umutk.pulse.features.FilePreviews.load(this@PulseKeyboardService, f) else null
        bmp?.let { b -> val sc = 96f / maxOf(b.width, b.height); (if (sc < 1f) android.graphics.Bitmap.createScaledBitmap(b, (b.width * sc).toInt().coerceAtLeast(1), (b.height * sc).toInt().coerceAtLeast(1), true) else b).asImageBitmap() }
    }

    private fun withPersonal(typed: String) {
        val p = personalItems(typed)
        if (p.isEmpty()) return
        val w = slotWords.copyOf(); val s = st.slots.toMutableList()
        fun put(i: Int, v: String) { if (i < w.size) w[i] = v; if (i < s.size) s[i] = v }
        put(2, p[0])
        if (p.size > 1 && !s.getOrElse(0) { "" }.startsWith("\"")) put(0, p[1])
        slotWords = w; st.slots = s
    }

    /** The words written last (newest first), for a bar that would stay empty. */
    private val recentWords = java.util.ArrayDeque<String>()

    // ---- numbers typed again and again (a phone number, an ID number): they are counted here, on the phone only, and offered from their first digits ----
    private var passwordField = false
    private var numPending: String? = null
    private var numOffered: List<String> = emptyList()

    /** A payment card number (13-19 digits that pass the Luhn check) is never kept. */
    private fun cardLike(d: String): Boolean {
        if (d.length !in 13..19) return false
        var sum = 0; var alt = false
        for (i in d.indices.reversed()) { var n = d[i] - '0'; if (alt) { n *= 2; if (n > 9) n -= 9 }; sum += n; alt = !alt }
        return sum % 10 == 0
    }
    private fun numbersKept(): Map<String, Int> = KbNumbers.all(this)

    /** The run of digits just before the cursor, when it stands alone (not part of a word or of a longer text). */
    private fun digitsBefore(c: android.view.inputmethod.InputConnection): String {
        val b = c.getTextBeforeCursor(24, 0)?.toString().orEmpty()
        val run = b.takeLastWhile { it.isDigit() }
        if (run.isEmpty()) return ""
        val prev = b.getOrNull(b.length - run.length - 1)
        return if (prev == null || !prev.isLetterOrDigit()) run else ""
    }

    /** What the digits before the cursor are worth remembering: a number of 7 to 19 digits, from a field that is not a password, not a card number. */
    private fun noteNumber() {
        if (!KeyboardPrefs.rememberNumbers || st.incognito || passwordField) { numPending = null; return }
        val d = digitsBefore(currentInputConnection ?: return)
        numPending = if (d.length in 7..19 && !cardLike(d)) d else null
    }

    /** The number typed last counts once more (when the field is left, or Enter or space follows it). */
    private fun commitNumber() {
        val n = numPending ?: return
        numPending = null
        if (!KeyboardPrefs.rememberNumbers || st.incognito || passwordField || cardLike(n) || KeyboardDictionary.isBanned(n)) return
        val all = numbersKept().toMutableMap()
        // a number is kept once it was typed three times; the shorter and the longer form of one number are not two entries: a shorter one typed after the full number counts for the full one,
        // and the full number typed after its shorter drafts takes their count over (the last, complete form is the one that stays)
        val longer = all.keys.firstOrNull { it.length > n.length && it.startsWith(n) }
        if (longer != null) all[longer] = (all[longer] ?: 0) + 1
        else {
            var count = 1
            all.keys.filter { it.length < n.length && n.startsWith(it) }.forEach { count += all.remove(it) ?: 0 }
            all[n] = (all[n] ?: 0) + count
        }
        // 20 numbers at most: the least used go
        KbNumbers.save(this, all.byValueDesc().take(20).associate { it.key to it.value })
    }

    /** The saved numbers that continue the digits typed: typed three times at least (or added by hand in the settings), the most used first. */
    private fun numberOffers(): List<String> {
        if (!KeyboardPrefs.rememberNumbers || st.incognito || passwordField || emailField) return emptyList()
        val d = digitsBefore(currentInputConnection ?: return emptyList())
        if (d.length < 3) return emptyList()
        return numbersKept().filter { it.value >= KbNumbers.KEEP_AT && it.key.startsWith(d) && it.key.length > d.length && !KeyboardDictionary.isBanned(it.key) }
            .byValueDesc().take(3).map { it.key }
    }

    /** For a bar that would stay empty: the last words you wrote (a few of them), the most common words of the language filling what is missing (capital at the start when shift is on). */
    private fun commonNext(code: String, up: Boolean): List<String> {
        val common = if (KeyboardLayouts.dict(code) == "tr") listOf("ve", "bir", "bu") else listOf("the", "to", "and")
        val w = (recentWords.toList().take(3) + common).filter { !KeyboardDictionary.isBanned(it) }.distinct().take(3)
        return if (up) w.map { it.replaceFirstChar { c -> c.uppercase() } } else w
    }

    /** How many words the bar shows: three on a phone, five from 600 dp of width, six from 900 dp (a tablet, also in landscape). */
    /** The sentence-start words beyond the first three, for a wide bar. */
    private var startsMore: List<String> = emptyList()

    private fun slotCount(): Int {
        val w = resources.configuration.screenWidthDp
        // about one word per 115 dp of the bar: landscape and low-DPI screens (more dp across) show more, a portrait phone keeps three
        return (w / 115).coerceIn(3, 7)
    }

    /** The first three slots stay as they are (the word, the one in the middle, the special one); more words follow them when the screen is wide. */
    private fun widenBar(words: Array<String>, list: List<String>, typed: String, code: String, prev: String?, up: Boolean): Pair<List<String>, Array<String>> {
        val n = slotCount()
        if (n <= 3) return list to words
        val have = HashSet<String>(); list.forEach { if (it.isNotEmpty()) have.add(it.lowercase(lang.locale)) }
        val lowerTyped = typed.lowercase(lang.locale)
        val cands: List<String> = if (typed.isEmpty()) KeyboardDictionary.next(code, prev, 14).filter { !KeyboardDictionary.isBanned(it) }
            .map { if (up) it.replaceFirstChar { c -> c.titlecase(lang.locale) } else KeyboardDictionary.form(it) }
        else KeyboardDictionary.complete(dictCodes(code).first(), lowerTyped, 14).map { it.word }
            .map { if (up) it.replaceFirstChar { c -> c.titlecase(lang.locale) } else it }
        val extra = cands.filter { it.isNotEmpty() && have.add(it.lowercase(lang.locale)) }.take(n - 3)
        val newList = list.take(3).let { it + List(maxOf(0, 3 - it.size)) { "" } } + extra
        val newWords = words.take(3).toTypedArray().let { arr -> Array(3) { arr.getOrElse(it) { "" } } } + extra.toTypedArray()
        return newList to newWords
    }

    /** Two words run together with a slip in one of them: one half a word of the list, the other a near word ("kşabye" -> "kısa bye"). Null when there is no such pair. */
    private fun looseSplit(code: String, lower: String): String? {
        var best: Pair<String, Float>? = null
        for (i in 2..lower.length - 2) {
            val a = lower.substring(0, i); val b = lower.substring(i)
            val aw = KeyboardDictionary.isWord(code, a); val bw = KeyboardDictionary.isWord(code, b)
            if (!aw && !bw) continue
            val ca = if (aw) KeyboardDictionary.Cand(a, 0f, 0f) else KeyboardDictionary.correct(code, a, .9f).filter { !KeyboardDictionary.isBanned(it.word) }.minByOrNull { it.cost } ?: continue
            val cb = if (bw) KeyboardDictionary.Cand(b, 0f, 0f) else KeyboardDictionary.correct(code, b, .9f).filter { !KeyboardDictionary.isBanned(it.word) }.minByOrNull { it.cost } ?: continue
            val cost = ca.cost + cb.cost
            if (best == null || cost < best.second) best = (ca.word + " " + cb.word) to cost
        }
        return best?.takeIf { it.second <= 1.2f }?.first
    }

    /** The three slots of the bar (shown and as words), and the word the space key would correct [typed] to. */
    private fun compute(code: String, loc: Locale, typed: String, prev: String?, up: Boolean, prev2: String? = null, tc: KeyboardDictionary.TouchCtx? = null, fast: Boolean = false, ctxText: String? = null): Triple<List<String>, Array<String>, String?> {
        KeyboardDictionary.begin()
        if (typed.isEmpty()) {
            // the next word is chosen by the sentence so far, not only by the last two words: the candidates (the person's own pairs, their words, the language's pairs) are weighed by
            // the neural model after the whole text before the cursor; the order of the old rule still counts a little, so what the person writes most is not lost
            val pool = KeyboardDictionary.next(code, prev, 16, prev2).filter { !KeyboardDictionary.isBanned(it) }
            val nnLang0 = KeyboardLayouts.dict(code)
            val lm0 = if (!fast && KeyboardPrefs.neuralOn) NeuralLm.get(this, nnLang0) else null
            val ranked = if (lm0 == null || pool.size <= 3) pool else runCatching {
                val t0 = System.nanoTime()
                val stt = NeuralLm.state(lm0, nnLang0, (ctxText ?: lmCtx).let { if (lm0.cased()) it else it.lowercase(loc) })
                val r = pool.withIndex().toList().sortedByKeyOnce { (i, w) -> lm0.score(stt, w) + (pool.size - i) * .15f }.map { it.value }
                NeuralLm.took((System.nanoTime() - t0) / 1_000_000L)
                r
            }.getOrDefault(pool)
            val n = ranked.take(3).map { if (up) it.replaceFirstChar { c -> c.titlecase(loc) } else KeyboardDictionary.form(it) }
            // after a word and a space: the emoji of the word just written takes the last place
            val em = if (KeyboardPrefs.emojiPredictions && prev != null) (KeyboardDictionary.emoji(prev) ?: EmojiData.forWord(prev)) else null
            val w = arrayOf(n.getOrElse(0) { "" }, n.getOrElse(1) { "" }, em ?: n.getOrElse(2) { "" })
            return Triple(w.toList(), w, null)
        }
        val lower = typed.lowercase(loc)
        val codes = dictCodes(code)
        // candidates come from the sentence's language; a word that is valid in any language of the keyboard is still left alone (valid below)
        val candCodes = if (ctxLang != null) codes.take(1) else codes
        val comp = candCodes.flatMap { KeyboardDictionary.complete(it, lower, 6) }.sortedByKeyOnce { it.score + KeyboardDictionary.pairBoost(prev, it.word, prev2) }.take(5)
        val maxCost = if (KeyboardLayouts.dict(code) == "tr") (if (lower.length >= 5) .8f else .65f) else if (lower.length >= 4) 1.3f else .7f
        // a learned slip never turns a real word into a rarer one ("tak" into something else because both were typed once): it must lead to a clearly likelier word
        val learned = KeyboardDictionary.typoFix(this, lower)?.takeIf { l ->
            !codes.any { KeyboardDictionary.isWord(it, lower) } || codes.maxOf { KeyboardDictionary.baseScore(it, l) } > codes.maxOf { KeyboardDictionary.baseScore(it, lower) } + 2f
        }
        val tr = codes.any { KeyboardDictionary.dictOf(it) == "tr" }
        // long words may hold more slips; Turkish words are also mended as a base word plus its endings
        // a word that is the chosen share alike (90% by default) to a known word is mended on its own: 12 letters allow one slip, 20 letters two
        val ratioLimit = ((100 - KeyboardPrefs.autoRatio) / 100f * maxOf(lower.length, 4)).coerceAtMost(1.2f)
        val limit = maxOf(if (lower.length >= 8) maxCost + .5f else maxCost, ratioLimit)
        // the fast pass (completions only) gives the bar its words at once; the full pass with the corrections follows and replaces it
        // the word list is read once, as far as the neural model reaches; the nearer words of the rule are taken from the same reading
        val reach = if (KeyboardPrefs.neuralOn && lower.length >= 3) maxOf(limit, NN_REACH) else limit
        val nearAll = if (!fast && lower.length >= 2) candCodes.flatMap { KeyboardDictionary.correct(it, lower, reach, tc) } else emptyList()
        // the words the touches point to (a letter on a key's edge could have been the next key's)
        val touchC = if (!fast && tc != null && lower.length >= 3) candCodes.flatMap { KeyboardDictionary.touchCands(it, lower, tc, 4) } else emptyList()
        val corr = if (!fast && lower.length >= 2) (nearAll.filter { it.cost <= limit } + touchC + (if (tr) KeyboardDictionary.trCorrect(lower, 4, tc) else emptyList()) +
            (if (learned != null) listOf(KeyboardDictionary.Cand(learned, 40f, 0f)) else emptyList())).sortedByKeyOnce { it.score + KeyboardDictionary.pairBoost(prev, it.word, prev2) }.distinctBy { it.word }.take(6) else if (fast && learned != null) listOf(KeyboardDictionary.Cand(learned, 40f, 0f)) else emptyList()
        val valid = learned == null && codes.any { KeyboardDictionary.isWord(it, lower) }
        // the word the space key puts in: a near word, unless the typed one is already a word or too short to guess
        // tested on 100 000 of the author's own chat lines (a Python copy of this rule): a correction needs a cost of .8 at most (one neighbouring key, an accent, a swap)
        // and a candidate that is a common word (base score 7 up, the word's own frequency before the cost is taken off); that took the wrong corrections from 27 to 11 of 120 and the changed words of one's own from 105 to 41 of 160
        // a stretched word on purpose ("tamamdırrr", "buuu"): three of one letter in a row is left as it is
        val stretched = Regex("(\\p{L})\\1\\1").containsMatchIn(lower)
        // the strength setting (modest to very aggressive) sets how far a correction may reach and how common the word must be; a word that follows the one before it
        // in the user's own or the built-in pairs may reach a little further, as the context backs it
        val capL = floatArrayOf(.6f, .8f, 1.0f, 1.2f)[KeyboardPrefs.autoLevel.coerceIn(0, 3)]
        val minL = floatArrayOf(8f, 7f, 6f, 5f)[KeyboardPrefs.autoLevel.coerceIn(0, 3)]
        // a name or brand typed with capitals inside ("iPhone", "McDonald") or a short all-capital word (an acronym) is left as it is
        val keepCase = typed.length >= 2 && (typed.drop(1).any { it.isUpperCase() } && !typed.all { it.isUpperCase() } || (typed.all { it.isUpperCase() } && typed.length <= 5))
        val fix0 = if (stretched || keepCase || fast) null else corr.firstOrNull { it.word != lower && !KeyboardDictionary.isBanned(it.word) }?.takeIf {
            val ctx = KeyboardDictionary.pairBoost(prev, it.word, prev2) > 0f
            lower.length >= 3 && kotlin.math.abs(it.word.length - lower.length) <= 1 && it.cost <= minOf(limit, capL + (if (ctx) .2f else 0f)) && it.score + 4f * it.cost >= minL - (if (ctx) 1f else 0f) &&
                (!valid || it.score - (corr.firstOrNull { c -> c.word == lower }?.score ?: 0f) > 5f)
        }
        // two words run together ("birdahaki"): the space goes between them
        // two neighbouring letters swapped into a common word ("wrok" for "work"): always a slip, whatever was learned or undone before
        val swapFix = if (stretched || lower.length !in 3..20 || codes.any { KeyboardDictionary.baseScore(it, lower) > 0f } || (tr && KeyboardDictionary.trKnown(lower))) null else (0 until lower.length - 1).mapNotNull { i ->
            if (lower[i] == lower[i + 1]) null else {
                val w = lower.substring(0, i) + lower[i + 1] + lower[i] + lower.substring(i + 2)
                val s = codes.maxOf { KeyboardDictionary.baseScore(it, w) }
                if (s >= 9f && !KeyboardDictionary.isBanned(w) && KeyboardDictionary.rejectCount(lower, w) < KeyboardPrefs.undoToKeep) KeyboardDictionary.Cand(w, s, .6f) else null
            }
        }.maxByOrNull { it.score }
        // two words that run together (a key typed for the space among them) are split before any other mending
        // (a word the person typed together by habit is learned as a word, and a word of the list can be two common ones: both are split too; only an inflected form is left whole)
        val ownBase = codes.maxOfOrNull { KeyboardDictionary.baseScore(it, lower) } ?: 0f
        val split = if (!fast && !stretched && lower.length >= 6 && !(tr && KeyboardDictionary.trKnown(lower) && ownBase == 0f)) codes.firstNotNullOfOrNull { KeyboardDictionary.splitWords(it, lower, ownBase) }?.let { KeyboardDictionary.Cand(it, 0f, .5f) } else null
        // the neural model (CharLm, trained on open Turkish and English text): every near word is weighed by how well it fits the sentence before it, the typing
        // cost (in bits, NN_LAM per unit) taken off; the typed word wins unless a near word beats it by a margin (a real word needs a bigger one).
        // It also reaches a missing or an extra letter ("mrhaba", "gelyorum"), which the old rule could not afford, and orders the bar by the sentence.
        val nnLang = KeyboardLayouts.dict(code)
        val lm = if (!fast && KeyboardPrefs.neuralOn && lower.length >= 2 && !stretched && !keepCase) NeuralLm.get(this, nnLang) else null
        val nnScore = HashMap<String, Float>()
        var nnFix: KeyboardDictionary.Cand? = null
        val nnT0 = System.nanoTime()
        if (lm != null) runCatching {
            // the model knows capitals and punctuation: the text before goes in as it is, and every word is weighed in the case it would be written in
            val cased = lm.cased()
            val stt = NeuralLm.state(lm, nnLang, (ctxText ?: lmCtx).let { if (cased) it else it.lowercase(loc) })
            val capFirst = cased && typed.firstOrNull()?.isUpperCase() == true
            fun form(w: String) = if (capFirst) w.replaceFirstChar { it.titlecase(loc) } else w
            val wider = if (lower.length >= 3 && limit < NN_REACH) nearAll else emptyList()
            val pool = (corr + wider).filter { it.word != lower && !KeyboardDictionary.isBanned(it.word) && kotlin.math.abs(it.word.length - lower.length) <= 2 }
                .sortedBy { it.cost }.distinctBy { it.word }.take(6)
            // the words the person writes weigh a little more (log2 of how often), but a learned word is not taken as a real one: typos left
            // uncorrected were learned too ("öok", "dğzelt"), and they must still be mended
            // the person's own count and the word's count in the language both weigh in (log2): tested on the owner's 1500 real slips with their own
            // sentences, this took the mended ones from 69% to 83% and the wrong mends from 20% to 12%, the synthetic test from 82% to 85% (clean words: 0.1%)
            fun mine(w: String) = NN_USER * kotlin.math.ln(KeyboardDictionary.userCount(w) + 1f) / kotlin.math.ln(2f) +
                NN_FREQ * codes.maxOf { KeyboardDictionary.baseScore(it, w) } / kotlin.math.ln(2f)
            // the typed word first: a near word that cannot come within NN_SLACK bits of the best so far (or of the lowest the typed word could be beaten at)
            // is dropped as soon as its letters have made it too unlikely, so most near words cost one or two steps of the model, not the whole word
            val typedLm = lm.score(stt, form(lower)) + mine(lower)
            var top = typedLm - 4f
            fun weigh(w: String, pen: Float): Float {
                val m = mine(w)
                val sc = lm.score(stt, form(w), top - NN_SLACK + pen - m) - pen + m
                if (sc > top) top = sc
                return sc
            }
            // a typed word that is no word at all is a slip more readily than a real word is: its edits cost less
            val typedListed = codes.any { KeyboardDictionary.inLexicon(it, lower) }
            for (cd in pool) nnScore[cd.word] = weigh(cd.word, NN_LAM * cd.cost * (if (typedListed) 1f else NN_UNK_MUL))
            for (cd in comp) if (cd.word != lower && cd.word !in nnScore) nnScore[cd.word] = weigh(cd.word, NN_COMP)
            val listed = codes.any { KeyboardDictionary.inLexicon(it, lower) }
            val best = pool.maxByOrNull { nnScore.getValue(it.word) }
            if (best != null) {
                val diacriticOnly = best.cost <= .15f * lower.length && best.word.length == lower.length
                val margin = (if (listed) (if (nnLang == "en") NN_VALID_EN else NN_VALID) else NN_UNKNOWN) + (if (lower.length <= 3 && !diacriticOnly) NN_SHORT else 0f) -
                    // the strength setting moves the margin: modest asks 2 bits more, aggressive 2 less, very aggressive 4 less
                    (KeyboardPrefs.autoLevel.coerceIn(0, 3) - 1) * 2f
                if ((lower.length >= 3 || diacriticOnly) && nnScore.getValue(best.word) > typedLm + margin) nnFix = best
            }
        }
        if (lm != null) NeuralLm.took((System.nanoTime() - nnT0) / 1_000_000L)
        // a blocked word is never the correction, whichever of the ways found it
        val fix = (swapFix ?: split ?: if (lm != null) (learned?.takeIf { it != lower }?.let { KeyboardDictionary.Cand(it, 40f, 0f) } ?: nnFix) else fix0)?.takeIf { !KeyboardDictionary.isBanned(it.word) }
        val others = (comp.map { it.word } + corr.map { it.word } + nnScore.keys).distinct().filter { it != lower && it != fix?.word && !KeyboardDictionary.isBanned(it) }
            .let { o -> if (nnScore.isEmpty()) o else o.sortedByKeyOnce { nnScore[it] ?: -1e9f } }
        // a word nothing is near (a slip the rule cannot reach): the bar is not left empty. A wider look for near words, and two words run together ("kşabye": "kısa bye")
        val others2 = if (!fast && fix == null && others.size < 2 && lower.length >= 3 && !stretched && !keepCase) {
            val wide = candCodes.flatMap { KeyboardDictionary.correct(it, lower, limit + 1.6f, tc) }.filter { it.word != lower && !KeyboardDictionary.isBanned(it.word) }
                .sortedBy { it.cost }.distinctBy { it.word }.map { it.word }.take(3)
            val pair = if (lower.length in 5..16) candCodes.firstNotNullOfOrNull { c0 -> looseSplit(c0, lower) } else null
            (others + listOfNotNull(pair) + wide).distinct().filter { it != lower }
        } else others
        val emoji = if (KeyboardPrefs.emojiPredictions) (KeyboardDictionary.emoji(lower) ?: EmojiData.forWord(lower)) else null
        val mid: String; val left: String; val right: String
        // Shift pressed while the word is open: the middle holds the capital-letter form (the one the correction would give, when there is one), and a tap puts it in
        val loc = lang.locale
        val capital = up && typed.isNotEmpty() && typed[0].isLetter() && typed[0].isLowerCase()
        fun capped(w: String) = if (capital) w.replaceFirstChar { it.titlecase(loc) } else w
        if (fix != null) { mid = capped(cased(fix.word, typed)); left = "\"$typed\""; right = emoji ?: others2.firstOrNull()?.let { cased(it, typed) }.orEmpty() }
        else if (capital) { mid = capped(typed); left = "\"$typed\""; right = emoji ?: others2.getOrNull(0)?.let { capped(cased(it, typed)) }.orEmpty() }
        else { mid = typed; left = others2.getOrNull(0)?.let { cased(it, typed) }.orEmpty(); right = emoji ?: others2.getOrNull(1)?.let { cased(it, typed) }.orEmpty() }
        val words = arrayOf(if (fix != null || capital) typed else left, mid, right)
        return Triple(listOf(left, mid, right), words, fix?.let { capped(cased(it.word, typed)) })
    }
}

// The neural correction rule (bits): cost weight, how far it reaches, the handicap of a completion, and the margin the typed word keeps
// (a known word, an unknown one, a short one). Tuned on held-out sentences with made-up slips (see the PR notes).
// (26, was 20: on the owner's 1123 real slips without their sentences, 20 mended 75% and changed 3.0% of clean words; 28 mended 78% and changed 1.7%.
// With the sentence the model has more to say, so the weight is moved most of the way, not all of it.)
private const val NN_LAM = 26f
private const val NN_REACH = 1.05f
private const val NN_UNK_MUL = 1f
private const val NN_COMP = 3f
private const val NN_VALID = 8f
// English: a real word is changed only when another word is 14 bits likelier (made-up slips, 4000 words: the clean words that were changed fell from 7.2% to 3.5%, the slips mended stayed)
private const val NN_VALID_EN = 14f
private const val NN_UNKNOWN = 0f
private const val NN_SHORT = 4f
private const val NN_USER = 3f
private const val NN_FREQ = 1f
private const val NN_SLACK = 4f

/** A picture made of characters (block shading, several lines): not an emoji, so it is left out of the recent list. */
private fun isArt(s: String): Boolean =
    s.contains('\n') || s.contains('\u23CE') || s.any { it in '\u2580'..'\u259F' || it in '\u2800'..'\u28FF' }

/** A space already stands right after the cursor (the text after a mark was typed before), so the keyboard adds none. */
private fun spaceAfterCursor(c: android.view.inputmethod.InputConnection): Boolean =
    c.getTextAfterCursor(1, 0)?.firstOrNull()?.isWhitespace() == true

/** The Turkish endings that follow a name (case, the locative, the ablative, with the vowel of the name); the apostrophe goes before them. */
private val TR_GEO_HEADS = listOf("üniversitesi", "havalimanı", "köprüsü", "caddesi", "sokağı", "meydanı", "stadyumu", "parkı", "müzesi", "vadisi", "körfezi", "denizi", "boğazı", "ovası", "yaylası", "nehri", "gölü", "adası", "dağı")

private val TR_ENDINGS = setOf("ı", "i", "u", "ü", "a", "e", "na", "ne", "nı", "ni", "nu", "nü", "ya", "ye", "yı", "yi", "yu", "yü", "da", "de", "ta", "te", "dan", "den", "tan", "ten", "nın", "nin", "nun", "nün", "ın", "in", "un", "ün", "la", "le", "yla", "yle", "ndan", "nden", "nda", "nde")

/** The commands of a shell that a terminal bar offers from their first letters (Termux, adb, git, Python, Rust, Gradle). */
private val TERM_CMDS = listOf("ls", "cd", "cp", "mv", "rm", "mkdir", "rmdir", "cat", "less", "head", "tail", "nano", "vim", "pwd", "echo", "touch", "chmod", "chown", "ln", "du", "df", "find", "grep",
    "sed", "awk", "sort", "uniq", "wc", "tar", "zip", "unzip", "gzip", "curl", "wget", "ssh", "scp", "rsync", "ping", "ps", "top", "htop", "kill", "killall", "clear", "exit", "history", "which", "man", "env",
    "export", "source", "git", "git status", "pkg", "apt", "pip", "python", "python3", "node", "npm", "cargo", "rustup", "rustc", "gradle", "gradlew", "adb", "rish", "termux-setup-storage", "termux-open",
    "termux-clipboard-get", "termux-clipboard-set", "sudo", "su", "tmux", "make", "cmake", "java", "ffmpeg", "yt-dlp", "storage", "sdcard", "Download")

/**
 * Sorts by a score read once for each item. sortedByDescending asks for the score at every comparison, and a score that is read from the learning tables (which another thread
 * updates while the keyboard works) can change between two comparisons: the sort then stops with "Comparison method violates its general contract".
 */
private fun <T> List<T>.sortedByKeyOnce(key: (T) -> Float): List<T> = map { it to key(it).let { k -> if (k.isNaN()) -1e9f else k } }.sortedByDescending { it.second }.map { it.first }
