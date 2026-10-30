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
    private var lastEmail: String? = null
    private class Correction(val original: String, val corrected: String, val sep: String)
    private var lastCorrection: Correction? = null
    /** The word as it stood before the user started deleting letters of it: if the corrected form ends as a real word, the first form was a typo. */
    private var peak: String? = null
    // a letter was typed right behind "word." with no space: the space and a capital are put in when the word ends, unless it is an address
    private var dotFix = false
    private val tlds = setOf("com", "net", "org", "edu", "gov", "io", "co", "me", "app", "dev", "info", "biz", "tr", "uk", "de", "fr", "es", "it", "ru", "us", "tv", "xyz", "online", "site", "tech", "store", "ai", "gg", "ly", "to", "cc", "eu", "nl", "pl", "jp", "cn", "br", "in", "au", "ca", "ch", "se", "no", "fi", "dk", "gen", "web", "html", "php")
    private var corrAt = 0L
    private var selStart = 0
    private var selEnd = 0
    private var shiftTap = 0L
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
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        // everything copied is kept (a password field's copy and a sensitive one are not), texts and pictures
        val clip = clipboard?.primaryClip?.takeIf { it.itemCount > 0 } ?: return@OnPrimaryClipChangedListener
        if (st.incognito || st.plain) return@OnPrimaryClipChangedListener
        if (android.os.Build.VERSION.SDK_INT >= 24 && clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return@OnPrimaryClipChangedListener
        val item = clip.getItemAt(0)
        val uri = item.uri
        if (uri != null && clip.description.hasMimeType("image/*")) {
            scope.launch(Dispatchers.IO) { val l = ClipStore.addImage(this@PulseKeyboardService, uri); withContext(Dispatchers.Main) { st.clipItems = l } }
        } else {
            val t = item.coerceToText(this)?.toString()?.trim()
            // a long copy used to skip the quick-paste slot entirely (nothing showed); it belongs there too, the bar label is cut short regardless of the length
            if (!t.isNullOrEmpty()) { quickClip = t; quickAt = SystemClock.uptimeMillis(); quickLeft = 2; updateSuggestions(); st.clipItems = ClipStore.addText(this, t); if (t.length <= 600) st.clips = (listOf(t) + st.clips.filter { it != t }).take(20) }
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
    override fun clipPin(id: Long) { st.clipItems = ClipStore.togglePin(this, id) }
    override fun clipDelete(ids: Set<Long>) { st.clipItems = ClipStore.delete(this, ids) }
    override fun clipClear() { st.clipItems = ClipStore.clear(this) }

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
        val now = System.currentTimeMillis() / 1000L
        val accounts = runCatching { com.umutk.pulse.features.AuthStore.load(this) }.getOrDefault(emptyList())
        st.authCodes = accounts.map { a ->
            val name = if (a.issuer.isBlank()) a.name else "${a.issuer} · ${a.name}"
            name to runCatching { com.umutk.pulse.features.Totp.code(com.umutk.pulse.features.Totp.base32Decode(a.secret), now, a.digits, a.period, a.algo) }.getOrDefault("------")
        }
        st.panel = "auth"
    }

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
        KeyboardPrefs.load(this)
        owner = StandByOwner().also { it.start() }
        st.lang = KeyboardPrefs.langList().first()
        scope.launch(Dispatchers.IO) { runCatching { EmojiData.index(this@PulseKeyboardService) } }
        st.recents = getSharedPreferences("pulse_keyboard", Context.MODE_PRIVATE).getString("recents", "")?.let { r -> if ('\n' in r) r.split('\n') else r.split(' ') }?.filter { it.isNotEmpty() } ?: emptyList()
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

    // ---- a screenshot just taken waits in the right slot of the bar, to be pasted from there --------------------------------------------------
    private var shotItem: ClipItem? = null
    private var shotAt = 0L
    private var shotId = -1L
    private val shotObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { scope.launch { delay(900); checkShot() } }
    }

    private suspend fun checkShot() {
        if (st.incognito) return
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val found = withContext(Dispatchers.IO) {
            runCatching {
                val cols = arrayOf(android.provider.MediaStore.Images.Media._ID, android.provider.MediaStore.Images.Media.DISPLAY_NAME, android.provider.MediaStore.Images.Media.DATE_ADDED, android.provider.MediaStore.Images.Media.DATA)
                // a "LIMIT" in the sort order is refused from Android 11 on (the whole lookup threw and nothing was ever found): the limit goes in a Bundle
                val q = android.os.Bundle().apply {
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(android.provider.MediaStore.Images.Media.DATE_ADDED))
                    putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 1)
                }
                (if (android.os.Build.VERSION.SDK_INT >= 26) contentResolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, q, null)
                else contentResolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols, null, null, android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 1"))?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    val name = c.getString(1).orEmpty(); val path = c.getString(3).orEmpty()
                    if (System.currentTimeMillis() / 1000 - c.getLong(2) > 8 || !(name.contains("screenshot", true) || path.contains("screenshot", true))) return@use null
                    c.getLong(0)
                }
            }.getOrNull()
        } ?: return
        if (found == shotId) return
        shotId = found
        val uri = android.net.Uri.withAppendedPath(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, found.toString())
        val items = withContext(Dispatchers.IO) { ClipStore.addImage(this@PulseKeyboardService, uri) }
        st.clipItems = items
        val item = items.filter { it.image != null }.maxByOrNull { it.time } ?: return
        shotItem = item; shotAt = SystemClock.uptimeMillis()
        st.shotThumb = withContext(Dispatchers.IO) { runCatching { android.graphics.BitmapFactory.decodeFile(item.image, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap() }.getOrNull() }
        updateSuggestions()
    }

    override fun onDestroy() {
        runCatching { recognizer?.destroy() }; recognizer = null
        clipboard?.removePrimaryClipChangedListener(clipListener)
        runCatching { contentResolver.unregisterContentObserver(shotObserver) }
        KeyboardDictionary.saveUser(this)
        owner?.stop(); scope.cancel()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        val o = owner ?: StandByOwner().also { it.start(); owner = it }
        // Compose finds its lifecycle on the root of the window, which for a keyboard is the window's decor view
        window.window?.decorView?.let { it.setViewTreeLifecycleOwner(o); it.setViewTreeSavedStateRegistryOwner(o) }
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(o); setViewTreeSavedStateRegistryOwner(o)
            setContent { KeyboardView(st, this@PulseKeyboardService) }
            // the bottom keys sit where the system's home swipe starts: the whole keyboard keeps its touches (Android 10+)
            if (android.os.Build.VERSION.SDK_INT >= 29) addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ -> v.systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, r - l, b - t)) }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        st.inline = emptyList(); st.emojiQuery = null
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
        noAuto = (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        st.plain = (cls != InputType.TYPE_CLASS_TEXT && cls != 0) ||
            v in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_FILTER)
        emailField = cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || v == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS); lastEmail = null
        uriField = cls == InputType.TYPE_CLASS_TEXT && v == InputType.TYPE_TEXT_VARIATION_URI
        nameField = cls == InputType.TYPE_CLASS_TEXT && v == InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        st.numpad = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        st.page = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME) 1 else 0
        st.panel = "keys"; st.toolbar = false
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
    }

    // a keyboard that is closed with the emoji (or clipboard, or tool) page up comes back as the plain keys, whichever way it is shown again
    private fun backToKeys() { st.panel = "keys"; st.emojiQuery = null; st.toolbar = false }
    override fun onFinishInputView(finishingInput: Boolean) { super.onFinishInputView(finishingInput); backToKeys() }
    override fun onWindowHidden() { super.onWindowHidden(); backToKeys() }
    override fun onWindowShown() { super.onWindowShown(); backToKeys() }

    override fun onFinishInput() {
        super.onFinishInput()
        st.inline = emptyList()
        lastEmail?.let { e -> getSharedPreferences("pulse_kb_emails", MODE_PRIVATE).let { p -> p.edit().putString("list", (listOf(e) + p.getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() && it != e }).take(5).joinToString("\n")).apply() } }
        lastEmail = null
        word.clear(); lastCorrection = null
        KeyboardDictionary.saveUser(this)
        Thread { runCatching { com.umutk.pulse.AutoBackup.maybeRun(applicationContext) } }.start()
    }

    private var editAt = 0L

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
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
        prevWord = before.dropLast(w.length).trimEnd().takeLastWhile { it.isLetter() || it == '\'' }.lowercase().ifEmpty { null }
        updateSuggestions()
    }

    // ---- typing ------------------------------------------------------------------------------------------------------------------------------------

    override fun type(s: String) {
        val c = currentInputConnection ?: return
        if (s.isEmpty()) return
        // the emoji search is open: the keys type into its box
        st.emojiQuery?.let { if (st.panel == "emoji") { st.emojiQuery = it + s; return } }
        st.clipName?.let { if (st.panel == "clip") { if (s.all { ch -> ch.isLetterOrDigit() }) st.clipName = it + s; return } }
        if (st.trans) { st.transIn += s; scheduleTrans(); return }
        if (s.codePointCount(0, s.length) > 1 || Character.isSurrogate(s[0])) { finishWord("", false); c.commitText(s, 1); afterText(); return }
        lastGlide = null
        if (s.length == 1 && (st.ctrl > 0 || st.alt > 0 || st.win > 0 || st.shiftMod > 0) && sendChord(s[0])) return
        var ch = s[0]
        val letter = ch.isLetter() || (ch == '\'' && word.isNotEmpty())
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
            if (punctTyped && KeyboardPrefs.autoSpace && !st.plain && ch.isLetter()) {
                c.commitText(" ", 1)
                // behind a full stop, ! or ? the next sentence starts with a capital
                if (dotEnd && KeyboardPrefs.autoCap && word.isEmpty()) ch = ch.toString().uppercase(lang.locale).first()
            }
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
            if (tp != null) st.letterRects[ch.lowercase()]?.let { r -> KeyboardDictionary.learnTouch(ch.lowercaseChar(), tp, r.center, r.width) }
            c.setComposingText(word, 1)
            lastCorrection = null
            if (st.shift == 1) st.shift = 0
            updateSuggestions()
        } else {
            // a word is only mended when the space key ends it: a period, comma or bracket leaves what was typed as it is
            // a full stop behind a word of the dictionary ends a sentence (not in addresses, passwords or numbers): the space and the capital come with the next letter
            val before = word.toString().lowercase(lang.locale)
            val dotOk = ch == '.' && before.length >= 2 && KeyboardDictionary.isKnown(before) && !st.plain && !emailField && !urlNow() && KeyboardPrefs.autoSpace
            if (!finishWord(s, false, s)) c.commitText(s, 1)
            punctTyped = ch in ",;!?:" || dotOk
            dotEnd = ch in "!?" || dotOk
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
        // a correction is only made to the word the bar shows in the middle for exactly this typed word; an old suggestion (the bar had not caught up with the last letters) is never used
        val t = target?.takeIf { targetFor.equals(typed, true) && slotWords.getOrNull(1).equals(it, true) }
        // "word.next" typed together: a space (and a capital) after the period, unless the next word is a domain ending, ends with a dot itself, or is no word at all
        var spaceBefore = false
        if (dotFix) {
            dotFix = false
            val lw = typed.lowercase(lang.locale)
            if (sep != "." && typed.length >= 2 && lw !in tlds && KeyboardDictionary.isKnown(lw)) spaceBefore = true
        }
        if (correct && KeyboardPrefs.autocorrect && !KeyboardPrefs.practicing && !st.plain && !urlNow() && (!noAuto || uriField) && t != null && !t.equals(typed, true) && (!KeyboardDictionary.isRejected(typed.lowercase(lang.locale), t.lowercase(lang.locale)) || (KeyboardDictionary.isSwap(typed.lowercase(lang.locale), t.lowercase(lang.locale)) && KeyboardDictionary.rejectCount(typed.lowercase(lang.locale), t.lowercase(lang.locale)) < 1))) out = t
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
        corrAt = SystemClock.uptimeMillis()
        learnFromDelete(out)
        if (out == typed && KeyboardPrefs.learn && !st.plain && !st.incognito) KeyboardDictionary.learn(this, prevWord2, prevWord, typed)
        prevWord2 = prevWord; prevWord = out
        word.clear(); target = null
        return true
    }

    private fun afterText() { updateCaps(); updateSuggestions() }

    override fun space() {
        val c = currentInputConnection ?: return
        captureEmail(c)
        st.emojiQuery?.let { if (st.panel == "emoji") { if (it.isNotEmpty() && !it.endsWith(" ")) st.emojiQuery = "$it "; return } }
        if (st.clipName != null && st.panel == "clip") return
        if (st.trans) { if (st.transIn.isNotEmpty() && !st.transIn.endsWith(" ")) st.transIn += " "; return }
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
        lastWasSpace = false
        lastGlide?.let { g ->
            val before = c.getTextBeforeCursor(g.length + 1, 0)?.toString()
            lastGlide = null
            if (before == "$g ") { c.deleteSurroundingText(g.length + 1, 0); afterText(); return }
        }
        // right after an automatic correction, Backspace takes it back: the word as it was typed opens again, and that correction is remembered as unwanted,
        // so the same word is not corrected the same way the next time
        val lc = lastCorrection
        if (lc != null && word.isEmpty() && c.getSelectedText(0).isNullOrEmpty()) {
            val n = lc.corrected.length + lc.sep.length
            if (c.getTextBeforeCursor(n, 0)?.toString() == lc.corrected + lc.sep) {
                lastCorrection = null
                if (!st.incognito) KeyboardDictionary.reject(this, lc.original, lc.corrected)
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
                prevWord = before.ifEmpty { null }?.lowercase()
                updateCaps(); updateSuggestions(); return
            }
        }
        if (c.getSelectedText(0)?.isNotEmpty() == true) c.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        // text was deleted that the remembered words may have belonged to: they are forgotten, so no old prediction is written back
        prevWord = null; prevWord2 = null; lastCorrection = null; lastGlide = null
        updateCaps(); updateSuggestions()
    }

    // ---- keys of the keyboard ----------------------------------------------------------------------------------------------------------------------

    override fun shift() {
        val now = SystemClock.uptimeMillis()
        st.shift = when (st.shift) { 0 -> { shiftTap = now; 1 }; 1 -> if (now - shiftTap < 450) 2 else 0; else -> 0 }
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
    override fun onInlineSuggestionsResponse(response: android.view.inputmethod.InlineSuggestionsResponse): Boolean {
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
                lastCorrection?.let { KeyboardDictionary.reject(this, it.original, it.corrected) }
                android.widget.Toast.makeText(this, com.umutk.pulse.tr("Saved \"$typed\" as a right word", "\"$typed\" doğru kelime olarak kaydedildi"), android.widget.Toast.LENGTH_SHORT).show()
                updateSuggestions()
            }
            return
        }
        if (w.isEmpty() || !w[0].isLetter() || st.slots.getOrNull(i)?.startsWith("\u0001") == true) return
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
        val qc = quickClip
        // the screenshot just taken: a tap pastes it (when the field takes pictures)
        if (i == 2 && word.isEmpty() && st.slots.getOrNull(2)?.startsWith("\u0002") == true) {
            shotItem?.let { pasteClip(it) }
            shotItem = null; st.shotThumb = null; updateSuggestions(); return
        }
        if (i == 2 && qc != null && word.isEmpty() && st.slots.getOrNull(2)?.startsWith("\u0001") == true) {
            currentInputConnection?.commitText(qc, 1)
            if (--quickLeft <= 0) quickClip = null
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
                c.beginBatchEdit(); c.deleteSurroundingText(n, 0); c.setComposingText(lcb.original, 1); c.endBatchEdit()
                word.append(lcb.original); target = null
            }
            updateSuggestions(); return
        }
        val w = slotWords.getOrNull(i).orEmpty()
        if (w.isEmpty()) return
        if (emailField) {
            val before = c.getTextBeforeCursor(100, 0)?.toString().orEmpty()
            if (w.startsWith("@") && '@' in before) c.deleteSurroundingText(before.length - before.lastIndexOf('@'), 0)
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
            if (isEmoji) { c.commitText("$typed $w ", 1) } else {
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
            "onehand" -> { KeyboardPrefs.oneHand = when (KeyboardPrefs.oneHand) { "off" -> "right"; "right" -> "left"; else -> "off" }; KeyboardPrefs.save(this) }
            "arrows" -> { KeyboardPrefs.arrows = !KeyboardPrefs.arrows; KeyboardPrefs.save(this) }
            "keyset" -> st.panel = if (st.panel == "keyset") "keys" else "keyset"
            "layout" -> st.panel = if (st.panel == "layout") "keys" else "layout"
            "numbers" -> { KeyboardPrefs.numberRow = !KeyboardPrefs.numberRow; KeyboardPrefs.save(this) }
            "proRow" -> { KeyboardPrefs.proRow = !KeyboardPrefs.proRow; KeyboardPrefs.save(this) }
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

    override fun emoji(e: String) {
        val c = currentInputConnection ?: return
        finishWord("", false)
        c.commitText(e, 1)
        st.recents = (listOf(e) + st.recents.filter { it != e }).take(30)
        getSharedPreferences("pulse_keyboard", Context.MODE_PRIVATE).edit().putString("recents", st.recents.joinToString("\n")).apply()
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
        c.sendKeyEvent(KeyEvent(t, t, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code, 0))
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
    }

    private fun cased(w: String, typed: String): String {
        // a word that is English and not Turkish is capitalised the English way (if -> If, not İf)
        val loc = if (lang.locale.language == "tr" && w.none { it in "çğıöşü" } && KeyboardDictionary.isWord("en", w) && !KeyboardDictionary.isWord("tr", w)) Locale.ENGLISH else lang.locale
        return when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> w.uppercase(loc)
            typed.isNotEmpty() && typed[0].isUpperCase() -> w.replaceFirstChar { it.titlecase(loc) }
            else -> KeyboardDictionary.form(w)
        }
    }

    private val domains = listOf("@gmail.com", "@outlook.com", "@hotmail.com", "@yahoo.com", "@icloud.com", "@yandex.com")
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
        }
    }

    private fun emailSuggestions() {
        val before = currentInputConnection?.getTextBeforeCursor(100, 0)?.toString().orEmpty()
        if (emailRe.matches(before)) lastEmail = before
        val list = when {
            before.isEmpty() -> getSharedPreferences("pulse_kb_emails", MODE_PRIVATE).getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() }.take(3)
            '@' in before -> domains.filter { it.startsWith("@" + before.substringAfterLast('@'), true) && it.length > before.substringAfterLast('@').length + 1 }.take(3)
            // the domains (@gmail.com ...) only after an "@" is typed; otherwise they would replace words that have nothing to do with e-mail
            else -> emptyList()
        }
        slotWords = arrayOf(list.getOrElse(0) { "" }, list.getOrElse(1) { "" }, list.getOrElse(2) { "" })
        st.slots = slotWords.toList()
    }

    /** With nothing being typed, the right slot of the bar holds the text copied last (two taps paste it, then it goes). */
    private fun showClipSlot() {
        // a screenshot that was just taken takes the right slot for two minutes
        if (shotItem != null && SystemClock.uptimeMillis() - shotAt > 120_000L) { shotItem = null; st.shotThumb = null }
        if (shotItem != null && word.isEmpty() && !emailField && !st.plain) {
            slotWords = arrayOf(slotWords[0], slotWords[1], "")
            st.slots = listOf(st.slots.getOrElse(0) { "" }, st.slots.getOrElse(1) { "" }, "\u0002")
            return
        }
        val q = quickClip ?: return
        // the copied text stays in the bar for two minutes; after that it is only in the clipboard
        if (SystemClock.uptimeMillis() - quickAt > 120_000L) { quickClip = null; return }
        if (word.isNotEmpty() || emailField || st.plain) return
        val label = "\u0001" + q.replace('\n', ' ').take(14) + if (q.length > 14) "…" else ""
        slotWords = arrayOf(slotWords[0], slotWords[1], q)
        st.slots = listOf(st.slots.getOrElse(0) { "" }, st.slots.getOrElse(1) { "" }, label)
    }

    private fun updateSuggestions() {
        job?.cancel()
        if (st.trans) scheduleTrans()
        if (emailField) { emailSuggestions(); return }
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
            slotWords = arrayOf("", "", ""); target = null
            st.slots = listOf("\"${shown.original}\"", shown.corrected, "")
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
        job = scope.launch {
            // first the completions alone (a prefix look-up, no waiting), then the same with the corrections
            if (typed.length >= 2) {
                val q = withContext(Dispatchers.Default) { compute(code, l.locale, typed, prev, up, prev2, tc, fast = true) }
                if (word.toString() == typed) { target = q.third; targetFor = typed; slotWords = q.second; st.slots = q.first }
            }
            val res = withContext(Dispatchers.Default) { compute(code, l.locale, typed, prev, up, prev2, tc) }
            if (word.toString() == typed) {
                // a shortcut of the clipboard: the name typed offers the whole text in the middle
                // saved e-mail addresses are offered only in e-mail fields (above) or after an "@", never while ordinary words are typed
                val sc = if (typed.length >= 2) KeyShortcuts.find(this@PulseKeyboardService, typed.lowercase(l.locale)) else null
                if (sc != null) { target = null; slotWords = arrayOf(typed, sc, res.second[2]); st.slots = listOf("\"$typed\"", sc, res.first[2]) }
                else { target = res.third; targetFor = typed; slotWords = res.second; st.slots = res.first }
                showClipSlot()
            }
        }
    }

    /** The three slots of the bar (shown and as words), and the word the space key would correct [typed] to. */
    private fun compute(code: String, loc: Locale, typed: String, prev: String?, up: Boolean, prev2: String? = null, tc: KeyboardDictionary.TouchCtx? = null, fast: Boolean = false): Triple<List<String>, Array<String>, String?> {
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
        val corr = if (!fast && lower.length >= 2) (candCodes.flatMap { KeyboardDictionary.correct(it, lower, limit, 5, tc) } + (if (tr) KeyboardDictionary.trCorrect(lower, 4, tc) else emptyList()) +
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
        val fix = swapFix ?: fix0 ?: if (!fast && !valid && !stretched && lower.length >= 6) codes.firstNotNullOfOrNull { KeyboardDictionary.splitWords(it, lower) }?.let { KeyboardDictionary.Cand(it, 0f, .5f) } else null
        val others = (comp.map { it.word } + corr.map { it.word }).distinct().filter { it != lower && it != fix?.word && !KeyboardDictionary.isBanned(it) }
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
