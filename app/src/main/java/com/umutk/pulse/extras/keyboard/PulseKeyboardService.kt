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
        if (!uriField) return false
        val b = currentInputConnection?.getTextBeforeCursor(60, 0)?.toString().orEmpty() + word
        return b.any { it == '.' || it == '/' || it == ':' || it == '@' } && !b.contains(' ')
    }
    private var noAuto = false
    // the last text copied: the right slot of the bar offers it for two pastes
    private var quickClip: String? = null
    private var quickAt = 0L
    private var quickLeft = 0
    private var quickPinned = false
    // the copied text leaves the bar after two minutes even when nothing is typed: the bar is refreshed then (it only refreshed on a key before)
    private val quickExpiry = android.os.Handler(android.os.Looper.getMainLooper())
    private val quickExpire = Runnable { if (quickClip != null && !quickPinned && SystemClock.uptimeMillis() - quickAt >= 120_000L) { quickClip = null; updateSuggestions() } }
    private var lastEmail: String? = null
    private class Correction(val original: String, val corrected: String, val sep: String)
    private var lastCorrection: Correction? = null
    // the space that came by itself behind a comma: Backspace takes it away with the comma
    private var commaSpace = false
    /** The word as it stood before the user started deleting letters of it: if the corrected form ends as a real word, the first form was a typo. */
    private var peak: String? = null
    // a letter was typed right behind "word." with no space: the space and a capital are put in when the word ends, unless it is an address
    private var dotFix = false
    private val tlds = setOf("com", "net", "org", "edu", "gov", "io", "co", "me", "app", "dev", "info", "biz", "tr", "uk", "de", "fr", "es", "it", "ru", "us", "tv", "xyz", "online", "site", "tech", "store", "ai", "gg", "ly", "to", "cc", "eu", "nl", "pl", "jp", "cn", "br", "in", "au", "ca", "ch", "se", "no", "fi", "dk", "gen", "web", "html", "php")
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
        val clip = runCatching { clipboard?.primaryClip }.getOrNull()?.takeIf { it.itemCount > 0 } ?: return
        if (st.incognito || st.plain) return
        if (android.os.Build.VERSION.SDK_INT >= 24 && clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return
        val stamp = if (android.os.Build.VERSION.SDK_INT >= 26) clip.description.timestamp else 0L
        val sig = "$stamp|" + (clip.getItemAt(0).text?.toString()?.take(200) ?: clip.getItemAt(0).uri?.toString().orEmpty())
        if (sig == lastClipSig) return
        if (onOpen && (stamp == 0L || android.os.SystemClock.elapsedRealtime() - stamp > 120_000)) { lastClipSig = sig; return }
        lastClipSig = sig
        val item = clip.getItemAt(0)
        val uri = item.uri
        if (uri != null && clip.description.hasMimeType("image/*")) {
            scope.launch(Dispatchers.IO) { val l = ClipStore.addImage(this@PulseKeyboardService, uri); withContext(Dispatchers.Main) { st.clipItems = l } }
        } else {
            // a very long copy can come as a text file behind an address instead of inline: it is read from there (up to 2 MB)
            val t = (runCatching { item.coerceToText(this)?.toString() }.getOrNull()?.takeIf { uri == null || it != uri.toString() }
                ?: uri?.let { u -> runCatching { contentResolver.openInputStream(u)?.use { ins -> String(ins.readBytes().let { b -> if (b.size > 2_000_000) b.copyOf(2_000_000) else b }) } }.getOrNull() })?.trim()
            // a long copy used to skip the quick-paste slot entirely (nothing showed); it belongs there too, the bar label is cut short regardless of the length
            // the newest copy wins the slot: a copied text takes it from a screenshot that is still shown
            if (!t.isNullOrEmpty()) { shotItem = null; st.shotThumb = null; quickClip = t; quickAt = SystemClock.uptimeMillis(); quickLeft = 2; quickPinned = false; st.quickPinned = false; quickExpiry.removeCallbacks(quickExpire); quickExpiry.postDelayed(quickExpire, 120_000L); updateSuggestions(); st.clipItems = ClipStore.addText(this, t); if (t.length <= 600) st.clips = (listOf(t) + st.clips.filter { it != t }).take(20) }
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
    override fun authPick(code: String) {
        currentInputConnection?.commitText(code, 1)
        st.panel = "keys"
        afterText()
    }

    /** Quick auth: the fingerprint first when the setting asks for it, then the codes of the accounts (computed now). */
    private fun openAuth() {
        if (!KeyboardPrefs.authLock || android.os.Build.VERSION.SDK_INT < 28) { showAuth(); return }
        val cancel = android.os.CancellationSignal()
        val prompt = android.hardware.biometrics.BiometricPrompt.Builder(this)
            .setTitle(com.umutk.pulse.tr("Quick auth", "Hızlı kimlik doğrulama"))
            .setDescription(com.umutk.pulse.tr("Confirm it is you to see the codes", "Kodları görmek için kimliğini doğrula"))
            .setNegativeButton(com.umutk.pulse.tr("Cancel", "İptal"), mainExecutor) { _, _ -> cancel.cancel() }
            .build()
        prompt.authenticate(cancel, mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) { showAuth() }
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

    override fun authTick() { if (st.panel == "auth") refreshAuth() }

    override fun pasteClip(item: ClipItem) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        val path = item.image
        // a text goes in and is also the copied text again (the phone's clipboard), so it can be pasted from anywhere
        if (path == null) { c.commitText(item.text, 1); clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("", item.text)); st.panel = "keys"; afterText(); return }
        val info = currentInputEditorInfo
        val types = if (info != null) androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(info) else emptyArray()
        val ok = types.any { it.startsWith("image/") || it == "*/*" }
        if (!ok) { android.widget.Toast.makeText(this, com.umutk.pulse.tr("This field takes no pictures", "Bu alan resim kabul etmiyor"), android.widget.Toast.LENGTH_SHORT).show(); return }
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
        val sorted = votes.entries.sortedByDescending { it.value }
        val top = sorted.firstOrNull(); val second = sorted.getOrNull(1)?.value ?: 0
        ctxLang = if (top != null && top.value >= 2 && top.value >= 2 * second) top.key else null
    }

    override fun onCreate() {
        super.onCreate()
        runCatching { (getSystemService(Context.INPUT_SERVICE) as android.hardware.input.InputManager).registerInputDeviceListener(deviceListener, null) }
        KeyboardPrefs.load(this)
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
        runCatching { contentResolver.registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, shotObserver) }
    }

    // ---- a screenshot or a camera photo just taken waits in the right slot of the bar, to be pasted from there --------------------------------------------------
    private var shotItem: ClipItem? = null
    // a photo or screenshot waits 30 seconds in the bar, then it goes (the bar is refreshed then too)
    private val shotExpiry = android.os.Handler(android.os.Looper.getMainLooper())
    private val shotExpire = Runnable { if (shotItem != null && !shotPinned) { shotItem = null; st.shotThumb = null; updateSuggestions() } }
    private var shotPinned = false
    private var shotAt = 0L
    private val shotObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        // looked at three times: a screenshot is listed only when its file is finished, which takes a moment (longer for a long or an edited one)
        override fun onChange(selfChange: Boolean) { scope.launch { delay(900); checkShot(); delay(2500); checkShot(); delay(6000); checkShot() } }
    }
    /** The pictures already taken in (their numbers in the phone's picture list), so none is taken twice and none is skipped when two come close together. */
    private val shotsTaken = LinkedHashSet<Long>()
    private val shotLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Takes the new screenshots, camera photos and downloaded pictures into the clipboard: only those made in the last moments, each of them once (two made
     * close together are both taken). The newest goes to the bar.
     */
    private suspend fun checkShot() {
        if (st.incognito) return
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        if (!shotLock.tryLock()) return
        var items: List<ClipItem>? = null
        var fresh = false
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
                    val out = ArrayList<Pair<Long, Long>>()
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
                            val down = path.contains("/download", true)
                            // only what is new: a picture made in the last moments, never the older ones of the gallery
                            if (shot && age <= 45 || cam && age <= 30 || down && age <= 45) out += c.getLong(0) to age
                        }
                    }
                    out
                }.getOrNull()
            }.orEmpty().filter { it.first !in shotsTaken }
            if (found.isEmpty()) return
            // the oldest first, so the newest ends up the newest of the clipboard
            for ((id, age) in found.reversed()) {
                val uri = android.net.Uri.withAppendedPath(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                val got = withContext(Dispatchers.IO) { val n0 = ClipStore.load(this@PulseKeyboardService).count { it.image != null }; val l = ClipStore.addImage(this@PulseKeyboardService, uri); if (l.count { it.image != null } > n0) l else null }
                // a file that could not be read yet (still being written) is not marked: the next look takes it
                if (got != null) { items = got; shotsTaken += id; if (age <= 40) fresh = true }
            }
            while (shotsTaken.size > 40) shotsTaken.remove(shotsTaken.first())
            sp.edit().putString("ids", shotsTaken.joinToString(",")).apply()
        } finally { shotLock.unlock() }
        val list = items ?: return
        st.clipItems = list
        if (!fresh) return
        val item = list.filter { it.image != null }.maxByOrNull { it.time } ?: return
        // the newest copy wins the slot: a screenshot takes it from a copied text
        quickClip = null; shotItem = item; shotAt = SystemClock.uptimeMillis(); shotPinned = false; st.quickPinned = false
        st.shotThumb = withContext(Dispatchers.IO) { runCatching {
            val bmp = android.graphics.BitmapFactory.decodeFile(item.image, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }) ?: return@runCatching null
            // the camera stores a portrait photo sideways with an orientation tag: it is turned upright here
            val deg = when (android.media.ExifInterface(item.image!!).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
            (if (deg == 0f) bmp else android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(deg) }, true)).asImageBitmap()
        }.getOrNull() }
        shotExpiry.removeCallbacks(shotExpire); shotExpiry.postDelayed(shotExpire, 30_000L)
        updateSuggestions()
    }

    override fun onDestroy() {
        runCatching { recognizer?.destroy() }; recognizer = null
        clipboard?.removePrimaryClipChangedListener(clipListener)
        runCatching { contentResolver.unregisterContentObserver(shotObserver) }
        KeyboardDictionary.saveUser(this)
        runCatching { (getSystemService(Context.INPUT_SERVICE) as android.hardware.input.InputManager).unregisterInputDeviceListener(deviceListener) }
        owner?.stop(); scope.cancel()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    private var kbRoot: View? = null

    /**
     * The floating keyboard: the window is tall (the keyboard sits at the bottom of it and moves up inside it), so only the keyboard's own part takes touches and
     * the app does not make room for it; everything else in the window is the app's. The normal keyboard keeps the system's own insets.
     */
    override fun onComputeInsets(outInsets: android.inputmethodservice.InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        val r = st.floatRect
        val root = kbRoot
        if (!KeyboardPrefs.floatNow) return
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
        val o = owner ?: StandByOwner().also { it.start(); owner = it }
        // Compose finds its lifecycle on the root of the window, which for a keyboard is the window's decor view
        window.window?.decorView?.let { it.setViewTreeLifecycleOwner(o); it.setViewTreeSavedStateRegistryOwner(o) }
        return ComposeView(this).apply {
            kbRoot = this
            // the floating keyboard moved: the system is asked again which part of the window takes touches
            st.refreshInsets = { post { this@PulseKeyboardService.window.window?.decorView?.requestLayout() } }
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

    /** Takes the kind of the field from [info] (plain keys, address, web address, name, no automatic correction); true when any of them changed. */
    private fun applyKind(info: EditorInfo): Boolean {
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        val nAuto = (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        val nPlain = info.inputType == 0 || (cls != InputType.TYPE_CLASS_TEXT && cls != 0) ||
            v in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        val nEmail = cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || v == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        val nUri = cls == InputType.TYPE_CLASS_TEXT && v == InputType.TYPE_TEXT_VARIATION_URI
        val nName = cls == InputType.TYPE_CLASS_TEXT && v == InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        val changed = nAuto != noAuto || nPlain != st.plain || nEmail != emailField || nUri != uriField || nName != nameField
        noAuto = nAuto; st.plain = nPlain; emailField = nEmail; uriField = nUri; nameField = nName
        return changed
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // a restart of the input with the keyboard already up: the new kind is taken at once
        if (restarting) syncKind(attribute)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        if (!restarting) runCatching { takeClip(true) }
        // a screenshot or download made a moment before the keyboard came up is offered too
        if (!restarting) scope.launch { runCatching { checkShot() } }
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
    }

    /**
     * Is a physical keyboard attached? The configuration alone is not enough: a Bluetooth or USB keyboard connected after the service started may not show in it yet,
     * so the input devices are asked too (a real keyboard with letter keys, not the phone's own virtual one).
     */
    private fun hardwareAttached(): Boolean {
        if (resources.configuration.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS) return true
        return runCatching {
            android.view.InputDevice.getDeviceIds().any { id ->
                val d = android.view.InputDevice.getDevice(id) ?: return@any false
                !d.isVirtual && (d.sources and android.view.InputDevice.SOURCE_KEYBOARD) == android.view.InputDevice.SOURCE_KEYBOARD &&
                    d.keyboardType == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC
            }
        }.getOrDefault(false)
    }

    // a keyboard plugged in or paired while a field is open: the screen keyboard follows at once (no key press needed)
    private val deviceListener = object : android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) { st.hardware = hardwareAttached() }
        override fun onInputDeviceRemoved(deviceId: Int) { st.hardware = hardwareAttached(); if (!st.hardware) st.hwOpen = false }
        override fun onInputDeviceChanged(deviceId: Int) { st.hardware = hardwareAttached() }
    }

    // a keyboard that is closed with the emoji (or clipboard, or tool) page up comes back as the plain keys, whichever way it is shown again
    private fun backToKeys() { st.panel = "keys"; st.emojiQuery = null; st.toolbar = false }
    override fun onFinishInputView(finishingInput: Boolean) { super.onFinishInputView(finishingInput); backToKeys() }
    override fun onWindowHidden() { super.onWindowHidden(); backToKeys() }
    override fun onWindowShown() { super.onWindowShown(); backToKeys() }

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

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
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
            currentInputConnection?.finishComposingText()
            word.clear(); peak = null; lastCorrection = null; updateSuggestions()
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

    private fun turkishQ(keyCode: Int, shift: Boolean): String? = when (keyCode) {
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
        val tr = if (KeyboardPrefs.langList().firstOrNull() == "tr" && asciiDevice) turkishQ(keyCode, e.isShiftPressed) else null
        val ch = tr ?: e.getUnicodeChar(e.metaState).takeIf { it >= 32 }?.let { String(Character.toChars(it)) } ?: return false
        turkishDown.add(keyCode)
        type(ch)
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val e = event ?: return super.onKeyDown(keyCode, event)
        // a key from a real keyboard: one is attached, whatever the configuration said (its letters then go through the keyboard's own typing: suggestions and automatic correction)
        if (!st.hardware && e.device?.let { d -> !d.isVirtual && d.keyboardType == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC } == true) st.hardware = true
        if (st.hardware && hardwareKey(keyCode, e)) return true
        if (e.isCtrlPressed || e.isAltPressed || e.isMetaPressed || e.repeatCount > 0) return super.onKeyDown(keyCode, event)
        if (KeyboardPrefs.langList().firstOrNull() != "tr") return super.onKeyDown(keyCode, event)
        // the device layout already types Turkish (not plain ASCII): the system's own letter is right, nothing to change
        val device = e.getUnicodeChar(0)
        if (device !in 1..127) return super.onKeyDown(keyCode, event)
        val ch = turkishQ(keyCode, e.isShiftPressed) ?: return super.onKeyDown(keyCode, event)
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
        st.keyTick++
        val c = currentInputConnection ?: return
        if (s.isEmpty()) return
        // the emoji search is open: the keys type into its box
        st.emojiQuery?.let { if (st.panel == "emoji") { st.emojiQuery = it + s; return } }
        st.clipName?.let { if (st.panel == "clip") { if (s.all { ch -> ch.isLetterOrDigit() }) st.clipName = it + s; return } }
        if (st.trans) { st.transIn += s; scheduleTrans(); return }
        if (st.artOn) { st.artIn += s; return }
        if (s.codePointCount(0, s.length) > 1 || Character.isSurrogate(s[0])) { finishWord("", false); c.commitText(s, 1); afterText(); return }
        lastGlide = null
        commaSpace = false
        if (s.length == 1 && (st.ctrl > 0 || st.alt > 0 || st.win > 0 || st.shiftMod > 0) && sendChord(s[0])) return
        var ch = s[0]
        val letter = ch.isLetter() || (ch == '\'' && word.isNotEmpty())
        // fast typing: the keys are redrawn a moment after the automatic capital is used, so the next letters can still arrive as capitals; inside the word they stay small
        if (letter && ch.isUpperCase() && st.shift == 0 && word.isNotEmpty() && !st.plain && SystemClock.uptimeMillis() - shiftDropAt < 700) ch = ch.toString().lowercase(lang.locale).first()
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
        } else {
            // a second comma right behind a comma (or the comma and its space) is dropped: one comma stays
            if (ch == ',' && word.isEmpty() && c.getTextBeforeCursor(2, 0)?.toString()?.let { it.endsWith(",") || it == ", " } == true) return
            // a word is only mended when the space key ends it: a period, comma or bracket leaves what was typed as it is
            // a full stop behind a word of the dictionary ends a sentence (not in addresses, passwords or numbers): the space and the capital come with the next letter
            val before = word.toString().lowercase(lang.locale)
            val dotOk = ch == '.' && before.length >= 2 && KeyboardDictionary.isKnown(before) && !st.plain && !emailField && !urlNow() && KeyboardPrefs.autoSpace
            if (!finishWord(s, false, s)) c.commitText(s, 1)
            punctTyped = ch in ",;!?:" || dotOk
            dotEnd = ch in "!?" || dotOk
            // a mark behind a word (, ; : ! ? or a full stop after a known word) is followed by a space at once (not in numbers, addresses, passwords or web addresses)
            if ((ch == ',' || ch in ";:!?" || dotOk) && !st.plain && !emailField && !urlNow() && c.getTextBeforeCursor(2, 0)?.getOrNull(0)?.isLetter() == true && !spaceAfterCursor(c)) {
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
                try { f.get(70, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (e: java.util.concurrent.TimeoutException) { pending = f; timedOut = true; null }
            }.getOrNull()
        // "word.next" typed together: a space (and a capital) after the period, unless the next word is a domain ending, ends with a dot itself, or is no word at all
        var spaceBefore = false
        if (dotFix) {
            dotFix = false
            val lw = typed.lowercase(lang.locale)
            if (!inAddr && sep != "." && typed.length >= 2 && lw !in tlds && KeyboardDictionary.isKnown(lw)) spaceBefore = true
        }
        if (correct && KeyboardPrefs.autocorrect && !KeyboardPrefs.practicing && !st.plain && !urlNow() && (!noAuto || uriField) && t != null && !t.equals(typed, true) && !KeyboardDictionary.isKept(typed) && (!KeyboardDictionary.isRejected(typed.lowercase(lang.locale), t.lowercase(lang.locale)) || (KeyboardDictionary.isSwap(typed.lowercase(lang.locale), t.lowercase(lang.locale)) && KeyboardDictionary.rejectCount(typed.lowercase(lang.locale), t.lowercase(lang.locale)) < 1))) out = t
        // a name typed in lower case takes its capital when the word is ended: adana becomes Adana (words that are also ordinary words do not)
        if (lang.code == "tr" && out == typed && typed.none { it.isUpperCase() } && typed.lowercase(lang.locale).let { it in KeyboardDictionary.properNouns && it !in KeyboardDictionary.commonNames })
            out = typed.replaceFirstChar { it.titlecase(lang.locale) }
        // English grammar on the word just ended: a lone "i" and its contractions, and the common contractions typed without the apostrophe
        if (lang.code == "en" && out == typed) EN_GRAMMAR[typed.lowercase(Locale.ENGLISH)]?.let { out = it }
        // an English first name or place typed in lower case is written with its capital (wendy -> Wendy, london -> London)
        if (lang.code == "en" && out == typed && typed.none { it.isUpperCase() } && typed.lowercase(Locale.ENGLISH) in KeyboardDictionary.properNamesEn)
            out = typed.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
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
        if (!out.equals(typed, true)) flashFix(c, typed, out, tail.length)
        peak?.let { pk ->
            val lc = lang.locale
            val a = pk.lowercase(lc); val b = typed.lowercase(lc)
            if (out == typed && KeyboardPrefs.learn && !st.plain && !st.incognito && a != b && a.length >= 3 && b.length >= 3 && Math.abs(a.length - b.length) <= 2 &&
                (KeyboardDictionary.similar(a, b) || KeyboardDictionary.similarLoose(a, b)) && KeyboardDictionary.isWord(st.lang, b)) KeyboardDictionary.learnTypo(this, a, b)
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
        word.clear(); target = null
        // typing fast left no time to mend the word: it is mended a moment later, in its place, when nothing new has been started behind it
        if (timedOut && out == typed) lateFix(typed, sep, tail, codeNow, locNow, pwNow, pw2Now, upNow, ctxNow, pending)
        return true
    }

    /** Works the correction out for a word that was left uncorrected (typing fast) and puts it in its place, only when the text before the cursor still ends with it and no word is open. */
    private fun lateFix(typed: String, sep: String, tail: String, code: String, loc: Locale, pw: String?, pw2: String?, up: Boolean, ctx: String = lmCtx, pending: java.util.concurrent.Future<String?>? = null) {
        if (!KeyboardPrefs.autocorrect || !KeyboardPrefs.suggestions) return
        scope.launch {
            // the work that was already running goes on (nothing is started again); a new run only when there was none
            val fix = withContext(Dispatchers.Default) {
                runCatching { if (pending != null) pending.get(3, java.util.concurrent.TimeUnit.SECONDS) else KeyboardDictionary.uncancelled { compute(code, loc, typed, pw, up, pw2, ctxText = ctx).third } }.getOrNull()
            } ?: return@launch
            val lo = typed.lowercase(loc); val fl = fix.lowercase(loc)
            if (fl == lo || KeyboardDictionary.isKept(typed) || KeyboardDictionary.isRejected(lo, fl)) return@launch
            val key = typed + tail
            // the word is mended where it stands, at once: while the next word is being typed too (its letters, still open, are put back behind the mended word
            // as the open word), or when the next word is closed. Only when something else is going on (a selection, another edit) does it wait a moment.
            var tries = 0
            while (tries++ < 30) {
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
        runCatching {
            val end = (if (android.os.Build.VERSION.SDK_INT >= 31) c.getSurroundingText(1, 0, 0)?.let { it.offset + it.selectionStart } else null)
                ?: c.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.let { it.startOffset + it.selectionStart } ?: return
            val at = end - after - new.length
            if (at >= 0) c.commitCorrection(android.view.inputmethod.CorrectionInfo(at, old, new))
        }
    }

    private fun afterText() { updateCaps(); updateSuggestions() }

    override fun space() {
        val c = currentInputConnection ?: return
        captureEmail(c)
        st.emojiQuery?.let { if (st.panel == "emoji") { if (it.isNotEmpty() && !it.endsWith(" ")) st.emojiQuery = "$it "; return } }
        if (st.clipName != null && st.panel == "clip") return
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
            val tail = c.getTextBeforeCursor(prevWord!!.length + 1, 0)?.toString().orEmpty()
            val ends = tail.length == prevWord!!.length + 1 && tail.last() == ' ' && tail.dropLast(1).equals(prevWord, true)
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
        val c = currentInputConnection ?: return
        if (st.clipName != null && st.panel == "clip") { clipShortcutSave(); return }
        if (st.trans) { transOp("apply"); return }
        captureEmail(c)
        runCatching { trackLogin() }
        finishWord("\n", true)
        if (st.enter != "newline") {
            val info = currentInputEditorInfo
            c.performEditorAction(info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_DONE)
        } else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        lastWasSpace = false; punctTyped = false; prevWord = null
        afterText()
    }

    override fun backspace() {
        val c = currentInputConnection ?: return
        st.emojiQuery?.let { if (st.panel == "emoji") { st.emojiQuery = it.dropLast(1); return } }
        st.clipName?.let { if (st.panel == "clip") { st.clipName = it.dropLast(1); return } }
        if (st.trans && st.transIn.isNotEmpty()) { st.transIn = st.transIn.dropLast(1); scheduleTrans(); return }
        if (st.artOn) { if (st.artIn.isNotEmpty()) st.artIn = st.artIn.dropLast(1); return }
        lastWasSpace = false
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
                if (!st.incognito) KbStats.add(this, "undone")
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
        if (commaSpace && tb.length >= 2 && tb.last() == ' ' && tb[tb.length - 2] in ",;:!?." && c.getSelectedText(0).isNullOrEmpty()) { commaSpace = false; c.deleteSurroundingText(2, 0) }
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
        // a word is open: the bar shows (or drops) its capital-letter form
        if (word.isNotEmpty()) updateSuggestions()
    }

    override fun page(p: Int) { finishWord("", false); st.page = p; st.panel = "keys"; if (p == 0) updateCaps() }

    override fun emojiPanel() { finishWord("", false); st.emojiQuery = null; if (st.panel != "emoji") st.emojiTab = if (st.recents.isEmpty()) 1 else 0; st.panel = if (st.panel == "emoji") "keys" else "emoji"; st.toolbar = false }

    override fun lang() {
        val l = KeyboardPrefs.langList()
        finishWord("", false)
        st.lang = l[(l.indexOf(st.lang) + 1).mod(l.size)]
        scope.launch(Dispatchers.IO) { KeyboardDictionary.load(this@PulseKeyboardService, st.lang); withContext(Dispatchers.Main) { updateSuggestions() } }
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
        // the quoted word is what was typed: held, it is saved as a right word (never "corrected" again)
        if (w.startsWith("\"") || (w.isEmpty() && i == 0 && st.slots.getOrNull(0)?.startsWith("\"") == true)) {
            val typed = if (word.isNotEmpty()) word.toString() else lastCorrection?.original.orEmpty()
            if (typed.length >= 2 && typed.none { it.isDigit() }) {
                KeyboardDictionary.learnWord(typed, 4)
                lastCorrection?.let { KeyboardDictionary.reject(this, it.original, it.corrected); KbStats.add(this, "undone") }
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
        android.widget.Toast.makeText(this, com.umutk.pulse.tr("Will not suggest \"$w\" again", "\"$w\" bir daha önerilmeyecek"), android.widget.Toast.LENGTH_SHORT).show()
        updateSuggestions()
    }

    override fun slot(i: Int) {
        st.banAsk = -1
        // a login of the vault: its user name or password is written as it is
        if (vaultTexts.isNotEmpty()) { vaultTexts.getOrNull(i)?.let { fillField(it) }; return }
        val qc = quickClip
        // the screenshot just taken: a tap pastes it (when the field takes pictures)
        if (i == 2 && word.isEmpty() && st.slots.getOrNull(2)?.startsWith("\u0002") == true) {
            shotItem?.let { pasteClip(it) }
            if (!shotPinned) { shotItem = null; st.shotThumb = null }; updateSuggestions(); return
        }
        if (i == 2 && qc != null && word.isEmpty() && st.slots.getOrNull(2)?.startsWith("\u0001") == true) {
            currentInputConnection?.commitText(qc, 1)
            // pasted twice, it leaves the bar (a pinned one stays); it stays in the clipboard list either way
            quickLeft--
            if (quickLeft <= 0 && !quickPinned) quickClip = null
            afterText(); return
        }
        val c = currentInputConnection ?: return
        // the word the typing was just corrected from stands quoted at the left: a tap puts it back
        val lcb = lastCorrection
        if (i == 0 && word.isEmpty() && lcb != null && st.slots.getOrNull(0)?.startsWith("\"") == true) {
            val n = lcb.corrected.length + lcb.sep.length
            lastCorrection = null
            if (c.getTextBeforeCursor(n, 0)?.toString() == lcb.corrected + lcb.sep) {
                if (KeyboardPrefs.learn && !st.incognito) KeyboardDictionary.reject(this, lcb.original, lcb.corrected)
                if (!st.incognito) KbStats.add(this, "undone")
                c.beginBatchEdit(); c.deleteSurroundingText(n, 0); c.setComposingText(lcb.original, 1); c.endBatchEdit()
                word.append(lcb.original); target = null
            }
            updateSuggestions(); return
        }
        val w = slotWords.getOrNull(i).orEmpty()
        if (w.isEmpty()) return
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
            word.clear(); target = null
            // a tapped suggestion that is not a completion: this typo means that word from now on
            if (!isEmoji && KeyboardPrefs.learn && !st.plain && !st.incognito) KeyboardDictionary.learnTypo(this, typed.lowercase(lang.locale), w.lowercase(lang.locale))
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
        if (!multi) {
            val pos = if (delta < 0) selStart - before.length else selStart + after.length
            val p = pos.coerceAtLeast(0)
            if (p != selStart || p != selEnd) { c.setSelection(p, p); selStart = p; selEnd = p }
            return
        }
        val code = if (delta < 0) android.view.KeyEvent.KEYCODE_DPAD_UP else android.view.KeyEvent.KEYCODE_DPAD_DOWN
        repeat(Math.abs(delta)) {
            c.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
            c.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
        }
    }

    override fun deleteWord() {
        val c = currentInputConnection ?: return
        finishWord("", false); lastGlide = null; lastCorrection = null
        feedback()
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
        when (id) {
            "menu" -> st.panel = if (st.panel == "menu") "keys" else "menu"
            "clip" -> st.panel = if (st.panel == "clip") "keys" else "clip"
            "auth" -> if (st.panel == "auth") st.panel = "keys" else openAuth()
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
            "onehand" -> { KeyboardPrefs.oneHand = when (KeyboardPrefs.oneHand) { "off" -> "right"; "right" -> "left"; else -> "off" }; KeyboardPrefs.save(this) }
            "arrows" -> { KeyboardPrefs.arrows = !KeyboardPrefs.arrows; KeyboardPrefs.save(this) }
            "keyset" -> st.panel = if (st.panel == "keyset") "keys" else "keyset"
            "layout" -> st.panel = if (st.panel == "layout") "keys" else "layout"
            // (turned sideways the keyboard has its own two switches)
            "numbers" -> { if (st.land) KeyboardPrefs.landNumberRow = !KeyboardPrefs.landNumberRow else KeyboardPrefs.numberRow = !KeyboardPrefs.numberRow; KeyboardPrefs.save(this) }
            "proRow" -> { if (st.land) KeyboardPrefs.landProRow = !KeyboardPrefs.landProRow else KeyboardPrefs.proRow = !KeyboardPrefs.proRow; KeyboardPrefs.save(this) }
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

    override fun hide() { finishWord("", false); requestHideSelf(0) }

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

    private fun stopListening() {
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
        if (st.ctrl == 1) st.ctrl = 0
        if (st.alt == 1) st.alt = 0
        if (st.win == 1) st.win = 0
        if (st.shiftMod == 1) st.shiftMod = 0
    }

    private fun sendKey(code: Int, extraMeta: Int = 0) {
        val c = currentInputConnection ?: return
        val t = SystemClock.uptimeMillis(); val meta = metaNow() or extraMeta
        c.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        c.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
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
        evs.filter { it.keyCode !in setOf(59, 60, 57, 58, 113, 114, 117, 118) }.forEach { e -> sendKey(e.keyCode, if (shifted) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0) }
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

    override fun extraKey(id: String) {
        val d = ExtraKeys.byId(id) ?: return
        finishWord("", false); lastGlide = null; lastWasSpace = false
        when (id) {
            "ctrl" -> st.ctrl = (st.ctrl + 1) % 3
            "alt" -> st.alt = (st.alt + 1) % 3
            "win" -> st.win = (st.win + 1) % 3
            "shiftm" -> st.shiftMod = (st.shiftMod + 1) % 3
            "fn" -> st.fnOpen = !st.fnOpen
            "email" -> insertEmailDomain()
            "selectAll", "copy", "cut", "paste", "undo", "redo" -> edit(id)
            // Alt + Tab: the phone's recent apps (the accessibility helper), as the editor cannot switch windows
            "tab" -> if (st.alt > 0) { com.umutk.pulse.extras.launcher.globalAction(this, android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS); useModifiers() } else sendKey(d.code)
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
            tok.isEmpty() -> saved.take(8)
            // the continuation of an address: saved addresses first, then the domains after the "@"
            '@' in tok -> {
                val local = tok.substringBefore('@')
                val domainPart = tok.substringAfterLast('@')
                val mine = saved.filter { it.startsWith(tok, true) && !it.equals(tok, true) }
                // the domains are offered as "@gmail.com" alone: that is what is missing, it is short enough to read on the bar, and a tap puts only it after the typed name
                val dom = rankedDomains().filter { it.startsWith("@" + domainPart, true) && it.length > domainPart.length + 1 }
                // an address you saved that fits what is typed comes first (a tap writes it whole), then the domains, the ones used most first: four show, four more are a swipe away
                (mine.take(2) + dom.take(8)).distinct()
            }
            // the domains (@gmail.com ...) only after an "@" is typed; otherwise they would replace words that have nothing to do with e-mail
            else -> emptyList()
        }
        val list = list0.filter { !KeyboardDictionary.isBanned(it) }
        // at least three slots, so the code that reads the first three never meets a short list
        slotWords = (list + listOf("", "", "")).take(maxOf(3, list.size)).toTypedArray()
        st.slots = slotWords.toList()
        // the chips of the e-mail bar read in full (no cut-off words) and scroll, in an e-mail field and anywhere an address is typed
        st.emailBar = list.isNotEmpty()
    }

    /** With nothing being typed, the right slot of the bar holds the text copied last (two taps paste it, then it goes). */
    private fun showClipSlot() {
        // a screenshot that was just taken takes the right slot for two minutes
        if (shotItem != null && !shotPinned && SystemClock.uptimeMillis() - shotAt > 30_000L) { shotItem = null; st.shotThumb = null }
        if (shotItem != null && word.isEmpty() && !emailField && !st.plain) {
            slotWords = arrayOf(slotWords[0], slotWords[1], "")
            st.slots = listOf(st.slots.getOrElse(0) { "" }, st.slots.getOrElse(1) { "" }, "\u0002")
            return
        }
        val q = quickClip ?: return
        // the copied text stays in the bar for two minutes; after that it is only in the clipboard (a newer copy replaces it)
        if (!quickPinned && SystemClock.uptimeMillis() - quickAt > 120_000L) { quickClip = null; return }
        if (word.isNotEmpty() || emailField || st.plain) return
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
        if (currentInputConnection == null || currentInputEditorInfo?.inputType == 0) { st.slots = listOf("", "", ""); slotWords = arrayOf("", "", ""); target = null; return }
        if (st.trans) scheduleTrans()
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
        if (!KeyboardPrefs.suggestions || st.plain || urlNow()) { st.slots = listOf("", "", ""); slotWords = arrayOf("", "", ""); target = null; return }
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
                val starts = if (KeyboardLayouts.dict(code) == "tr") listOf("Merhaba", "Ben", "Evet") else listOf("I", "The", "Hello")
                slotWords = arrayOf(starts[0], starts[1], starts[2]); st.slots = starts; target = null; showClipSlot(); return
            }
        }
        // every keystroke: the older correction work stops, and the new word is looked at at once (the completions); the corrections follow only when typing pauses
        KeyboardDictionary.touch()
        job?.cancel()
        job = scope.launch {
            // first the completions alone (a prefix look-up, no waiting), then the same with the corrections
            if (typed.length >= 2) {
                val q = withContext(Dispatchers.Default) { compute(code, l.locale, typed, prev, up, prev2, tc, fast = true) }
                if (word.toString() == typed) { target = q.third; targetFor = typed; val wd = widenBar(q.second, q.first, typed, code, prev, up); slotWords = wd.second; st.slots = wd.first; withPersonal(typed) }
            }
            // the full pass (the corrections) waits for a short pause in the typing: while the letters come fast nothing heavy runs behind them
            kotlinx.coroutines.delay(20)
            if (word.toString() != typed) return@launch
            val res = withContext(Dispatchers.Default) { compute(code, l.locale, typed, prev, up, prev2, tc) }
            if (word.toString() == typed) {
                // a shortcut of the clipboard: the name typed offers the whole text in the middle
                // saved e-mail addresses are offered only in e-mail fields (above) or after an "@", never while ordinary words are typed
                val sc = if (typed.length >= 2) KeyShortcuts.find(this@PulseKeyboardService, typed.lowercase(l.locale)) else null
                if (sc != null) { target = null; slotWords = arrayOf(typed, sc, res.second[2]); st.slots = listOf("\"$typed\"", sc, res.first[2]) }
                else { target = res.third; targetFor = typed; val wd = widenBar(res.second, res.first, typed, code, prev, up); slotWords = wd.second; st.slots = wd.first; withPersonal(typed) }
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
    private fun withPersonal(typed: String) {
        val p = personalItems(typed)
        if (p.isEmpty()) return
        val w = slotWords.copyOf(); val s = st.slots.toMutableList()
        fun put(i: Int, v: String) { if (i < w.size) w[i] = v; if (i < s.size) s[i] = v }
        put(2, p[0])
        if (p.size > 1 && !s.getOrElse(0) { "" }.startsWith("\"")) put(0, p[1])
        slotWords = w; st.slots = s
    }

    /** How many words the bar shows: three on a phone, five from 600 dp of width, six from 900 dp (a tablet, also in landscape). */
    private fun slotCount(): Int {
        val w = resources.configuration.screenWidthDp
        return when { w >= 900 -> 6; w >= 600 -> 5; else -> 3 }
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

    /** The three slots of the bar (shown and as words), and the word the space key would correct [typed] to. */
    private fun compute(code: String, loc: Locale, typed: String, prev: String?, up: Boolean, prev2: String? = null, tc: KeyboardDictionary.TouchCtx? = null, fast: Boolean = false, ctxText: String? = null): Triple<List<String>, Array<String>, String?> {
        KeyboardDictionary.begin()
        if (typed.isEmpty()) {
            val n = KeyboardDictionary.next(code, prev, 6).filter { !KeyboardDictionary.isBanned(it) }.take(3).map { if (up) it.replaceFirstChar { c -> c.titlecase(loc) } else KeyboardDictionary.form(it) }
            // after a word and a space: the emoji of the word just written takes the last place
            val em = if (KeyboardPrefs.emojiPredictions && prev != null) (KeyboardDictionary.emoji(prev) ?: EmojiData.forWord(prev)) else null
            val w = arrayOf(n.getOrElse(0) { "" }, n.getOrElse(1) { "" }, em ?: n.getOrElse(2) { "" })
            return Triple(w.toList(), w, null)
        }
        val lower = typed.lowercase(loc)
        val codes = dictCodes(code)
        // candidates come from the sentence's language; a word that is valid in any language of the keyboard is still left alone (valid below)
        val candCodes = if (ctxLang != null) codes.take(1) else codes
        val comp = candCodes.flatMap { KeyboardDictionary.complete(it, lower, 6) }.sortedByDescending { it.score + KeyboardDictionary.pairBoost(prev, it.word, prev2) }.take(5)
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
        val reach = if (KeyboardPrefs.neural && lower.length >= 3) maxOf(limit, NN_REACH) else limit
        val nearAll = if (!fast && lower.length >= 2) candCodes.flatMap { KeyboardDictionary.correct(it, lower, reach, tc) } else emptyList()
        val corr = if (!fast && lower.length >= 2) (nearAll.filter { it.cost <= limit } + (if (tr) KeyboardDictionary.trCorrect(lower, 4, tc) else emptyList()) +
            (if (learned != null) listOf(KeyboardDictionary.Cand(learned, 40f, 0f)) else emptyList())).sortedByDescending { it.score + KeyboardDictionary.pairBoost(prev, it.word, prev2) }.distinctBy { it.word }.take(6) else if (fast && learned != null) listOf(KeyboardDictionary.Cand(learned, 40f, 0f)) else emptyList()
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
                if (s >= 9f && !KeyboardDictionary.isBanned(w) && KeyboardDictionary.rejectCount(lower, w) < 1) KeyboardDictionary.Cand(w, s, .6f) else null
            }
        }.maxByOrNull { it.score }
        // two words that run together (a key typed for the space among them) are split before any other mending
        val split = if (!fast && !valid && !stretched && lower.length >= 6) codes.firstNotNullOfOrNull { KeyboardDictionary.splitWords(it, lower) }?.let { KeyboardDictionary.Cand(it, 0f, .5f) } else null
        // the neural model (CharLm, trained on open Turkish and English text): every near word is weighed by how well it fits the sentence before it, the typing
        // cost (in bits, NN_LAM per unit) taken off; the typed word wins unless a near word beats it by a margin (a real word needs a bigger one).
        // It also reaches a missing or an extra letter ("mrhaba", "gelyorum"), which the old rule could not afford, and orders the bar by the sentence.
        val nnLang = KeyboardLayouts.dict(code)
        val lm = if (!fast && KeyboardPrefs.neural && lower.length >= 2 && !stretched && !keepCase) NeuralLm.get(this, nnLang) else null
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
            for (cd in pool) nnScore[cd.word] = weigh(cd.word, NN_LAM * cd.cost)
            for (cd in comp) if (cd.word != lower && cd.word !in nnScore) nnScore[cd.word] = weigh(cd.word, NN_COMP)
            val listed = codes.any { KeyboardDictionary.inLexicon(it, lower) }
            val best = pool.maxByOrNull { nnScore.getValue(it.word) }
            if (best != null) {
                val diacriticOnly = best.cost <= .15f * lower.length && best.word.length == lower.length
                val margin = (if (listed) NN_VALID else NN_UNKNOWN) + (if (lower.length <= 3 && !diacriticOnly) NN_SHORT else 0f) -
                    // the strength setting moves the margin: modest asks 2 bits more, aggressive 2 less, very aggressive 4 less
                    (KeyboardPrefs.autoLevel.coerceIn(0, 3) - 1) * 2f
                if ((lower.length >= 3 || diacriticOnly) && nnScore.getValue(best.word) > typedLm + margin) nnFix = best
            }
        }
        if (lm != null) NeuralLm.took((System.nanoTime() - nnT0) / 1_000_000L)
        val fix = swapFix ?: split ?: if (lm != null) (learned?.takeIf { it != lower }?.let { KeyboardDictionary.Cand(it, 40f, 0f) } ?: nnFix) else fix0
        val others = (comp.map { it.word } + corr.map { it.word } + nnScore.keys).distinct().filter { it != lower && it != fix?.word && !KeyboardDictionary.isBanned(it) }
            .let { o -> if (nnScore.isEmpty()) o else o.sortedByDescending { nnScore[it] ?: -1e9f } }
        val emoji = if (KeyboardPrefs.emojiPredictions) (KeyboardDictionary.emoji(lower) ?: EmojiData.forWord(lower)) else null
        val mid: String; val left: String; val right: String
        // Shift pressed while the word is open: the middle holds the capital-letter form (the one the correction would give, when there is one), and a tap puts it in
        val loc = lang.locale
        val capital = up && typed.isNotEmpty() && typed[0].isLetter() && typed[0].isLowerCase()
        fun capped(w: String) = if (capital) w.replaceFirstChar { it.titlecase(loc) } else w
        if (fix != null) { mid = capped(cased(fix.word, typed)); left = "\"$typed\""; right = emoji ?: others.firstOrNull()?.let { cased(it, typed) }.orEmpty() }
        else if (capital) { mid = capped(typed); left = "\"$typed\""; right = emoji ?: others.getOrNull(0)?.let { capped(cased(it, typed)) }.orEmpty() }
        else { mid = typed; left = others.getOrNull(0)?.let { cased(it, typed) }.orEmpty(); right = emoji ?: others.getOrNull(1)?.let { cased(it, typed) }.orEmpty() }
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
private const val NN_COMP = 3f
private const val NN_VALID = 8f
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
